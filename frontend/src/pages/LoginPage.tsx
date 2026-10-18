import { useState } from 'react';
import { api, ROLES, session, type Role } from '../api/client';

export default function LoginPage({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<Role>('RECEPTIONIST');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const auth = mode === 'login'
        ? await api.login(email, password)
        : await api.register(email, password, displayName, role);
      session.save(auth);
      onLoggedIn();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="card">
        <h1>Hospital Management</h1>
        <p className="muted">Staff sign-in. Patient records are restricted by role.</p>
        <form onSubmit={submit}>
          {mode === 'register' && (
            <>
              <label>
                Display name
                <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
              </label>
              <label>
                Role
                <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
                  {ROLES.map((r) => (
                    <option key={r} value={r}>{r}</option>
                  ))}
                </select>
              </label>
            </>
          )}
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={mode === 'register' ? 8 : undefined}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <div className="row" style={{ marginTop: '0.75rem' }}>
            <button type="submit" disabled={busy}>{mode === 'login' ? 'Log in' : 'Register'}</button>
            <button
              type="button"
              className="secondary"
              onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
            >
              {mode === 'login' ? 'Create staff account' : 'Back to login'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
