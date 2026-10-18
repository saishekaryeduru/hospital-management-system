# Hospital Management System

A full-stack platform for the day-to-day work of a small hospital: staff
sign in with role-based access, the front desk registers patients, and
clinical staff can find and update a patient record in seconds. Patient
registration is the first module; appointments, doctors, and billing are
on the roadmap below and will land as separate, tested modules rather
than one large drop.

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
  ├── model/       JPA entities (User, Patient) and enums (Role, Gender, BloodGroup)
  ├── repository/  Spring Data repositories
  └── common/      ApiException + global exception handler (one error shape)
```

The backend is a layered monolith on purpose: controllers stay thin,
services own the rules, repositories are the only code that talks to
JPA. Each clinical/administrative module (patients today; appointments,
billing next) gets its own package rather than growing a shared
service layer.

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

## Roadmap

- Doctor directory and department management
- Appointments: scheduling against a doctor, status lifecycle, day views
- Medical records: visits, diagnoses, prescriptions (structured allergies)
- Billing: invoice per visit, payment status tracking
- Dashboard: today's appointments, admission counts, department load

## License

MIT — see [LICENSE](LICENSE). Copyright © 2026 Sai Shekar Yeduru.
