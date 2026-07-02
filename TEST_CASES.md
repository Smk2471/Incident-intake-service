# Manual Test Cases

Run these against either `http://localhost:8080` or your Railway URL — just
swap `BASE_URL`. Same requests work directly in Swagger UI's "Try it out" if
you'd rather click through them there; the request bodies below are exactly
what to paste into each endpoint's example field.

```bash
export BASE_URL=http://localhost:8080
# or: export BASE_URL=https://your-app.up.railway.app
```

---

## 1. Happy path — create an incident

```bash
curl -i -X POST $BASE_URL/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Transformer fire at substation 4",
    "description": "Visible smoke, no injuries reported yet",
    "severity": "HIGH",
    "reportedBy": "field-agent-217"
  }'
```
**Expect:** `201 Created`, body has a generated `id`, `status: "OPEN"`,
`duplicate: false`, and one `CREATED` event in `events`.

Save the returned `id` for the tests below:
```bash
export INCIDENT_ID=<paste id here>
```

---

## 2. Validation error — missing required fields

```bash
curl -i -X POST $BASE_URL/incidents \
  -H "Content-Type: application/json" \
  -d '{"description": "no title, no severity, no reporter"}'
```
**Expect:** `400 Bad Request`, `code: "VALIDATION_ERROR"`, `fieldErrors`
listing `title`, `severity`, `reportedBy`.

---

## 3. Validation error — invalid severity enum value

```bash
curl -i -X POST $BASE_URL/incidents \
  -H "Content-Type: application/json" \
  -d '{"title": "x", "severity": "SUPER_BAD", "reportedBy": "y"}'
```
**Expect:** `400 Bad Request`, `code: "MALFORMED_REQUEST_BODY"` (severity
fails to parse as an enum before reaching field validation).

---

## 4. Idempotency — same externalReferenceId submitted twice

```bash
curl -i -X POST $BASE_URL/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Gas leak reported",
    "severity": "HIGH",
    "reportedBy": "dispatcher-9",
    "externalReferenceId": "cad-ticket-55231"
  }'
# note the returned id and status (should be 201)

curl -i -X POST $BASE_URL/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Gas leak reported (retry)",
    "severity": "HIGH",
    "reportedBy": "dispatcher-9",
    "externalReferenceId": "cad-ticket-55231"
  }'
```
**Expect:** first call `201 Created`. Second call `200 OK`, `duplicate:
true`, **same `id`** as the first call, and the original `title` unchanged
(the retry's title is ignored, not merged in).

---

## 5. Retrieve a single incident

```bash
curl -i $BASE_URL/incidents/$INCIDENT_ID
```
**Expect:** `200 OK`, full incident including the `events` timeline.

---

## 6. Retrieve a non-existent incident

```bash
curl -i $BASE_URL/incidents/00000000-0000-0000-0000-000000000000
```
**Expect:** `404 Not Found`, `code: "INCIDENT_NOT_FOUND"`, a
`correlationId` present in the response body.

---

## 7. List incidents, filter by severity

```bash
curl -i "$BASE_URL/incidents?severity=HIGH"
```
**Expect:** `200 OK`, paginated `content` array containing only `HIGH`
severity incidents.

---

## 8. List incidents, filter by status + pagination

```bash
curl -i "$BASE_URL/incidents?status=OPEN&page=0&size=5"
```
**Expect:** `200 OK`, at most 5 items, all `status: "OPEN"`.

---

## 9. Valid lifecycle transition

```bash
curl -i -X PATCH $BASE_URL/incidents/$INCIDENT_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status": "ACKNOWLEDGED", "actor": "oncall-3", "note": "picked up"}'
```
**Expect:** `200 OK`, `status: "ACKNOWLEDGED"`, `events` array grew by one
`STATUS_CHANGED` entry.

---

## 10. Invalid lifecycle transition (illegal jump)

```bash
curl -i -X PATCH $BASE_URL/incidents/$INCIDENT_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status": "RESOLVED"}'
```
**Expect:** `409 Conflict`, `code: "INVALID_STATUS_TRANSITION"` — because
the incident is `ACKNOWLEDGED`, and `ACKNOWLEDGED → RESOLVED` isn't legal
without passing through `IN_PROGRESS` first (message tells you the actual
allowed next states).

---

## 11. Transition into a terminal state, then confirm it's locked

```bash
curl -i -X PATCH $BASE_URL/incidents/$INCIDENT_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'

curl -i -X PATCH $BASE_URL/incidents/$INCIDENT_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status": "RESOLVED"}'

curl -i -X PATCH $BASE_URL/incidents/$INCIDENT_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status": "CLOSED"}'

# now try to move it again
curl -i -X PATCH $BASE_URL/incidents/$INCIDENT_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status": "OPEN"}'
```
**Expect:** the first three each return `200 OK` and advance the status.
The last returns `409 Conflict` — `CLOSED` is terminal.

---

## 12. Correlation ID round-trip

```bash
curl -i $BASE_URL/incidents/00000000-0000-0000-0000-000000000000 \
  -H "X-Correlation-Id: my-test-run-001"
```
**Expect:** response header `X-Correlation-Id: my-test-run-001` (echoed
back, not regenerated), and the same value inside the JSON error body's
`correlationId` field — confirms you can hand this ID to logs to find the
exact request.
