# Specification map

Read in this order: [constitution](../constitution.md), [platform shared contract](platform/MODULE.md),
the owning module's MODULE.md, then the relevant feature SPEC.md. [Decisions](../decisions.md)
explain choices; [plan.md](../../plan.md) is the only progress tracker.

## Root layout

```text
.sdd/specs/
├── README.md
├── _template.md
├── identity/
│   ├── MODULE.md
│   ├── CHANGELOG.md
│   └── features/
│       ├── authentication/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── account-lifecycle/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── password-management/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── first-admin-bootstrap/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
├── internship/
│   ├── MODULE.md
│   ├── CHANGELOG.md
│   └── features/
│       ├── lifecycle/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── responsible-mentor/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
├── calendar/
│   ├── MODULE.md
│   ├── CHANGELOG.md
│   └── features/
│       ├── attendance-policy/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── global-calendar/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── holiday-import/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
├── attendance/
│   ├── MODULE.md
│   ├── CHANGELOG.md
│   └── features/
│       ├── punches-and-results/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── leave/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── missed-checkout-correction/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── attendance-exception/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── period-finalization/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
├── project/
│   ├── MODULE.md
│   ├── CHANGELOG.md
│   └── features/
│       ├── lifecycle/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── membership-and-leadership/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── invitations/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── membership-exit/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── task-management/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── work-logs-and-effort/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
├── notification/
│   ├── MODULE.md
│   ├── CHANGELOG.md
│   └── features/
│       ├── inbox/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── email-delivery/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
├── reporting/
│   ├── MODULE.md
│   ├── CHANGELOG.md
│   └── features/
│       ├── attendance-report/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── project-task-report/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
│       ├── daily-work-report/
│       │   ├── SPEC.md
│       │   └── CHANGELOG.md
└── platform/
    ├── MODULE.md
    ├── CHANGELOG.md
    └── features/
        ├── smtp-configuration/
        │   ├── SPEC.md
        │   └── CHANGELOG.md
        ├── shared-interface/
        │   ├── SPEC.md
        │   └── CHANGELOG.md
        ├── architecture/
        │   ├── SPEC.md
        │   ├── CHANGELOG.md
        │   ├── PLAN.md
        │   └── TASKS.md
        ├── security/
        │   ├── SPEC.md
        │   ├── CHANGELOG.md
        │   ├── PLAN.md
        │   └── TASKS.md
        ├── authorization/
        │   ├── SPEC.md
        │   ├── CHANGELOG.md
        │   ├── PLAN.md
        │   └── TASKS.md
        └── data-model/
            ├── SPEC.md
            ├── CHANGELOG.md
            ├── PLAN.md
            └── TASKS.md
```

## Module and feature entry points

| Module | Cohesive features |
|---|---|
| [identity](identity/MODULE.md) | [Authentication](identity/features/authentication/SPEC.md), [Account lifecycle](identity/features/account-lifecycle/SPEC.md), [Password management](identity/features/password-management/SPEC.md), [First Admin bootstrap](identity/features/first-admin-bootstrap/SPEC.md) |
| [internship](internship/MODULE.md) | [Internship lifecycle](internship/features/lifecycle/SPEC.md), [Responsible Mentor](internship/features/responsible-mentor/SPEC.md) |
| [calendar](calendar/MODULE.md) | [Attendance policy](calendar/features/attendance-policy/SPEC.md), [Global calendar](calendar/features/global-calendar/SPEC.md), [Holiday import](calendar/features/holiday-import/SPEC.md) |
| [attendance](attendance/MODULE.md) | [Punches and results](attendance/features/punches-and-results/SPEC.md), [Leave](attendance/features/leave/SPEC.md), [Missed-checkout correction](attendance/features/missed-checkout-correction/SPEC.md), [Attendance exception](attendance/features/attendance-exception/SPEC.md), [Period finalization and reopening](attendance/features/period-finalization/SPEC.md) |
| [project](project/MODULE.md) | [Project lifecycle](project/features/lifecycle/SPEC.md), [Membership and leadership](project/features/membership-and-leadership/SPEC.md), [Project invitations](project/features/invitations/SPEC.md), [Membership exit and transfer](project/features/membership-exit/SPEC.md), [Task management](project/features/task-management/SPEC.md), [Work logs and effort](project/features/work-logs-and-effort/SPEC.md) |
| [notification](notification/MODULE.md) | [Notification inbox](notification/features/inbox/SPEC.md), [Notification email delivery](notification/features/email-delivery/SPEC.md) |
| [reporting](reporting/MODULE.md) | [Attendance reports](reporting/features/attendance-report/SPEC.md), [Project and Task reports](reporting/features/project-task-report/SPEC.md), [Daily Project Work Report](reporting/features/daily-work-report/SPEC.md) |
| [platform](platform/MODULE.md) | [SMTP configuration](platform/features/smtp-configuration/SPEC.md), [Shared interface](platform/features/shared-interface/SPEC.md), [Architecture](platform/features/architecture/SPEC.md), [Authorization](platform/features/authorization/SPEC.md), [Security](platform/features/security/SPEC.md), [Data model](platform/features/data-model/SPEC.md) |

