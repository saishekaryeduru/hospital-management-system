# Hospital Management System

A full-stack platform for the day-to-day work of a small hospital: staff
sign in with role-based access, the front desk registers patients, and
clinical staff can find and update a patient record in seconds. Patient
registration landed first; the doctor directory and departments followed
as a separate, tested module, then appointments — scheduling, overlap
protection, and the visit lifecycle — as the next. Billing is on the
roadmap below rather than arriving as one large drop.

The design constraint that shapes everything here is that patient data
is sensitive. Access is not just authenticated, it is authorized per
role: who may read a record, who may change it, and who may delete it
are different questions, and the API answers them separately.

## Features (current)

- **Staff accounts & auth** — register/login with bcrypt-hashed passwords
  and stateless JWT sessions. Tokens carry the staff role, so
  authorization needs no database lookup per request.
- **Role-based access control** — four roles (ADMIN, DOCTOR, NURSE,
  RECEPTIONIST) with a deliberate permission split on patient records:
  every role can read; ADMIN and RECEPTIONIST register new patients;
  clinical roles can update records as part of care; only ADMIN can
  delete. The split is enforced in one security configuration and
  covered by integration tests (including the 403 cases).
- **Patient registration & records** — demographics, contact details,
  blood group, emergency contact, and a short allergies note, with
  server-side validation (including a date-of-birth that cannot be in
  the future) and per-field error details.
- **Search & pagination** — the patient list is paged (20 per page) and
  searchable from one box by name fragment or phone digits, which is
  how front-desk staff actually look someone up.
- **Departments** — the hospital's department structure, maintained by
  administrators only. Names are unique (case-insensitively), every
  department reports its doctor count, and a department that still has
  doctors assigned cannot be deleted — they must be reassigned first.
- **Doctor directory** — every doctor belongs to a department and has a
  specialization, contact details, and an active flag (inactive doctors
  stay listed for history but should not take new bookings). The
  directory is paged and searchable by name or specialization, with an
  optional department filter; both compose. Front desk and
  administrators maintain entries; every role can read the directory;
  only administrators can remove an entry.
- **Appointments** — the front desk books a patient against a doctor for
  a start time and a duration (30 minutes by default, 5–240 accepted).
  A booking that overlaps one of the doctor's live appointments is a
  409, not a double-booked clinic; back-to-back appointments are fine.
  Inactive doctors take no new bookings, bookings cannot be made in the
  past, and the day view lists one day's appointments in time order,
  composable with doctor, patient, and status filters. The lifecycle is
  enforced server-side: SCHEDULED → CHECKED_IN → COMPLETED, with
  CANCELLED and NO_SHOW as terminal outs — a booking cannot be completed
  before check-in, and cancelling frees the slot for rebooking.
- **Consistent API errors** — one JSON error shape everywhere, with
  per-field details on validation failures.
- **One-command run** — Docker Compose brings up PostgreSQL, the API,
  and the frontend together; no local Java, Node, or Postgres install
  needed.

## Tech stack

| Layer    | Technology |
|----------|------------|
| Backend  | Java 17, Spring Boot 3, Spring Web, Spring Data JPA, Spring Security |
| Auth     | JWT (JJWT), BCrypt, role-based authorization |
| Database | PostgreSQL (production), H2 (dev/test), Flyway migrations |
| Frontend | React 18, TypeScript, Vite |
| Tests    | JUnit 5, Spring MockMvc integration tests |

## Architecture

```
frontend/  React + TypeScript SPA (Vite dev server proxies /api -> :8081)
backend/   Spring Boot API
  ├── auth/        register/login, token issuing (role embedded)
  ├── security/    JWT filter, security config, current-user resolution
  ├── patient/     patient CRUD, search, pagination
  ├── department/  department CRUD with doctor counts and a guarded delete
  ├── doctor/      doctor directory CRUD, search, department filter
  ├── appointment/ appointment booking, overlap check, status lifecycle, day views
  ├── model/       JPA entities (User, Patient, Department, Doctor, Appointment) and enums (Role, Gender, BloodGroup, AppointmentStatus)
  ├── repository/  Spring Data repositories
  └── common/      ApiException + global exception handler (one error shape)
```

The backend is a layered monolith on purpose: controllers stay thin,
services own the rules, repositories are the only code that talks to
JPA. Each clinical/administrative module (patients, doctors,
appointments today; billing next) gets its own package rather than
growing a shared service layer.

## Design decisions

- **Authorization rules live in the security configuration, not in
  controllers.** The full patient-record permission matrix is readable
  in one place, and a controller cannot accidentally open an endpoint
  by forgetting a check. Method-level rules would scatter the matrix
  across the codebase.
- **The role travels in the JWT.** Authorization stays a pure function
  of the token, so no per-request user lookup is needed. The tradeoff
  is staleness — a role change takes effect at the next login — which
  is acceptable for staff accounts that an administrator provisions
  and retires deliberately.
- **Stable pagination.** The list sorts by last name, first name, then
  id. Without the final tie-breaker, two patients who share a name
  could swap pages (or vanish between pages) as records change.
- **One search box, three fields.** Front-desk lookup is by name or
  phone; the repository query matches a fragment against first name,
  last name, or phone instead of asking staff to pick a search mode.
- **Allergies are a short free-text note, for now.** A structured
  allergy record (substance, reaction, severity) belongs with the
  medical-record module; baking a half-model into the patient table
  now would be the wrong schema to migrate away from later.
