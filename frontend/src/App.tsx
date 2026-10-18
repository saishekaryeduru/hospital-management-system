import { useState } from 'react';
import { session } from './api/client';
import LoginPage from './pages/LoginPage';
import PatientsPage from './pages/PatientsPage';

export default function App() {
  const [loggedIn, setLoggedIn] = useState<boolean>(() => Boolean(session.token()));

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
      <main>
        <PatientsPage />
      </main>
    </div>
  );
}
