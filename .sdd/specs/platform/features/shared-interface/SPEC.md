# Shared interface Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `platform` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Provide the common server-rendered shell, navigation, theme and accessibility contract.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Every UI user; each business feature supplies its authorized view and actions.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Shell, navigation and theme

UI-001 through UI-007 define common layout and local preferences.

### Accessible components and charts

The remaining UI rules govern presentation, keyboard/focus behavior, icons and alternative chart content.

### Role-oriented screens

UI-019 supplies the page/workflow contract; the module screen inventory and each business feature supply context and permissions.

### Canonical feature rules

| ID | Requirement |
|---|---|
| UI-001 | THE system SHALL present a quiet, high-density operations shell built from Tailwind tokens and Thymeleaf fragments, and SHALL NOT import React or a React component runtime. |
| UI-002 | THE system SHALL treat desktop as the supported v1 interface target, with a roughly 16rem fixed sidebar collapsible to a roughly 4rem icon rail. Mobile and tablet behavior is best-effort and SHALL NOT be required to reach workflow parity or provide a dedicated navigation pattern. |
| UI-003 | THE system SHALL persist the sidebar collapse state in `localStorage`, SHALL generate navigation from authorization scope, and SHALL NOT show an action the authenticated user cannot perform. |
| UI-004 | THE system SHALL place the sidebar toggle, breadcrumb or page title, notification access, and contextual primary actions in the content header, and SHALL expose profile, theme, and logout in the lower sidebar account area. |
| UI-005 | THE system SHALL use near-white and near-black canvases, slightly contrasting sidebar and panel surfaces, one-pixel neutral borders, 10 to 12 pixel radii, compact controls, restrained shadows, tabular numerals, and muted secondary text. |
| UI-006 | WHILE dark mode is active, THE system SHALL use the near-black canvas and charcoal panel hierarchy rather than inverting the light palette, and SHALL reserve the accent color for status, focus, validation, and a small number of primary actions. |
| UI-007 | WHEN a visitor arrives for the first time, THE system SHALL follow the system color preference. THE system SHALL offer a Light, Dark, and System selector, SHALL store the override in the browser, and SHALL apply it before first paint so that no theme flash occurs. THE system SHALL NOT require a database table for theme preference. |
| UI-008 | THE system SHALL provide reusable fragments for the shell, navigation, button, input, select, checkbox, cards, metric cards, badges, tabs, tables, pagination, alerts, confirmation dialog, empty state, skeleton state, and notification menu. |
| UI-009 | THE system SHALL pin Lucide Static 1.27.0 as a build dependency and reduce it to a local build-time SVG sprite. THE system SHALL NOT use an icon CDN, an icon font, a runtime DOM replacement pass, or a React adapter. |
| UI-010 | WHERE an icon sits beside visible text, THE system SHALL mark it decorative and hide it from assistive technology. WHERE a control carries only an icon, THE system SHALL give it an accessible name, a visible tooltip, keyboard focus, and a target of at least 24 by 24 CSS pixels, the WCAG 2.2 AA minimum under success criterion 2.5.8. |
| UI-011 | THE system SHALL use Chart.js 4.5.1 only for meaningful attendance and Project trends, and SHALL give every canvas an accessible name and an adjacent text or table summary, because canvas content is not inherently available to a screen reader. |
| UI-012 | THE system SHALL draw charts from the theme tokens, SHALL honor a reduced-motion preference, and SHALL NOT let a chart be the only representation of a value or status. |
| UI-013 | THE system SHALL present the interface in English only in v1, SHALL display business dates as `dd/MM/yyyy`, and SHALL display times in 24-hour local form with timezone context where ambiguity matters. |
| UI-014 | THE system SHALL give every form field an associated label, inline field errors, an error summary, retained safe input after validation, keyboard operation, and visible focus. WHERE the selected role is not `INTERN`, THE system SHALL disable and clear the role-dependent Intern fields while server validation remains authoritative. WHERE policy input is bound to a month, THE system SHALL use a native month control rather than invite an arbitrary invalid date. THE system SHALL NOT communicate status through color alone. |
| UI-015 | THE system SHALL keep tables fully usable at supported desktop widths. WHERE the viewport is narrower, THE system SHOULD prioritize columns, wrap, or scroll horizontally to avoid avoidable corruption, and complete mobile workflow support remains outside v1 acceptance. |
| UI-016 | WHEN a terminal or destructive action is requested, including internship withdrawal, Project completion, Project cancellation, Project deletion, account deactivation, direct member removal, membership-exit approval, Task soft-delete, and SMTP retirement, THE system SHALL require an explicit confirmation describing the consequences. WHERE the action is exit approval, THE system SHALL show replacement and unfinished-Task readiness; WHERE it is a transfer, THE system SHALL show the selected Task count and the recipient. |
| UI-017 | THE system SHALL NOT use decorative gradients, glass effects, card-within-card repetition, oversized marketing headings, remote fonts or assets, or a chart that carries no information. |
| UI-018 | THE system SHALL meet WCAG 2.2 AA contrast in both light and dark themes: at least 4.5:1 for normal text, at least 3:1 for large text and meaningful non-text boundaries, and a visible focus indicator at 3:1 against adjacent colors. |
| UI-019 | THE system SHALL provide Leader invitation list, create and revoke; Intern invitation accept and decline; Leader removal request; member leave and cancel; persistent pending-exit warnings; a Leader-only side drawer for repeated multi-Task single-recipient transfer batches; and an owning-Mentor decision surface. THE system SHALL provide separate Intern My Leave, My Corrections, and My Exceptions workflows and separate Mentor Leave Decisions, Correction Decisions, and Exception Decisions workflows, each Mentor queue listing only the Interns that Mentor is responsible for, and each placing the actionable queue before retained history and showing the monthly leave balance defined by `LEV-004`. THE system SHALL give an Admin focused Account Directory, Detail and Edit workflows, Attendance Policy with History, Global Calendar with History, Holiday Import with provider History, and SMTP with History. THE system SHALL keep Admin dashboard content account and configuration only, without an Active Projects metric or a report-like Project or Task summary. THE system SHALL give an Admin read-only dedicated Attendance, Project or Task, and Daily Project Work Report navigation, as `RPT-004`, `RPT-005`, and `RPT-011` grant, and SHALL limit each report's navigation to the scopes its rule grants. THE system SHALL keep the global Daily entry visible to Mentors and Admins, and SHALL show it to an active Intern only WHILE current leadership makes at least one `PLANNED` or `ACTIVE` Project eligible; Project-detail Daily generation SHALL remain available to the current Leader only. THE system SHALL redirect `/admin/settings` to `/admin/attendance-policies` and `/attendance/requests` to `/attendance/leave`. THE system SHALL provide one authorized Project History tab. Existing mockups are illustrative and SHALL NOT override a numbered requirement. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

