import { useState } from 'react';
import {
  BLOOD_GROUPS,
  GENDERS,
  formatBloodGroup,
  type BloodGroup,
  type Gender,
  type Patient,
  type PatientPayload,
} from '../api/client';

interface Props {
  initial?: Patient;
  onSubmit: (payload: PatientPayload) => Promise<void>;
  onCancel: () => void;
}

export default function PatientForm({ initial, onSubmit, onCancel }: Props) {
  const [firstName, setFirstName] = useState(initial?.firstName ?? '');
  const [lastName, setLastName] = useState(initial?.lastName ?? '');
  const [dateOfBirth, setDateOfBirth] = useState(initial?.dateOfBirth ?? '');
  const [gender, setGender] = useState<Gender>(initial?.gender ?? 'UNSPECIFIED');
  const [phone, setPhone] = useState(initial?.phone ?? '');
  const [email, setEmail] = useState(initial?.email ?? '');
  const [bloodGroup, setBloodGroup] = useState<BloodGroup>(initial?.bloodGroup ?? 'UNKNOWN');
  const [address, setAddress] = useState(initial?.address ?? '');
  const [emergencyContactName, setEmergencyContactName] = useState(initial?.emergencyContactName ?? '');
  const [emergencyContactPhone, setEmergencyContactPhone] = useState(initial?.emergencyContactPhone ?? '');
  const [allergies, setAllergies] = useState(initial?.allergies ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit({
        firstName,
        lastName,
        dateOfBirth,
        gender,
        phone,
        email: email || undefined,
        bloodGroup,
        address: address || undefined,
        emergencyContactName: emergencyContactName || undefined,
        emergencyContactPhone: emergencyContactPhone || undefined,
        allergies: allergies || undefined,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the patient.');
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{initial ? 'Edit patient' : 'Register patient'}</h2>
      <div className="form-grid">
        <label>First name<input value={firstName} onChange={(e) => setFirstName(e.target.value)} required /></label>
        <label>Last name<input value={lastName} onChange={(e) => setLastName(e.target.value)} required /></label>
        <label>
          Date of birth
          <input type="date" value={dateOfBirth} onChange={(e) => setDateOfBirth(e.target.value)} required />
        </label>
        <label>
          Gender
          <select value={gender} onChange={(e) => setGender(e.target.value as Gender)}>
            {GENDERS.map((g) => <option key={g} value={g}>{g}</option>)}
          </select>
        </label>
        <label>Phone<input value={phone} onChange={(e) => setPhone(e.target.value)} required /></label>
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></label>
        <label>
          Blood group
          <select value={bloodGroup} onChange={(e) => setBloodGroup(e.target.value as BloodGroup)}>
            {BLOOD_GROUPS.map((b) => <option key={b} value={b}>{formatBloodGroup(b)}</option>)}
          </select>
        </label>
        <label>Address<input value={address} onChange={(e) => setAddress(e.target.value)} /></label>
        <label>
          Emergency contact
          <input value={emergencyContactName} onChange={(e) => setEmergencyContactName(e.target.value)} />
        </label>
        <label>
          Emergency phone
          <input value={emergencyContactPhone} onChange={(e) => setEmergencyContactPhone(e.target.value)} />
        </label>
        <label>Allergies<input value={allergies} onChange={(e) => setAllergies(e.target.value)} /></label>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <button type="submit" disabled={busy}>{initial ? 'Save changes' : 'Register patient'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
