import { useState } from 'react';
import type { Appointment, AppointmentPayload, Doctor, Patient } from '../api/client';

interface Props {
  initial?: Appointment;
  patients: Patient[];
  doctors: Doctor[];
  onSubmit: (payload: AppointmentPayload) => Promise<void>;
  onCancel: () => void;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

// datetime-local inputs work in local time with no offset; the API speaks
// UTC instants, so both directions convert explicitly instead of letting
// a raw string cross the boundary in the wrong zone.
export function toLocalInputValue(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function defaultStart(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(9, 0, 0, 0);
  return toLocalInputValue(d.toISOString());
}

export default function AppointmentForm({ initial, patients, doctors, onSubmit, onCancel }: Props) {
  const [patientId, setPatientId] = useState<number>(initial?.patientId ?? patients[0]?.id ?? 0);
  const [doctorId, setDoctorId] = useState<number>(initial?.doctorId ?? doctors.find((d) => d.active)?.id ?? 0);
  const [startsAtLocal, setStartsAtLocal] = useState<string>(
    initial ? toLocalInputValue(initial.startsAt) : defaultStart(),
  );
  const [durationMinutes, setDurationMinutes] = useState<number>(initial?.durationMinutes ?? 30);
  const [reason, setReason] = useState(initial?.reason ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // An inactive doctor cannot take a new booking, but stays selectable
  // when editing one of their existing appointments so the form can be
  // saved without silently reassigning the visit.
  const bookableDoctors = doctors.filter((d) => d.active || d.id === initial?.doctorId);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit({
        patientId,
        doctorId,
        startsAt: new Date(startsAtLocal).toISOString(),
        durationMinutes,
        reason: reason.trim() ? reason.trim() : undefined,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the appointment.');
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{initial ? 'Edit appointment' : 'Book appointment'}</h2>
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
          Doctor
          <select value={doctorId} onChange={(e) => setDoctorId(Number(e.target.value))} required>
            {bookableDoctors.map((d) => (
              <option key={d.id} value={d.id}>
                Dr. {d.firstName} {d.lastName} — {d.departmentName}{d.active ? '' : ' (inactive)'}
              </option>
            ))}
          </select>
        </label>
        <label>
          Starts at
          <input
            type="datetime-local"
            value={startsAtLocal}
            onChange={(e) => setStartsAtLocal(e.target.value)}
            required
          />
        </label>
        <label>
          Duration (minutes)
          <input
            type="number"
            min={5}
            max={240}
            step={5}
            value={durationMinutes}
            onChange={(e) => setDurationMinutes(Number(e.target.value))}
            required
          />
        </label>
        <label>
          Reason
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={500}
            placeholder="Follow-up, annual physical, ..."
          />
        </label>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <button type="submit" disabled={busy}>{initial ? 'Save changes' : 'Book appointment'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
