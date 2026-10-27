import { useCallback, useEffect, useState } from 'react';
import { api, formatAllergySeverity, formatBloodGroup, session, type Patient } from '../api/client';
import PatientForm from '../components/PatientForm';

const PAGE_SIZE = 20;

export default function PatientsPage() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Patient | null>(null);

  const role = session.role();
  const canRegister = role === 'ADMIN' || role === 'RECEPTIONIST';
  const canEdit = canRegister || role === 'DOCTOR' || role === 'NURSE';

  const load = useCallback(async () => {
    try {
      const result = await api.listPatients(query, page, PAGE_SIZE);
      setPatients(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load patients.');
    }
  }, [query, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(patient: Patient) {
    if (!window.confirm(`Delete the record for ${patient.firstName} ${patient.lastName}?`)) return;
    try {
      await api.deletePatient(patient.id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete the patient.');
    }
  }

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1>Patients <span className="muted small">({totalElements})</span></h1>
        {canRegister && !showForm && (
          <button onClick={() => { setEditing(null); setShowForm(true); }}>Register patient</button>
        )}
      </div>

      <div className="row">
        <input
          placeholder="Search by name or phone"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setPage(0); }}
          style={{ maxWidth: 320 }}
        />
      </div>

      {error && <p className="error">{error}</p>}

      {showForm && (
        <PatientForm
          initial={editing ?? undefined}
          onCancel={() => { setShowForm(false); setEditing(null); }}
          onSubmit={async (payload) => {
            if (editing) {
              await api.updatePatient(editing.id, payload);
            } else {
              await api.createPatient(payload);
            }
            setShowForm(false);
            setEditing(null);
            await load();
          }}
        />
      )}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Date of birth</th>
              <th>Blood</th>
              <th>Phone</th>
              <th>Allergies</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {patients.map((p) => (
              <tr key={p.id}>
                <td>{p.firstName} {p.lastName}<div className="muted small">{p.email ?? ''}</div></td>
                <td>{p.dateOfBirth}</td>
                <td>{formatBloodGroup(p.bloodGroup)}</td>
                <td>{p.phone}</td>
                <td>
                  {p.allergies.length === 0
                    ? '—'
                    : p.allergies
                        .map((a) => `${a.substance} (${formatAllergySeverity(a.severity)})`)
                        .join(', ')}
                </td>
                <td>
                  <div className="row">
                    {canEdit && (
                      <button className="secondary" onClick={() => { setEditing(p); setShowForm(true); }}>
                        Edit
                      </button>
                    )}
                    {role === 'ADMIN' && (
                      <button className="danger" onClick={() => void remove(p)}>Delete</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {patients.length === 0 && (
              <tr><td colSpan={6} className="muted">No patients found.</td></tr>
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