- **A directory entry is not a login.** A doctor in the directory and
  a staff account with the DOCTOR role are separate records: the
  directory is who the hospital employs and how to reach them, the
  account is who may sign in. Appointments book against the directory
  entry, because the front desk books for every doctor, signed in or
  not; linking an entry to an account only becomes necessary if a
  doctor's view is ever scoped to their own schedule.
- **Guards return conflicts, not silent failures.** Deleting a
  department with doctors still assigned, reusing a department name,
  or adding a second doctor with the same email all return 409 with
  an explanation, because each would quietly corrupt the directory
  that appointments are booked against.
- **An appointment's end is derived, never stored.** The record keeps
  a start and a duration; the overlap check compares half-open
  intervals ([start, start + duration)), so back-to-back bookings
  share a boundary without colliding, and a reschedule can never
  leave a stored end disagreeing with the start. Only SCHEDULED and
  CHECKED_IN appointments block a calendar — a cancellation or a
  no-show frees the slot immediately, which is what the front desk
  expects when a patient calls to cancel and someone else books in.
- **Appointment days are UTC days.** Start times are instants, and a
  day view is the half-open UTC range for the requested date, so a
  booking at exactly midnight belongs to exactly one day and every
  client sees the same schedule. A multi-site hospital spanning time
  zones would key the view to the site's zone instead; for one site
  the UTC rule is the simpler contract to reason about.
- **One origin in every environment.** In development the Vite server
  proxies `/api` to the backend; in the Compose stack nginx does the
  same job. The frontend always calls relative paths, so there is no
  CORS configuration to keep in sync and no API URL baked into the
  bundle at build time.

## Running it locally

### Docker Compose (full stack)

```bash
cp .env.example .env   # set a real JWT_SECRET before anything non-local
docker compose up --build
```

Open `http://localhost:5174`. Compose starts PostgreSQL (with a health
check the backend waits on), the API on port 8081, and the frontend
served by nginx on port 5174. Ports differ from my Job Tracker project
so both stacks can run side by side. Data survives restarts in the
`db-data` volume; `docker compose down -v` removes it.

### Backend (manual)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # in-memory H2, no database to install
```

The API listens on `http://localhost:8081` in this mode (the dev
profile moves it off the default 8080, matching the Vite proxy). For a
PostgreSQL-backed run, drop the `dev` profile and set `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`.

### Frontend (manual)

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5174`, create a staff account, and register a
patient. The dev proxy expects the API on port 8081, which is where
both the Compose stack and the backend `dev` profile listen.

### Tests

```bash
cd backend && mvn test     # JUnit + MockMvc suite on the H2 test profile
cd frontend && npm run build   # type-check + production build
```

GitHub Actions runs both on every push and pull request, and also
builds the two Docker images so the Compose stack can't rot silently
(see `.github/workflows/ci.yml`).

## API overview

All endpoints except `/api/auth/*` require `Authorization: Bearer <token>`.

| Method | Endpoint | Who | Description |
|--------|----------|-----|-------------|
| POST | `/api/auth/register` | public | Create a staff account (with role), returns a JWT |
| POST | `/api/auth/login` | public | Log in, returns a JWT |
| GET | `/api/patients?q=asha&page=0&size=20` | all roles | List patients, paged; optional case-insensitive search over first name, last name, phone |
| GET | `/api/patients/{id}` | all roles | Get one patient |
| POST | `/api/patients` | ADMIN, RECEPTIONIST | Register a patient |
| PUT | `/api/patients/{id}` | ADMIN, RECEPTIONIST, DOCTOR, NURSE | Update a patient record |
| DELETE | `/api/patients/{id}` | ADMIN | Delete a patient record |
| GET | `/api/departments` | all roles | List departments in name order, each with its doctor count |
| POST | `/api/departments` | ADMIN | Create a department (name must be unique, case-insensitive) |
| PUT | `/api/departments/{id}` | ADMIN | Rename or redescribe a department |
| DELETE | `/api/departments/{id}` | ADMIN | Delete a department (409 while doctors are still assigned) |
| GET | `/api/doctors?q=cardio&departmentId=2&page=0&size=20` | all roles | List doctors, paged; optional search over name/specialization, composable with the department filter |
| GET | `/api/doctors/{id}` | all roles | Get one doctor |
| POST | `/api/doctors` | ADMIN, RECEPTIONIST | Add a doctor (department must exist, email must be unique) |
| PUT | `/api/doctors/{id}` | ADMIN, RECEPTIONIST | Update a doctor, including the active flag |
| DELETE | `/api/doctors/{id}` | ADMIN | Remove a doctor from the directory |
| GET | `/api/appointments?date=2026-11-02&doctorId=2&page=0&size=20` | all roles | List appointments, soonest first; optional UTC day view, doctor / patient / status filters |
| GET | `/api/appointments/{id}` | all roles | Get one appointment |
| POST | `/api/appointments` | ADMIN, RECEPTIONIST | Book an appointment (409 on overlap or inactive doctor, 400 on dangling patient/doctor or a past start) |
| PUT | `/api/appointments/{id}` | ADMIN, RECEPTIONIST | Reschedule or edit a SCHEDULED appointment |
| PATCH | `/api/appointments/{id}/status` | all roles | Move through the lifecycle: SCHEDULED → CHECKED_IN → COMPLETED, or CANCELLED / NO_SHOW |
| DELETE | `/api/appointments/{id}` | ADMIN | Delete an appointment record |

## Roadmap

- Medical records: visits, diagnoses, prescriptions (structured allergies)
- Billing: invoice per visit, payment status tracking
- Dashboard: today's appointments, admission counts, department load

## License

MIT — see [LICENSE](LICENSE). Copyright © 2026 Sai Shekar Yeduru.
