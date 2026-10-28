import { useCallback, useEffect, useState } from 'react';
import {
  api,
  formatAppointmentStatus,
  APPOINTMENT_STATUSES,
  type Dashboard,
} from '../api/client';

function todayLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

function formatBooked(minutes: number): string {
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

export default function DashboardPage() {
  const [date, setDate] = useState(todayLocal());
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setDashboard(await api.getDashboard(date));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the dashboard.');
    }
  }, [date]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>Dashboard</h2>
        <label>
          Day{' '}
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
      </div>
      {error && <p className="error">{error}</p>}
      {dashboard && (
        <>
          <div className="grid">
            <div className="card">
              <div className="muted small">Appointments</div>
              <strong style={{ fontSize: '1.6rem' }}>{dashboard.appointmentsTotal}</strong>
              <div className="muted small">
                {APPOINTMENT_STATUSES.map(
                  (s) => `${formatAppointmentStatus(s)}: ${dashboard.appointmentsByStatus[s] ?? 0}`,
                ).join(' · ')}
              </div>
            </div>
            <div className="card">
              <div className="muted small">Checked in now</div>
              <strong style={{ fontSize: '1.6rem' }}>
                {dashboard.appointmentsByStatus.CHECKED_IN ?? 0}
              </strong>
              <div className="muted small">patients currently in a visit</div>
            </div>
            <div className="card">
              <div className="muted small">Completed</div>
              <strong style={{ fontSize: '1.6rem' }}>
                {dashboard.appointmentsByStatus.COMPLETED ?? 0}
              </strong>
              <div className="muted small">visits finished this day</div>
            </div>
            <div className="card">
              <div className="muted small">Patients registered</div>
              <strong style={{ fontSize: '1.6rem' }}>{dashboard.patientsRegistered}</strong>
              <div className="muted small">{dashboard.patientsTotal} patients on record</div>
            </div>
          </div>

          <div className="card">
            <h3>Department load</h3>
            {dashboard.departmentLoad.length === 0 ? (
              <p className="muted">No departments yet.</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Department</th>
                    <th>Doctors (active / total)</th>
                    <th>Appointments</th>
                    <th>Completed</th>
                    <th>Booked time</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.departmentLoad.map((loadEntry) => (
                    <tr key={loadEntry.departmentId}>
                      <td>{loadEntry.departmentName}</td>
                      <td>
                        {loadEntry.activeDoctorCount} / {loadEntry.doctorCount}
                      </td>
                      <td>{loadEntry.appointments}</td>
                      <td>{loadEntry.completed}</td>
                      <td>{formatBooked(loadEntry.bookedMinutes)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div className="card">
            <h3>Appointments for the day</h3>
            {dashboard.appointments.length === 0 ? (
              <p className="muted">No appointments on this day.</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Patient</th>
                    <th>Doctor</th>
                    <th>Department</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.appointments.map((a) => (
                    <tr key={a.id}>
                      <td>{formatTime(a.startsAt)}</td>
                      <td>{a.patientName}</td>
                      <td>{a.doctorName}</td>
                      <td>{a.departmentName}</td>
                      <td>{formatAppointmentStatus(a.status)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </div>
  );
}
