# Security Plan

**Owner:** Loc-LX · each part carries its own state in the table below.

How the rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not a tracker:
progress belongs in [`plan.md`](../../../../../plan.md). Where this plan and a spec disagree, the spec wins.
Its tasks are in [TASKS.md](TASKS.md).

| Part | Subject | Rules | State |
|---|---|---|---|
| E | Evidence for the production profile, below | `SEC-011`, `SEC-013`, `AC-SEC-008` | Draft of 22 September 2026, moved from part B of the platform plan under `D40`; revised and approved on 24 September 2026 |

## Part E — Evidence for the production profile

**Implements** the evidence `SEC-011` and `SEC-013` lack, gaps the constitution lists. It was
task B-05 of part B of the platform plan, drafted on 22 September 2026, until `D40` placed it with
the rules it builds. It starts after part A is done.

### E.1 Order of work

The task is E-01 in [TASKS.md](TASKS.md). It depends on nothing in part B and may run at any time
after part A. The test constructs the `prod` profile with non-secret values that exist only in test
configuration for the required master key, HTTPS public origin, datasource and proxy policy. It
does not read `.env` or depend on a developer machine's production configuration.

The headers and the session cookie are read from a real server. The test starts the application on
a random port (`RANDOM_PORT`) and reads the security headers and `Set-Cookie` from real HTTP
responses: the cookie attributes of `server.servlet.session.cookie.*` are applied only by the
servlet container, and MockMvc never sends that cookie. A request counts as secure by arriving from
a trusted proxy with `X-Forwarded-Proto: https`, which `prod` honors through
`forward-headers-strategy=framework`. The trusted proxy range exists only in the test configuration
and covers the client's address: it includes `::1/128`, or the client connects to `127.0.0.1`.
Spring Security emits HSTS only for a secure request, so an HTTP request cannot prove that
production HSTS is configured.

Every case is seen failing before it passes, each for its own reason. For a header or a cookie
attribute, one setting is temporarily removed or relaxed and only that assertion fails. For a
refusal of production readiness, that one check is temporarily disabled and only that case fails.
Each defect is restored before GREEN.

Under `prod`, the test treats every relaxation that `SEC-013` permits in development or test as a
separate negative case. With all other production values safe, production readiness refuses each of
these alone: a public origin over `http://`, a `localhost` origin, `SameSite=Lax`, and a session
cookie without `Secure`. HSTS, the fifth relaxation, is checked on the secure response instead: the
header must be present. Each refusal case asserts that the failure names only its own check; a
single startup failure containing several unsafe values does not satisfy these cases.

### E.2 Tests this part changes

E-01 adds the production-profile web test and its test-only configuration. Any other test changes
only as part A's section A.7 allows. The temporary defects used for RED are restored before the
task is complete and none is retained as a production change.

### E.3 When the part is done

- The test of E-01 passes.
- The full Maven suite, the end-to-end suite and `npm run test:ui` pass.
- The constitution's gap rows for `SEC-011` and `SEC-013` are closed in the same change as the test
  that closes each. Because this changes the constitution's enforcement index and known-gap text,
  the closure first gets a new decision in [decisions.md](../../../../decisions.md), the maintainer
  agrees to the wording, and the constitution version is incremented under its Amendment table, as
  `D41` did after part A.
