import { useState } from 'react';
import type { Department, DepartmentPayload } from '../api/client';

interface Props {
  initial?: Department;
  onSubmit: (payload: DepartmentPayload) => Promise<void>;
  onCancel: () => void;
}

export default function DepartmentForm({ initial, onSubmit, onCancel }: Props) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit({ name, description: description || undefined });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the department.');
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{initial ? 'Edit department' : 'Add department'}</h2>
      <div className="form-grid">
        <label>Name<input value={name} onChange={(e) => setName(e.target.value)} required /></label>
        <label>Description<input value={description} onChange={(e) => setDescription(e.target.value)} /></label>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <button type="submit" disabled={busy}>{initial ? 'Save changes' : 'Add department'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
