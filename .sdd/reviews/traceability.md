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

Eighty-eight classes predate that rule and carry no identifiers. Until they are
back-filled, this file is the only mapping for them and nothing detects it
drifting from the tests. Treat a row here as a claim to verify, not a fact.

One row has since been verified and was false. `ARC-008` claimed two classes.
The rule governs a reviewed DDL that lives in the separate documentation
repository and has never been tracked here, so no test in this repository can
observe it. The row now reads `none`, and the counts above move with it.

## Coverage

| | Count |
|---|---:|
| Numbered rules in the specification | 269 |
| Rules with at least one test class | 220 |
| Rules a test could protect but none does | 37 |
| Rules no test can protect, being process or scope statements | 12 |
| Distinct test classes named | 88 |

## Coverage by rule group

| Group | Rules | Tested | Untested |
|---|---:|---:|---:|
| `ACC` | 25 | 20 | 5 |
| `ARC` | 8 | 7 | 1 |
| `ATT` | 18 | 18 | 0 |
| `AUTH` | 11 | 10 | 1 |
| `CAL` | 9 | 6 | 3 |
| `COR` | 9 | 6 | 3 |
| `DB` | 13 | 11 | 2 |
| `ERR` | 7 | 2 | 5 |
| `GOV` | 16 | 3 | 13 |
| `INT` | 10 | 8 | 2 |
| `LEV` | 12 | 9 | 3 |
| `NOT` | 10 | 10 | 0 |
| `OPS` | 21 | 19 | 2 |
| `PRJ` | 22 | 16 | 6 |
| `RPT` | 13 | 13 | 0 |
| `SEC` | 14 | 13 | 1 |
| `TSK` | 22 | 20 | 2 |
| `TST` | 10 | 10 | 0 |
| `UI` | 19 | 19 | 0 |

## Rules no test protects

These 37 rules describe behavior a test could assert, and none does.
Each is a place where the code can drift away from the requirement without
anything failing.

| Rule | Group |
|---|---|
| `GOV-004` | GOV |
| `GOV-014` | GOV |
| `ACC-004` | ACC |
| `ACC-015` | ACC |
| `ACC-016` | ACC |
| `ACC-022` | ACC |
| `ACC-024` | ACC |
| `AUTH-008` | AUTH |
| `PRJ-002` | PRJ |
| `PRJ-003` | PRJ |
| `PRJ-005` | PRJ |
| `PRJ-009` | PRJ |
| `PRJ-018` | PRJ |
| `PRJ-019` | PRJ |
| `TSK-017` | TSK |
| `TSK-022` | TSK |
| `CAL-003` | CAL |
| `CAL-004` | CAL |
| `CAL-008` | CAL |
| `COR-003` | COR |
| `COR-004` | COR |
| `COR-005` | COR |
| `LEV-006` | LEV |
| `LEV-008` | LEV |
| `LEV-009` | LEV |
| `INT-009` | INT |
| `INT-010` | INT |
| `SEC-008` | SEC |
| `OPS-002` | OPS |
| `OPS-010` | OPS |
| `DB-001` | DB |
| `DB-002` | DB |
| `ERR-002` | ERR |
| `ERR-003` | ERR |
| `ERR-005` | ERR |
| `ERR-006` | ERR |
| `ERR-007` | ERR |

These 11 are process or scope statements. They bind people and
review, not code, so the absence of a test is correct rather than a gap.
See [`.sdd/constitution.md`](../constitution.md).

