// Small typed client for the Hospital Management API.
// The JWT lives in localStorage; every call attaches it when present.

export type Role = 'ADMIN' | 'DOCTOR' | 'NURSE' | 'RECEPTIONIST';
export const ROLES: Role[] = ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST'];

export type Gender = 'FEMALE' | 'MALE' | 'OTHER' | 'UNSPECIFIED';
export const GENDERS: Gender[] = ['FEMALE', 'MALE', 'OTHER', 'UNSPECIFIED'];

export type BloodGroup =
  | 'A_POSITIVE' | 'A_NEGATIVE'
  | 'B_POSITIVE' | 'B_NEGATIVE'
  | 'AB_POSITIVE' | 'AB_NEGATIVE'
  | 'O_POSITIVE' | 'O_NEGATIVE'
  | 'UNKNOWN';
export const BLOOD_GROUPS: BloodGroup[] = [
  'A_POSITIVE', 'A_NEGATIVE', 'B_POSITIVE', 'B_NEGATIVE',
  'AB_POSITIVE', 'AB_NEGATIVE', 'O_POSITIVE', 'O_NEGATIVE', 'UNKNOWN',
];

export function formatBloodGroup(group: BloodGroup): string {
  return group.replace('_POSITIVE', '+').replace('_NEGATIVE', '-');
}

export interface Patient {
  id: number;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: Gender;
  phone: string;
  email: string | null;
  bloodGroup: BloodGroup;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  allergies: string | null;
}

export interface PatientPayload {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: Gender;
  phone: string;
  email?: string;
  bloodGroup: BloodGroup;
  address?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  allergies?: string;
}

