import { useCallback, useEffect, useState } from 'react';
import { api, session, type Department, type Doctor } from '../api/client';
import DepartmentForm from '../components/DepartmentForm';
import DoctorForm from '../components/DoctorForm';

const PAGE_SIZE = 20;

export default function DoctorsPage() {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [query, setQuery] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showDoctorForm, setShowDoctorForm] = useState(false);
  const [editingDoctor, setEditingDoctor] = useState<Doctor | null>(null);
  const [showDepartmentForm, setShowDepartmentForm] = useState(false);
  const [editingDepartment, setEditingDepartment] = useState<Department | null>(null);

  const role = session.role();
  const isAdmin = role === 'ADMIN';
  const canMaintainDoctors = isAdmin || role === 'RECEPTIONIST';

  const loadDepartments = useCallback(async () => {
    try {
      setDepartments(await api.listDepartments());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load departments.');
    }
  }, []);

  const loadDoctors = useCallback(async () => {
    try {
      const result = await api.listDoctors(query, departmentFilter, page, PAGE_SIZE);
      setDoctors(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load doctors.');
    }
  }, [query, departmentFilter, page]);

  useEffect(() => {
    void loadDepartments();
  }, [loadDepartments]);

  useEffect(() => {
    void loadDoctors();
  }, [loadDoctors]);

  async function reloadAll() {
    await loadDepartments();
    await loadDoctors();
  }

  async function removeDoctor(doctor: Doctor) {
    if (!window.confirm(`Remove Dr. ${doctor.firstName} ${doctor.lastName} from the directory?`)) return;
    try {
      await api.deleteDoctor(doctor.id);
      await reloadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove the doctor.');
    }
  }

  async function removeDepartment(department: Department) {
    if (!window.confirm(`Delete the ${department.name} department?`)) return;
    try {
      await api.deleteDepartment(department.id);
      if (departmentFilter === department.id) setDepartmentFilter(null);
      await reloadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete the department.');
    }
  }

  return (
    <div>
      <h1>Departments</h1>
      {isAdmin && !showDepartmentForm && (
        <div className="row">
          <button onClick={() => { setEditingDepartment(null); setShowDepartmentForm(true); }}>
            Add department
          </button>
        </div>
      )}
      {showDepartmentForm && (
        <DepartmentForm
          initial={editingDepartment ?? undefined}
          onCancel={() => { setShowDepartmentForm(false); setEditingDepartment(null); }}
          onSubmit={async (payload) => {
            if (editingDepartment) {
              await api.updateDepartment(editingDepartment.id, payload);
            } else {
              await api.createDepartment(payload);
            }
            setShowDepartmentForm(false);
            setEditingDepartment(null);
            await reloadAll();
          }}
        />
      )}
      <div className="grid">
        {departments.map((d) => (
          <div className="card" key={d.id} style={{ margin: 0 }}>
            <strong>{d.name}</strong>
            <div className="muted small">{d.description ?? ''}</div>
            <div className="small" style={{ marginTop: '0.4rem' }}>
              {d.doctorCount} {d.doctorCount === 1 ? 'doctor' : 'doctors'}
            </div>
            {isAdmin && (
              <div className="row" style={{ marginTop: '0.5rem' }}>
                <button
                  className="secondary"
                  onClick={() => { setEditingDepartment(d); setShowDepartmentForm(true); }}
                >
                  Edit
                </button>
                <button className="danger" onClick={() => void removeDepartment(d)}>Delete</button>
              </div>
            )}
          </div>
        ))}
        {departments.length === 0 && <p className="muted">No departments yet.</p>}
      </div>

      <div className="row" style={{ justifyContent: 'space-between', marginTop: '1.5rem' }}>
        <h1>Doctors <span className="muted small">({totalElements})</span></h1>
        {canMaintainDoctors && !showDoctorForm && departments.length > 0 && (
          <button onClick={() => { setEditingDoctor(null); setShowDoctorForm(true); }}>Add doctor</button>
        )}
      </div>

      <div className="row">
        <input
          placeholder="Search by name or specialization"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setPage(0); }}
          style={{ maxWidth: 320 }}
        />
        <select
          value={departmentFilter === null ? '' : String(departmentFilter)}
          onChange={(e) => {
            setDepartmentFilter(e.target.value === '' ? null : Number(e.target.value));
            setPage(0);
          }}
          style={{ maxWidth: 220 }}
        >
          <option value="">All departments</option>
          {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
      </div>

      {error && <p className="error">{error}</p>}

      {showDoctorForm && (
        <DoctorForm
          initial={editingDoctor ?? undefined}
          departments={departments}
          onCancel={() => { setShowDoctorForm(false); setEditingDoctor(null); }}
          onSubmit={async (payload) => {
            if (editingDoctor) {
              await api.updateDoctor(editingDoctor.id, payload);
            } else {
              await api.createDoctor(payload);
            }
            setShowDoctorForm(false);
            setEditingDoctor(null);
            await reloadAll();
          }}
        />
      )}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table>
          <thead>
            <tr>
              <th>Doctor</th>
              <th>Specialization</th>
              <th>Department</th>
              <th>Phone</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {doctors.map((d) => (
              <tr key={d.id}>
                <td>Dr. {d.firstName} {d.lastName}<div className="muted small">{d.email}</div></td>
                <td>{d.specialization}</td>
                <td>{d.departmentName}</td>
                <td>{d.phone}</td>
                <td>{d.active ? 'Active' : 'Inactive'}</td>
                <td>
                  <div className="row">
                    {canMaintainDoctors && (
                      <button className="secondary" onClick={() => { setEditingDoctor(d); setShowDoctorForm(true); }}>
                        Edit
                      </button>
                    )}
                    {isAdmin && (
                      <button className="danger" onClick={() => void removeDoctor(d)}>Delete</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {doctors.length === 0 && (
              <tr><td colSpan={6} className="muted">No doctors found.</td></tr>
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
