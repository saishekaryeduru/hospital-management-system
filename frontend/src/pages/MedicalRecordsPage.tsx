import { useCallback, useEffect, useState } from 'react';
import {
  api,
  session,
  type Doctor,
  type MedicalRecord,
  type Patient,
} from '../api/client';
import MedicalRecordForm from '../components/MedicalRecordForm';

const PAGE_SIZE = 20;

function formatVisit(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })} `
    + d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export default function MedicalRecordsPage() {
  const [records, setRecords] = useState<MedicalRecord[]>([]);
  const [patients, setPatients] = useState<Patient[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [query, setQuery] = useState('');
  const [patientFilter, setPatientFilter] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<MedicalRecord | null>(null);

  const role = session.role();
  const isAdmin = role === 'ADMIN';
  // The API restricts authoring to doctors and administrators; nurses
  // see the chart read-only, and receptionists never reach this page.
  const canAuthor = isAdmin || role === 'DOCTOR';

  const loadRecords = useCallback(async () => {
    try {
      const result = await api.listMedicalRecords(
        { patientId: patientFilter, q: query },
        page,
        PAGE_SIZE,
      );
      setRecords(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load medical records.');
    }
  }, [patientFilter, query, page]);

  const loadDirectories = useCallback(async () => {
    try {
      const [patientPage, doctorPage] = await Promise.all([
        api.listPatients('', 0, 100),
        api.listDoctors('', null, 0, 100),
      ]);
      setPatients(patientPage.content);
      setDoctors(doctorPage.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load patients and doctors.');
    }
  }, []);

  useEffect(() => {
    void loadDirectories();
  }, [loadDirectories]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

  async function remove(record: MedicalRecord) {
    if (!window.confirm(`Delete the medical record for ${record.patientName}?`)) return;
    try {
      await api.deleteMedicalRecord(record.id);
      await loadRecords();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete the medical record.');
    }
  }

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1>Medical Records <span className="muted small">({totalElements})</span></h1>
        {canAuthor && !showForm && patients.length > 0 && doctors.length > 0 && (
          <button onClick={() => { setEditing(null); setShowForm(true); }}>Add record</button>
        )}
      </div>

      <div className="row">
        <label style={{ maxWidth: 280 }}>
          Search diagnosis or complaint
          <input
            value={query}
            onChange={(e) => { setQuery(e.target.value); setPage(0); }}
            placeholder="angina, fracture, fever..."
          />
        </label>
        <label style={{ maxWidth: 240 }}>
          Patient
          <select
            value={patientFilter === null ? '' : String(patientFilter)}
            onChange={(e) => {
              setPatientFilter(e.target.value === '' ? null : Number(e.target.value));
              setPage(0);
            }}
          >
            <option value="">All patients</option>
            {patients.map((p) => (
              <option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className="error">{error}</p>}

      {showForm && (
        <MedicalRecordForm
          initial={editing ?? undefined}
          patients={patients}
          doctors={doctors}
          onCancel={() => { setShowForm(false); setEditing(null); }}
          onSubmit={async (payload) => {
            if (editing) {
              await api.updateMedicalRecord(editing.id, payload);
            } else {
              await api.createMedicalRecord(payload);
            }
            setShowForm(false);
            setEditing(null);
            await loadRecords();
          }}
        />
      )}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table>
          <thead>
            <tr>
              <th>Visit</th>
              <th>Patient</th>
              <th>Doctor</th>
              <th>Diagnosis</th>
              <th>Prescription</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {records.map((r) => (
              <tr key={r.id}>
                <td>
                  {formatVisit(r.visitAt)}
                  <div className="muted small">{r.chiefComplaint}</div>
                </td>
                <td>{r.patientName}</td>
                <td>Dr. {r.doctorName}<div className="muted small">{r.departmentName}</div></td>
                <td>
                  {r.diagnosis}
                  {r.notes && <div className="muted small">{r.notes}</div>}
                </td>
                <td>
                  {r.prescriptions.length === 0 && <span className="muted small">None</span>}
                  {r.prescriptions.map((p, i) => (
                    <div key={i} className="small">
                      {p.medication} {p.dosage} — {p.frequency}
                      {p.durationDays !== null && <> for {p.durationDays} days</>}
                      {p.instructions && <div className="muted">{p.instructions}</div>}
                    </div>
                  ))}
                </td>
                <td>
                  <div className="row">
                    {canAuthor && (
                      <button className="secondary" onClick={() => { setEditing(r); setShowForm(true); }}>
                        Edit
                      </button>
                    )}
                    {isAdmin && (
                      <button className="danger" onClick={() => void remove(r)}>Delete</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {records.length === 0 && (
              <tr><td colSpan={6} className="muted">No medical records found.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="row">
          <button className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span className="muted small">Page {page + 1} of {totalPages}</span>
          <button className="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}
