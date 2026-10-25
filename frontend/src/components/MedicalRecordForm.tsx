import { useState } from 'react';
import type {
  Doctor,
  MedicalRecord,
  MedicalRecordPayload,
  Patient,
  PrescriptionPayload,
} from '../api/client';
import { toLocalInputValue } from './AppointmentForm';

interface Props {
  initial?: MedicalRecord;
  patients: Patient[];
  doctors: Doctor[];
  onSubmit: (payload: MedicalRecordPayload) => Promise<void>;
  onCancel: () => void;
}

interface PrescriptionRow {
  medication: string;
  dosage: string;
  frequency: string;
  durationDays: string;
  instructions: string;
}

const EMPTY_ROW: PrescriptionRow = {
  medication: '',
  dosage: '',
  frequency: '',
  durationDays: '',
  instructions: '',
};

function rowsFrom(initial?: MedicalRecord): PrescriptionRow[] {
  if (!initial || initial.prescriptions.length === 0) return [{ ...EMPTY_ROW }];
  return initial.prescriptions.map((p) => ({
    medication: p.medication,
    dosage: p.dosage,
    frequency: p.frequency,
    durationDays: p.durationDays === null ? '' : String(p.durationDays),
    instructions: p.instructions ?? '',
  }));
}

export default function MedicalRecordForm({ initial, patients, doctors, onSubmit, onCancel }: Props) {
  const [patientId, setPatientId] = useState<number>(initial?.patientId ?? patients[0]?.id ?? 0);
  const [doctorId, setDoctorId] = useState<number>(initial?.doctorId ?? doctors[0]?.id ?? 0);
  const [visitAtLocal, setVisitAtLocal] = useState<string>(
    initial ? toLocalInputValue(initial.visitAt) : toLocalInputValue(new Date().toISOString()),
  );
  const [chiefComplaint, setChiefComplaint] = useState(initial?.chiefComplaint ?? '');
  const [diagnosis, setDiagnosis] = useState(initial?.diagnosis ?? '');
  const [notes, setNotes] = useState(initial?.notes ?? '');
  const [rows, setRows] = useState<PrescriptionRow[]>(() => rowsFrom(initial));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function updateRow(index: number, patch: Partial<PrescriptionRow>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    // A row the clinician left completely blank is not a prescription
    // line; a partly filled row is sent and the API's field errors
    // surface here rather than the row being silently dropped.
    const prescriptions: PrescriptionPayload[] = rows
      .filter((row) => Object.values(row).some((value) => value.trim() !== ''))
      .map((row) => ({
        medication: row.medication.trim(),
        dosage: row.dosage.trim(),
        frequency: row.frequency.trim(),
        durationDays: row.durationDays.trim() ? Number(row.durationDays) : undefined,
        instructions: row.instructions.trim() ? row.instructions.trim() : undefined,
      }));
    try {
      await onSubmit({
        patientId,
        doctorId,
        appointmentId: initial?.appointmentId ?? undefined,
        visitAt: new Date(visitAtLocal).toISOString(),
        chiefComplaint: chiefComplaint.trim(),
        diagnosis: diagnosis.trim(),
        notes: notes.trim() ? notes.trim() : undefined,
        prescriptions,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the medical record.');
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{initial ? 'Edit medical record' : 'Add medical record'}</h2>
      <div className="form-grid">
        <label>
          Patient
          <select value={patientId} onChange={(e) => setPatientId(Number(e.target.value))} required>
            {patients.map((p) => (
              <option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>
            ))}
          </select>
        </label>
        <label>
          Attending doctor
          <select value={doctorId} onChange={(e) => setDoctorId(Number(e.target.value))} required>
            {/* Unlike booking, every doctor is selectable here: a record
                can document a past visit by a doctor who has since left. */}
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>
                Dr. {d.firstName} {d.lastName} — {d.departmentName}{d.active ? '' : ' (inactive)'}
              </option>
            ))}
          </select>
        </label>
        <label>
          Visit time
          <input
            type="datetime-local"
            value={visitAtLocal}
            onChange={(e) => setVisitAtLocal(e.target.value)}
            required
          />
        </label>
        <label>
          Chief complaint
          <input
            value={chiefComplaint}
            onChange={(e) => setChiefComplaint(e.target.value)}
            maxLength={500}
            placeholder="Chest discomfort on exertion"
            required
          />
        </label>
        <label>
          Diagnosis
          <input
            value={diagnosis}
            onChange={(e) => setDiagnosis(e.target.value)}
            maxLength={1000}
            placeholder="Stable angina, under observation"
            required
          />
        </label>
        <label>
          Notes
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={2000}
            rows={3}
            placeholder="Examination findings, follow-up plan..."
          />
        </label>
      </div>

      <h3>Prescription</h3>
      {rows.map((row, index) => (
        <div className="form-grid" key={index} style={{ marginBottom: '0.5rem' }}>
          <label>
            Medication
            <input
              value={row.medication}
              onChange={(e) => updateRow(index, { medication: e.target.value })}
              maxLength={120}
              placeholder="Aspirin"
            />
          </label>
          <label>
            Dosage
            <input
              value={row.dosage}
              onChange={(e) => updateRow(index, { dosage: e.target.value })}
              maxLength={80}
              placeholder="75 mg"
            />
          </label>
          <label>
            Frequency
            <input
              value={row.frequency}
              onChange={(e) => updateRow(index, { frequency: e.target.value })}
              maxLength={80}
              placeholder="Once daily"
            />
          </label>
          <label>
            Duration (days)
            <input
              type="number"
              min={1}
              max={365}
              value={row.durationDays}
              onChange={(e) => updateRow(index, { durationDays: e.target.value })}
            />
          </label>
          <label>
            Instructions
            <input
              value={row.instructions}
              onChange={(e) => updateRow(index, { instructions: e.target.value })}
              maxLength={300}
              placeholder="Take after food"
            />
          </label>
          <div className="row" style={{ alignSelf: 'end' }}>
            <button
              type="button"
              className="secondary"
              onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
            >
              Remove line
            </button>
          </div>
        </div>
      ))}
      <button
        type="button"
        className="secondary"
        onClick={() => setRows((current) => [...current, { ...EMPTY_ROW }])}
      >
        Add prescription line
      </button>

      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <button type="submit" disabled={busy}>{initial ? 'Save changes' : 'Add record'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