## What belongs at each level

- **Platform MODULE.md:** governance, shared integration secrets, delivery/test standards and
  error conventions. The architecture, the authorization model, the security controls and the
  complete data model are the platform features Architecture, Authorization, Security and Data
  model (`D40`).
- **Business MODULE.md:** shared rules, module data ownership, workflows that cross
  features, integration scenarios, dependency checks and shared unresolved questions.
- **Feature SPEC.md:** one cohesive outcome, its operation headings, canonical rules,
  actors, use cases, acceptance scenarios, data/dependency pointers and open questions.
- **Operation heading:** Login, Logout, Complete internship, Withdraw internship, etc.
  A button or endpoint does not automatically become a feature, module or Git branch.

A rule or acceptance row has exactly one canonical location. Some rules cover several
features and stay shared: Password management, for example, references the same password,
token and session rules that account activation and authentication use. A cross-feature
scenario stays in MODULE.md instead of being copied to every feature it exercises.

The legacy A1–A5/P1–P6 labels map to attendance/project feature folders. Platform F1–F6
still classify shared responsibilities; only SMTP configuration and the shared interface
have feature documents. The history each module carries from before the `D34` split is in
its own `CHANGELOG.md`, under *Retained history* (`D38`).

## What the Status field means

Every `MODULE.md` and `SPEC.md` carries `**Status:**` in its header. It states whether the
document's own content is settled. It is not a claim about the code, which is tracked in
[`plan.md`](../../plan.md) alone.

