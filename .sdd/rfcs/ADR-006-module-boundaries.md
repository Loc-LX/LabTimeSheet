# ADR-006 — Modules follow dependency, and their dependencies form no cycle

- **Status:** Accepted
- **Date:** 2026-09-17
- **Changes:** `ARC-005`, `ARC-006`, `AC-ARC-001`
- **Decision record:** `D28` in [`.sdd/decisions.md`](../decisions.md)
- **Implemented:** not yet

## Context

The seven packages below `feature` follow the order in which the product was built, not
what depends on what. Measured at `91ff99a` with `scripts/module-boundaries.cjs`:

- Three cycles exist in the code today: account and project, account and integration, project and task.
- Controllers sit in the wrong package. `AdminSettingsController` serves policy, calendar and HolidayAPI routes from `reporting` and imports nothing of `reporting`; `NotificationController` sits in `reporting` while `notification` has no controller.
- The global calendar lives inside `attendance`, and `task` reaches into `attendance` only to ask for it.
- `LayerStructureTest` forbids repository and entity imports across features. It does not look for cycles, and it reads imports only, so an entity named inside a JPQL string passes unseen: `AppUserRepository` joins `InternProfile` that way.

With the target modules, before any resolution, all eight form one strongly connected group.

## Decision

1. The packages are those of `ARC-005`: `attendance`, `calendar`, `identity`, `internship`, `notification`, `project` and `reporting` below `feature`; `platform` for code that belongs to no single feature; `config` for wiring only.
2. The dependencies among `platform` and the features form a directed acyclic graph. `platform` depends on no feature. `config` may depend on everything, and nothing depends on `config`.
3. The repository and entity boundary of `ARC-006` also holds for `platform`.
4. The ban on one-implementation abstraction layers keeps one exception: an interface that a module declares and itself calls, every implementation of which lives in a different module that would still depend on the declaring module if that implementation were removed.

Computed layers, bottom first:

| Layer | Modules |
|---|---|
| 0 | `platform` |
| 1 | `identity` |
| 2 | `calendar`, `notification` |
| 3 | `internship` |
| 4 | `attendance`, `project` |
| 5 | `reporting` |

## Interfaces that use the exception

Every interface relying on decision 4 is listed here. A new one must meet the four
conditions and be added to this table in the same change.

| Interface | Declared and called by | Implemented by | Dependency that exists without it | Built in |
|---|---|---|---|---|
| `InternshipLifecycleReadiness`: does an Intern still hold a leadership term or own an unfinished Task (`ACC-022`) | `internship` | `project` | `project` needs internship state for invitation and direct addition (`PRJ-017`) and locks the Intern profile for work-log totals (`DB-008`); 42 code references today | step 6 of `D28` |
| Calendar change impact: what a change to the calendar affects (`CAL-007`, `CAL-008`) | `calendar` | `attendance`, `project` | `attendance` needs days off (`CAL-009`, `LEV-002`), the business date and the policy; `project` needs days off for due dates (`CAL-009`) and the business date | when `CAL-007` and `CAL-008` are implemented; if built as two interfaces, each gets its own row |

## Rationale

- **Acyclic is the industry rule, not a local taste.** Spring Modulith's `ApplicationModules.verify()` rejects any cycle at the application module level; Shopify's Packwerk requires the dependency graph within the package system to be acyclic.
- **The boundaries match those of a large open-source system.** Odoo 17.0 keeps accounts (`res.users`, in `base`) apart from people records (`hr.employee`); its `resource.calendar` holds timezone, working hours and global leaves in one model; its outgoing mail server configuration sits in `base`. It separates attendance from time off, but only because its attendance has no period close; here leave, corrections and exceptions share one period and one decision history, so they stay together.
- **The exception exists for one reason.** Without it, the owner of `ACC-022` cannot check the rule itself: today `AccountService.completeInternship` trusts a guard its caller computes, which is safe only while convention keeps one caller. The ban targets speculative layering; the four conditions admit only an interface that would otherwise have to be a direct call closing a cycle.

## Consequences

- Two existing tests guard the layout this decision replaces and must change with the code in step 6. `LayerStructureTest` approves the old feature list; it takes the modules of `ARC-005`, adds `platform` to the approved root packages, and extends the repository and entity check to `platform`. `AttendanceLayerStructureTest` requires `AttendancePolicy` inside `attendance`, which this decision moves to `calendar`; `AttendanceCurrentUserService` stays in `attendance`. Until then neither protects `ARC-005`, and the constitution does not credit them for it.
- A cycle test is written first in step 6, in plain Java without a new dependency. It reads type references with comments removed and string literals kept, fails on a cycle or a reference into `config`, and checks the four conditions for every interface listed above. It starts with the list of violations known at that moment; every task of step 6 shortens it, and the last leaves it empty. Until the test exists the constitution records three gaps.
- `scripts/module-boundaries.cjs` describes the structure before the split. After step 6 its cycle check becomes that test or the script is retired.
- The role type belongs to `platform`, because the authorization policy decides on the actor's role (`AUTH-012`) and `platform` depends on no feature. `GlobalRole` leaves the account package in step 6.
- The constitution becomes `2.0.0`: obligations are added and one is weakened by the exception.
- The technical plans written against the old packages are superseded; the platform plan is revised in step 8 of `D28`.

## What this does not decide

- The order in which business slices are implemented.
- Whether the calendar change impact is one interface or two.
- Any rule text beyond `ARC-005`, `ARC-006` and `AC-ARC-001`.
