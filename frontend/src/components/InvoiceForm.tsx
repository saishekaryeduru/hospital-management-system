import { useState } from 'react';
import type {
  ChargeItemPayload,
  Invoice,
  InvoicePayload,
  Patient,
} from '../api/client';
import { formatMoney } from '../api/client';
import { toLocalInputValue } from './AppointmentForm';

interface Props {
  initial?: Invoice;
  patients: Patient[];
  onSubmit: (payload: InvoicePayload) => Promise<void>;
  onCancel: () => void;
}

interface ChargeRow {
  description: string;
  quantity: string;
  // The desk types dollars ("150.00"); the API speaks integer cents,
  // so the conversion happens once, here, at the form boundary.
  unitPrice: string;
}

const EMPTY_ROW: ChargeRow = { description: '', quantity: '1', unitPrice: '' };

function rowsFrom(initial?: Invoice): ChargeRow[] {
  if (!initial || initial.lines.length === 0) return [{ ...EMPTY_ROW }];
  return initial.lines.map((line) => ({
    description: line.description,
    quantity: String(line.quantity),
    unitPrice: (line.unitPriceCents / 100).toFixed(2),
  }));
}

export default function InvoiceForm({ initial, patients, onSubmit, onCancel }: Props) {
  const [patientId, setPatientId] = useState<number>(initial?.patientId ?? patients[0]?.id ?? 0);
  const [issuedAtLocal, setIssuedAtLocal] = useState<string>(
    initial ? toLocalInputValue(initial.issuedAt) : toLocalInputValue(new Date().toISOString()),
  );
  const [notes, setNotes] = useState(initial?.notes ?? '');
  const [rows, setRows] = useState<ChargeRow[]>(() => rowsFrom(initial));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function updateRow(index: number, patch: Partial<ChargeRow>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  const totalCents = rows.reduce((sum, row) => {
    const quantity = Number(row.quantity);
    const unitPriceCents = Math.round(Number(row.unitPrice) * 100);
    if (!Number.isFinite(quantity) || !Number.isFinite(unitPriceCents)) return sum;
    return sum + quantity * unitPriceCents;
  }, 0);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    // A row the desk left completely blank is not a charge line; a
    // partly filled row is sent and the API's field errors surface
    // here rather than the row being silently dropped.
    const lines: ChargeItemPayload[] = rows
      .filter((row) => row.description.trim() !== '' || row.unitPrice.trim() !== '')
      .map((row) => ({
        description: row.description.trim(),
        quantity: Number(row.quantity),
        unitPriceCents: Math.round(Number(row.unitPrice) * 100),
      }));
    try {
      await onSubmit({
        patientId,
        appointmentId: initial?.appointmentId ?? undefined,
        issuedAt: new Date(issuedAtLocal).toISOString(),
        notes: notes.trim() ? notes.trim() : undefined,
        lines,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the invoice.');
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{initial ? 'Edit invoice' : 'New invoice'}</h2>
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
          Issued at
          <input
            type="datetime-local"
            value={issuedAtLocal}
            onChange={(e) => setIssuedAtLocal(e.target.value)}
            required
          />
        </label>
        <label>
          Notes
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={1000}
            rows={3}
            placeholder="Payment terms, insurer reference..."
          />
        </label>
      </div>

      <h3>Charges</h3>
      {rows.map((row, index) => (
        <div className="form-grid" key={index} style={{ marginBottom: '0.5rem' }}>
          <label>
            Description
            <input
              value={row.description}
              onChange={(e) => updateRow(index, { description: e.target.value })}
              maxLength={200}
              placeholder="Consultation"
            />
          </label>
          <label>
            Quantity
            <input
              type="number"
              min={1}
              max={1000}
              value={row.quantity}
              onChange={(e) => updateRow(index, { quantity: e.target.value })}
            />
          </label>
          <label>
            Unit price ($)
            <input
              type="number"
              min={0}
              step="0.01"
              value={row.unitPrice}
              onChange={(e) => updateRow(index, { unitPrice: e.target.value })}
              placeholder="150.00"
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
        Add charge line
      </button>
      <p className="muted small" style={{ marginTop: '0.5rem' }}>
        Total: {formatMoney(totalCents)} (recalculated by the API from the saved lines)
      </p>

      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <button type="submit" disabled={busy}>{initial ? 'Save changes' : 'Issue invoice'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
