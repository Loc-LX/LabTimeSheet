package com.lab.labtimesheet.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.calendar.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.calendar.service.AttendancePolicyApplicationService;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.calendar.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportExportService;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.project.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.feature.project.model.dto.TaskStatusChangeCommand;
import com.lab.labtimesheet.feature.project.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.TaskService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exercises registered §5.2 capabilities through their application services.
 *
 * <p>Rules protected: {@code AC-AUTH-011}, {@code AUTH-012}, {@code TSK-007}, {@code TSK-023}, {@code ACC-028},
 * and {@code ACC-029}. Observable break: a cell can return the opposite
 * of the matrix. Expected values come from the hand-authored §5.2 matrix at runtime, and the first granted actor
 * runs as a counterexample before every other actor against a freshly reconstructed equivalent fixture.
 */
@Import({TestcontainersConfiguration.class, AuthorizationMatrixIntegrationTest.ProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class AuthorizationMatrixIntegrationTest {

    private static final LocalDate PROBE_WORK_DATE = LocalDate.of(2026, 8, 14);
    private static final Path SPEC = Path.of(".sdd/specs/platform/features/authorization/SPEC.md");
    private static final Path OPEN_CELLS = Path.of("src/test/resources/authorization/open-matrix-cells.tsv");
    private static final Path UNBUILT_OPERATIONS = Path.of("src/test/resources/authorization/unbuilt-operations.tsv");
    private static final List<String> REGISTERED_CAPABILITIES = List.of(
            "Manage accounts/global roles at creation",
            "Lock/deactivate accounts; manage internship lifecycle",
            "Manage SMTP, HolidayAPI, attendance policy, global calendar",
            "Create Project",
            "Edit/activate/complete Project",
            "Delete an empty `PLANNED` Project (`PRJ-002`)",
            "Cancel a `PLANNED` or `ACTIVE` Project (`PRJ-023`)",
            "Directly add/remove Project members",
            "Issue/revoke Project invitation",
            "Request/decide membership exit",
            "Appoint/change Project Leader",
            "Create Task",
            "Assign/reassign Task",
            "Set/replace/clear Task estimate before the first work log",
            "Edit/soft-delete Task",
            "Change the status of one's own assigned Task (any `TSK-007` transition)",
            "Block, unblock, or reopen another member's Task (`TSK-023`)",
            "Start another member's Task or mark it `DONE`",
            "Comment on Task",
            "View all Projects/tasks/progress",
            "View aggregate Project progress",
            "View Project/Task retained history",
            "View Intern attendance",
            "Record a Remaining effort forecast (`TSK-022`, `TSK-024`)",
            "Create/edit own Task work log",
            "View and export the Attendance report (`RPT-004`)",
            "View own attendance for a date (`RPT-015`)",
            "Redistribute unfinished Tasks for pending exit",
            "Submit own leave, correction, or exception request",
            "Withdraw own `PENDING` or `OVERDUE` leave request (`LEV-013`)",
            "View per-member Project hours",
            "View and export the Project/Task report (`RPT-005`)",
            "View and export the Daily Project Work Report (`RPT-011`)",
            "Decide leave, correction, or attendance exception; amend or reverse that decision where its rule permits (`ATT-024`)",
            "Ask to reopen a finalized attendance period (`ATT-022`)",
            "Approve or reject a request to reopen a finalized attendance period (`ATT-022`)");
    private static final List<UnbuiltOperation> UNBUILT_IN_CODE = List.of(
            new UnbuiltOperation("Edit Project details", "AUTH-012", "Project lifecycle plan (to be written)"),
            new UnbuiltOperation("Leader block, unblock or reopen of another member's Task", "TSK-023, TSK-025",
                    "Task management plan, after part C creates task_status_transitions (Authorization plan B.7)"),
            new UnbuiltOperation("Submit an attendance exception request", "ATT-024",
                    "Attendance plan C-04, after part C creates the exception request model"),
            new UnbuiltOperation("Decide leave, correction, or attendance exception; amend or reverse that decision",
                    "ATT-024", "Attendance exception plan, after part C adds the responsible Mentor (intern_profiles.responsible_mentor_user_id, V3)"),
            new UnbuiltOperation("Ask to reopen a finalized attendance period", "ATT-022",
                    "Period finalization plan, after part C creates the reopen request data"),
            new UnbuiltOperation("Approve or reject a request to reopen a finalized attendance period", "ATT-022",
                    "Period finalization plan, after part C creates the reopen request data"));
    private static final Map<String, List<String>> UNBUILT_BY_CAPABILITY = Map.of(
            "Edit/activate/complete Project", List.of("Edit Project details"),
            "Block, unblock, or reopen another member's Task (`TSK-023`)",
                    List.of("Leader block, unblock or reopen of another member's Task"),
            "Submit own leave, correction, or exception request",
                    List.of("Submit an attendance exception request"),
            "Decide leave, correction, or attendance exception; amend or reverse that decision where its rule permits (`ATT-024`)",
                    List.of("Decide leave, correction, or attendance exception; amend or reverse that decision"),
            "Ask to reopen a finalized attendance period (`ATT-022`)",
                    List.of("Ask to reopen a finalized attendance period"),
            "Approve or reject a request to reopen a finalized attendance period (`ATT-022`)",
                    List.of("Approve or reject a request to reopen a finalized attendance period"));
    private static final Map<String, List<String>> REQUIRED_PROBES_BY_CAPABILITY = Map.of(
            "Lock/deactivate accounts; manage internship lifecycle",
            List.of("deactivate pending account (ACC-028)", "reinstate deactivated account (ACC-029)"));
    private static final List<String> ACTORS = List.of(
            "Admin", "Owning Mentor", "Current Leader", "Active member / assignee");
    private static final AtomicLong FIXTURE_SEQUENCE = new AtomicLong();

    @Autowired private JdbcClient jdbc;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private HolidayApiConfigurationService holidayApi;
    @Autowired private AttendancePolicyApplicationService policies;
    @Autowired private CalendarApplicationService calendar;
    @Autowired private AttendanceApplicationService attendance;
    @Autowired private AttendanceCorrectionApplicationService corrections;
    @Autowired private LeaveApplicationService leaves;
    @Autowired private AttendanceReportService attendanceReports;
    @Autowired private ReportExportService reportExports;
    @Autowired private ProjectTaskReportService projectTaskReports;
    @Autowired private DailyProjectWorkReportService dailyReports;
    @Autowired private ProjectService projects;
    @Autowired private ProjectQueryService projectQueries;
    @Autowired private TaskService tasks;
    @Autowired private PlatformTransactionManager transactionManager;

    /** Checks registered cells against the matrix and validates explicit open and unbuilt ledgers. */
    @Test
    void registeredCapabilitiesMatchTheSpecAndOpenCellLedger() throws Exception {
        List<MatrixRow> matrix = readMatrix();
        Map<String, List<Subprobe>> registry = probes();
        assertThat(registry.keySet()).containsExactlyElementsOf(REGISTERED_CAPABILITIES);
        List<UnbuiltOperation> unbuiltOperations = readUnbuiltOperations();
        assertThat(unbuiltOperations).containsExactlyElementsOf(UNBUILT_IN_CODE);
        assertThat(registry.values().stream().flatMap(List::stream).map(Subprobe::name).toList())
                .doesNotContainAnyElementsOf(UNBUILT_IN_CODE.stream().map(UnbuiltOperation::operation).toList());
        REQUIRED_PROBES_BY_CAPABILITY.forEach((capability, requiredProbes) -> assertThat(
                        registry.get(capability).stream().map(Subprobe::name).toList())
                .as("ACC-028 and ACC-029 must keep executable probes in %s", capability)
                .containsAll(requiredProbes));

        Map<Cell, Outcome> mismatches = new LinkedHashMap<>();
        List<String> resultLines = new ArrayList<>();
        long testedCells = 0;
        long unbuiltCells = 0;
        List<MatrixRow> registeredRows = matrix.stream().filter(row -> registry.containsKey(row.capability())).toList();
        for (MatrixRow row : registeredRows) {
            List<Subprobe> capabilityProbes = registry.get(row.capability());
            if (capabilityProbes.isEmpty()) {
                List<String> unbuilt = UNBUILT_BY_CAPABILITY.get(row.capability());
                assertThat(unbuilt).as("empty probe list for %s must have an unbuilt-operation entry", row.capability())
                        .isNotNull().isNotEmpty();
                assertThat(unbuilt).as("unbuilt operations for %s must be present in unbuilt-operations.tsv",
                        row.capability()).allMatch(operation -> unbuiltOperations.stream()
                                .anyMatch(entry -> entry.operation().equals(operation)));
                for (String actor : ACTORS) {
                    resultLines.add(row.capability() + "\t" + actor + "\tUNBUILT\t"
                            + unbuilt);
                    unbuiltCells++;
                }
                continue;
            }
            for (String actor : ACTORS) {
                if (isUnbuiltCell(row.capability(), actor)) {
                    resultLines.add(row.capability() + "\t" + actor + "\tUNBUILT\t"
                            + UNBUILT_BY_CAPABILITY.get(row.capability()));
                    unbuiltCells++;
                    continue;
                }
                testedCells++;
                boolean expectedAllowed = expectsAllowed(row, actor);
                List<Boolean> observed = new ArrayList<>();
                for (Subprobe subprobe : capabilityProbes) {
                    String controlActor = subprobe.controlActor() == null
                            ? firstAllowedActor(row) : subprobe.controlActor();
                    invokeCounterexample(row.capability(), subprobe, controlActor);
                    if (actor.equals(controlActor)) { observed.add(true); continue; }
                    boolean actualAllowed = invokeActor(row.capability(), actor, subprobe);
                    observed.add(actualAllowed);
                    if (actualAllowed != expectedAllowed) {
                        mismatches.put(new Cell(row.capability(), actor),
                                new Outcome(actualAllowed ? "ALLOWED" : "DENIED", fixTaskFor(row.capability())));
                    }
                }
                boolean sameOutcome = observed.stream().distinct().count() == 1;
                String actual = sameOutcome ? (observed.getFirst() ? "ALLOWED" : "DENIED") : "MIXED";
                List<String> unbuilt = UNBUILT_BY_CAPABILITY.getOrDefault(row.capability(), List.of());
                String completeness = unbuilt.isEmpty() ? actual : "PARTIAL " + actual + "; unbuilt=" + unbuilt;
                resultLines.add(row.capability() + "\t" + actor + "\t" + completeness + "\t"
                        + capabilityProbes.stream().map(Subprobe::name).toList());
            }
        }
        String matrixLabel = matrixResultLabel();
        resultLines.forEach(line -> System.out.println(matrixLabel + " cell: " + line));
        System.out.println(matrixLabel + " matrix result: " + testedCells + " cells exercised; " + unbuiltCells
                + " cells unbuilt; " + mismatches.size() + " mismatches");
        System.out.println(matrixLabel + " unbuilt operations: " + readUnbuiltOperations());
        assertThat(testedCells + unbuiltCells).as("every §5.2 actor cell is either exercised or unbuilt")
                .isEqualTo((long) matrix.size() * ACTORS.size());
        assertThat(mismatches).containsExactlyEntriesOf(expectedMismatches());
        assertThat(registeredRows).hasSize(REGISTERED_CAPABILITIES.size());
    }

    /** @return matrix label used by this catalogue-specific test context */
    String matrixResultLabel() {
        return "B-01";
    }

    /** @return expected matrix exceptions for this policy catalogue */
    Map<Cell, Outcome> expectedMismatches() throws Exception {
        return readOpenCells();
    }

    private static boolean isUnbuiltCell(String capability, String actor) {
        return capability.equals("Block, unblock, or reopen another member's Task (`TSK-023`)")
                && actor.equals("Current Leader");
    }

    private String fixTaskFor(String capability) {
        return capability.equals(REGISTERED_CAPABILITIES.get(16))
                || capability.equals("View own attendance for a date (`RPT-015`)")
                || capability.equals("View per-member Project hours")
                || capability.equals("View and export the Project/Task report (`RPT-005`)")
                || capability.equals("View and export the Daily Project Work Report (`RPT-011`)")
                        ? "B-03" : "B-02";
    }

    private Map<String, List<Subprobe>> probes() {
        Map<String, List<Subprobe>> result = new LinkedHashMap<>();
        result.put(REGISTERED_CAPABILITIES.get(0), List.of(
                probe("create account", (fixture, actor) -> internships.create(
                        new CreateAccountCommand(fixture.email("new-account"), "Matrix-created", GlobalRole.MENTOR,
                                null, null, null), actor)),
                probe("resend activation", (fixture, actor) ->
                        accounts.resendActivation(fixture.pendingAccountId(), actor), fixture -> {
                            var pending = internships.create(new CreateAccountCommand(
                                    fixture.email("resend-target"), "Matrix resend target", GlobalRole.MENTOR,
                                    null, null, null), fixture.adminId());
                            fixture.pendingAccountId(pending.userId());
                        }),
                probe("read account directory", (fixture, actor) -> accounts.administrationViews(actor)),
                probe("read accounts by ID", (fixture, actor) ->
                        accounts.administrationViewsByIds(actor, List.of(fixture.targetMentorId())))));
        result.put(REGISTERED_CAPABILITIES.get(1), List.of(
                probe("lock account", (fixture, actor) -> accounts.lockAccount(fixture.targetMentorId(), actor)),
                probe("unlock account", (fixture, actor) -> accounts.unlockAccount(fixture.targetMentorId(), actor),
                        fixture -> accounts.lockAccount(fixture.targetMentorId(), fixture.adminId())),
                probe("deactivate account", (fixture, actor) -> accounts.deactivateAccount(fixture.targetMentorId(), actor)),
                probe("deactivate locked account", (fixture, actor) -> accounts.deactivateAccount(fixture.targetMentorId(), actor),
                        fixture -> accounts.lockAccount(fixture.targetMentorId(), fixture.adminId())),
                probe("deactivate pending account (ACC-028)",
                        (fixture, actor) -> accounts.deactivateAccount(fixture.pendingAccountId(), actor), fixture -> {
                            var pending = internships.create(new CreateAccountCommand(
                                    fixture.email("pending-target"), "Matrix pending target", GlobalRole.MENTOR,
                                    null, null, null), fixture.adminId());
                            fixture.pendingAccountId(pending.userId());
                        }),
                probe("reinstate deactivated account (ACC-029)",
                        (fixture, actor) -> accounts.reinstateAccount(fixture.targetMentorId(), actor),
                        fixture -> accounts.deactivateAccount(fixture.targetMentorId(), fixture.adminId())),
                probe("activate internship", (fixture, actor) -> internships.activateInternship(fixture.targetInternId(), actor),
                        fixture -> jdbc.sql("update intern_profiles set internship_status = 'NOT_STARTED' where user_id = :id")
                                .param("id", fixture.targetInternId()).update()),
                probe("complete internship", (fixture, actor) -> internships.completeInternship(fixture.targetInternId(), actor)),
                probe("withdraw internship", (fixture, actor) -> internships.withdrawInternship(fixture.targetInternId(), actor))));
        result.put(REGISTERED_CAPABILITIES.get(2), List.of(
                probe("save SMTP configuration", (fixture, actor) -> {
                    long verified = accounts.requireActiveAdminId(actor);
                    long draftId = smtp.saveDraft(verified, fixture.smtpDraft());
                    smtp.testDraft(draftId, verified, fixture.email("admin"));
                }),
                probe("save HolidayAPI configuration", (fixture, actor) -> holidayApi.saveDraft(actor,
                        new HolidayApiDraft("matrix-holiday-key"))),
                probe("schedule attendance policy", (fixture, actor) -> policies.schedule(actor, fixture.policyCommand())),
                probe("create calendar event", (fixture, actor) -> calendar.createManual(actor,
                        LocalDate.now().plusDays(30), "Matrix event", true))));
        result.put(REGISTERED_CAPABILITIES.get(3), List.of(probe("create Project", (fixture, actor) ->
                projects.create(actor, new ProjectCreateCommand("Matrix-created " + fixture.label, null,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), fixture.leaderId())))));
        result.put(REGISTERED_CAPABILITIES.get(4), List.of(
                probe("activate Project", (fixture, actor) -> projects.activate(actor, fixture.projectId())),
                probe("complete Project", (fixture, actor) -> projects.complete(actor, fixture.projectId()),
                        fixture -> projects.activate(fixture.mentorId(), fixture.projectId()))));
        result.put(REGISTERED_CAPABILITIES.get(5), List.of(probe("delete empty PLANNED Project",
                (fixture, actor) -> {
                    long emptyProjectId = projects.create(fixture.mentorId(), new ProjectCreateCommand(
                            "Matrix-empty " + fixture.label, null,
                            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), fixture.leaderId()));
                    projects.delete(actor, emptyProjectId);
                })));
        result.put(REGISTERED_CAPABILITIES.get(6), List.of(
                probeWithControl("cancel PLANNED Project", (fixture, actor) ->
                        cancelProjectForMatrix(fixture, actor, "PLANNED"), fixture -> {}, "Owning Mentor"),
                probeWithControl("cancel ACTIVE Project", (fixture, actor) ->
                        cancelProjectForMatrix(fixture, actor, "ACTIVE"), fixture -> {}, "Owning Mentor")));
        result.put(REGISTERED_CAPABILITIES.get(7), List.of(
                probe("directly add Project member", (fixture, actor) ->
                        projects.addMember(actor, fixture.projectId(), fixture.targetInternId())),
                probe("directly remove Project member", (fixture, actor) ->
                        projects.directRemoveMember(actor, fixture.projectId(), fixture.memberMembershipId(), null))));
        result.put(REGISTERED_CAPABILITIES.get(8), List.of(probeWithControl("issue/revoke or respond to own invitation",
                (fixture, actor) -> {
                    if (actor == fixture.adminId() || actor == fixture.mentorId()) {
                        projects.revokeInvitation(actor, fixture.invitationId());
                    } else if (actor == fixture.leaderId()) {
                        projects.revokeInvitation(actor, fixture.invitationId());
                        long newInvitation = projects.issueInvitation(actor, fixture.projectId(), fixture.targetInternId());
                        projects.revokeInvitation(actor, newInvitation);
                    } else {
                        projects.respondToInvitation(fixture.targetInternId(), fixture.invitationId(),
                                com.lab.labtimesheet.feature.project.model.InvitationResponse.ACCEPT);
                    }
                }, fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    fixture.invitationId(projects.issueInvitation(
                            fixture.leaderId(), fixture.projectId(), fixture.targetInternId()));
                }, "Owning Mentor")));
        result.put(REGISTERED_CAPABILITIES.get(9), List.of(probeWithControl("request/decide or cancel membership exit",
                (fixture, actor) -> {
                    if (actor == fixture.adminId()) {
                        projects.approveExit(actor, fixture.exitRequestId(), "Matrix decision");
                    } else if (actor == fixture.mentorId()) {
                        projects.approveExit(actor, fixture.exitRequestId(), "Matrix decision");
                    } else if (actor == fixture.leaderId()) {
                        projects.requestMemberRemoval(actor, fixture.projectId(),
                                fixture.memberMembershipId(), "Matrix leader request");
                    } else {
                        long requestId = projects.requestOwnLeave(actor, fixture.projectId(), "Matrix own leave");
                        projects.cancelExit(actor, fixture.projectId(), requestId);
                    }
                }, fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    fixture.exitRequestId(projects.requestMemberRemoval(fixture.leaderId(), fixture.projectId(),
                            fixture.controlMembershipId(), "Matrix mentor decision"));
                }, "Owning Mentor")));
        result.put(REGISTERED_CAPABILITIES.get(10), List.of(probe("change Project Leader", (fixture, actor) ->
                projects.changeLeader(actor, fixture.projectId(), fixture.memberId()))));
        result.put(REGISTERED_CAPABILITIES.get(11), List.of(probe("create Task", (fixture, actor) -> {
            long assigneeMembership = fixture.membershipForActor(actor);
            tasks.create(fixture.emailForActor(actor), new CreateTaskCommand(
                    fixture.projectId(), assigneeMembership, "Matrix Task", null, null));
        })));
        result.put(REGISTERED_CAPABILITIES.get(12), List.of(probe("reassign unfinished Task", (fixture, actor) -> {
            long taskId = seedTask(fixture, fixture.memberId(), fixture.memberMembershipId());
            tasks.reassign(fixture.emailForActor(actor), fixture.projectId(), taskId, null,
                    fixture.leaderMembershipId());
        })));
        result.put(REGISTERED_CAPABILITIES.get(13), List.of(
                probe("set Task estimate", (fixture, actor) -> {
                    long taskId = seedTask(fixture, fixture.memberId(), fixture.memberMembershipId());
                    tasks.estimate(fixture.emailForActor(actor), fixture.projectId(), taskId, null, 60);
                }),
                probe("replace Task estimate", (fixture, actor) -> {
                    long taskId = seedTask(fixture, fixture.memberId(), fixture.memberMembershipId());
                    tasks.estimate(fixture.email("leader"), fixture.projectId(), taskId, null, 60);
                    tasks.estimate(fixture.emailForActor(actor), fixture.projectId(), taskId, null, 120);
                }),
                probe("clear Task estimate", (fixture, actor) -> {
                    long taskId = seedTask(fixture, fixture.memberId(), fixture.memberMembershipId());
                    tasks.estimate(fixture.email("leader"), fixture.projectId(), taskId, null, 60);
                    tasks.estimate(fixture.emailForActor(actor), fixture.projectId(), taskId, null, null);
                })));
        result.put(REGISTERED_CAPABILITIES.get(14), List.of(
                probe("edit unfinished Task", (fixture, actor) -> {
                    long taskId = seedTask(fixture, fixture.memberId(), fixture.memberMembershipId());
                    tasks.edit(fixture.emailForActor(actor), fixture.projectId(), taskId,
                            "Matrix edited Task", null, null);
                }),
                probe("soft-delete unfinished Task", (fixture, actor) -> {
                    long taskId = seedTask(fixture, fixture.memberId(), fixture.memberMembershipId());
                    tasks.softDelete(fixture.emailForActor(actor), fixture.projectId(), taskId);
                })));
        result.put(REGISTERED_CAPABILITIES.get(15), List.of(probe("change own assigned Task status", (fixture, actor) -> {
            projects.activate(fixture.mentorId(), fixture.projectId());
            long assignedMembership = actor == fixture.leaderId()
                    ? fixture.leaderMembershipId() : fixture.memberMembershipId();
            long taskId = seedTask(fixture, fixture.leaderId(), assignedMembership);
            tasks.changeStatus(fixture.emailForActor(actor), fixture.projectId(), taskId, TaskStatus.IN_PROGRESS);
        })));
        result.put(REGISTERED_CAPABILITIES.get(16), List.of(
                probe("block another member's Task", (fixture, actor) -> {
                    long taskId = seedOtherMembersTask(fixture, actor);
                    tasks.changeStatus(fixture.emailForActor(actor), fixture.projectId(), taskId, TaskStatus.BLOCKED);
                }),
                probe("unblock another member's Task", (fixture, actor) -> {
                    long taskId = seedOtherMembersTask(fixture, actor);
                    tasks.changeStatus(fixture.email("mentor"), fixture.projectId(), taskId, TaskStatus.BLOCKED);
                    tasks.changeStatus(fixture.emailForActor(actor), fixture.projectId(), taskId, TaskStatus.TODO);
                }),
                probe("reopen another member's Task", (fixture, actor) -> {
                    long taskId = seedOtherMembersTask(fixture, actor);
                    String assigneeKey = actor == fixture.memberId() ? "leader" : "member";
                    tasks.changeStatus(fixture.email(assigneeKey), fixture.projectId(), taskId, TaskStatus.IN_PROGRESS);
                    tasks.changeStatus(fixture.email(assigneeKey), fixture.projectId(), taskId, TaskStatus.DONE);
                    tasks.changeStatus(fixture.emailForActor(actor), fixture.projectId(), taskId, null,
                            new TaskStatusChangeCommand(TaskStatus.IN_PROGRESS, "Matrix reopen reason"));
                })));
        result.put(REGISTERED_CAPABILITIES.get(17), List.of(
                probeWithControl("start another member's Task", (fixture, actor) -> {
                    long taskId = seedTask(fixture, fixture.leaderId(), fixture.controlMembershipId());
                    tasks.changeStatus(fixture.emailForActor(actor), fixture.projectId(), taskId, TaskStatus.IN_PROGRESS);
                }, fixture -> projects.activate(fixture.mentorId(), fixture.projectId()), "Task assignee control"),
                probeWithControl("mark another member's Task DONE", (fixture, actor) -> {
                    long taskId = seedTask(fixture, fixture.leaderId(), fixture.controlMembershipId());
                    tasks.changeStatus(fixture.email(fixture.controlAssigneeKey()), fixture.projectId(), taskId,
                            TaskStatus.IN_PROGRESS);
                    tasks.changeStatus(fixture.emailForActor(actor), fixture.projectId(), taskId, TaskStatus.DONE);
                }, fixture -> projects.activate(fixture.mentorId(), fixture.projectId()), "Task assignee control")));
        result.put(REGISTERED_CAPABILITIES.get(18), List.of(probe("comment on Task", (fixture, actor) -> {
            long taskId = seedTask(fixture, fixture.memberId(), fixture.memberMembershipId());
            tasks.addComment(fixture.emailForActor(actor), fixture.projectId(), taskId, "Matrix comment");
        })));
        result.put(REGISTERED_CAPABILITIES.get(19), List.of(probe("list visible Projects and Tasks with progress",
                (fixture, actor) -> {
                    projectQueries.listVisible(actor);
                    tasks.list(fixture.emailForActor(actor), fixture.projectId());
                }, fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    seedTask(fixture, fixture.leaderId(), fixture.memberMembershipId());
                })));
        result.put(REGISTERED_CAPABILITIES.get(20), List.of(probe("read scoped aggregate Project progress",
                (fixture, actor) -> {
                    projectQueries.dashboardSummary(actor);
                    tasks.list(fixture.emailForActor(actor), fixture.projectId());
                }, fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    seedTask(fixture, fixture.leaderId(), fixture.memberMembershipId());
                })));
        result.put(REGISTERED_CAPABILITIES.get(21), List.of(probe("read Project and Task history",
                (fixture, actor) -> {
                    projectQueries.history(actor, fixture.projectId());
                    long taskId = seedTask(fixture, fixture.leaderId(), fixture.leaderMembershipId());
                    tasks.details(fixture.emailForActor(actor), fixture.projectId(), taskId);
                }, fixture -> projects.activate(fixture.mentorId(), fixture.projectId()))));
        result.put(REGISTERED_CAPABILITIES.get(22), List.of(probe("read attendance history within actor scope",
                (fixture, actor) -> {
                    boolean internActor = actor == fixture.leaderId() || actor == fixture.memberId();
                    long target = internActor ? actor : fixture.memberId();
                    attendance.history(new AttendanceActor(actor, fixture.roleFor(actor)), target,
                            LocalDate.now().minusDays(7), LocalDate.now());
                })));
        result.put(REGISTERED_CAPABILITIES.get(23), List.of(probe("record remaining-effort forecast on worked Task",
                (fixture, actor) -> {
                    tasks.reassign(fixture.emailForActor(actor), fixture.projectId(), fixture.forecastTaskId(), null,
                            fixture.memberMembershipId(), new RemainingEffortForecastInput(180, "Matrix handoff"));
                }, fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    long taskId = seedTask(fixture, fixture.leaderId(), fixture.leaderMembershipId());
                    tasks.addWorkLog(fixture.email("leader"), fixture.projectId(), taskId,
                            PROBE_WORK_DATE, 30, "Worked before reassignment");
                    fixture.forecastTaskId(taskId);
                })));
        result.put(REGISTERED_CAPABILITIES.get(24), List.of(
                probe("create own Task work log", (fixture, actor) -> {
                    long taskId = actor == fixture.leaderId()
                            ? fixture.leaderTaskId() : fixture.memberTaskId();
                    tasks.addWorkLog(fixture.emailForActor(actor), fixture.projectId(), taskId,
                            PROBE_WORK_DATE, 30, "Matrix work log");
                }, fixture -> prepareOwnTasks(fixture, false)),
                probe("edit own Task work log", (fixture, actor) -> {
                    TaskWorkLogView workLog = actor == fixture.leaderId()
                            ? fixture.leaderWorkLog() : fixture.memberWorkLog();
                    tasks.correctWorkLog(fixture.emailForActor(actor), fixture.projectId(), workLog.id(),
                            null, workLog.version(), 45, "Matrix corrected work log");
                }, fixture -> prepareOwnTasks(fixture, true))));
        result.put(REGISTERED_CAPABILITIES.get(25), List.of(probeWithControl("build and export Attendance report",
                (fixture, actor) -> {
                    Principal principal = () -> fixture.emailForActor(actor);
                    Long requestedIntern = actor == fixture.adminId() || actor == fixture.mentorId()
                            ? fixture.memberId() : null;
                    var report = attendanceReports.build(principal, requestedIntern,
                            PROBE_WORK_DATE, PROBE_WORK_DATE);
                    reportExports.attendanceXlsx(report);
                    reportExports.attendancePdf(report);
                }, fixture -> { }, "Owning Mentor")));
        result.put(REGISTERED_CAPABILITIES.get(26), List.of(probe("view only actor's own attendance date",
                (fixture, actor) -> attendance.history(new AttendanceActor(actor, fixture.roleFor(actor)), actor,
                        PROBE_WORK_DATE, PROBE_WORK_DATE))));
        result.put(REGISTERED_CAPABILITIES.get(27), List.of(probe("transfer unfinished Task for pending member exit",
                (fixture, actor) -> projects.transferTasks(actor, fixture.projectId(),
                        fixture.memberMembershipId(), Set.of(fixture.transferTaskId()), fixture.leaderMembershipId()),
                fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    long taskId = seedTask(fixture, fixture.leaderId(), fixture.memberMembershipId());
                    long requestId = projects.requestMemberRemoval(fixture.leaderId(), fixture.projectId(),
                            fixture.memberMembershipId(), "Matrix exit");
                    fixture.exitRequestId(requestId);
                    fixture.transferTaskId(taskId);
                })));
        result.put(REGISTERED_CAPABILITIES.get(28), List.of(
                probe("submit own leave request", (fixture, actor) -> leaves.submit(
                        new AttendanceActor(actor, fixture.roleFor(actor)),
                        new LeaveRequestCommand(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "Matrix leave"))),
                probe("submit own correction request", (fixture, actor) -> {
                    long recordId = actor == fixture.leaderId()
                            ? fixture.leaderCorrectionRecordId() : fixture.memberCorrectionRecordId();
                    corrections.submit(new AttendanceActor(actor, fixture.roleFor(actor)), recordId,
                            new CorrectionRequestCommand(
                                    java.time.LocalDateTime.of(2026, 8, 13, 17, 0), "Matrix correction"));
                }, this::prepareMissingCheckoutRows)));
        result.put(REGISTERED_CAPABILITIES.get(29), List.of(probeCommitted("withdraw own pending leave request",
                (fixture, actor) -> leaves.cancel(new AttendanceActor(actor, fixture.roleFor(actor)),
                        actor == fixture.leaderId() ? fixture.leaderLeaveId() : fixture.memberLeaveId()),
                fixture -> fixture.leaveRequestIds(
                        leaves.submit(new AttendanceActor(fixture.leaderId(), GlobalRole.INTERN),
                                new LeaveRequestCommand(LocalDate.of(2026, 8, 17),
                                        LocalDate.of(2026, 8, 17), "Leader matrix leave")).id(),
                        leaves.submit(new AttendanceActor(fixture.memberId(), GlobalRole.INTERN),
                                new LeaveRequestCommand(LocalDate.of(2026, 8, 17),
                                        LocalDate.of(2026, 8, 17), "Member matrix leave")).id()))));
        result.put(REGISTERED_CAPABILITIES.get(30), List.of(probeWithControl("read per-member Project hours",
                (fixture, actor) -> {
                    var report = projectTaskReports.build(fixture.emailForActor(actor), fixture.projectId(),
                            null, null, null, null);
                    if (!report.detailedMemberHours() || report.memberHours().isEmpty()) {
                        throw new org.springframework.security.access.AccessDeniedException(
                                "Per-member Project hours are not available to this actor");
                    }
                }, fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    seedTask(fixture, fixture.leaderId(), fixture.memberMembershipId());
                }, "Owning Mentor")));
        result.put(REGISTERED_CAPABILITIES.get(31), List.of(probeWithControl("build Project/Task report",
                (fixture, actor) -> projectTaskReports.build(fixture.emailForActor(actor), fixture.projectId(),
                        null, null, null, null), fixture -> {
                    projects.activate(fixture.mentorId(), fixture.projectId());
                    seedTask(fixture, fixture.leaderId(), fixture.memberMembershipId());
                }, "Owning Mentor")));
        result.put(REGISTERED_CAPABILITIES.get(32), List.of(probeWithControl("build Daily Project Work Report",
                (fixture, actor) -> dailyReports.build(fixture.emailForActor(actor), fixture.projectId(), PROBE_WORK_DATE),
                fixture -> projects.activate(fixture.mentorId(), fixture.projectId()), "Owning Mentor")));
        result.put(REGISTERED_CAPABILITIES.get(33), List.of());
        result.put(REGISTERED_CAPABILITIES.get(34), List.of());
        result.put(REGISTERED_CAPABILITIES.get(35), List.of());
        return result;
    }

    private void prepareMissingCheckoutRows(Fixture fixture) {
        long policyId = jdbc.sql("select id from attendance_policy_versions order by id limit 1")
                .query(Long.class).single();
        fixture.correctionRecordIds(
                insertMissingCheckout(fixture.leaderId(), policyId),
                insertMissingCheckout(fixture.memberId(), policyId));
    }

    private long insertMissingCheckout(long internId, long policyId) {
        return jdbc.sql("""
                insert into attendance_records (intern_user_id, work_date, policy_version_id, check_in_at)
                values (:intern, date '2026-08-13', :policy, timestamptz '2026-08-13 02:00:00+00')
                returning id
                """).param("intern", internId).param("policy", policyId).query(Long.class).single();
    }

    private void prepareOwnTasks(Fixture fixture, boolean withWorkLogs) {
        projects.activate(fixture.mentorId(), fixture.projectId());
        long leaderTask = seedTask(fixture, fixture.leaderId(), fixture.leaderMembershipId());
        long memberTask = seedTask(fixture, fixture.leaderId(), fixture.memberMembershipId());
        fixture.ownTaskIds(leaderTask, memberTask);
        if (withWorkLogs) {
            fixture.ownWorkLogs(
                    tasks.addWorkLog(fixture.email("leader"), fixture.projectId(), leaderTask,
                            PROBE_WORK_DATE, 30, "Leader's prior work"),
                    tasks.addWorkLog(fixture.email("member"), fixture.projectId(), memberTask,
                            PROBE_WORK_DATE, 30, "Member's prior work"));
        }
    }

    private void cancelProjectForMatrix(Fixture fixture, long actorId, String initialStatus) {
        long projectId = projects.create(fixture.mentorId(), new ProjectCreateCommand(
                "Matrix-cancel " + initialStatus + " " + fixture.label, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), fixture.leaderId()));
        if (initialStatus.equals("ACTIVE")) {
            projects.activate(fixture.mentorId(), projectId);
        }
        try {
            projects.cancel(actorId, projectId, "Matrix authorization probe");
        } catch (ProjectAccessDeniedException denied) {
            assertThat(projectStatus(projectId)).isEqualTo(initialStatus);
            throw denied;
        }
        assertThat(actorId).isEqualTo(fixture.mentorId());
        assertThat(projectStatus(projectId)).isEqualTo("CANCELLED");
    }

    /** Protects PRJ-023 and AUTH-012: an owning Mentor cannot cancel a completed Project. */
    @Test
    void owningMentorCannotCancelACompletedProject() {
        new TransactionTemplate(transactionManager).execute(status -> {
            Fixture fixture = seedFixture("CANCEL_PROJECT completed Mentor probe");
            long projectId = projects.create(fixture.mentorId(), new ProjectCreateCommand(
                    "Matrix-completed-cancel " + fixture.label, null,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), fixture.leaderId()));
            projects.activate(fixture.mentorId(), projectId);
            projects.complete(fixture.mentorId(), projectId);
            assertThatThrownBy(() -> projects.cancel(fixture.mentorId(), projectId, "Completed probe"))
                    .isInstanceOf(ProjectAccessDeniedException.class);
            assertThat(projectStatus(projectId)).isEqualTo("COMPLETED");
            status.setRollbackOnly();
            return null;
        });
    }

    private String projectStatus(long projectId) {
        return jdbc.sql("select status from projects where id = :id")
                .param("id", projectId)
                .query(String.class)
                .single();
    }

    private long seedTask(Fixture fixture, long creatorUserId, long assigneeMembershipId) {
        return tasks.create(fixture.emailForActor(creatorUserId), new CreateTaskCommand(
                fixture.projectId(), assigneeMembershipId, "Matrix fixture Task", null, null)).id();
    }

    private long seedOtherMembersTask(Fixture fixture, long actorUserId) {
        projects.activate(fixture.mentorId(), fixture.projectId());
        long assigneeMembership = actorUserId == fixture.memberId()
                ? fixture.leaderMembershipId() : fixture.memberMembershipId();
        return seedTask(fixture, fixture.memberId(), assigneeMembership);
    }

    private static Subprobe probe(String name, ProbeAction action) {
        return probe(name, action, fixture -> { });
    }

    private static Subprobe probe(String name, ProbeAction action, FixtureSetup setup) {
        return new Subprobe(name, action, setup, null, false);
    }

    private static Subprobe probeWithControl(String name, ProbeAction action, FixtureSetup setup, String controlActor) {
        return new Subprobe(name, action, setup, controlActor, false);
    }

    private static Subprobe probeCommitted(String name, ProbeAction action, FixtureSetup setup) {
        return new Subprobe(name, action, setup, null, true);
    }

    private void invokeCounterexample(String capability, Subprobe probe, String actor) {
        Fixture fixture = inRolledBackFixture(capability + " / " + actor + " counterexample", probe, actor);
        assertThat(fixture.controlSucceeded())
                .as("setup invalid for %s / %s counterexample / %s (%s)",
                        capability, actor, probe.name(), fixture.failure())
                .isTrue();
    }

    private static String firstAllowedActor(MatrixRow row) {
        return ACTORS.stream().filter(actor -> !row.cells().get(actor).equals("No")).findFirst()
                .orElseThrow(() -> new AssertionError("Matrix row grants no actor: " + row.capability()));
    }

    private boolean invokeActor(String capability, String actorColumn, Subprobe probe) {
        Fixture fixture = inRolledBackFixture(capability + " / " + actorColumn, probe, actorColumn);
        if (fixture.deniedByAuthorization()) return false;
        if (fixture.controlSucceeded()) return true;
        throw new AssertionError("setup invalid for " + capability + " / " + actorColumn,
                fixture.failure());
    }

    private Fixture inRolledBackFixture(String label, Subprobe probe, String actorColumn) {
        if (probe.commitSetup()) return inCommittedFixture(label, probe, actorColumn);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                Fixture fixture = seedFixture(label);
                String phase = "fixture setup";
                try {
                    probe.setup().arrange(fixture);
                    long actorId = fixture.actorId(actorColumn);
                    phase = "service action";
                    probe.action().run(fixture, actorId);
                    fixture.controlSucceeded(true);
                } catch (Throwable failure) {
                    if (isAuthorizationDenial(failure)) {
                        fixture.deniedByAuthorization(true);
                        fixture.failure(new IllegalStateException(phase + " was denied: " + failure, failure));
                    }
                    else fixture.failure(failure);
                }
                status.setRollbackOnly();
                return fixture;
            });
        } catch (Throwable setupFailure) {
            throw new AssertionError("setup invalid for " + label + " / " + probe.name(), setupFailure);
        }
    }

    private Fixture inCommittedFixture(String label, Subprobe probe, String actorColumn) {
        Fixture fixture = new TransactionTemplate(transactionManager).execute(status -> {
            Fixture committed = seedFixture(label);
            try {
                probe.setup().arrange(committed);
            } catch (Throwable failure) {
                committed.failure(failure);
            }
            return committed;
        });
        if (fixture.failure() != null) return fixture;
        try {
            probe.action().run(fixture, fixture.actorId(actorColumn));
            fixture.controlSucceeded(true);
        } catch (Throwable failure) {
            if (isAuthorizationDenial(failure)) {
                fixture.deniedByAuthorization(true);
                fixture.failure(new IllegalStateException(
                        "committed-fixture service action was denied: " + failure, failure));
            } else fixture.failure(failure);
        }
        return fixture;
    }

    private Fixture seedFixture(String label) {
        long sequence = FIXTURE_SEQUENCE.incrementAndGet();
        String suffix = "mx" + sequence;
        long admin = insertUser(suffix + "-admin@example.test", "ADMIN");
        long mentor = insertUser(suffix + "-mentor@example.test", "MENTOR");
        long leader = insertIntern(suffix + "-leader@example.test");
        long member = insertIntern(suffix + "-member@example.test");
        long controlAssignee = insertIntern(suffix + "-control-assignee@example.test");
        long targetMentor = insertUser(suffix + "-target-mentor@example.test", "MENTOR");
        long targetIntern = insertIntern(suffix + "-target-intern@example.test");
        long projectId = jdbc.sql("""
                insert into projects (mentor_user_id, name, status, start_date, end_date)
                values (:mentor, :name, 'PLANNED', date '2026-08-01', date '2026-12-31') returning id
                """).param("mentor", mentor).param("name", suffix + " Project")
                .query(Long.class).single();
        long leaderMembership = insertMembership(projectId, leader, mentor);
        long memberMembership = insertMembership(projectId, member, mentor);
        long controlMembership = insertMembership(projectId, controlAssignee, mentor);
        jdbc.sql("""
                insert into project_leadership_terms
                    (project_id, membership_id, started_at, appointed_by_mentor_user_id)
                values (:project, :membership, timestamptz '2026-08-01 00:00:00+00', :mentor)
                """).param("project", projectId).param("membership", leaderMembership)
                .param("mentor", mentor).update();

        long smtpDraft = smtp.saveDraft(admin, new SmtpDraft("mailpit", 1025, SecurityMode.NONE,
                null, null, suffix + "-admin@example.test", "Lab Timesheet"));
        smtp.testDraft(smtpDraft, admin, suffix + "-admin@example.test");
        smtp.activate(smtpDraft, admin);
        return new Fixture(label, suffix, admin, mentor, leader, member, controlAssignee, targetMentor, targetIntern,
                projectId, leaderMembership, memberMembership, controlMembership);
    }

    private long insertUser(String email, String role) {
        return jdbc.sql("""
                insert into app_users (email, display_name, password_hash, global_role, account_status, activated_at)
                values (:email, :email, 'test-hash', :role, 'ACTIVE', current_timestamp) returning id
                """).param("email", email).param("role", role).query(Long.class).single();
    }

    private long insertIntern(String email) {
        long id = insertUser(email, "INTERN");
        jdbc.sql("""
                insert into intern_profiles
                    (user_id, student_code, internship_start_date, internship_end_date, internship_status, activated_at)
                values (:id, :code, date '2026-08-01', date '2026-12-31', 'ACTIVE', current_timestamp)
                """).param("id", id).param("code", "S" + id).update();
        return id;
    }

    private long insertMembership(long projectId, long internId, long mentorId) {
        return jdbc.sql("""
                insert into project_memberships (project_id, intern_user_id, joined_at, added_by_user_id)
                values (:project, :intern, timestamptz '2026-08-01 00:00:00+00', :mentor) returning id
                """).param("project", projectId).param("intern", internId).param("mentor", mentorId)
                .query(Long.class).single();
    }

    private List<MatrixRow> readMatrix() throws Exception {
        List<String> lines = Files.readAllLines(SPEC);
        int header = -1;
        int end = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| Capability | Admin |")) header = i;
            if (header >= 0 && i > header && lines.get(i).startsWith("| ID | Requirement |")) { end = i; break; }
        }
        assertThat(header).as("§5.2 matrix header in %s", SPEC).isGreaterThanOrEqualTo(0);
        assertThat(end).as("end of §5.2 matrix in %s", SPEC).isGreaterThan(header);
        List<MatrixRow> rows = new ArrayList<>();
        for (int i = header + 2; i < end; i++) {
            String line = lines.get(i);
            if (!line.startsWith("| ")) continue;
            List<String> cells = splitRow(line);
            if (cells.size() != 5) continue;
            rows.add(new MatrixRow(cells.get(0), Map.of(
                    ACTORS.get(0), cells.get(1), ACTORS.get(1), cells.get(2),
                    ACTORS.get(2), cells.get(3), ACTORS.get(3), cells.get(4))));
        }
        assertThat(rows).hasSize(36);
        for (MatrixRow row : rows) for (String actor : ACTORS) assertRecognized(row.capability(), actor, row.cells().get(actor));
        return rows;
    }

    private static List<String> splitRow(String line) {
        return java.util.Arrays.stream(line.substring(1, line.length() - 1).split("\\|", -1))
                .map(String::trim).toList();
    }

    private static void assertRecognized(String capability, String actor, String cell) {
        if (cell.equals("No") || cell.startsWith("Yes") || cell.equals("Own") || cell.equals("Assigned")
                || cell.equals("Membership scope") || cell.startsWith("Revoke ") || cell.startsWith("Issue/revoke ")
                || cell.startsWith("Accept/decline ") || cell.startsWith("Decide ") || cell.startsWith("Request ")
                || cell.startsWith("Request/cancel ") || cell.startsWith("Unfinished ") || cell.startsWith("If active Intern")
                || cell.startsWith("Responsible Mentor only ") || cell.startsWith("Own history only")
                || cell.startsWith("Own, if active Intern") || cell.startsWith("Own, in ")
                || cell.startsWith("Own; ") || cell.startsWith("Current membership;")
                || cell.startsWith("Own, `ACTIVE` Project")
                || cell.startsWith("Project scope") || cell.startsWith("Aggregate only") || cell.startsWith("Led ")
                || cell.startsWith("Assigned/own log") || cell.startsWith("Unfinished self-created ")
                || cell.startsWith("Self-assigned only")) return;
        throw new AssertionError("Unrecognized authorization matrix cell: " + capability + " / " + actor + " = " + cell);
    }

    private static boolean expectsAllowed(MatrixRow row, String actor) {
        return !row.cells().get(actor).equals("No");
    }

    private Map<Cell, Outcome> readOpenCells() throws Exception {
        Map<Cell, Outcome> open = new LinkedHashMap<>();
        for (String line : Files.readAllLines(OPEN_CELLS)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            assertThat(fields).as("open-cell TSV row: %s", line).hasSize(4);
            open.put(new Cell(fields[0], fields[1]), new Outcome(fields[2], fields[3]));
        }
        return open;
    }

    private List<UnbuiltOperation> readUnbuiltOperations() throws Exception {
        List<UnbuiltOperation> operations = new ArrayList<>();
        for (String line : Files.readAllLines(UNBUILT_OPERATIONS)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            assertThat(fields).as("unbuilt-operation TSV row: %s", line).hasSize(3);
            operations.add(new UnbuiltOperation(fields[0], fields[1], fields[2]));
        }
        return operations;
    }

    private static boolean isAuthorizationDenial(Throwable failure) {
        if (failure instanceof AccessDeniedException) return true;
        if (failure instanceof ProjectAccessDeniedException) return true;
        if (failure instanceof TaskNotFoundException) return true;
        if (failure instanceof IllegalArgumentException && failure.getMessage() != null) {
            return Set.of("Admin not found", "An active Admin is required").contains(failure.getMessage());
        }
        return false;
    }

    @FunctionalInterface private interface ProbeAction { void run(Fixture fixture, long actorId) throws Exception; }
    @FunctionalInterface private interface FixtureSetup { void arrange(Fixture fixture) throws Exception; }
    private record Subprobe(String name, ProbeAction action, FixtureSetup setup, String controlActor,
            boolean commitSetup) {}

    private record MatrixRow(String capability, Map<String, String> cells) {}
    record Cell(String capability, String actor) {}
    record Outcome(String actual, String task) {}
    private record UnbuiltOperation(String operation, String rule, String plan) {}

    private static final class Fixture {
        private final String label;
        private final String suffix;
        private final long admin;
        private final long mentor;
        private final long leader;
        private final long member;
        private final long controlAssignee;
        private final long leaderIntern;
        private final long targetMentor;
        private final long targetIntern;
        private long pendingAccountId;
        private final long project;
        private final long leaderMembership;
        private final long memberMembership;
        private final long controlMembership;
        private long forecastTaskId;
        private long leaderTaskId;
        private long memberTaskId;
        private TaskWorkLogView leaderWorkLog;
        private TaskWorkLogView memberWorkLog;
        private long exitRequestId;
        private long invitationId;
        private long transferTaskId;
        private long leaderCorrectionRecordId;
        private long memberCorrectionRecordId;
        private long leaderLeaveId;
        private long memberLeaveId;
        private boolean controlSucceeded;
        private boolean deniedByAuthorization;
        private Throwable failure;

        Fixture(String label, String suffix, long admin, long mentor, long leader, long member, long controlAssignee,
                long targetMentor, long targetIntern, long project, long leaderMembership, long memberMembership,
                long controlMembership) {
            this.label = label; this.suffix = suffix; this.admin = admin; this.mentor = mentor;
            this.leader = leader; this.member = member; this.controlAssignee = controlAssignee;
            this.targetMentor = targetMentor; this.targetIntern = targetIntern;
            this.leaderIntern = leader;
            this.project = project; this.leaderMembership = leaderMembership; this.memberMembership = memberMembership;
            this.controlMembership = controlMembership;
        }
        long actorId(String actor) { return switch (actor) {
            case "Admin" -> admin; case "Owning Mentor" -> mentor; case "Current Leader" -> leader;
            case "Active member / assignee" -> member; case "Task assignee control" -> controlAssignee;
            default -> throw new IllegalArgumentException(actor);
        }; }
        String email(String key) { return suffix + "-" + key + "@example.test"; }
        long targetMentorId() { return targetMentor; }
        long targetInternId() { return targetIntern; }
        long pendingAccountId() { return pendingAccountId; }
        void pendingAccountId(long value) { pendingAccountId = value; }
        long memberId() { return member; }
        long memberMembershipId() { return memberMembership; }
        long leaderMembershipId() { return leaderMembership; }
        long leaderId() { return leaderIntern; }
        long controlMembershipId() { return controlMembership; }
        String controlAssigneeKey() { return "control-assignee"; }
        long forecastTaskId() { return forecastTaskId; }
        void forecastTaskId(long value) { forecastTaskId = value; }
        long leaderTaskId() { return leaderTaskId; }
        long memberTaskId() { return memberTaskId; }
        void ownTaskIds(long leaderTask, long memberTask) {
            leaderTaskId = leaderTask; memberTaskId = memberTask;
        }
        TaskWorkLogView leaderWorkLog() { return leaderWorkLog; }
        TaskWorkLogView memberWorkLog() { return memberWorkLog; }
        void ownWorkLogs(TaskWorkLogView leaderLog, TaskWorkLogView memberLog) {
            leaderWorkLog = leaderLog; memberWorkLog = memberLog;
        }
        long exitRequestId() { return exitRequestId; }
        void exitRequestId(long value) { exitRequestId = value; }
        long invitationId() { return invitationId; }
        void invitationId(long value) { invitationId = value; }
        long transferTaskId() { return transferTaskId; }
        void transferTaskId(long value) { transferTaskId = value; }
        long leaderCorrectionRecordId() { return leaderCorrectionRecordId; }
        long memberCorrectionRecordId() { return memberCorrectionRecordId; }
        void correctionRecordIds(long leaderRecord, long memberRecord) {
            leaderCorrectionRecordId = leaderRecord; memberCorrectionRecordId = memberRecord;
        }
        long leaderLeaveId() { return leaderLeaveId; }
        long memberLeaveId() { return memberLeaveId; }
        void leaveRequestIds(long leaderRequest, long memberRequest) {
            leaderLeaveId = leaderRequest; memberLeaveId = memberRequest;
        }
        long projectId() { return project; }
        long membershipForActor(long actor) {
            if (actor == leader) return leaderMembership;
            if (actor == member) return memberMembership;
            return memberMembership;
        }
        String emailForActor(long actor) {
            if (actor == admin) return email("admin");
            if (actor == mentor) return email("mentor");
            if (actor == leader) return email("leader");
            if (actor == member) return email("member");
            if (actor == controlAssignee) return email(controlAssigneeKey());
            throw new IllegalArgumentException("Unknown fixture actor id " + actor);
        }
        long adminId() { return admin; }
        GlobalRole roleFor(long actor) {
            if (actor == admin) return GlobalRole.ADMIN;
            if (actor == mentor) return GlobalRole.MENTOR;
            return GlobalRole.INTERN;
        }
        long mentorId() { return mentor; }
        SmtpDraft smtpDraft() { return new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null,
                email("admin"), "Lab Timesheet"); }
        AttendancePolicyCommand policyCommand() { return new AttendancePolicyCommand(
                LocalDate.now().plusMonths(2).withDayOfMonth(1), ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30), LocalTime.of(17, 30), 15, 15, 3, new BigDecimal("0.25"),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)); }
        boolean controlSucceeded() { return controlSucceeded; }
        void controlSucceeded(boolean value) { controlSucceeded = value; }
        boolean deniedByAuthorization() { return deniedByAuthorization; }
        void deniedByAuthorization(boolean value) { deniedByAuthorization = value; }
        Throwable failure() { return failure; }
        void failure(Throwable value) { failure = value; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean @Primary RecordingSmtpProbe recordingSmtpProbe() { return new RecordingSmtpProbe(); }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {
        @Override public void send(SmtpConnection connection, String recipient, String subject, String body) { }
    }
}
