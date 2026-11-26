# Doctor Appointment Booking and Management API

The backend uses Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, MySQL 8, and Flyway. Controllers validate requests and return DTOs. Services enforce authorization, ownership, and business rules, and manage transactions.

## Run locally

To run React, Spring Boot, and MySQL together, follow the [full Docker setup](../README.md#run-the-full-stack-with-docker).

For host development, run Maven from the `backend` directory and follow the [frontend setup guide](../frontend/README.md) to start React.

1. Install JDK 21, Maven 3.9, and Docker with Compose, or use an existing MySQL 8 instance.
2. For the Docker development database, run `docker compose up -d --wait` from `backend`. Compose starts MySQL 8.4 on the loopback interface and stores data in a named volume. You can also use the [standalone MySQL docker run command](../README.md#run-only-mysql-with-docker).

   | Setting | Local development value |
   | --- | --- |
   | JDBC URL | `jdbc:mysql://localhost:3306/doctor_appointment_db` |
   | Database | `doctor_appointment_db` |
   | Username | `clinic_app` |
   | Password | `carepoint-local-db` |

   For a native MySQL installation, connect as a MySQL administrator and create the same database and user. Use the sample password for a disposable local setup or choose a different password and set `DB_PASSWORD` accordingly.

   ```sql
   CREATE DATABASE doctor_appointment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'clinic_app'@'localhost' IDENTIFIED BY 'carepoint-local-db';
   GRANT ALL PRIVILEGES ON doctor_appointment_db.* TO 'clinic_app'@'localhost';
   ```

3. Set the backend configuration and start it.

   **Windows PowerShell**

   ```powershell
   $env:DB_URL = 'jdbc:mysql://localhost:3306/doctor_appointment_db'
   $env:DB_USERNAME = 'clinic_app'
   $env:DB_PASSWORD = 'carepoint-local-db'
   $key = New-Object byte[] 32
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($key)
   $rng.Dispose()
   $env:JWT_SECRET = [Convert]::ToBase64String($key)
   $env:ADMIN_EMAIL = '<admin email address>'
   $env:ADMIN_PASSWORD = '<generated admin password>'
   mvn spring-boot:run
   ```

   **macOS or Linux**

   ```sh
   export DB_URL='jdbc:mysql://localhost:3306/doctor_appointment_db'
   export DB_USERNAME='clinic_app'
   export DB_PASSWORD='carepoint-local-db'
   export JWT_SECRET="$(openssl rand -base64 32)"
   export ADMIN_EMAIL='admin@example.test'
   export ADMIN_PASSWORD='<choose a password of at least 12 characters>'
   mvn spring-boot:run
   ```

   Retain the JWT signing secret across restarts to preserve sessions. The optional admin variables create the first administrator when the supplied email is new. Remove both variables from subsequent launches after the administrator is created. Patients register through the API and admins create doctor accounts. Passwords need at least 12 characters and can contain up to 72 UTF-8 bytes.

4. Open [Swagger UI](http://localhost:8080/swagger-ui/index.html) or the [OpenAPI JSON](http://localhost:8080/v3/api-docs). Log in with `POST /api/v1/auth/login` and paste the `accessToken` into Swagger’s **Authorize** dialog. Use a token for the role required by each endpoint.

Flyway applies the schema migrations at startup and Hibernate validates the result. To stop the local database while keeping its data, run `docker compose stop` from `backend`. Configure separate environment credentials for other deployments. For a runnable JAR, run `mvn package` followed by `java -jar target/doctor-appointment-1.0.0-SNAPSHOT.jar` in the configured shell.

## Configuration

| Environment variable | Default or purpose |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/doctor_appointment_db` |
| `DB_USERNAME`, `DB_PASSWORD` | Required. For the supplied Compose service, use `clinic_app` and `carepoint-local-db`. |
| `JWT_SECRET` | Base64-encoded secret with at least 32 decoded bytes |
| `JWT_TTL` | `PT30M` |
| `SERVER_PORT` | `8080` |
| `CLINIC_ZONE` | `Asia/Kolkata` |
| `FRONTEND_ORIGIN` | `http://localhost:5173`, the permitted React origin |
| `CANCELLATION_CUTOFF` | `PT24H`, an ISO 8601 duration. Use `PT0S` to allow changes up to the start time. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Optional bootstrap credentials, supplied together |
| `SMTP_HOST`, `SMTP_PORT` | `localhost`, `587`. Set to the SMTP relay or local capture server. |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP credentials |
| `SMTP_AUTH`, `SMTP_STARTTLS` | Both default to `true`. For a local mail capture server that requires neither, set both to `false`. |
| `MAIL_FROM` | `no-reply@example.com`. Use a sender permitted by the mail relay. |
| `PASSWORD_RESET_TTL` | `PT30M` |
| `PASSWORD_RESET_URL` | `{FRONTEND_ORIGIN}/reset-password`. The backend appends the one-time token. |

Configure SMTP before exercising password recovery. The forgot-password response is the same for known and unknown accounts. The React reset page reads `token` from the emailed URL and submits it with `newPassword`. The backend stores token hashes, expires tokens, and permits one use per token. Changing a password or deactivating an account revokes existing JWTs.

## API reference

All paths below are relative to `/api/v1`. Send JSON writes with `Content-Type: application/json` and authenticated calls with `Authorization: Bearer <accessToken>`. Paged lists accept zero-based `page` and `size` (defaults 0 and 20, maximum size 100). They return `content`, `page`, `size`, `totalElements`, and `totalPages`.

| Access | Routes |
| --- | --- |
| Public | `POST /auth/register`, `POST /auth/login`, `POST /auth/forgot-password`, `POST /auth/reset-password` |
| Authenticated | `GET /auth/me`, `PATCH /auth/change-password` |
| Public directory | `GET /departments`, `GET /departments/{departmentId}`, `GET /doctors`, `GET /doctors/{doctorId}`. Admins can also see inactive records and doctor contact details. |
| Admin | `POST /departments`, `PUT /departments/{departmentId}`, `PATCH /departments/{departmentId}/status` |
| Admin | `POST /doctors`, `PUT /doctors/{doctorId}`, `PATCH /doctors/{doctorId}/status` |
| Doctor | `GET /doctors/me`, `PUT /doctors/me`. Doctors can update their own contact and professional profile. |
| Patient | `GET /patients/me`, `PUT /patients/me` |
| Admin | `GET /patients`, `GET /patients/{patientId}`, `PATCH /patients/{patientId}/status` |
| Doctor | `POST /doctors/me/availability`, `GET /doctors/me/availability` (active windows), `PUT /doctors/me/availability/{availabilityId}`, `DELETE /doctors/me/availability/{availabilityId}` |
| Authenticated | `GET /doctors/{doctorId}/slots?date=YYYY-MM-DD` |
| Patient | `POST /appointments`, `GET /appointments/my`, `PATCH /appointments/{appointmentId}/cancel`, `PATCH /appointments/{appointmentId}/reschedule` |
| Doctor | `GET /appointments/doctor/my`, `PATCH /appointments/{appointmentId}/complete`, `PATCH /appointments/{appointmentId}/no-show` |
| Owner, assigned doctor, or admin | `GET /appointments/{appointmentId}` |
| Admin | `GET /admin/dashboard/summary`, `GET /admin/appointments`, `GET /admin/appointments/{appointmentId}`, `PATCH /admin/appointments/{appointmentId}/status` |
| Authenticated | `GET /notifications`, `PATCH /notifications/{notificationId}/read`, `PATCH /notifications/read-all` |

Doctor lists support `departmentId`, `search` (name or specialization), `specialization`, and `active`. The admin patient list accepts `active`. Account and department status changes take `{"active":false}`. Appointment lists support `doctorId`, `patientId`, `status`, exact `date`, and inclusive `fromDate` and `toDate` filters. Ownership still applies to role-specific lists. Appointment responses include `allowedStatuses` for the authenticated role, current status, and clinic time. Patients can cancel or reschedule while `CANCELLED` is available.

### Scheduling rules

- Appointment dates and times are clinic-local. Audit, cancellation, completion, and read timestamps are UTC instants.
- Windows use minute precision, contain whole slots, and stay within one calendar date. Overlapping windows are rejected. Edits and deactivation must preserve upcoming confirmed appointments.
- The booking transaction derives the patient from the JWT, locks the doctor and applicable availability row, then checks for overlapping confirmed appointments. Notifications for the patient and doctor commit with the appointment.
- Rescheduling keeps the doctor and only changes the appointment after reserving the replacement slot. A failed reschedule keeps the existing slot.
- `CONFIRMED` can transition to `CANCELLED`, `COMPLETED`, or `NO_SHOW`. Cancellation and rescheduling share the configured cutoff. Completing or marking a visit as a no-show requires the scheduled start time to have passed or arrived. The last three statuses are terminal.
- Deactivation preserves history. Doctor and department deactivation hide them from the public directory and prevent new bookings. Existing appointments are still available to their authorized viewers.

### Example requests and responses

**Register a patient** · `POST /auth/register` · `201 Created`

```json
{
  "fullName": "Alex Kumar",
  "email": "alex@example.com",
  "password": "StrongPassword123",
  "phone": "9876543210",
  "dateOfBirth": "2000-05-20",
  "gender": "MALE",
  "address": "Coimbatore"
}
```

```json
{"id":4,"fullName":"Alex Kumar","email":"alex@example.com","role":"PATIENT"}
```

Birth date, gender, and address are optional at registration. Log in with `email` and `password` and read `accessToken`, `tokenType`, `expiresIn` (seconds), and `user`.

**Create availability** · `POST /doctors/me/availability` · `201 Created`

```json
{"date":"2026-12-10","startTime":"10:00","endTime":"12:00","slotDurationMinutes":30}
```

**Book an appointment** · `POST /appointments` · `201 Created`

```json
{"doctorId":10,"appointmentDate":"2026-12-10","startTime":"10:00","reason":"General consultation"}
```

```json
{
  "id":7,
  "doctorId":10,
  "doctorName":"Dr. Nair",
  "patientId":2,
  "patientName":"Alex Kumar",
  "appointmentDate":"2026-12-10",
  "startTime":"10:00:00",
  "endTime":"10:30:00",
  "reason":"General consultation",
  "status":"CONFIRMED",
  "createdAt":"2026-12-07T04:00:00Z",
  "updatedAt":"2026-12-07T04:00:00Z",
  "cancelledAt":null,
  "cancellationReason":null,
  "completedAt":null,
  "allowedStatuses":["CANCELLED"]
}
```

Use a future date when running these examples. Rescheduling takes `appointmentDate` and `startTime`. Admin status changes take `status`. Cancellations accept an optional `cancellationReason` and otherwise record a default reason for the actor’s role.

**Booking conflict** · `409 Conflict`

```json
{
  "timestamp":"2026-12-07T04:00:01Z",
  "status":409,
  "code":"APPOINTMENT_SLOT_UNAVAILABLE",
  "message":"The selected appointment slot is no longer available.",
  "path":"/api/v1/appointments"
}
```

Request-body validation errors can include a `fieldErrors` object keyed by field name.

## Tests

Start Docker and run `mvn test` on JDK 21. The suite starts a clean MySQL 8.4 Testcontainer and exercises the Flyway migrations, Spring Security, controller endpoints, and simultaneous booking attempts. It checks the running Swagger UI and OpenAPI paths and uses Mockito to capture password-reset mail. Docker is required for the integration suite, which reports skipped tests when Docker is unavailable.
