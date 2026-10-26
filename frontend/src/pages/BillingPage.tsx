import { useCallback, useEffect, useState } from 'react';
import {
  api,
  formatInvoiceStatus,
  formatMoney,
  session,
  INVOICE_STATUSES,
  type Invoice,
  type InvoiceStatus,
  type Patient,
} from '../api/client';
import InvoiceForm from '../components/InvoiceForm';

const PAGE_SIZE = 20;

function formatIssued(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })} `
    + d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export default function BillingPage() {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [patients, setPatients] = useState<Patient[]>([]);
  const [patientFilter, setPatientFilter] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Invoice | null>(null);

  const isAdmin = session.role() === 'ADMIN';
  // Both roles that can reach this page (ADMIN, RECEPTIONIST) may
  // issue and settle invoices; only administrators may delete one.

  const loadInvoices = useCallback(async () => {
    try {
      const result = await api.listInvoices(
        { patientId: patientFilter, status: statusFilter },
        page,
        PAGE_SIZE,
      );
      setInvoices(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load invoices.');
    }
  }, [patientFilter, statusFilter, page]);

  const loadPatients = useCallback(async () => {
    try {
      const patientPage = await api.listPatients('', 0, 100);
      setPatients(patientPage.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load patients.');
    }
  }, []);

  useEffect(() => {
    void loadPatients();
  }, [loadPatients]);

  useEffect(() => {
    void loadInvoices();
  }, [loadInvoices]);

  async function remove(invoice: Invoice) {
    if (!window.confirm(`Delete the unpaid invoice for ${invoice.patientName}?`)) return;
    try {
      await api.deleteInvoice(invoice.id);
      await loadInvoices();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete the invoice.');
    }
  }

  async function settle(invoice: Invoice, status: InvoiceStatus) {
    const verb = status === 'PAID' ? 'mark as paid' : 'void';
    if (!window.confirm(`Really ${verb} the invoice for ${invoice.patientName} (${formatMoney(invoice.totalCents)})? This cannot be undone.`)) return;
    try {
      await api.updateInvoiceStatus(invoice.id, status);
      await loadInvoices();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update the invoice status.');
    }
  }

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1>Billing <span className="muted small">({totalElements})</span></h1>
        {!showForm && patients.length > 0 && (
          <button onClick={() => { setEditing(null); setShowForm(true); }}>New invoice</button>
        )}
      </div>

      <div className="row">
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
        <label style={{ maxWidth: 200 }}>
          Status
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value as InvoiceStatus | ''); setPage(0); }}
          >
            <option value="">All statuses</option>
            {INVOICE_STATUSES.map((s) => (
              <option key={s} value={s}>{formatInvoiceStatus(s)}</option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className="error">{error}</p>}

      {showForm && (
        <InvoiceForm
          initial={editing ?? undefined}
          patients={patients}
          onCancel={() => { setShowForm(false); setEditing(null); }}
          onSubmit={async (payload) => {
            if (editing) {
              await api.updateInvoice(editing.id, payload);
            } else {
              await api.createInvoice(payload);
            }
            setShowForm(false);
            setEditing(null);
            await loadInvoices();
          }}
        />
      )}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table>
          <thead>
            <tr>
              <th>Issued</th>
              <th>Patient</th>
              <th>Charges</th>
              <th>Total</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {invoices.map((invoice) => (
              <tr key={invoice.id}>
                <td>
                  {formatIssued(invoice.issuedAt)}
                  {invoice.appointmentId !== null && (
                    <div className="muted small">Visit #{invoice.appointmentId}</div>
                  )}
                  {invoice.notes && <div className="muted small">{invoice.notes}</div>}
                </td>
                <td>{invoice.patientName}</td>
                <td>
                  {invoice.lines.map((line, i) => (
                    <div key={i} className="small">
                      {line.description} x{line.quantity} — {formatMoney(line.lineTotalCents)}
                    </div>
                  ))}
                </td>
                <td>{formatMoney(invoice.totalCents)}</td>
                <td>
                  {formatInvoiceStatus(invoice.status)}
                  {invoice.paidAt && (
                    <div className="muted small">Paid {formatIssued(invoice.paidAt)}</div>
                  )}
                </td>
                <td>
                  <div className="row">
                    {invoice.status === 'UNPAID' && (
                      <>
                        <button className="secondary" onClick={() => { setEditing(invoice); setShowForm(true); }}>
                          Edit
                        </button>
                        <button className="secondary" onClick={() => void settle(invoice, 'PAID')}>
                          Mark paid
                        </button>
                        <button className="secondary" onClick={() => void settle(invoice, 'VOID')}>
                          Void
                        </button>
                        {isAdmin && (
                          <button className="danger" onClick={() => void remove(invoice)}>Delete</button>
                        )}
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {invoices.length === 0 && (
              <tr><td colSpan={6} className="muted">No invoices found.</td></tr>
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