| Status | Meaning | When it may be used |
|---|---|---|
| `DRAFT` | Being written. Nothing in it may be planned against. | The template's default, until a first review. |
| `INHERITED BASELINE; SEE OPEN QUESTIONS` | Its rules were approved earlier, but at least one question affecting it is open. | Exactly while the document's own *Notes / Open Questions*, its module's, or [§22.3](platform/MODULE.md#223-open-questions) names something unsettled that this document needs. |
| `APPROVED BUSINESS BASELINE` | The maintainer has accepted every rule in it and no question affecting it is open. | Exactly while no such question is open. |

The maintainer is the approving authority (§1.1); the instructor reviews decisions and is
not a gate on them (`D26`). Two things this field never means: that the code implements the
document, and that the rules are right for the laboratory. The second is what §22.2 reserves
for a person, and no mechanical check can supply it.

A document that closes its last open question moves to `APPROVED BUSINESS BASELINE` in the
same change, with a changelog entry. A document that acquires a new open question moves back.
The document hierarchy check enforces that the field and the open questions agree.

## Planning and branches

### Reference practices and local mapping

This layout adapts public product guidance; it does not claim to reproduce the
internal engineering process of Microsoft or Atlassian. References checked on
21 September 2026:

| Guidance | Application to Lab Timesheet |
|---|---|
| [Azure Boards: features and epics](https://learn.microsoft.com/en-us/azure/devops/boards/backlogs/define-features-epics?view=azure-devops) | Group related user outcomes into a feature; decompose delivery work into smaller stories and tasks. |
| [Azure Boards: Agile workflow](https://learn.microsoft.com/en-us/azure/devops/boards/work-items/guidance/agile-process-workflow?view=azure-devops) | Describe the actor, goal and reason, then clear acceptance conditions. Technical implementation belongs in PLAN/TASKS. Shared technical capabilities may be architectural work. |
| [Jira: default hierarchy](https://support.atlassian.com/jira-cloud-administration/docs/configure-the-issue-type-hierarchy/) | The default levels are Epic, standard work items and Subtask. Jira does not require the extra Feature level used by Azure's Agile hierarchy. |
| [Atlassian: acceptance criteria](https://www.atlassian.com/work-management/project-management/acceptance-criteria/) | Use observable outcomes with pass/fail conditions; a title or requirement citation alone is not an acceptance test. |
| [Azure Boards: dependencies](https://learn.microsoft.com/en-us/azure/devops/boards/plans/track-dependencies?view=azure-devops) | Scheduling dependencies need direction. Shared tests or related screens alone do not establish a predecessor. |

The mapping below is a local interpretation for documentation, not a requirement to
adopt either tracker. Progress still lives only in plan.md.

| Repository concept | Possible Azure Boards mapping | Possible default Jira mapping |
|---|---|---|
| Long-lived business module, such as identity | Area/ownership grouping | Component/ownership grouping |
| Cohesive capability, such as Authentication | Feature under a delivery Epic when appropriate | Epic containing outcome-focused stories when appropriate |
| Bounded user outcome, such as ending an authenticated session | User Story with acceptance criteria | Story with acceptance criteria |
| Implementation work derived from the approved design | Task | Subtask under the chosen standard work item |

A module is an architectural boundary; an Epic groups delivery scope. Their lifetimes
and purposes differ. The example mappings do not turn every module into an Epic,
every operation heading into a complete story, or every feature into one sprint.

### What a feature contributes to planning

Each feature's operation table links the actor/outcome to canonical rules, existing
acceptance scenarios and any missing decision or evidence. Shared scenarios stay in
their canonical contract. A reference is not a claim that every clause is covered.

In section 5, **Required contracts** names the facts or behavior a feature consumes,
with their owner and rule trace. **Related workflows and joint checks** names interactions
that need coordination or combined evidence. Neither is an implementation call graph.
The technical plan selects actual predecessor work from the required contracts; the
existing implementation may already supply one. Do not turn every reference into a
blocking task dependency.

The constitution remains the authority for Definition of Done. This operation map
does not create a second completion checklist or a second progress tracker.

Every PLAN.md and TASKS.md belongs to one feature and sits beside its SPEC.md; no module keeps
one of its own, with no exception (`D40`). Work on a rule a MODULE.md holds is planned in the
feature whose code it changes. A schema change shared by several features is planned once in the
[Data model](platform/features/data-model/SPEC.md) feature, and each affected feature's plan
points to it. Create a feature's PLAN.md only when its technical design is written, then
TASKS.md when that design can be broken into verifiable work. Neither file is an empty
placeholder here. Read the shared module constraints before planning one feature independently.
The [Architecture plan](platform/features/architecture/PLAN.md) holds the completed part A
after `A-12` closed it; it is not blanket approval for all new features.

A targeted implementation follows OPS-019 on an isolated work/fix/<area>/<what> branch
from verified main, with branch-name collision checks. Choose a cohesive change that can
be reviewed and tested; this folder tree does not require a permanent branch per folder
or a separate branch for Login and Logout. Documentation restructuring uses the docs area.

The split preserves the business baseline but does not close its open questions. In
particular, invitation/exit states, account/delivery states and incomplete password
operation acceptance still need attention before approving their dependent plans.
`D35` settles Task creation/unblocking, cancelled-Project internship readiness and
current-session logout in the owning feature contracts. `D36` additionally excludes
soft-deleted Tasks from internship readiness, preserving their history. Settled behavior can now be
designed without waiting for unrelated questions. D34 records the organization and
rollback point; [D35](../decisions.md#d35-which-workflow-and-logout-defaults-does-the-product-adopt) records the subsequent business decisions.
