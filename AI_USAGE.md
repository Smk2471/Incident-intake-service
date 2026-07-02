# AI Usage

## Tools used

- Claude (Anthropic) — used conversationally for the full build: domain
  modeling, endpoint design, entity/DTO/service/controller code, tests,
  and these docs.

## Where AI helped

- **Scaffolding boilerplate fast**: entity classes, DTOs, repository
  interfaces, the global exception handler, the correlation-ID filter —
  standard Spring Boot plumbing that doesn't benefit from hand-typing.
- **Surfacing a design question I might have rushed past**: the AI raised
  the idempotency question explicitly (fail loud with 409 vs. silently
  succeed vs. return-existing) instead of picking one silently. I picked
  return-existing-with-200 and had it write the reasoning into the code
  comments and README so a reviewer doesn't have to guess why.
- **Catching a real bug during generation**: the AI flagged that with
  `spring.jpa.open-in-view: false` (my choice, not its default), lazily
  loaded `IncidentEvent` collections would throw
  `LazyInitializationException` when serialized outside the transaction —
  and fixed it with a targeted `@EntityGraph` query used only where the
  timeline is actually needed, rather than the lazier fix of just turning
  `open-in-view` back on everywhere.
- **Test scaffolding**: generating the MockMvc integration test skeletons
  and JUnit boilerplate so I could focus review time on whether the
  *assertions* were meaningful, not on getting `@SpringBootTest` wiring
  right.

## Where AI didn't help / what I did myself

- **The actual domain decisions**: what counts as "enough information to
  answer what happened and when" (append-only event log vs. just a status
  field + updatedAt), the specific state machine transitions, which fields
  are required vs. optional, and the idempotency behavior itself — these
  were decisions I made and had the AI implement, not decisions I asked it
  to make for me.
- **Verification**: I could not execute `mvn test` in the environment I
  used to write this (no network access to Maven Central from that
  sandbox — only a small allowlist of package-registry domains, which
  didn't include it). So I did NOT get a green test run from this tool.
  I manually traced every test method against the service/controller
  implementation line by line, checked brace/import balance across every
  file, and specifically reasoned through the lazy-loading behavior
  described above rather than trusting it would "probably work."
  **I ran `mvn test` locally after generating this before treating it as
  done** — [fill in actual result / any fixes you made once you run it
  yourself].

## Example prompts / workflow

Roughly, in order:
1. "Here's the take-home spec — help me design the domain model before
   writing any code." → discussed field choices, the idempotency question,
   and the event-log vs. status-field tradeoff.
2. "Now scaffold the Spring Boot project: entities, DTOs, repository,
   service, controller, exception handling." → generated iteratively,
   file by file, so I could review each layer before moving to the next.
3. "What's the riskiest part of this to leave untested?" → landed on
   idempotent creation and the status state machine, which is where most
   of the unit test effort went.
4. "Where would open-in-view=false bite us?" → caught the lazy-loading
   issue above.
5. "Write the README and this AI_USAGE file."

## What I changed or rejected from AI output

- Rejected the AI's first instinct to allow *any* status → any status
  transition (simpler code) in favor of an explicit allowed-transitions
  map — I wanted illegal jumps (e.g. `CLOSED → IN_PROGRESS`) to be
  impossible at the API boundary, not just discouraged by convention.
- Rejected returning `409 Conflict` on a duplicate `externalReferenceId`
  submission (the AI's first suggestion) in favor of `200 OK` +
  `duplicate: true` — a 409 pushes retry-handling logic onto every caller;
  treating the ID as a true idempotency key doesn't.
- Simplified one integration test's JSONPath assertion (a filter
  expression `[?(@.severity != 'CRITICAL')]`) to a plain length + index
  check — the filter syntax adds a dependency on JsonPath's more advanced
  operator support that isn't worth it for a test this simple.
- Trimmed an initial version of the event types down to three
  (`CREATED`, `STATUS_CHANGED`, `DUPLICATE_SUBMISSION_DETECTED`) after the
  AI proposed a longer list including things like `VIEWED` that would add
  write load on every read with no real debugging value.

## What I'd do differently for production

- **Swap H2 for a real database** (Postgres) with Flyway/Liquibase
  migrations instead of `ddl-auto: update` — fine for a take-home, not for
  anything with real data.
- **Add authn/authz**: `actor` on status updates is currently free-text;
  in production this should come from a verified identity (JWT/session),
  not a client-supplied string.
- **Rate-limit `POST /incidents`** and consider a request-size cap —
  it's an unauthenticated-by-default intake endpoint.
- **Make the idempotency window explicit**: right now a duplicate
  `externalReferenceId` matches forever. In production I'd want to discuss
  whether that's actually desired (e.g. should the same reference ID be
  reusable after the original incident is `CLOSED`, for a genuinely new
  occurrence?) rather than assuming "forever" is right.
- **Add pagination limits / max page size** to `GET /incidents` to avoid
  an unbounded `size=999999` query.
- **Emit metrics**, not just logs — counts of incidents by severity,
  status-transition latency, duplicate-submission rate — the kind of
  thing an on-call dashboard needs that grep-through-logs doesn't give you
  at 2am.
- **Actually run this in CI** with `mvn test` gating merges, which I
  couldn't do in the tool I used to build it.
