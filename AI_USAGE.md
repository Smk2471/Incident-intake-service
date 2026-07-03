# AI Usage
 
## Tools used
 
Claude (Anthropic), used conversationally throughout — domain modeling,
code generation, tests, deployment debugging, and these docs.
 
## Where AI helped
 
- **Boilerplate, fast**: entity classes, DTOs, repository interfaces, the
  exception handler, the correlation-ID filter. Standard Spring plumbing
  that doesn't need to be typed by hand to be understood.
- **Forcing the idempotency decision instead of letting me skip it**: the
  spec just says "handle duplicate `externalReferenceId` gracefully." I
  worked through three options with the AI (hard-fail with 409, silently
  create a second incident, or return the existing one) before picking
  return-existing-with-200 — a duplicate submission from a retry-happy
  field client shouldn't fragment responder attention across two records,
  and shouldn't require every caller to build retry/reconciliation logic
  just to call this endpoint safely.
- **Catching a lazy-loading bug before it shipped**: with
  `spring.jpa.open-in-view: false` (my call — it's a known footgun to leave
  on in production), the `IncidentEvent` timeline would throw
  `LazyInitializationException` when serialized outside the transaction.
  Fixed with a targeted `@EntityGraph` query used only where the timeline
  is actually needed, rather than just flipping `open-in-view` back on
  everywhere and eating the connection-pool cost.
- **Test scaffolding**: MockMvc/JUnit wiring, so review time went into
  whether the assertions actually proved something, not into
  `@SpringBootTest` ceremony.
## What I actually caught myself, on the deployed service
 
Passing `mvn test` locally isn't the same as the thing working in
production, so I deployed to Railway and manually exercised it — and hit two
real bugs neither of us caught from the code alone:
 
1. **`GET /` returned `500` instead of `404`.** Spring's
   `NoResourceFoundException` for an unmapped path was falling into my
   catch-all `Exception` handler and getting mapped to a 500. Added a
   specific handler for it (proper 404, `code: ROUTE_NOT_FOUND`) plus a
   redirect from `/` to Swagger UI so there's something sensible to land on.
2. **Swagger UI's "Try it out" failed with a mixed-content error** — the
   generated OpenAPI server URL was `http://...` while the page itself was
   `https://...`. Railway terminates TLS at its edge and forwards plain
   HTTP internally; Spring doesn't know the original request was HTTPS
   unless told to trust the proxy. Fixed with
   `server.forward-headers-strategy: framework`.
3. **The `GET /incidents` pagination params were unusable from Swagger UI.**
   Springdoc was rendering Spring Data's `Pageable` as an editable JSON body
   (`{"page":0,"size":1,"sort":["string"]}`) instead of the three separate
   query params (`page`, `size`, `sort`) the endpoint actually reads —
   `sort` in particular needs to be a plain `property,direction` string like
   `createdAt,desc`, not a JSON array. Fixed by annotating the parameter
   with `@ParameterObject` so Swagger UI shows the real query params
   instead of a body shape that doesn't match how the request is parsed.
None of these three would show up in a green `mvn test` run — they only
surface once the app is actually behind a real reverse proxy and someone is
clicking through Swagger UI like a real caller would. That's the main reason
I didn't stop at "tests pass locally."
 
## Where AI didn't help — decisions that were mine
 
- What "enough information to answer what happened and when" actually means
  (an append-only event log, not just a status field + `updatedAt`).
- The exact state machine — which transitions are legal, which states are
  terminal, and that a same-status PATCH is a no-op rather than an error.
- Which fields are required vs. optional, and the idempotency behavior
  itself. I had the AI implement these, not decide them.
- Rejected the AI's first-pass suggestion to allow any status → any status
  transition (less code) in favor of an explicit allowed-transitions map —
  I wanted an illegal jump like `CLOSED → IN_PROGRESS` to be structurally
  impossible, not just discouraged by convention.
- Rejected `409 Conflict` on a duplicate `externalReferenceId` submission
  (the AI's first suggestion) for the reasons above.
- Trimmed the event-type list down to three (`CREATED`, `STATUS_CHANGED`,
  `DUPLICATE_SUBMISSION_DETECTED`) after a longer proposed list included
  things like a `VIEWED` event that would add write load on every read for
  no real debugging value.
## How I verified
 
- `mvn test` locally — service-layer tests against a real in-memory H2
  database (not mocks; idempotency and constraint behavior needs real DB
  semantics to mean anything) plus full MockMvc integration tests.
- Manually walked through every endpoint via Swagger UI on the deployed
  Railway instance, which is how I found the three bugs above.
- Manually traced the JPA lazy-loading behavior around `open-in-view=false`
  rather than assuming the generated code would "probably work."
## What I'd do differently for production
 
- Swap H2 for Postgres with real migrations (Flyway/Liquibase) instead of
  `ddl-auto: update`.
- Real authn/authz — `actor` on status updates is currently free-text; it
  should come from a verified identity, not a client-supplied string.
- Rate-limit `POST /incidents` — it's an unauthenticated intake endpoint.
- Decide explicitly whether a duplicate `externalReferenceId` should match
  forever, or only while the original incident is still open (e.g. should
  the same reference ID be reusable for a genuinely new occurrence after
  the original is `CLOSED`?). Right now it matches forever, which was a
  reasonable default but not one I'd want to leave undiscussed.
- Cap max page size on `GET /incidents`.
- Emit metrics, not just logs — incident counts by severity, transition
  latency, duplicate-submission rate — the kind of thing a dashboard needs
  that grepping logs doesn't give you at 2am.
- Wire `mvn test` into CI so it gates merges instead of being something I
  run by hand.
