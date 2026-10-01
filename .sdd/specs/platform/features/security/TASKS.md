# Security Tasks

The tasks of [PLAN.md](PLAN.md), grouped by the same parts. Each part's tasks are approved with
that part of the plan; `plan.md` tracks which are done. E-01 was B-05 of the platform tasks.

## Part E — Evidence for the production profile

**State:** draft of 22 September 2026, revised and approved on 24 September 2026 together with part E of the plan.

Tasks run on a branch `work/fix/<area>/<what>` from `main` (`OPS-019`), after part A is done. Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires. After each task the full Maven suite and `npm run test:ui` pass. A test changes only as plan section E.2 allows.

| Task | What | Rules |
|---|---|---|
| E-01 | Add a production-profile test on a real server (`RANDOM_PORT`) using non-secret, test-only values for every required production setting and never `.env`. Read the headers and the session cookie from real `Set-Cookie` and header values, with requests made secure by `X-Forwarded-Proto: https` from a trusted proxy range declared only in test configuration that covers the client (`::1/128` included, or the client on `127.0.0.1`). See every case fail for its own reason before GREEN: remove or relax one header or cookie setting, or disable one readiness check, see only that case fail, then restore it. Assert the exact `AC-SEC-008` headers and cookie attributes, and check separately under `prod` that production readiness refuses each `SEC-013` relaxation alone, with every other production value safe: an `http://` public origin, a `localhost` origin, `SameSite=Lax` and a session cookie without `Secure`, each failure naming only its own check; HSTS is checked present on the secure response. Close the constitution's `SEC-011` and `SEC-013` gaps only after a new decision in `decisions.md`, maintainer agreement to the wording and a constitution version increment under Amendment, following `D41` | `SEC-011`, `SEC-013`, `AC-SEC-008`, `TST-001` |
