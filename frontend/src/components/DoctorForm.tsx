import { useState } from 'react';
import type { Department, Doctor, DoctorPayload } from '../api/client';

interface Props {
  initial?: Doctor;
  departments: Department[];
  onSubmit: (payload: DoctorPayload) => Promise<void>;
  onCancel: () => void;
}

export default function DoctorForm({ initial, departments, onSubmit, onCancel }: Props) {
  const [firstName, setFirstName] = useState(initial?.firstName ?? '');
  const [lastName, setLastName] = useState(initial?.lastName ?? '');
  const [specialization, setSpecialization] = useState(initial?.specialization ?? '');
  const [departmentId, setDepartmentId] = useState<number>(
    initial?.departmentId ?? departments[0]?.id ?? 0,
  );
  const [phone, setPhone] = useState(initial?.phone ?? '');
  const [email, setEmail] = useState(initial?.email ?? '');
  const [active, setActive] = useState(initial?.active ?? true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit({ firstName, lastName, specialization, departmentId, phone, email, active });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the doctor.');
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{initial ? 'Edit doctor' : 'Add doctor'}</h2>
      <div className="form-grid">
        <label>First name<input value={firstName} onChange={(e) => setFirstName(e.target.value)} required /></label>
        <label>Last name<input value={lastName} onChange={(e) => setLastName(e.target.value)} required /></label>
        <label>Specialization<input value={specialization} onChange={(e) => setSpecialization(e.target.value)} required /></label>
        <label>
          Department
          <select value={departmentId} onChange={(e) => setDepartmentId(Number(e.target.value))} required>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </label>
        <label>Phone<input value={phone} onChange={(e) => setPhone(e.target.value)} required /></label>
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>
          Status
          <select value={active ? 'active' : 'inactive'} onChange={(e) => setActive(e.target.value === 'active')}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </select>
        </label>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <button type="submit" disabled={busy}>{initial ? 'Save changes' : 'Add doctor'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