export interface PatientPage {
  content: Patient[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Department {
  id: number;
  name: string;
  description: string | null;
  doctorCount: number;
}

export interface DepartmentPayload {
  name: string;
  description?: string;
}

export interface Doctor {
  id: number;
  firstName: string;
  lastName: string;
  specialization: string;
  departmentId: number;
  departmentName: string;
  phone: string;
  email: string;
  active: boolean;
}

export interface DoctorPayload {
  firstName: string;
  lastName: string;
  specialization: string;
  departmentId: number;
  phone: string;
  email: string;
  active: boolean;
}

export interface DoctorPage {
  content: Doctor[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type AppointmentStatus = 'SCHEDULED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export const APPOINTMENT_STATUSES: AppointmentStatus[] = [
  'SCHEDULED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW',
];

export function formatAppointmentStatus(status: AppointmentStatus): string {
  return status
    .toLowerCase()
    .replace(/(^|_)(\w)/g, (_match, _sep: string, ch: string) => ` ${ch.toUpperCase()}`)
    .trim();
}

export interface Appointment {
  id: number;
  patientId: number;
  patientName: string;
  doctorId: number;
  doctorName: string;
  departmentId: number;
  departmentName: string;
  startsAt: string;
  durationMinutes: number;
  endsAt: string;
  reason: string | null;
  status: AppointmentStatus;
}

export interface AppointmentPayload {
  patientId: number;
  doctorId: number;
  startsAt: string;
  durationMinutes?: number;
  reason?: string;
}

export interface AppointmentPage {
  content: Appointment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Prescription {
  medication: string;
  dosage: string;
  frequency: string;
  durationDays: number | null;
  instructions: string | null;
}

export interface PrescriptionPayload {
  medication: string;
  dosage: string;
  frequency: string;
  durationDays?: number;
  instructions?: string;
}

export interface MedicalRecord {
  id: number;
  patientId: number;
  patientName: string;
  doctorId: number;
  doctorName: string;
  departmentId: number;
  departmentName: string;
  appointmentId: number | null;
  visitAt: string;
  chiefComplaint: string;
  diagnosis: string;
  notes: string | null;
  prescriptions: Prescription[];
}

export interface MedicalRecordPayload {
  patientId: number;
  doctorId: number;
  appointmentId?: number;
  visitAt: string;
  chiefComplaint: string;
  diagnosis: string;
  notes?: string;
  prescriptions: PrescriptionPayload[];
}

export interface MedicalRecordPage {
  content: MedicalRecord[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuthResponse {
  token: string;
  email: string;
  displayName: string;
  role: Role;
}

const TOKEN_KEY = 'hospital.token';
const ROLE_KEY = 'hospital.role';

export const session = {
  token: () => localStorage.getItem(TOKEN_KEY),
  role: () => localStorage.getItem(ROLE_KEY) as Role | null,
  save: (auth: AuthResponse) => {
    localStorage.setItem(TOKEN_KEY, auth.token);
    localStorage.setItem(ROLE_KEY, auth.role);
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
  },
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = session.token();
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(path, { ...init, headers: { ...headers, ...init.headers } });
  if (response.status === 401) {
    session.clear();
    throw new Error('Session expired — please log in again.');
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      /* keep the default message */
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  register: (email: string, password: string, displayName: string, role: Role) =>
    request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName, role }),
    }),

  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  listPatients: (query = '', page = 0, size = 20) => {
    const search = new URLSearchParams();
    if (query.trim()) search.set('q', query.trim());
    search.set('page', String(page));
    search.set('size', String(size));
    return request<PatientPage>(`/api/patients?${search.toString()}`);
  },

  createPatient: (payload: PatientPayload) =>
    request<Patient>('/api/patients', { method: 'POST', body: JSON.stringify(payload) }),

  updatePatient: (id: number, payload: PatientPayload) =>
    request<Patient>(`/api/patients/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  deletePatient: (id: number) =>
    request<void>(`/api/patients/${id}`, { method: 'DELETE' }),

  listDepartments: () => request<Department[]>('/api/departments'),

  createDepartment: (payload: DepartmentPayload) =>
    request<Department>('/api/departments', { method: 'POST', body: JSON.stringify(payload) }),

  updateDepartment: (id: number, payload: DepartmentPayload) =>
    request<Department>(`/api/departments/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  deleteDepartment: (id: number) =>
    request<void>(`/api/departments/${id}`, { method: 'DELETE' }),

  listDoctors: (query = '', departmentId: number | null = null, page = 0, size = 20) => {
    const search = new URLSearchParams();
    if (query.trim()) search.set('q', query.trim());
    if (departmentId !== null) search.set('departmentId', String(departmentId));
    search.set('page', String(page));
    search.set('size', String(size));
    return request<DoctorPage>(`/api/doctors?${search.toString()}`);
  },

  createDoctor: (payload: DoctorPayload) =>
    request<Doctor>('/api/doctors', { method: 'POST', body: JSON.stringify(payload) }),

  updateDoctor: (id: number, payload: DoctorPayload) =>
    request<Doctor>(`/api/doctors/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  deleteDoctor: (id: number) =>
    request<void>(`/api/doctors/${id}`, { method: 'DELETE' }),

  listAppointments: (
    filters: { date?: string; doctorId?: number | null; patientId?: number | null; status?: AppointmentStatus | '' } = {},
    page = 0,
    size = 20,
  ) => {
    const search = new URLSearchParams();
    if (filters.date) search.set('date', filters.date);
    if (filters.doctorId != null) search.set('doctorId', String(filters.doctorId));
    if (filters.patientId != null) search.set('patientId', String(filters.patientId));
    if (filters.status) search.set('status', filters.status);
    search.set('page', String(page));
    search.set('size', String(size));
    return request<AppointmentPage>(`/api/appointments?${search.toString()}`);
  },

  createAppointment: (payload: AppointmentPayload) =>
    request<Appointment>('/api/appointments', { method: 'POST', body: JSON.stringify(payload) }),

  updateAppointment: (id: number, payload: AppointmentPayload) =>
    request<Appointment>(`/api/appointments/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  updateAppointmentStatus: (id: number, status: AppointmentStatus) =>
    request<Appointment>(`/api/appointments/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  deleteAppointment: (id: number) =>
    request<void>(`/api/appointments/${id}`, { method: 'DELETE' }),

  listMedicalRecords: (
    filters: { patientId?: number | null; doctorId?: number | null; q?: string } = {},
    page = 0,
    size = 20,
  ) => {
    const search = new URLSearchParams();
    if (filters.patientId != null) search.set('patientId', String(filters.patientId));
    if (filters.doctorId != null) search.set('doctorId', String(filters.doctorId));
    if (filters.q?.trim()) search.set('q', filters.q.trim());
    search.set('page', String(page));
    search.set('size', String(size));
    return request<MedicalRecordPage>(`/api/medical-records?${search.toString()}`);
  },

  createMedicalRecord: (payload: MedicalRecordPayload) =>
    request<MedicalRecord>('/api/medical-records', { method: 'POST', body: JSON.stringify(payload) }),

  updateMedicalRecord: (id: number, payload: MedicalRecordPayload) =>
    request<MedicalRecord>(`/api/medical-records/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  deleteMedicalRecord: (id: number) =>
    request<void>(`/api/medical-records/${id}`, { method: 'DELETE' }),
};
