# Incident Intake Service

A Spring Boot backend service that acts as the entry point for field incident
reports (weather events, security breaches, facility outages, etc.).

## Stack

- Java 17
- Spring Boot 3.3.4 (Web, Data JPA, Validation)
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

**Swagger UI**: `http://localhost:8080/swagger-ui.html`
**Raw OpenAPI spec**: `http://localhost:8080/v3/api-docs`

## Deploying (Railway)

This is a stateful JVM process (not a static site or serverless function),
so **Vercel isn't a fit** — Vercel runs edge/serverless functions with cold
starts and no persistent process, and doesn't support long-running Spring
Boot apps. **Railway** does, and there's a `Dockerfile` in this repo for it.

1. Push this repo to GitHub.
2. In Railway: **New Project → Deploy from GitHub repo** → select this repo.
   Railway will detect the `Dockerfile` and build from it automatically.
3. Railway injects a `PORT` env var at runtime; `application.yml` already
   reads it (`server.port: ${PORT:8080}`), so no config changes needed.
4. Once deployed, Railway gives you a public URL like
   `https://incident-intake-service-production.up.railway.app`. Swagger UI
   will be at `<that-url>/swagger-ui.html`.
5. No environment variables are required for this to run as-is (H2 is
   in-memory) — note in your submission that data resets on every restart,
   since there's no persistent volume/database attached.

If you'd rather not use the Dockerfile, Railway's Nixpacks builder also
auto-detects Maven projects from `pom.xml` — either path works; the
Dockerfile is just more predictable across environments.

## Running tests

```bash
mvn test
```

This runs both the service-layer unit tests (`IncidentServiceTest`, backed by
a real in-memory H2 database rather than mocks — see rationale below) and the
full-stack MockMvc integration tests (`IncidentControllerIntegrationTest`).

> **Note on verification**: this code was written and reviewed carefully, but
> I was not able to execute `mvn test` in the environment I used to produce
> it (no outbound access to Maven Central from that sandbox). I manually
> traced every test against the implementation, checked brace/import
> correctness, and reasoned through the JPA lazy-loading implications
> explicitly (see the `findWithEventsById` note below). **Before relying on
> this for the interview, run `mvn test` yourself and treat any failures as
> mine to fix, not yours to work around.**

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

- `title`, `severity`, `reportedBy` are required. `severity` must be one of
  `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- `externalReferenceId` is optional. **If it's re-submitted, the service does
  not create a duplicate incident** — it returns the existing incident with
  `200 OK` and `"duplicate": true`, and logs a `duplicate_submission_detected`
  event on that incident's timeline. New incidents return `201 Created`.
  See `IncidentService.createIncident` for the full rationale (short version:
  field-reporting clients retry on flaky connections; a retry should be safe
  to send twice, not require special client-side deduplication logic).
- All failures — validation errors, malformed JSON, not-found, illegal state
  transitions, unexpected errors — return the same JSON error shape:

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

Returns the incident plus its full event timeline (see "Domain model"
below) — this is what answers "what happened, and when?" for a single
incident. `404` with `code: INCIDENT_NOT_FOUND` if it doesn't exist.

### `GET /incidents?severity=&status=&page=&size=` — list incidents

Filterable by `severity` and/or `status`; both are optional and combinable.
Standard Spring pagination (`page`, `size`, `sort`). List rows are a slimmer
summary shape (no event timeline) to keep list payloads small — fetch a
single incident's timeline via the `GET /incidents/{id}` endpoint.

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

`CLOSED` and `REJECTED` are terminal. Attempting an unlisted transition
(e.g. `OPEN → RESOLVED`, or anything out of `CLOSED`) returns `409 Conflict`
with `code: INVALID_STATUS_TRANSITION` and tells you which transitions
*are* legal from the current state. Setting the same status as current is a
no-op (200, no new event) rather than an error, so retries are safe here too.

## Domain model

- **`Incident`** — the current-state record: title, description, severity,
  reporter, status, optional external reference ID, created/updated
  timestamps.
- **`IncidentEvent`** — an append-only history entry (`CREATED`,
  `STATUS_CHANGED`, `DUPLICATE_SUBMISSION_DETECTED`), each with a timestamp,
  optional actor, and note. This is deliberately a separate table rather than
  just overwriting `status` in place: it's the only way to reliably answer
  "what happened to this incident, and when?" after the fact, and it gives
  the duplicate-submission case somewhere honest to be recorded (instead of
  silently dropping the retry on the floor).
- Internal ID (`UUID`, server-generated, immutable) is intentionally distinct
  from the caller-supplied `externalReferenceId` — the caller should never be
  able to influence our primary key, but should still get idempotent
  behavior when it retries.

### A JPA detail worth flagging

`spring.jpa.open-in-view` is disabled (it's a well-known footgun in
production — it silently keeps DB connections open for the length of view
rendering). Because of that, `IncidentEvent` is lazily loaded, and the
`GET /incidents/{id}` and `PATCH /incidents/{id}/status` endpoints need the
timeline eagerly for their response body. `IncidentRepository` has a
dedicated `findWithEventsById` query (`@EntityGraph(attributePaths =
"events")`) used only where the timeline is actually needed, so the list
endpoint doesn't pay for events it never returns.

## Logging

Every request is tagged with a correlation ID (from the `X-Correlation-Id`
request header if the caller supplies one, otherwise generated) via
`CorrelationIdFilter`, placed in MDC, and echoed back in the response header.
Every log line includes it, e.g.:

```
2026-07-02T10:15:00.123Z INFO  [correlationId=b3f1c2a0-...] c.e.i.service.IncidentService - event=incident_created incidentId=... severity=HIGH reportedBy=field-agent-217 externalReferenceId=scada-alert-9981 correlationId=b3f1c2a0-...
```

Log lines use a flat `event=... key=value` shape so a downstream log
aggregator can parse and filter on `event` type without regex. Key events
logged: `incident_created`, `duplicate_submission_detected`,
`incident_status_changed`, `invalid_status_transition_attempt`,
`incident_lookup_miss`, `validation_failed`, `unhandled_exception`
(with full stack trace). If a caller reports "my request failed," ask them
for the `X-Correlation-Id` from the response — that's a single grep across
all log lines for that request.

## What I intentionally left out (scope)

- Auth/authz — no `actor` identity verification (accepted as free-text on
  status updates).
- Rate limiting / abuse protection on `POST /incidents`.
- Persistent database config (Postgres/MySQL) — using H2 to keep this
  runnable with zero setup.
- Soft delete / incident archival.

See `AI_USAGE.md` for what I'd change before shipping this to production.