| Rule |
|---|
| `GOV-001` |
| `GOV-002` |
| `GOV-003` |
| `GOV-006` |
| `GOV-016` |
| `GOV-007` |
| `GOV-008` |
| `GOV-009` |
| `GOV-010` |
| `GOV-015` |
| `GOV-013` |

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
| `GOV-013` | none |
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
| `ACC-015` | none |
| `ACC-016` | none |
| `ACC-017` | `AccountIdentityCorrectionIntegrationTest` |
| `ACC-018` | `AccountIdentityCorrectionIntegrationTest`, `AccountLifecycleIntegrationTest`, `AccountRecoveryLockOrderIntegrationTest`, `AccountSessionInvalidationWebIntegrationTest`, `PasswordResetIntegrationTest` |
| `ACC-019` | `AccountActivationIntegrationTest`, `AccountIdentityCorrectionIntegrationTest`, `AccountWebIntegrationTest`, `E2eProfileIntegrationTest`, `EligibleInternOptionIntegrationTest`, `InternMutationEligibilityIntegrationTest`, `InternWorkWindowIntegrationTest`, `TimeConfigurationTest` |
| `ACC-020` | `AccountActivationIntegrationTest`, `BootstrapIntegrationTest`, `E2eProfileIntegrationTest`, `EligibleInternOptionIntegrationTest`, `TimeConfigurationTest` |
| `ACC-021` | `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest`, `InternWorkWindowIntegrationTest` |
| `ACC-022` | none |
| `ACC-023` | `InternMutationEligibilityIntegrationTest` |
| `ACC-024` | none |
| `ACC-025` | `HistoricalInternReportingWindowIntegrationTest` |
| `AUTH-001` | `AccountWebIntegrationTest`, `AttendanceControllerTest`, `DashboardControllerWebTest`, `EligibleInternOptionIntegrationTest`, `InternMutationEligibilityIntegrationTest`, `NotificationControllerWebTest`, `ProjectControllerTest`, `ProjectEntityTest`, `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest`, `ProjectTaskMutationContextTest`, `ProjectTaskReportServiceTest`, `TaskControllerTest`, `TaskCreationIntegrationTest` |
| `AUTH-002` | `AccountWebIntegrationTest`, `AttendanceControllerTest`, `AttendanceTemplateIntegrationTest`, `CalendarAuthorizationWebIntegrationTest`, `DashboardControllerWebTest`, `InternMutationEligibilityIntegrationTest`, `NotificationControllerWebTest`, `ProjectControllerTest`, `ProjectInvitationExitIntegrationTest`, `ProjectTaskFormAccessibilityWebTest`, `ProjectTaskReportServiceTest`, `RoleDashboardWebIntegrationTest`, `SharedErrorTemplateWebTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `UiContractWebTest` |
| `AUTH-003` | `ActiveMentorIdentityIntegrationTest`, `AdminDashboardWebTest`, `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReadModelServiceTest`, `DashboardControllerWebTest`, `DashboardServiceTest`, `DashboardTemplateWebTest`, `ReportingArchitectureTest`, `RoleDashboardWebIntegrationTest`, `TaskDashboardServiceTest` |
| `AUTH-004` | `ProjectEntityTest`, `ProjectServiceIntegrationTest` |
| `AUTH-005` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDashboardServiceTest` |
| `AUTH-006` | `Iteration2WorkflowFragmentsWebTest`, `ProjectControllerTest`, `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest` |
| `AUTH-007` | none |
| `AUTH-008` | none |
| `AUTH-009` | `ProjectInvitationExitIntegrationTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDashboardServiceTest` |
| `AUTH-010` | `ProjectTaskReportFormulasTest` |
| `AUTH-011` | `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest`, `ProjectTaskMutationContextTest`, `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskMutationBoundaryTest` |
| `PRJ-001` | `ProjectControllerTest`, `ProjectEntityTest`, `ProjectServiceIntegrationTest` |
| `PRJ-002` | none |
| `PRJ-003` | none |
| `PRJ-004` | `ProjectControllerTest` |
| `PRJ-005` | none |
| `PRJ-006` | `ProjectControllerTest` |
| `PRJ-007` | `ProjectEntityTest`, `ProjectServiceIntegrationTest` |
| `PRJ-008` | none |
| `PRJ-009` | none |
| `PRJ-010` | none |
| `PRJ-011` | none |
| `PRJ-012` | `ProjectControllerTest`, `ProjectEntityTest`, `ProjectServiceIntegrationTest`, `ProjectTaskMutationContextTest`, `TaskQueryServiceTest` |
| `PRJ-013` | `TaskCreationIntegrationTest` |
| `PRJ-014` | `ProjectServiceIntegrationTest` |
| `PRJ-015` | `TaskControllerTest`, `TaskCreationIntegrationTest`, `TaskDomainRulesTest` |
| `PRJ-016` | `TaskCreationIntegrationTest`, `TaskDashboardServiceTest`, `TaskDomainRulesTest` |
| `PRJ-017` | `BootstrapIntegrationTest`, `EligibleInternOptionIntegrationTest`, `ProjectEntityTest`, `ProjectInvitationExitIntegrationTest`, `ProjectServiceIntegrationTest` |
| `PRJ-018` | none |
| `PRJ-019` | none |
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
| `TSK-022` | none |
| `ATT-001` | `AttendancePolicyApplicationServiceTest`, `AttendancePolicyTest` |
| `ATT-002` | `ApplicationTimeZoneIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendancePolicyTest`, `CalendarDevelopmentProfileWebIntegrationTest` |
| `ATT-003` | `AttendancePolicyApplicationServiceTest`, `AttendancePolicyControllerWebTest`, `AttendancePolicyTest`, `CalendarDevelopmentProfileWebIntegrationTest` |
| `ATT-004` | `AttendancePolicyTest` |
| `ATT-005` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest` |
| `ATT-006` | `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest` |
| `CAL-001` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `CalendarAuthorizationWebIntegrationTest` |
| `CAL-002` | `AttendancePersistenceIntegrationTest`, `CalendarImportServiceTest` |
| `CAL-003` | none |
| `CAL-004` | none |
| `CAL-005` | `AttendancePersistenceIntegrationTest`, `CalendarImportServiceTest` |
| `CAL-006` | `AttendancePersistenceIntegrationTest` |
| `CAL-007` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `CalendarImportServiceTest` |
| `CAL-008` | none |
| `CAL-009` | `AttendancePersistenceIntegrationTest` |
| `ATT-007` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest`, `BootstrapIntegrationTest` |
| `ATT-008` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest` |
| `ATT-009` | `AttendanceServiceTest` |
| `ATT-010` | `AttendanceApplicationServiceTest`, `AttendanceConcurrencyIntegrationTest`, `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceServiceTest` |
| `ATT-011` | `AttendanceServiceTest` |
| `ATT-012` | `AttendanceApplicationServiceTest`, `AttendanceServiceTest` |
| `ATT-013` | `AttendancePersistenceIntegrationTest`, `AttendanceReportFormulasTest` |
| `ATT-014` | `AttendancePersistenceIntegrationTest`, `AttendanceReportFormulasTest` |
| `ATT-015` | `AttendancePersistenceIntegrationTest`, `AttendanceReportFormulasTest` |
| `ATT-016` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReportFormulasTest`, `AttendanceServiceTest` |
| `ATT-017` | `AttendancePersistenceIntegrationTest`, `AttendanceReportFormulasTest` |
| `ATT-018` | `AttendancePersistenceIntegrationTest`, `HistoricalInternReportingWindowIntegrationTest` |
| `COR-001` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReadModelServiceTest` |
| `COR-002` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest` |
| `COR-003` | none |
| `COR-004` | none |
| `COR-005` | none |
| `COR-006` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest` |
| `COR-007` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest` |
| `COR-008` | `AttendanceCorrectionApplicationServiceTest`, `AttendancePersistenceIntegrationTest`, `LeaveApplicationServiceTest` |
| `COR-009` | `AttendanceReadModelServiceTest` |
| `LEV-001` | `LeaveApplicationServiceTest` |
| `LEV-002` | none |
| `LEV-003` | `AttendanceConcurrencyIntegrationTest`, `AttendancePersistenceIntegrationTest`, `AttendanceReadModelServiceTest` |
| `LEV-004` | `AttendanceReadModelServiceTest` |
| `LEV-005` | none |
| `LEV-006` | none |
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
| `INT-009` | none |
| `INT-010` | none |
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
| `SEC-008` | none |
| `SEC-009` | `SecurityResponseIntegrationTest`, `UserActionTokenCleanupIntegrationTest` |
| `SEC-010` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-011` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-012` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-013` | `CalendarAuthorizationWebIntegrationTest`, `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `SEC-014` | `OriginEnforcementFilterTest`, `ProductionReadinessTest`, `TrustedForwardedHeaderFilterTest` |
| `RPT-001` | `DailyProjectWorkReportControllerWebTest`, `DailyProjectWorkReportExportControllerWebTest`, `DailyProjectWorkReportExportServiceTest`, `ReportExportServiceTest`, `ReportingExportControllerWebTest` |
| `RPT-002` | `AttendancePersistenceIntegrationTest`, `AttendanceReportFormulasTest` |
| `RPT-003` | `ProjectTaskReportFormulasTest`, `ProjectTaskReportServiceTest` |
| `RPT-004` | `AttendanceControllerTest`, `AttendancePersistenceIntegrationTest` |
| `RPT-005` | `ProjectTaskReportFormulasTest`, `ProjectTaskReportServiceTest` |
| `RPT-006` | `DailyProjectWorkReportControllerWebTest`, `DailyProjectWorkReportExportControllerWebTest`, `DailyProjectWorkReportExportServiceTest`, `DailyProjectWorkReportServiceTest`, `ProjectControllerTest`, `ProjectServiceIntegrationTest`, `Q31RDailyProjectWorkReportServiceTest`, `ReportExportServiceTest`, `ReportingExportControllerWebTest` |
| `RPT-007` | `ReportingDependencyContractTest` |
| `RPT-008` | `Iteration2ComponentsWebTest`, `ProjectTaskReportControllerWebTest`, `ProjectTaskReportServiceTest` |
| `RPT-009` | `AttendanceReportFormulasTest`, `Iteration2ComponentsWebTest`, `ProjectTaskReportControllerWebTest`, `ProjectTaskReportFormulasTest`, `ProjectTaskReportServiceTest` |
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
| `DB-002` | none |
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
| `ERR-002` | none |
| `ERR-003` | none |
| `ERR-004` | `NotificationServiceIntegrationTest` |
| `ERR-005` | none |
| `ERR-006` | none |
| `ERR-007` | none |

## Named classes that do not exist

| Class named | Nearest existing class |
|---|---|
| `AttendanceReportFormulasTest` | `AttendanceReportServiceTest` |
| `ProjectTaskReportFormulasTest` | `ProjectTaskReportServiceTest` |
