# Duong's Iteration 2 Tracker — `work/attendance`

Writable repo-side tracker mirroring `PROJECT_PLAN.md` §5.4 and
`duong-attendance-scope.md` (the read-only docs hub holds the authoritative plan).
Each item follows the TDD workflow: read requirements → claim `IN_PROGRESS`
(owner + date) → identify test level/evidence file → RED → GREEN → affected
suite → evidence + commit SHA.

| ID | Deliverable | Status | Owner/date | Result/commit |
|---|---|---|---|---|
| I2-ATT-01 | Schedule future-month attendance-policy versions, including separate grace values, and preserve effective history. | `DONE` | Duong / 2026-08-21 | `3f617fe` (+ docs `3445451`); audited 2026-08-21 |
| I2-ATT-02 | Preview/import VN HolidayAPI candidates with Admin selection, override, provenance, and deduplication. | `DONE` | Duong / 2026-08-19 | `9be57ef`; audited 2026-08-21 |
| I2-ATT-03 | Materialize frozen full-day leave allocations and monthly/cross-month quota reservations. | `DONE` | Duong / 2026-08-19 | `69b54eb`; audited 2026-08-21 |
| I2-ATT-04 | Implement leave submit/approve/reject/cancel and overlap protection. | `DONE` | Duong / 2026-08-19 | `5e9e598`; audited 2026-08-21 |
| I2-ATT-05 | Implement missed-checkout correction submission and effective-checkout derivation. | `DONE` | Duong / 2026-08-20 | `4c09382`; audited 2026-08-21 |
| I2-ATT-06 | Implement Mentor approve/reject/revert and separate decision-window locking. | `DONE` | Duong / 2026-08-20 | `2ace890`; audited 2026-08-21 |
| I2-ATT-07 | Add idempotent schedulers and equivalent request-time deadline guards. | `DONE` | Duong / 2026-08-21 | `169830f`; audited 2026-08-21 |