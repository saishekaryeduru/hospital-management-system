import { useState } from 'react';
import {
  ALLERGY_SEVERITIES,
  BLOOD_GROUPS,
  GENDERS,
  formatAllergySeverity,
  formatBloodGroup,
  type AllergyPayload,
  type AllergySeverity,
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

interface AllergyRow {
  substance: string;
  reaction: string;
  severity: AllergySeverity;
}

const EMPTY_ROW: AllergyRow = { substance: '', reaction: '', severity: 'UNKNOWN' };

function rowsFrom(initial?: Patient): AllergyRow[] {
  if (!initial || initial.allergies.length === 0) return [{ ...EMPTY_ROW }];
  return initial.allergies.map((allergy) => ({
    substance: allergy.substance,
    reaction: allergy.reaction ?? '',
    severity: allergy.severity,
  }));
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
  const [rows, setRows] = useState<AllergyRow[]>(() => rowsFrom(initial));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function updateRow(index: number, patch: Partial<AllergyRow>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    // A row left completely blank is not an allergy entry; a partly
    // filled row is sent and the API's field errors surface here
    // rather than the row being silently dropped.
    const allergies: AllergyPayload[] = rows
      .filter((row) => row.substance.trim() !== '' || row.reaction.trim() !== '')
      .map((row) => ({
        substance: row.substance.trim(),
        reaction: row.reaction.trim() ? row.reaction.trim() : undefined,
        severity: row.severity,
      }));
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
        allergies,
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
      </div>

      <h3>Allergies</h3>
      {rows.map((row, index) => (
        <div className="form-grid" key={index} style={{ marginBottom: '0.5rem' }}>
          <label>
            Substance
            <input
              value={row.substance}
              onChange={(e) => updateRow(index, { substance: e.target.value })}
              maxLength={120}
              placeholder="Penicillin"
            />
          </label>
          <label>
            Reaction
            <input
              value={row.reaction}
              onChange={(e) => updateRow(index, { reaction: e.target.value })}
              maxLength={200}
              placeholder="Hives"
            />
          </label>
          <label>
            Severity
            <select
              value={row.severity}
              onChange={(e) => updateRow(index, { severity: e.target.value as AllergySeverity })}
            >
              {ALLERGY_SEVERITIES.map((s) => (
                <option key={s} value={s}>{formatAllergySeverity(s)}</option>
              ))}
            </select>
          </label>
          <div className="row" style={{ alignSelf: 'end' }}>
            <button
              type="button"
              className="secondary"
              onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
            >
              Remove allergy
            </button>
          </div>
        </div>
      ))}
      <button
        type="button"
        className="secondary"
        onClick={() => setRows((current) => [...current, { ...EMPTY_ROW }])}
      >
        Add allergy
      </button>

      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <button type="submit" disabled={busy}>{initial ? 'Save changes' : 'Register patient'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
