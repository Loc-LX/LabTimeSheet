# Traceability: requirement to test

Which numbered rule in
[the requirements](../requirements.md) is protected by
which test class, and which rules are protected by nothing.

Test classes live under `src/test/java`. A rule with no class in the
right-hand column can be changed without any test turning red.

**This table is a snapshot, and it will go stale.** It was extracted from 150
Markdown evidence records on 11 September 2026, one day before
[`ADR-004`](../rfcs/ADR-004-test-evidence-moves-into-the-test.md) removed them.
The rule it replaced them with, `TST-005`, puts the identifiers in the test
source, where they can be read back mechanically and a table like this one is
generated rather than maintained.

Six of the 122 classes carry that trace today. The other 116 predate the rule, so
for them this file is the only mapping and nothing detects it drifting from the
tests. Treat a row here as a claim to verify, not a fact.

Two checks have since been run against it, and both found it wrong. On
12 September `ARC-008` was found to claim two classes for a reviewed DDL that
lives in the separate documentation repository and has never been tracked here,
which no test here can observe; its row now reads `none`. On 13 September every
rule the file called unprotected was re-checked, and 31 of the 37 were
misfiled in one direction or the other. Both passes are recorded under
[Re-derivation](#re-derivation-13-september-2026).

## Coverage

**Re-derived on 13 September 2026.** The 37 rules this file listed as unprotected
were checked one by one against the test sources rather than against the extract.
Twenty-three were already protected, four were protected in one half of the rule
only, four bind people rather than the system and belong in the second table, and
six are genuinely unprotected. The counts below are the corrected ones; what the
check found is recorded under [Re-derivation](#re-derivation-13-september-2026).

| | Count |
|---|---:|
| Numbered rules in the specification | 269 |
| Rules with at least one test class | 248 |
| Rules a test could protect but none does | 6 |
| Rules no test can protect, being process or scope statements | 15 |
| Distinct test classes named below | 93 |
| Test classes that exist under `src/test/java` | 122 |

The last two rows differ by 29. Those classes are named by no row here and are
listed under the re-derivation.

## Coverage by rule group

Third column is rules a test could protect and none does. Fourth is rules no test
can protect. The three right-hand columns sum to the rule count on every row, and
the columns themselves sum to 248, 6 and 15.

| Group | Rules | Tested | Untested | Untestable |
|---|---:|---:|---:|---:|
| `ACC` | 25 | 24 | 0 | 1 |
| `ARC` | 8 | 7 | 0 | 1 |
| `ATT` | 18 | 18 | 0 | 0 |
| `AUTH` | 11 | 11 | 0 | 0 |
| `CAL` | 9 | 9 | 0 | 0 |
| `COR` | 9 | 8 | 1 | 0 |
| `DB` | 13 | 13 | 0 | 0 |
| `ERR` | 7 | 5 | 2 | 0 |
| `GOV` | 16 | 4 | 2 | 10 |
| `INT` | 10 | 10 | 0 | 0 |
| `LEV` | 12 | 11 | 1 | 0 |
| `NOT` | 10 | 10 | 0 | 0 |
| `OPS` | 21 | 19 | 0 | 2 |
| `PRJ` | 22 | 22 | 0 | 0 |
| `RPT` | 13 | 13 | 0 | 0 |
| `SEC` | 14 | 14 | 0 | 0 |
| `TSK` | 22 | 21 | 0 | 1 |
| `TST` | 10 | 10 | 0 | 0 |
| `UI` | 19 | 19 | 0 | 0 |

## Rules no test protects

These six rules describe behavior a test could assert, and none does. Each is a
place where the code can drift away from the requirement without anything
failing. Two of the six were already known: the constitution lists `GOV-004` and
`GOV-014` in its own gap table and says what would close each.

| Rule | Group | What is missing |
|---|---|---|
| `COR-003` | COR | The submission deadline is implemented as `scheduledEnd.plusSeconds(24 * 60 * 60)` in `AttendanceCorrectionApplicationService`, and the identifier `submissionDeadline` appears in no test file. The rule states a hand-checkable value, 15:30 the following day under the seeded defaults. |
| `LEV-009` | LEV | Nothing asserts the same-day submission boundary. The rule fixes it exactly: a request whose first counted date is today is valid before 08:30 and invalid at or after it. |
| `ERR-006` | ERR | Nothing asserts that a failed report returns an error, persists no partial report, and closes the stream. No test method name is even adjacent to it. |
| `ERR-007` | ERR | `PlatformFoundationTest#flywayCreatesApprovedPostgresCatalog` asserts the success path. Nothing simulates a failed migration, and `ProductionReadinessTest` fails readiness for other inputs only. |
| `GOV-004` | GOV | Relies on the two domains staying in separate features with no shared read path. A test asserting that no reporting query joins attendance to work logs would close it. |
| `GOV-014` | GOV | Relies on schema constraints in `V1__baseline.sql`. A test asserting that no repository exposes a hard-delete method for accounts, Projects, memberships, or Tasks would close it. |

Four more rules are protected in one half and unprotected in the other. They are
counted as tested above, because a rule with a test is not a rule with no test,
but each is a smaller version of the same gap.

| Rule | Half that is protected | Half that is not |
|---|---|---|
| `LEV-008` | An active Mentor may approve or reject. | That an Admin may not decide leave. This is the authorization half. |
| `PRJ-018` | Eligibility, the issuing leadership term, and the authenticated response. | At most one pending invitation per eligible Intern, and that an invitation never expires by time. |
| `PRJ-002` | Activation and completion, from both the entity and the service side. | Nothing attempts to reopen a `COMPLETED` Project. |
| `DB-001` | One column's type, `tasks.estimated_minutes`. | The schema-wide type policy, including the clause forbidding PostgreSQL enums, which one catalog query would settle. |

These 15 are process or scope statements. They bind people and
review, not code, so the absence of a test is correct rather than a gap.
See [`.sdd/constitution.md`](../constitution.md).

Four of them arrived on 13 September 2026 from the list above. The specification
already declared all four as deliberately not written in EARS, because they bind
a contributor or record an operational instruction rather than describe the
system; `TSK-017` is explicit that it "adds nothing of its own" and exists only to
point the reader at `CAL-009` and `GOV-004`. `ARC-008` is the fifth addition and
had been dropped from this list by accident when its row changed to `none`, which
is why the old counts summed to 268 rather than 269.

| Rule | Why no test can protect it |
|---|---|
| `GOV-001` | authority order between documents |
| `GOV-002` | terminology decision |
| `GOV-003` | statement of intent for v1 |
| `GOV-006` | requires a reviewed decision for unspecified features |
| `GOV-007` | declared exclusions |
| `GOV-008` | declared exclusions |
| `GOV-009` | declared exclusions |
| `GOV-010` | pipeline posture while no deployment host exists |
| `GOV-015` | declared exclusions for the effort-planning slice |
| `GOV-016` | one canonical location per requirement |
| `ARC-008` | a completed one-time adaptation of a DDL never tracked in this repository |
| `ACC-004` | an accepted operational risk during bootstrap, not a behavior |
| `OPS-002` | the local development loop a contributor sets up |
| `OPS-010` | a backup and upgrade procedure performed by an operator |
| `TSK-017` | a pointer to `CAL-009` and `GOV-004`, carrying no rule of its own |

`GOV-013` left this list on the same date and moved to the tested column. It had
been recorded here as a process statement, and it is not one: it requires every
mutable aggregate update to be transactional under optimistic locking, and the
constitution names two tests for it. Both exist and both carry the cited method,
`BootstrapIntegrationTest#concurrentBootstrapCreatesExactlyOneAdminAndPermanentlyCloses`
and `AccountRecoveryLockOrderIntegrationTest#concurrentAccountFirstConsumptionAndIssuanceComplete`.

## Rule to test class

| Rule | Test classes |
|---|---|
| `GOV-001` | none |
| `GOV-002` | none |
| `GOV-003` | none |
| `GOV-004` | none |
| `GOV-005` | `AttendancePersistenceIntegrationTest` |
| `GOV-006` | none |
| `GOV-016` | none |
| `GOV-007` | none |
| `GOV-008` | none |
| `GOV-009` | none |
| `GOV-010` | none |
| `GOV-015` | none |
| `GOV-011` | `ApplicationTimeZoneIntegrationTest`, `AttendanceServiceTest` |
| `GOV-012` | `AttendanceServiceTest` |
| `GOV-013` | `BootstrapIntegrationTest`, `AccountRecoveryLockOrderIntegrationTest` |
| `GOV-014` | none |
| `ARC-001` | `ApplicationTimeZoneIntegrationTest`, `LayerStructureTest`, `PlatformFoundationTest` |
| `ARC-002` | `LayerStructureTest`, `PlatformFoundationTest`, `ProjectPersistenceStructureTest`, `ReportingDependencyContractTest` |
| `ARC-003` | `ApplicationTimeZoneIntegrationTest`, `LayerStructureTest`, `PlatformFoundationTest` |
| `ARC-004` | `LayerStructureTest`, `PlatformFoundationTest`, `UiContractWebTest` |
| `ARC-005` | `AttendanceLayerStructureTest`, `LayerStructureTest`, `PlatformFoundationTest`, `ProjectPersistenceStructureTest` |
| `ARC-006` | `LayerStructureTest`, `PlatformFoundationTest` |
| `ARC-007` | `LayerStructureTest`, `PlatformFoundationTest`, `ProjectPersistenceStructureTest` |
| `ARC-008` | none |
| `ACC-001` | `BootstrapIntegrationTest`, `SecurityResponseIntegrationTest` |
| `ACC-002` | `BootstrapIntegrationTest` |
| `ACC-003` | `BootstrapIntegrationTest` |
| `ACC-004` | none |
| `ACC-005` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `DashboardControllerWebTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-006` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `DashboardControllerWebTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-007` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `DashboardControllerWebTest`, `RoleDashboardWebIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-008` | `AccountActivationIntegrationTest`, `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-009` | `AccountIdentityCorrectionIntegrationTest`, `AccountWebIntegrationTest`, `AuthenticationWebIntegrationTest`, `BootstrapIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-010` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-011` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `PasswordRecoveryWebIntegrationTest`, `PasswordResetIntegrationTest`, `SmtpIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-012` | `AccountActivationIntegrationTest`, `AccountRecoveryIntegrationTest`, `AccountWebIntegrationTest`, `ActivationResendWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `ACC-013` | `AccountRecoveryIntegrationTest`, `ActivationResendWebIntegrationTest` |
| `ACC-014` | `AccountActivationIntegrationTest`, `AccountLifecycleIntegrationTest`, `AccountSessionInvalidationWebIntegrationTest`, `AccountWebIntegrationTest`, `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest` |
| `ACC-015` | `AccountLifecycleIntegrationTest`, `AccountSessionInvalidationWebIntegrationTest` |
| `ACC-016` | `AccountLifecycleIntegrationTest`, `AccountSessionInvalidationWebIntegrationTest` |
| `ACC-017` | `AccountIdentityCorrectionIntegrationTest` |
| `ACC-018` | `AccountIdentityCorrectionIntegrationTest`, `AccountLifecycleIntegrationTest`, `AccountRecoveryLockOrderIntegrationTest`, `AccountSessionInvalidationWebIntegrationTest`, `PasswordResetIntegrationTest` |
| `ACC-019` | `AccountActivationIntegrationTest`, `AccountIdentityCorrectionIntegrationTest`, `AccountWebIntegrationTest`, `E2eProfileIntegrationTest`, `EligibleInternOptionIntegrationTest`, `InternMutationEligibilityIntegrationTest`, `InternWorkWindowIntegrationTest`, `TimeConfigurationTest` |
| `ACC-020` | `AccountActivationIntegrationTest`, `BootstrapIntegrationTest`, `E2eProfileIntegrationTest`, `EligibleInternOptionIntegrationTest`, `TimeConfigurationTest` |
| `ACC-021` | `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest`, `InternWorkWindowIntegrationTest` |
| `ACC-022` | `ProjectServiceIntegrationTest` |
| `ACC-023` | `InternMutationEligibilityIntegrationTest` |
| `ACC-024` | `HistoricalInternReportingWindowIntegrationTest`, `AccountIdentityCorrectionIntegrationTest` |
| `ACC-025` | `HistoricalInternReportingWindowIntegrationTest` |
| `AUTH-001` | `AccountWebIntegrationTest`, `AttendanceControllerTest`, `DashboardControllerWebTest`, `EligibleInternOptionIntegrationTest`, `InternMutationEligibilityIntegrationTest`, `NotificationControllerWebTest`, `ProjectControllerTest`, `ProjectEntityTest`, `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest`, `ProjectTaskMutationContextTest`, `ProjectTaskReportServiceTest`, `TaskControllerTest`, `TaskCreationIntegrationTest` |
| `AUTH-002` | `AccountWebIntegrationTest`, `AttendanceControllerTest`, `AttendanceTemplateIntegrationTest`, `CalendarAuthorizationWebIntegrationTest`, `DashboardControllerWebTest`, `InternMutationEligibilityIntegrationTest`, `NotificationControllerWebTest`, `ProjectControllerTest`, `ProjectInvitationExitIntegrationTest`, `ProjectTaskFormAccessibilityWebTest`, `ProjectTaskReportServiceTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `UiContractWebTest` |
| `AUTH-003` | `ActiveMentorIdentityIntegrationTest`, `AdminDashboardWebTest`, `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReadModelServiceTest`, `DashboardControllerWebTest`, `DashboardServiceTest`, `DashboardTemplateWebTest`, `ReportingArchitectureTest`, `RoleDashboardWebIntegrationTest`, `TaskDashboardServiceTest` |
| `AUTH-004` | `ProjectEntityTest`, `ProjectServiceIntegrationTest` |
| `AUTH-005` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDashboardServiceTest` |
| `AUTH-006` | `Iteration2WorkflowFragmentsWebTest`, `ProjectControllerTest`, `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest` |
| `AUTH-007` | none |
| `AUTH-008` | `TaskCreationIntegrationTest` |
| `AUTH-009` | `ProjectInvitationExitIntegrationTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDashboardServiceTest` |
| `AUTH-010` | `ProjectTaskReportServiceTest` |
| `AUTH-011` | `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest`, `ProjectTaskMutationContextTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskMutationBoundaryTest` |
| `PRJ-001` | `ProjectControllerTest`, `ProjectEntityTest`, `ProjectServiceIntegrationTest` |
| `PRJ-002` | none |
| `PRJ-003` | `ProjectEntityTest`, `ProjectInvitationExitIntegrationTest` |
| `PRJ-004` | `ProjectControllerTest` |
| `PRJ-005` | `ProjectEntityTest`, `ProjectServiceIntegrationTest` |
| `PRJ-006` | `ProjectControllerTest` |
| `PRJ-007` | `ProjectEntityTest`, `ProjectServiceIntegrationTest` |
| `PRJ-008` | none |
| `PRJ-009` | `ProjectInvitationExitIntegrationTest` |
| `PRJ-010` | none |
| `PRJ-011` | none |
| `PRJ-012` | `ProjectControllerTest`, `ProjectEntityTest`, `ProjectServiceIntegrationTest`, `ProjectTaskMutationContextTest`, `TaskQueryServiceTest` |
| `PRJ-013` | `TaskCreationIntegrationTest` |
| `PRJ-014` | `ProjectServiceIntegrationTest` |
| `PRJ-015` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDomainRulesTest` |
| `PRJ-016` | `TaskCreationIntegrationTest`, `TaskDashboardServiceTest`, `TaskDomainRulesTest` |
| `PRJ-017` | `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest`, `ProjectEntityTest`, `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest` |
| `PRJ-018` | none |
| `PRJ-019` | `ProjectInvitationExitIntegrationTest` |
| `PRJ-020` | `ProjectTaskMutationContextTest` |
| `PRJ-021` | `ProjectTaskMutationContextTest` |
| `PRJ-022` | `ProjectInvitationExitIntegrationTest` |
| `TSK-001` | `TaskCreationIntegrationTest`, `TaskDashboardServiceTest`, `TaskPersistenceStructureTest` |
| `TSK-002` | `TaskDashboardServiceTest` |
| `TSK-003` | `NotificationServiceIntegrationTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskMutationBoundaryTest` |
| `TSK-004` | `TaskDashboardServiceTest` |
| `TSK-005` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskPersistenceStructureTest` |
| `TSK-006` | none |
| `TSK-007` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDomainRulesTest`, `TaskMutationBoundaryTest`, `TaskPersistenceStructureTest` |
| `TSK-008` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDomainRulesTest` |
| `TSK-009` | `NotificationServiceIntegrationTest`, `TaskCreationIntegrationTest` |
| `TSK-010` | none |
| `TSK-011` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskPersistenceStructureTest` |
| `TSK-012` | `NotificationServiceIntegrationTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskMutationBoundaryTest`, `TaskPersistenceStructureTest` |
| `TSK-013` | none |
| `TSK-014` | none |
| `TSK-015` | none |
| `TSK-016` | none |
| `TSK-017` | none |
| `TSK-018` | `NotificationServiceIntegrationTest`, `TaskCreationIntegrationTest`, `TaskMutationBoundaryTest` |
| `TSK-019` | none |
| `TSK-020` | none |
| `TSK-021` | none |
| `TSK-022` | `TaskMutationBoundaryTest`, `ProjectInvitationExitIntegrationTest` |
| `ATT-001` | `AttendancePolicyApplicationServiceTest`, `AttendancePolicyTest` |
| `ATT-002` | `ApplicationTimeZoneIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendancePolicyTest`, `CalendarDevelopmentProfileWebIntegrationTest` |
| `ATT-003` | `AttendancePolicyApplicationServiceTest`, `AttendancePolicyControllerWebTest`, `AttendancePolicyTest`, `CalendarDevelopmentProfileWebIntegrationTest` |
| `ATT-004` | `AttendancePolicyTest` |
| `ATT-005` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest` |
| `ATT-006` | `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest` |
| `CAL-001` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `CalendarAuthorizationWebIntegrationTest` |
| `CAL-002` | `AttendancePersistenceIntegrationTest`, `CalendarImportServiceTest` |
| `CAL-003` | `CalendarImportServiceTest`, `AdminSettingsControllerWebTest` |
| `CAL-004` | `CalendarImportServiceTest` |
| `CAL-005` | `AttendancePersistenceIntegrationTest`, `CalendarImportServiceTest` |
| `CAL-006` | `AttendancePersistenceIntegrationTest` |
| `CAL-007` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `CalendarImportServiceTest` |
| `CAL-008` | `AttendancePersistenceIntegrationTest` |
| `CAL-009` | `AttendancePersistenceIntegrationTest` |
| `ATT-007` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest`, `BootstrapIntegrationTest` |
| `ATT-008` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest` |
| `ATT-009` | `AttendanceServiceTest` |
| `ATT-010` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest` |
| `ATT-011` | `AttendanceServiceTest` |
| `ATT-012` | `AttendanceApplicationServiceTest`, `AttendanceServiceTest` |
| `ATT-013` | `AttendancePersistenceIntegrationTest`, `AttendanceReportServiceTest` |
| `ATT-014` | `AttendancePersistenceIntegrationTest`, `AttendanceReportServiceTest` |
| `ATT-015` | `AttendancePersistenceIntegrationTest`, `AttendanceReportServiceTest` |
| `ATT-016` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReportServiceTest`, `AttendanceServiceTest` |
| `ATT-017` | `AttendancePersistenceIntegrationTest`, `AttendanceReportServiceTest` |
| `ATT-018` | `AttendancePersistenceIntegrationTest`, `HistoricalInternReportingWindowIntegrationTest` |
| `COR-001` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReadModelServiceTest` |
| `COR-002` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest` |
| `COR-003` | none |
| `COR-004` | `AttendancePersistenceIntegrationTest` |
| `COR-005` | `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest` |
| `COR-006` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest` |
| `COR-007` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest` |
| `COR-008` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest`, `LeaveApplicationServiceTest` |
| `COR-009` | `AttendanceReadModelServiceTest` |
| `LEV-001` | `LeaveApplicationServiceTest` |
| `LEV-002` | none |
| `LEV-003` | `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReadModelServiceTest` |
| `LEV-004` | `AttendanceReadModelServiceTest` |
| `LEV-005` | none |
| `LEV-006` | `AttendancePersistenceIntegrationTest` |
| `LEV-007` | none |
| `LEV-008` | none |
| `LEV-009` | none |
| `LEV-010` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest`, `LeaveApplicationServiceTest` |
| `LEV-011` | `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest` |
| `LEV-012` | none |
| `INT-001` | `DashboardControllerWebTest`, `RoleDashboardWebIntegrationTest`, `SmtpIntegrationTest` |
| `INT-002` | `SmtpIntegrationTest` |
| `INT-003` | `SmtpIntegrationTest` |
| `INT-004` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `SmtpIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `INT-005` | `JavaMailSmtpProbeTest`, `SmtpIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `INT-006` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `SmtpIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `INT-007` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `DashboardControllerWebTest`, `JavaMailSmtpProbeTest`, `SmtpIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `INT-008` | `AccountWebIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `SmtpIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `INT-009` | `HolidayApiIntegrationTest`, `HolidayApiHttpClientTest` |
| `INT-010` | `SecretCipherTest` |
| `NOT-001` | `NotificationServiceIntegrationTest`, `ProjectInvitationExitIntegrationTest` |
| `NOT-002` | `ActiveMentorIdentityIntegrationTest`, `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest`, `LeaveApplicationServiceTest`, `NotificationServiceIntegrationTest`, `ProjectInvitationExitIntegrationTest`, `TaskCreationIntegrationTest` |
| `NOT-003` | `NotificationServiceIntegrationTest`, `TaskCreationIntegrationTest` |
| `NOT-004` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest`, `LeaveApplicationServiceTest`, `NotificationServiceIntegrationTest`, `ProjectInvitationExitIntegrationTest`, `TaskCreationIntegrationTest` |
| `NOT-005` | `NotificationServiceIntegrationTest` |
| `NOT-006` | `NotificationServiceIntegrationTest` |
| `NOT-007` | `NotificationServiceIntegrationTest` |
| `NOT-008` | `AccountActivationIntegrationTest`, `AccountRecoveryIntegrationTest`, `JavaMailSmtpProbeTest`, `PasswordRecoveryWebIntegrationTest`, `PasswordResetIntegrationTest` |
| `NOT-009` | `DashboardControllerWebTest`, `DashboardTemplateWebTest`, `Iteration2WorkflowFragmentsWebTest`, `NotificationControllerWebTest` |
| `NOT-010` | `NotificationServiceIntegrationTest`, `ProjectInvitationExitIntegrationTest`, `TaskCreationIntegrationTest` |
| `SEC-001` | `AccountWebIntegrationTest`, `ActivationResendWebIntegrationTest`, `AuthenticationWebIntegrationTest`, `BootstrapIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `CalendarAuthorizationWebIntegrationTest`, `ProjectControllerTest`, `SecurityResponseIntegrationTest`, `SmtpIntegrationTest`, `SmtpOnboardingWebIntegrationTest` |
| `SEC-002` | `AccountActivationIntegrationTest`, `AccountWebIntegrationTest`, `PasswordResetIntegrationTest` |
| `SEC-003` | `AccountRecoveryIntegrationTest`, `AccountWebIntegrationTest`, `PasswordRecoveryWebIntegrationTest`, `SecurityResponseIntegrationTest`, `UserActionTokenCleanupIntegrationTest` |
| `SEC-004` | `AccountActivationIntegrationTest`, `AccountRecoveryIntegrationTest`, `AccountRecoveryLockOrderIntegrationTest`, `AccountWebIntegrationTest` |
| `SEC-005` | `AccountRecoveryLockOrderIntegrationTest`, `PasswordRecoveryWebIntegrationTest`, `PasswordResetIntegrationTest` |
| `SEC-006` | `LoginThrottleTest` |
| `SEC-007` | `LoginThrottleTest` |
| `SEC-008` | `NotificationActionContractTest`, `OriginEnforcementFilterTest` |
| `SEC-009` | `SecurityResponseIntegrationTest`, `UserActionTokenCleanupIntegrationTest` |
| `SEC-010` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-011` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-012` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-013` | `CalendarAuthorizationWebIntegrationTest`, `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-014` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `RPT-001` | `DailyProjectWorkReportControllerWebTest`, `DailyProjectWorkReportExportControllerWebTest`, `DailyProjectWorkReportExportServiceTest`, `ReportExportServiceTest`, `ReportingExportControllerWebTest` |
| `RPT-002` | `AttendancePersistenceIntegrationTest`, `AttendanceReportServiceTest` |
| `RPT-003` | `ProjectTaskReportServiceTest` |
| `RPT-004` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest` |
| `RPT-005` | `ProjectTaskReportServiceTest` |
| `RPT-006` | `DailyProjectWorkReportControllerWebTest`, `DailyProjectWorkReportExportControllerWebTest`, `DailyProjectWorkReportExportServiceTest`, `DailyProjectWorkReportServiceTest`, `ProjectControllerTest`, `ProjectServiceIntegrationTest`, `Q31RDailyProjectWorkReportServiceTest`, `ReportExportServiceTest`, `ReportingExportControllerWebTest` |
| `RPT-007` | `ReportingDependencyContractTest` |
| `RPT-008` | `Iteration2ComponentsWebTest`, `ProjectTaskReportControllerWebTest`, `ProjectTaskReportServiceTest` |
| `RPT-009` | `AttendanceReportServiceTest`, `Iteration2ComponentsWebTest`, `ProjectTaskReportControllerWebTest`, `ProjectTaskReportServiceTest` |
| `RPT-010` | `DailyProjectWorkReportControllerWebTest`, `DailyProjectWorkReportExportControllerWebTest`, `DailyProjectWorkReportExportServiceTest`, `ReportExportServiceTest`, `ReportingExportControllerWebTest` |
| `RPT-011` | `DailyProjectWorkReportControllerWebTest`, `DailyProjectWorkReportExportControllerWebTest`, `DailyProjectWorkReportServiceTest`, `ProjectControllerTest`, `ProjectServiceIntegrationTest`, `Q31RDailyProjectWorkReportServiceTest`, `TaskCreationIntegrationTest` |
| `RPT-012` | `DailyProjectWorkReportControllerWebTest`, `TaskCreationIntegrationTest` |
| `RPT-013` | `DailyProjectWorkReportControllerWebTest`, `DailyProjectWorkReportExportControllerWebTest`, `DailyProjectWorkReportExportServiceTest`, `DailyProjectWorkReportServiceTest`, `ProjectControllerTest`, `ProjectServiceIntegrationTest`, `Q31RDailyProjectWorkReportServiceTest` |
| `UI-001` | `AccountTemplateIntegrationTest`, `AttendanceTemplateIntegrationTest`, `DashboardTemplateWebTest`, `Iteration2ComponentsWebTest`, `Iteration2WorkflowFragmentsWebTest`, `UiContractWebTest` |
| `UI-002` | `AccountTemplateIntegrationTest`, `AttendanceTemplateIntegrationTest`, `Iteration2ComponentsWebTest` |
| `UI-003` | `AdminDashboardWebTest`, `AttendanceTemplateIntegrationTest`, `DashboardControllerWebTest`, `DashboardServiceTest`, `DashboardTemplateWebTest`, `FrontendSourceContractTest`, `ProjectControllerTest`, `ProjectTaskFormAccessibilityWebTest`, `ProjectTaskShellContractTest`, `ReportingArchitectureTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `TaskControllerTest`, `UiContractWebTest` |
| `UI-004` | `AccountTemplateIntegrationTest`, `AttendanceTemplateIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `DashboardControllerWebTest`, `FrontendSourceContractTest`, `NotificationControllerWebTest`, `ProjectControllerTest`, `ProjectTaskFormAccessibilityWebTest`, `ProjectTaskShellContractTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `SmtpOnboardingWebIntegrationTest`, `TaskControllerTest`, `UiContractWebTest` |
| `UI-005` | `FrontendSourceContractTest`, `Iteration2ComponentsWebTest`, `Iteration2WorkflowFragmentsWebTest`, `UiContractWebTest` |
| `UI-006` | `FrontendSourceContractTest`, `UiContractWebTest` |
| `UI-007` | `BootstrapOnboardingWebIntegrationTest`, `DashboardControllerWebTest`, `ProjectControllerTest`, `ProjectTaskShellContractTest`, `SmtpOnboardingWebIntegrationTest`, `TaskControllerTest` |
| `UI-008` | `AttendanceTemplateIntegrationTest`, `DashboardControllerWebTest`, `RoleDashboardWebIntegrationTest` |
| `UI-009` | `AccountTemplateIntegrationTest`, `DashboardControllerWebTest`, `ProjectControllerTest`, `ProjectTaskShellContractTest`, `RoleDashboardWebIntegrationTest`, `TaskControllerTest`, `UiContractWebTest` |
| `UI-010` | `AttendanceTemplateIntegrationTest`, `BootstrapOnboardingWebIntegrationTest`, `DashboardControllerWebTest`, `NotificationControllerWebTest`, `ProjectTaskFormAccessibilityWebTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `SmtpOnboardingWebIntegrationTest`, `UiContractWebTest` |
| `UI-011` | none |
| `UI-012` | none |
| `UI-013` | `AdminDashboardWebTest`, `AttendanceControllerTest`, `AttendanceTemplateIntegrationTest`, `DashboardControllerWebTest`, `DashboardServiceTest`, `DashboardTemplateWebTest`, `Iteration2ComponentsWebTest`, `Iteration2WorkflowFragmentsWebTest`, `ProjectControllerTest`, `ProjectTaskFormAccessibilityWebTest`, `ProjectTaskShellContractTest`, `ReportingArchitectureTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `TaskControllerTest`, `UiContractWebTest` |
| `UI-014` | `AttendancePolicyControllerWebTest`, `AttendanceTemplateIntegrationTest`, `ProjectTaskFormAccessibilityWebTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `TaskControllerTest`, `UiContractWebTest` |
| `UI-015` | none |
| `UI-016` | none |
| `UI-017` | none |
| `UI-018` | `UiContractWebTest` |
| `UI-019` | `AttendancePolicyControllerWebTest`, `AttendanceReadModelServiceTest`, `Iteration2ComponentsWebTest` |
| `OPS-001` | none |
| `OPS-002` | none |
| `OPS-003` | `PlatformFoundationTest` |
| `OPS-004` | none |
| `OPS-005` | `BootstrapIntegrationTest` |
| `OPS-006` | none |
| `OPS-007` | none |
| `OPS-008` | none |
| `OPS-009` | none |
| `OPS-010` | none |
| `OPS-011` | none |
| `OPS-012` | none |
| `OPS-013` | `BootstrapIntegrationTest` |
| `OPS-014` | none |
| `OPS-015` | none |
| `OPS-016` | none |
| `OPS-017` | `BootstrapIntegrationTest` |
| `TST-001` | `ApplicationTimeZoneIntegrationTest`, `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest`, `ProjectPersistenceStructureTest` |
| `TST-002` | `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-003` | `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-004` | `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-005` | `ApplicationTimeZoneIntegrationTest`, `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-006` | `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-007` | `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-008` | `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-009` | `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest` |
| `TST-010` | `EligibleInternOptionIntegrationTest`, `PlatformFoundationTest`, `ProjectPersistenceStructureTest` |
| `OPS-018` | `ProjectPersistenceStructureTest` |
| `OPS-019` | `ReportingDependencyContractTest` |
| `OPS-020` | `AttendanceLayerStructureTest`, `ProjectPersistenceStructureTest` |
| `OPS-021` | none |
| `DB-001` | none |
| `DB-002` | `AttendancePersistenceIntegrationTest` |
| `DB-003` | `AccountWebIntegrationTest`, `PlatformFoundationTest`, `ProjectServiceIntegrationTest` |
| `DB-004` | `PlatformFoundationTest` |
| `DB-005` | `PlatformFoundationTest` |
| `DB-006` | `PlatformFoundationTest` |
| `DB-007` | `PlatformFoundationTest`, `ProjectServiceIntegrationTest` |
| `DB-008` | `PlatformFoundationTest` |
| `DB-009` | `PlatformFoundationTest` |
| `DB-010` | `PlatformFoundationTest` |
| `DB-011` | `PlatformFoundationTest` |
| `DB-012` | `PlatformFoundationTest` |
| `DB-013` | `PlatformFoundationTest`, `TaskCreationIntegrationTest` |
| `ERR-001` | `AttendanceTemplateIntegrationTest`, `ProjectControllerTest`, `ProjectTaskFormAccessibilityWebTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `UiContractWebTest` |
| `ERR-002` | `TaskControllerTest`, `SharedErrorTemplateWebTest` |
| `ERR-003` | `ProjectInvitationExitIntegrationTest`, `TaskMutationBoundaryTest` |
| `ERR-004` | `NotificationServiceIntegrationTest` |
| `ERR-005` | `HolidayApiHttpClientTest`, `NotificationServiceIntegrationTest` |
| `ERR-006` | none |
| `ERR-007` | none |

## Re-derivation, 13 September 2026

The 37 rules this file listed as protected by nothing were checked one at a time
against the test sources. Six of them survived the check. The other 31 did not,
and they failed in three different ways.

| Outcome | Count | What it means |
|---|---:|---|
| Already protected | 23 | A test exists and asserts the rule. The row said `none`. |
| Protected in one half | 4 | Counted as tested; the unprotected half is tabled above. |
| Cannot be protected by any test | 4 | Moved to the process and scope table. |
| Genuinely unprotected | 6 | Tabled above with what would close each. |

**One error ran the other way.** `GOV-013` sat in the process and scope table,
where a rule is recorded as untestable. It is tested, by two classes the
constitution already named. A reader planning work from this file would have
skipped a rule that is covered, and separately would have written 31 tests that
already exist.

**Two dead class names were carried for two days.** `AttendanceReportFormulasTest`
and `ProjectTaskReportFormulasTest` were renamed to `AttendanceReportServiceTest`
and `ProjectTaskReportServiceTest` before this file was extracted, which
[`ADR-004`](../rfcs/ADR-004-test-evidence-moves-into-the-test.md) records as the
reason a Markdown trace cannot be trusted: it has no compiler. Both names are now
the current ones, and no row names a class that does not exist.

### Classes that exist and no row names

Twenty-nine of the 122 test classes under `src/test/java` are named by no row in
this file. They were written after the evidence records were extracted, or were
never covered by one. Each is a class whose rules are unknown to this mapping,
not a class that protects nothing.

`AccountAdministrationControllerWebTest`, `AccountScalarLookupIntegrationTest`,
`AttendanceDeadlineSchedulerTest`, `AttendanceLombokBoilerplateTest`,
`AttendancePolicyCommandQuotaBoundTest`, `AttendanceRecordCorrectionTest`,
`AttendanceReportControllerWebTest`, `AttendanceReportPageWebTest`,
`AttendanceReportQueryServiceAuthorizationTest`, `AttendanceRequestControllerWebTest`,
`CalendarControllerWebTest`, `DailyProjectWorkReportNavigationAdviceTest`,
`GlobalErrorPageHttpIntegrationTest`, `GlobalErrorPageWebTest`,
`IntegrationExternalTransactionIntegrationTest`, `InternshipLifecycleIntegrationTest`,
`Iteration2ProjectWorkflowWebTest`, `Iteration2TaskWorkflowWebTest`,
`NotificationInboxIntegrationTest`, `ProjectLifecycleLockIntegrationTest`,
`ProjectQueryIndexIntegrationTest`, `ProjectQueryServiceLeaderDailyTest`,
`ProjectTaskReportPageWebTest`, `TaskDefinitionRulesTest`,
`TaskIteration3QueryTest`, `TaskProjectQueryTest`, `TaskTransferServiceTest`,
`TaskWorkLogIntegrationTest`, `TaskWorkLogRulesTest`.

### Why this file should stop existing in this form

Every error above is the same error. A derived fact was copied into prose, was
true on the day it was written, and rotted. `ADR-004` already named the fix and
did not schedule it: when each test names the rules it protects in its own
source, this mapping is generated and cannot disagree with the tests. Six of the
122 classes carry such a trace today. Until the rest do, a row here is a claim to
verify, and this re-derivation is evidence of how often that claim is wrong.
