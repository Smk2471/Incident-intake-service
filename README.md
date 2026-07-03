# Incident Intake Service
 
A Spring Boot backend service that acts as the entry point for field incident
reports (weather events, security breaches, facility outages, etc.).
 
## Stack
 
- Java 17
- Spring Boot 3.3.4 (Web, Data JPA, Validation)
- springdoc-openapi (Swagger UI)
- H2 in-memory database (swap for Postgres/MySQL in production — see
  "What I'd do differently" in `AI_USAGE.md`)
- JUnit 5, MockMvc, AssertJ
## Running the service
 
```bash
mvn spring-boot:run
```
 
The service starts on `http://localhost:8080`. An H2 console is available at
`http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:incidents`, user
`sa`, empty password) for poking at the data during local development.
 
**Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
**Raw OpenAPI spec**: `http://localhost:8080/v3/api-docs`
 
## Deploying (Railway)
 
This is a stateful JVM process, not a static site or serverless function, so
Vercel isn't a fit — it only runs edge/serverless functions with cold starts,
not long-running Spring Boot apps. Railway runs containers, so that's what
this is set up for (there's a `Dockerfile` in the repo).
 
1. Push this repo to GitHub.
2. In Railway: **New Project → Deploy from GitHub repo** → select this repo.
   Railway detects the `Dockerfile` and builds from it automatically.
3. Railway injects a `PORT` env var at runtime; `application.yml` already
   reads it (`server.port: ${PORT:8080}`).
4. Railway also terminates TLS at its edge and forwards plain HTTP
   internally. `server.forward-headers-strategy: framework` is set so Spring
   trusts the `X-Forwarded-Proto` header and knows the real request was
   HTTPS — without it, the generated Swagger server URL comes out as
   `http://` on an `https://`-loaded page, which the browser blocks as mixed
   content.
5. Once deployed, Swagger UI is at `<your-railway-url>/swagger-ui/index.html`.
   Hitting the bare root URL redirects there automatically.
6. No environment variables are required to run as-is (H2 is in-memory) —
   data resets on every restart since there's no persistent volume attached.
## Running tests
 
```bash
mvn test
```
 
Runs the service-layer unit tests (`IncidentServiceTest`, backed by a real
in-memory H2 database rather than mocks — idempotency and status-transition
behavior depend on actual DB constraints, so mocking the repository would
give false confidence) and the full-stack MockMvc integration tests
(`IncidentControllerIntegrationTest`).
 
## API
 
### `POST /incidents` — create an incident
 
```json
{
  "title": "Transformer fire at substation 4",
  "description": "Visible smoke, no injuries reported yet",
  "severity": "HIGH",
  "reportedBy": "field-agent-217",
  "externalReferenceId": "scada-alert-9981"
}
```
 
- `title`, `severity`, `reportedBy` are required. `severity` is one of
  `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- `externalReferenceId` is optional. **Re-submitting the same one doesn't
  create a duplicate** — it returns the existing incident with `200 OK` and
  `"duplicate": true`, and logs a `duplicate_submission_detected` event on
  that incident's timeline. New incidents return `201 Created`. See
  `IncidentService.createIncident` for the full reasoning (short version:
  field-reporting clients retry on flaky connections, and a retry should be
  safe to send twice rather than needing client-side dedup logic).
- Every failure case — validation, malformed JSON, not-found, illegal state
  transitions, unexpected errors — returns the same JSON error shape:
```json
{
  "timestamp": "2026-07-02T10:15:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "One or more fields failed validation",
  "path": "/incidents",
  "correlationId": "b3f1...",
  "fieldErrors": [{ "field": "title", "message": "title must not be blank" }]
}
```
 
### `GET /incidents/{id}` — fetch one incident
 
Returns the incident plus its full event timeline — this is what answers
"what happened, and when?" for a single incident. `404` with
`code: INCIDENT_NOT_FOUND` if it doesn't exist.
 
### `GET /incidents` — list incidents
 
Query params, all optional:
- `severity` — one of `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `status` — one of `OPEN`, `ACKNOWLEDGED`, `IN_PROGRESS`, `RESOLVED`,
  `CLOSED`, `REJECTED`
- `page`, `size` — standard pagination (defaults: page 0, size 20)
- `sort` — format is `property,direction`, e.g. `sort=createdAt,desc`.
  Repeat the param for multiple sort keys:
  `sort=severity,asc&sort=createdAt,desc`. **This is a plain query string
  value, not a JSON array** — Swagger UI renders it as its own input field
  (via `@ParameterObject` on the controller), so just type e.g.
  `createdAt,desc` directly into that field.
List rows use a slimmer summary shape (no event timeline) to keep list
payloads small — fetch a single incident's timeline via `GET /incidents/{id}`.
 
### `PATCH /incidents/{id}/status` — advance the lifecycle
 
```json
{ "status": "ACKNOWLEDGED", "actor": "oncall-3", "note": "picked up, dispatching crew" }
```
 
Incidents move through an explicit state machine (see `IncidentStatus.java`):
 
```
OPEN ──► ACKNOWLEDGED ──► IN_PROGRESS ──► RESOLVED ──► CLOSED
  │            │                              │
  └─► REJECTED └─► REJECTED         IN_PROGRESS ◄┘ (reopen)
```
 
`CLOSED` and `REJECTED` are terminal. An unlisted transition (e.g.
`OPEN → RESOLVED`, or anything out of `CLOSED`) returns `409 Conflict` with
`code: INVALID_STATUS_TRANSITION`, and the message tells you which
transitions are actually legal from the current state. Setting the same
status as current is a no-op (200, no new event), so retries are safe here
too.
 
## Domain model
 
- **`Incident`** — the current-state record: title, description, severity,
  reporter, status, optional external reference ID, created/updated
  timestamps.
- **`IncidentEvent`** — an append-only history entry (`CREATED`,
  `STATUS_CHANGED`, `DUPLICATE_SUBMISSION_DETECTED`), each with a timestamp,
  optional actor, and note. This is a separate table rather than just
  overwriting `status` in place, because it's the only reliable way to
  answer "what happened to this incident, and when?" after the fact — and
  it gives the duplicate-submission case somewhere honest to be recorded
  instead of silently dropping the retry.
- The internal ID (`UUID`, server-generated, immutable) is intentionally
  separate from the caller-supplied `externalReferenceId` — a caller should
  never be able to influence the primary key, but should still get
  idempotent behavior when it retries.
### A JPA detail worth flagging
 
`spring.jpa.open-in-view` is disabled (it's a well-known footgun in
production — it silently keeps DB connections open for the length of view
rendering). Because of that, `IncidentEvent` is lazily loaded by default, and
the endpoints that need the timeline use a dedicated `findWithEventsById`
query (`@EntityGraph(attributePaths = "events")`) so the list endpoint
doesn't pay for events it never returns.
 
## Logging
 
Every request is tagged with a correlation ID (from the `X-Correlation-Id`
request header if supplied, otherwise generated) via `CorrelationIdFilter`,
placed in MDC, and echoed back in the response header. Every log line
includes it:
 
```
2026-07-02T10:15:00.123Z INFO  [correlationId=b3f1c2a0-...] c.e.i.service.IncidentService - event=incident_created incidentId=... severity=HIGH reportedBy=field-agent-217 externalReferenceId=scada-alert-9981 correlationId=b3f1c2a0-...
```
 
Log lines use a flat `event=... key=value` shape so a downstream log
aggregator can filter on `event` type without regex. Key events:
`incident_created`, `duplicate_submission_detected`,
`incident_status_changed`, `invalid_status_transition_attempt`,
`incident_lookup_miss`, `validation_failed`, `unmapped_route_requested`,
`unhandled_exception` (full stack trace). If someone reports "my request
failed," their `X-Correlation-Id` is a single grep across all log lines for
that exact request.
 
## What I intentionally left out (scope)
 
- Auth/authz — no identity verification on `actor` (accepted as free-text).
- Rate limiting on `POST /incidents`.
- A persistent database — using H2 to keep this runnable with zero setup.
- Soft delete / incident archival.