No new business tables. Local preferences and shared rendering assets follow the existing UI rules.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [platform](../../MODULE.md) | Authorization decision, CSRF and validation presentation contracts | [AUTH-012](../../MODULE.md), [SEC-001](../../MODULE.md), [ERR-001](../../MODULE.md) |

### Related workflows and joint checks

- Shared contracts and cross-module consumers are listed in [MODULE.md](../../MODULE.md).

Owning features provide their authorized page data. The shared interface contract
does not grant platform code access to feature repositories or entities.

## 6. Error Handling

Apply each refusal, deadline, conflict and delivery-failure clause in the numbered rules
above, together with [module error handling](../../MODULE.md#6-error-handling).
Platform §21 supplies common failure behavior; an operation summary does not override it.

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Shell, navigation and theme](#shell-navigation-and-theme) | Authorized user navigates the shared shell and persists local theme preferences | [UI-001](SPEC.md), [UI-002](SPEC.md), [UI-003](SPEC.md), [UI-004](SPEC.md), [UI-005](SPEC.md), [UI-006](SPEC.md), [UI-007](SPEC.md), [AUTH-012](../../MODULE.md) | [AC-UI-001](SPEC.md), [AC-UI-002](SPEC.md), [AC-UI-005](SPEC.md) | Exercise role-specific navigation; presentation never replaces service authorization. |
| [Accessible components and charts](#accessible-components-and-charts) | Keyboard/screen-reader user can operate controls and read chart alternatives | [UI-008](SPEC.md), [UI-009](SPEC.md), [UI-010](SPEC.md), [UI-011](SPEC.md), [UI-012](SPEC.md), [UI-013](SPEC.md), [UI-014](SPEC.md), [UI-015](SPEC.md), [UI-016](SPEC.md), [UI-017](SPEC.md), [UI-018](SPEC.md) | [AC-UI-003](SPEC.md), [AC-UI-004](SPEC.md), [AC-UI-005](SPEC.md) | Apply the declared desktop accessibility and contrast criteria; preserve non-JavaScript chart content. |
| [Role-oriented screens](#role-oriented-screens) | Each role reaches the specified operational screens and history surfaces | [UI-019](SPEC.md) | [AC-UI-005](SPEC.md) | Review each owning feature workflow with its authorization cases; one visual comparison is not proof of all actions. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-UI-001 | UI-001–UI-007 | First visit follows dark OS, user selects Light, then selects System | No initial theme flash; local override behaves as selected; no account preference row is written. |
| AC-UI-002 | UI-002–UI-004 | Sidebar is collapsed and restored on a supported desktop viewport | Desktop becomes an icon rail, the state persists locally, and all authorized navigation remains keyboard-accessible. Mobile behavior is not part of this acceptance gate. |
| AC-UI-003 | UI-009–UI-010 | Screen reader reaches icon-only actions | Local Lucide sprite loads; decorative icons are hidden; controls have distinct accessible names/tooltips. |
| AC-UI-004 | UI-011–UI-012 | Chart JavaScript disabled or canvas unavailable | Adjacent textual/table summary still conveys the same trend values. |
| AC-UI-005 | UI-005–UI-019 | Light/dark pages, account/configuration-only Admin dashboard and focused Admin configuration/history pages, role-aware report navigation, persistent exit warnings, transfer drawer, Project History, and separate Intern/Mentor Leave/Correction workflows are reviewed at supported desktop widths | Reference hierarchy, measured AA contrast, focus, keyboard operation, selected/remaining counts, actionable-before-history ordering, confirmation, secret redaction, wrapping, non-color status requirements, presence of Admin Attendance, Project/Task, and Daily report navigation, absence of the Active Projects metric, and conditional current-Leader Daily navigation pass. Legacy settings/request routes redirect as specified. Narrow-screen behavior receives best-effort smoke review only and does not block v1 acceptance. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

The screen inventory and visual references remain in MODULE.md for use across all features. They describe the existing UI contract and do not create additional feature modules.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
