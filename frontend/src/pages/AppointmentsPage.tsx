import { useCallback, useEffect, useState } from 'react';
import {
  api,
  formatAppointmentStatus,
  session,
  type Appointment,
  type AppointmentStatus,
  type Doctor,
  type Patient,
} from '../api/client';
import AppointmentForm from '../components/AppointmentForm';

const PAGE_SIZE = 20;

function todayLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

function formatDay(iso: string): string {
  return new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric' });
}

// The lifecycle actions each status offers, mirroring the transitions the
// API enforces: check in first, then complete; cancel/no-show stay
// available until the visit resolves.
const NEXT_ACTIONS: Record<AppointmentStatus, { status: AppointmentStatus; label: string }[]> = {
  SCHEDULED: [
    { status: 'CHECKED_IN', label: 'Check in' },
    { status: 'NO_SHOW', label: 'No-show' },
    { status: 'CANCELLED', label: 'Cancel' },
  ],
  CHECKED_IN: [
    { status: 'COMPLETED', label: 'Complete' },
    { status: 'NO_SHOW', label: 'No-show' },
    { status: 'CANCELLED', label: 'Cancel' },
  ],
  COMPLETED: [],
  CANCELLED: [],
  NO_SHOW: [],
};

export default function AppointmentsPage() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [patients, setPatients] = useState<Patient[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [date, setDate] = useState(todayLocal());
  const [allDates, setAllDates] = useState(false);
  const [doctorFilter, setDoctorFilter] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<AppointmentStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Appointment | null>(null);

  const role = session.role();
  const isAdmin = role === 'ADMIN';
  const canBook = isAdmin || role === 'RECEPTIONIST';

  const loadAppointments = useCallback(async () => {
    try {
      // The day view is a UTC day on the API; the date picker supplies
      // the viewer's local date, which is the day the front desk means.
      const result = await api.listAppointments(
        {
          date: allDates ? undefined : new Date(`${date}T00:00:00`).toISOString().slice(0, 10),
          doctorId: doctorFilter,
          status: statusFilter,
        },
        page,
        PAGE_SIZE,
      );
      setAppointments(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load appointments.');
    }
  }, [date, allDates, doctorFilter, statusFilter, page]);

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
    void loadAppointments();
  }, [loadAppointments]);

  async function changeStatus(appointment: Appointment, status: AppointmentStatus) {
    if (status === 'CANCELLED'
        && !window.confirm(`Cancel the appointment for ${appointment.patientName}?`)) return;
    try {
      await api.updateAppointmentStatus(appointment.id, status);
      await loadAppointments();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update the appointment.');
    }
  }

  async function remove(appointment: Appointment) {
    if (!window.confirm(`Delete the appointment for ${appointment.patientName}?`)) return;
    try {
      await api.deleteAppointment(appointment.id);
      await loadAppointments();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete the appointment.');
    }
  }

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1>Appointments <span className="muted small">({totalElements})</span></h1>
        {canBook && !showForm && patients.length > 0 && doctors.some((d) => d.active) && (
          <button onClick={() => { setEditing(null); setShowForm(true); }}>Book appointment</button>
        )}
      </div>

      <div className="row">
        <label style={{ maxWidth: 200 }}>
          Day
          <input
            type="date"
            value={date}
            disabled={allDates}
            onChange={(e) => { setDate(e.target.value); setPage(0); }}
          />
        </label>
        <label style={{ display: 'flex', gap: '0.4rem', alignItems: 'center', fontWeight: 400 }}>
          <input
            type="checkbox"
            checked={allDates}
            onChange={(e) => { setAllDates(e.target.checked); setPage(0); }}
            style={{ width: 'auto', marginTop: 0 }}
          />
          All dates
        </label>
        <label style={{ maxWidth: 240 }}>
          Doctor
          <select
            value={doctorFilter === null ? '' : String(doctorFilter)}
            onChange={(e) => {
              setDoctorFilter(e.target.value === '' ? null : Number(e.target.value));
              setPage(0);
            }}
          >
            <option value="">All doctors</option>
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>Dr. {d.firstName} {d.lastName}</option>
            ))}
          </select>
        </label>
        <label style={{ maxWidth: 200 }}>
          Status
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value as AppointmentStatus | ''); setPage(0); }}
          >
            <option value="">Any status</option>
            {(['SCHEDULED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW'] as AppointmentStatus[])
              .map((s) => <option key={s} value={s}>{formatAppointmentStatus(s)}</option>)}
          </select>
        </label>
      </div>

      {error && <p className="error">{error}</p>}

      {canBook && patients.length === 0 && (
        <p className="muted">Register a patient first — appointments are booked against a patient record.</p>
      )}

      {showForm && (
        <AppointmentForm
          initial={editing ?? undefined}
          patients={patients}
          doctors={doctors}
          onCancel={() => { setShowForm(false); setEditing(null); }}
          onSubmit={async (payload) => {
            if (editing) {
              await api.updateAppointment(editing.id, payload);
            } else {
              await api.createAppointment(payload);
            }
            setShowForm(false);
            setEditing(null);
            await loadAppointments();
          }}
        />
      )}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Patient</th>
              <th>Doctor</th>
              <th>Reason</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {appointments.map((a) => (
              <tr key={a.id}>
                <td>
                  {allDates && <>{formatDay(a.startsAt)} </>}
                  {formatTime(a.startsAt)}
                  <div className="muted small">{a.durationMinutes} min</div>
                </td>
                <td>{a.patientName}</td>
                <td>Dr. {a.doctorName}<div className="muted small">{a.departmentName}</div></td>
                <td>{a.reason ?? ''}</td>
                <td>{formatAppointmentStatus(a.status)}</td>
                <td>
                  <div className="row">
                    {NEXT_ACTIONS[a.status].map((action) => (
                      <button
                        key={action.status}
                        className={action.status === 'CANCELLED' ? 'danger' : 'secondary'}
                        onClick={() => void changeStatus(a, action.status)}
                      >
                        {action.label}
                      </button>
                    ))}
                    {canBook && a.status === 'SCHEDULED' && (
                      <button className="secondary" onClick={() => { setEditing(a); setShowForm(true); }}>
                        Edit
                      </button>
                    )}
                    {isAdmin && (
                      <button className="danger" onClick={() => void remove(a)}>Delete</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {appointments.length === 0 && (
              <tr><td colSpan={6} className="muted">No appointments found.</td></tr>
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
