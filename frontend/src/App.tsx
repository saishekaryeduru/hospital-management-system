import { useState } from 'react';
import { session } from './api/client';
import AppointmentsPage from './pages/AppointmentsPage';
import DoctorsPage from './pages/DoctorsPage';
import LoginPage from './pages/LoginPage';
import MedicalRecordsPage from './pages/MedicalRecordsPage';
import PatientsPage from './pages/PatientsPage';

type View = 'patients' | 'doctors' | 'appointments' | 'records';

export default function App() {
  const [loggedIn, setLoggedIn] = useState<boolean>(() => Boolean(session.token()));
  const [view, setView] = useState<View>('patients');

  if (!loggedIn) {
    return <LoginPage onLoggedIn={() => setLoggedIn(true)} />;
  }

  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Hospital Management</span>
        <div className="row">
          <span className="muted small">{session.role() ?? ''}</span>
          <button
            className="secondary"
            onClick={() => {
              session.clear();
              setLoggedIn(false);
            }}
          >
            Log out
          </button>
        </div>
      </header>
      <nav className="row" style={{ marginBottom: '0.5rem' }}>
        <button className={view === 'patients' ? undefined : 'secondary'} onClick={() => setView('patients')}>
          Patients
        </button>
        <button className={view === 'doctors' ? undefined : 'secondary'} onClick={() => setView('doctors')}>
          Doctors &amp; Departments
        </button>
        <button className={view === 'appointments' ? undefined : 'secondary'} onClick={() => setView('appointments')}>
          Appointments
        </button>
        {/* Medical records are clinical data: the API refuses them to
            receptionists, so the tab is not offered to that role at all. */}
        {session.role() !== 'RECEPTIONIST' && (
          <button className={view === 'records' ? undefined : 'secondary'} onClick={() => setView('records')}>
            Medical Records
          </button>
        )}
      </nav>
      <main>
        {view === 'patients' && <PatientsPage />}
        {view === 'doctors' && <DoctorsPage />}
        {view === 'appointments' && <AppointmentsPage />}
        {view === 'records' && <MedicalRecordsPage />}
      </main>
    </div>
  );
}
