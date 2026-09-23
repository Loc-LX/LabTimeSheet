package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskConflictException;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.TaskVarianceState;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskEffortPlanningView;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskRemainingEffortForecastView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogCandidate;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.model.entity.TaskRemainingEffortForecast;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRemainingEffortForecastRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes authorized Task creation, status, comment, and current/historical read use cases.
 *
 * <p>Project identity, lifecycle, ownership, leadership, and membership are obtained through
 * Project service DTOs. Authenticated mutations route through a scalar Account identifier, then
 * the Project mutation context, and finally feature-owned Task rows; work logs re-enter the
 * Account-owned date window before reading or writing daily totals. Access failures are translated
 * to a non-disclosing Task 404, while authenticated business-rule failures use Task validation.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskWorkLogRepository workLogs;
    private final ProjectQueryService projects;
    private final ProjectService projectMutations;
    private final CalendarApplicationService calendar;
    private final Clock clock;
    private final AccountService accounts;
    private final NotificationService notifications;

    private final TaskRemainingEffortForecastRepository forecasts;

    /**
     * Creates a TODO Task in a PLANNED or ACTIVE Project.
     *
     * <p>The Project is write-locked before membership and lifecycle checks. A current Leader may
     * choose any active same-Project member; another active member may choose only themselves.
     * Creator, assigner, and assignment time are stored from authenticated current context. An
     * optional due date must be within Project dates and not a current global day off. A membership
     * with a pending exit remains visible for existing rights but cannot receive a new Task. A
     * non-self assignment publishes a designated in-app and ordinary-email notification to the
     * new assignee; a validated self-Task invokes the notification boundary with its silence marker.
     *
     * @param actorEmail authenticated account email
     * @param command requested Project, membership, and Task fields
     * @return created Task projection
     * @throws TaskNotFoundException when current authorization/context is absent
     * @throws TaskValidationException when title or due date violates a business rule
     */
    @Transactional
    public TaskView create(String actorEmail, CreateTaskCommand command) {
        String title = requireTitle(command.title());
        TaskAccess access = requireMutationAccess(actorEmail, command.projectId());
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        ProjectTaskMemberView assignee = requireAssigneeMembership(
                access.project(), command.assigneeMembershipId());
        if (actorMembership.membershipId() != assignee.membershipId()
                && !Objects.equals(access.project().currentLeaderMembershipId(), actorMembership.membershipId())) {
            throw new TaskNotFoundException();
        }
        if (command.estimatedMinutes() != null
                && !Objects.equals(access.project().currentLeaderMembershipId(), actorMembership.membershipId())) {
            throw new TaskNotFoundException();
        }
        validateEstimate(command.estimatedMinutes());
        validateDueDate(access.project(), command.dueDate());

        Task task = new Task(
                access.project().projectId(),
                assignee.membershipId(),
                title,
                trimToNull(command.description()),
                command.dueDate(),
                actorMembership.membershipId(),
                clock.instant(), command.estimatedMinutes());
        TaskView result = view(saveTask(task), assignee.displayName());
        publish(
                new NotificationEvent(
                        NotificationType.TASK_ASSIGNED,
                        actorMembership.membershipId() == assignee.membershipId() ? "SELF_TASK" : "ASSIGNED",
                        "Task assigned",
                        "A Task was assigned to you."),
                new NotificationAction(taskAction(command.projectId(), result.id()),
                        actorMembership.membershipId() == assignee.membershipId()),
                notificationRecipients(access.project(), actorMembership.userId(),
                        List.of(assignee.membershipId())));
        return result;
    }

    /**
     * Changes an ACTIVE Project Task through one fixed workflow edge.
     *
     * <p>The transaction locks the Project before the Task row and permits the owning Mentor or
     * the current assignee membership to mutate status. Leadership alone does not substitute for
     * either authority. A successful change publishes an in-app-only event to the current Leader,
     * excluding the actor.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param expectedVersion client-observed Task version from the rendered form
     * @param target requested next status
     * @return updated Task projection
     * @throws TaskNotFoundException when scope, lifecycle, actor authority, or identifiers are invalid
     * @throws TaskValidationException when the requested status edge is forbidden
     */
    @Transactional
    public TaskView changeStatus(
            String actorEmail, long projectId, long taskId, Long expectedVersion, TaskStatus target) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        if (!"ACTIVE".equals(access.project().status())) {
            throw new TaskNotFoundException();
        }
        boolean owningMentor = access.actor().userId() == access.project().mentorUserId();
        ProjectTaskMemberView actorMembership = owningMentor
                ? null
                : requireActorMembership(access.project(), access.actor().userId());
        List<NotificationRecipient> leaderRecipients = notificationRecipients(
                access.project(), access.actor().userId(),
                access.project().currentLeaderMembershipId() == null
                        ? List.of()
                        : List.of(access.project().currentLeaderMembershipId()));
        Task task = requireLockedTask(projectId, taskId);
        if (!owningMentor
                && (actorMembership == null
                        || task.getAssigneeMembershipId() != actorMembership.membershipId())) {
            throw new TaskNotFoundException();
        }
        requireTaskVersion(task, expectedVersion);
        if (!task.getStatus().canTransitionTo(target)) {
            throw new TaskValidationException("Task status transition is not allowed");
        }
        task.changeStatus(target, clock.instant());
        TaskView result = view(saveTask(task), assigneeName(access, task.getAssigneeMembershipId()));
        publish(
                new NotificationEvent(
                        NotificationType.TASK_STATUS_CHANGED,
                        "STATUS_CHANGED",
                        "Task status changed",
                        "A Task status changed to " + target.name() + "."),
                new NotificationAction(taskAction(projectId, task.getId()), false),
                leaderRecipients);
        return result;
    }

    /**
     * Retains the programmatic mutation overload for callers that do not have a browser snapshot.
     * Web forms must use the expected-version overload above.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier
     * @param target requested next status
     * @return updated Task projection
     */
    @Transactional
    public TaskView changeStatus(String actorEmail, long projectId, long taskId, TaskStatus target) {
        return changeStatus(actorEmail, projectId, taskId, null, target);
    }

    /**
     * Edits the current definition of an unfinished Task under the Project write lock.
     *
     * <p>The current Leader may edit any unfinished Task in the Project. A non-Leader creator may
     * edit only while that creator is still the current assignee. Stored creator, assignment, and
     * lifecycle attribution remain unchanged.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param expectedVersion client-observed Task version from the rendered form
     * @param title required replacement title
     * @param description optional replacement description
     * @param dueDate optional replacement due date
     * @return updated current Task projection
     * @throws TaskNotFoundException when the actor or Task is outside the authorized mutation scope
     * @throws TaskValidationException when title or due date violates a Task rule
     */
    @Transactional
    public TaskView edit(
            String actorEmail,
            long projectId,
            long taskId,
            Long expectedVersion,
            String title,
            String description,
            LocalDate dueDate) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        Task task = requireLockedTask(projectId, taskId);
        requireUnfinished(task);
        requireDefinitionMutationActor(access.project(), actorMembership, task);
        requireTaskVersion(task, expectedVersion);
        validateDueDate(access.project(), dueDate);
        task.updateDefinition(requireTitle(title), trimToNull(description), dueDate, clock.instant());
        return view(saveTask(task), requireAssigneeName(
                projectMembers(access), task.getAssigneeMembershipId()));
    }

    /**
     * Retains the programmatic edit overload for callers that do not have a browser snapshot.
     * Web forms must use the expected-version overload above.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier
     * @param title replacement title
     * @param description replacement description
     * @param dueDate replacement due date
     * @return updated Task projection
     */
    @Transactional
    public TaskView edit(
            String actorEmail,
            long projectId,
            long taskId,
            String title,
            String description,
            LocalDate dueDate) {
        return edit(actorEmail, projectId, taskId, null, title, description, dueDate);
    }

    /**
     * Determines the server-derived create-time estimate capability.
     * @param actorEmail authenticated account email
     * @param projectId Project identifier
     * @return true only for the current Leader of an open Project
     * @throws TaskNotFoundException when the Project or actor is outside the authorized scope
     */
    @Transactional(readOnly = true)
    public boolean canSetEstimateOnCreate(String actorEmail, long projectId) {
        TaskAccess access = requireProjectAccess(actorEmail, projectId);
        ProjectTaskMemberView actor = requireActorMembership(access.project(), access.actor().userId());
        return isOpen(access.project())
                && Objects.equals(access.project().currentLeaderMembershipId(), actor.membershipId());
    }

    /**
     * Changes or clears the Leader-owned estimate before the first retained work log.
     * @param actorEmail authenticated current-Leader email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within the Project
     * @param expectedVersion client-observed Task version
     * @param estimatedMinutes new estimate, or null to clear it
     * @return updated Task projection
     * @throws TaskNotFoundException when authorization or scope is unavailable
     * @throws TaskConflictException when the expected version is stale
     * @throws TaskValidationException when bounds, lifecycle, or retained-work rules fail
     */
    @Transactional
    public TaskView estimate(String actorEmail, long projectId, long taskId, Long expectedVersion,
            Integer estimatedMinutes) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actor = requireActorMembership(access.project(), access.actor().userId());
        if (!Objects.equals(access.project().currentLeaderMembershipId(), actor.membershipId())) {
            throw new TaskNotFoundException();
        }
        validateEstimate(estimatedMinutes);
        Task task = requireLockedTask(projectId, taskId);
        requireUnfinished(task);
        requireTaskVersion(task, expectedVersion);
        if (workLogs.existsByTaskIdAndProjectId(taskId, projectId)) {
            throw new TaskValidationException("A Task estimate cannot change after work is logged.");
        }
        task.applyEstimatedMinutes(estimatedMinutes, clock.instant());
        return view(saveTask(task), requireAssigneeName(projectMembers(access), task.getAssigneeMembershipId()));
    }

    /**
     * Soft-deletes an unfinished Task while retaining the row and deletion attribution.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param expectedVersion client-observed Task version from the rendered form
     * @throws TaskNotFoundException when the actor or Task is outside the authorized mutation scope
     */
    @Transactional
    public void softDelete(String actorEmail, long projectId, long taskId, Long expectedVersion) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        Task task = requireLockedTask(projectId, taskId);
        requireUnfinished(task);
        requireDefinitionMutationActor(access.project(), actorMembership, task);
        requireTaskVersion(task, expectedVersion);
        task.softDelete(actorMembership.membershipId(), clock.instant());
        saveTask(task);
    }

    /**
     * Retains the programmatic deletion overload for callers that do not have a browser snapshot.
     * Web forms must use the expected-version overload above.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier
     */
    @Transactional
    public void softDelete(String actorEmail, long projectId, long taskId) {
        softDelete(actorEmail, projectId, taskId, null);
    }

    /**
     * Reassigns one unfinished Task to another eligible current Project member.
     *
     * <p>Only the current Leader may invoke this operation. The current Task status is retained;
     * a DONE Task must first be reopened by its current assignee through the fixed status graph.
     * Assignment actor/time change, while creator, comments, logs, and lifecycle timestamps stay
     * historical. A successful reassignment publishes one designated in-app and ordinary-email
     * event to the previous and new assignees, deduplicated by the notification boundary.
     *
     * @param actorEmail authenticated current-Leader account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param expectedVersion client-observed Task version from the rendered form
     * @param assigneeMembershipId eligible same-Project recipient membership
     * @return updated current Task projection
     * @throws TaskNotFoundException when authorization or recipient scope is invalid
     * @throws TaskValidationException when the Task is DONE or recipient is unchanged
     */
    @Transactional
    public TaskView reassign(
            String actorEmail,
            long projectId,
            long taskId,
            Long expectedVersion,
            long assigneeMembershipId) {
        return reassign(actorEmail, projectId, taskId, expectedVersion, assigneeMembershipId, null);
    }

    /** Reassigns manually, requiring an initial forecast when retained work exists. */
    @Transactional
    public TaskView reassign(String actorEmail, long projectId, long taskId, Long expectedVersion,
            long assigneeMembershipId, RemainingEffortForecastInput forecastInput) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        if (!Objects.equals(access.project().currentLeaderMembershipId(), actorMembership.membershipId())) {
            throw new TaskNotFoundException();
        }
        ProjectTaskMemberView recipient = requireAssigneeMembership(access.project(), assigneeMembershipId);
        Task task = requireLockedTask(projectId, taskId);
        requireUnfinished(task);
        long previousAssigneeMembershipId = task.getAssigneeMembershipId();
        // Validate the browser's optimistic snapshot before reporting state-dependent rules such
        // as an unchanged recipient. A stale request must have one stable conflict outcome even
        // when another transaction has already assigned the Task to the requested member.
        requireTaskVersion(task, expectedVersion);
        if (task.getAssigneeMembershipId() == recipient.membershipId()) {
            throw new TaskValidationException("Task is already assigned to that member");
        }
        boolean worked = workLogs.existsByTaskIdAndProjectId(taskId, projectId);
        if (worked && forecastInput == null) {
            throw new TaskValidationException("A remaining-effort forecast is required for a worked Task");
        }
        if (!worked && forecastInput != null) {
            throw new TaskValidationException("An unworked Task cannot receive a forecast");
        }
        if (worked) {
            forecastInput = validateForecastInput(forecastInput);
        }
        Instant assignmentStartedAt = clock.instant();
        if (worked) {
            forecasts.save(new TaskRemainingEffortForecast(projectId, taskId, recipient.membershipId(),
                    actorMembership.membershipId(), assignmentStartedAt,
                    forecastInput.remainingMinutes(), workLogs.sumMinutesByTaskIdAndProjectId(taskId, projectId),
                    forecastInput.note(), assignmentStartedAt));
        }
        List<NotificationRecipient> assigneeRecipients = notificationRecipients(
                access.project(), null,
                List.of(previousAssigneeMembershipId, recipient.membershipId()));
        task.reassign(recipient.membershipId(), actorMembership.membershipId(), assignmentStartedAt);
        TaskView result = view(saveTask(task), recipient.displayName());
        publish(
                new NotificationEvent(
                        NotificationType.TASK_REASSIGNED,
                        "REASSIGNED",
                        "Task reassigned",
                        "A Task assignment changed."),
                new NotificationAction(taskAction(projectId, task.getId()), false),
                assigneeRecipients);
        return result;
    }

    /**
     * Appends a correction successor to the latest forecast for an unfinished current Task.
     *
     * <p>Only the current Leader may correct before incoming-assignee work is created. Assignment
     * facts remain tied to the original context while the lifetime-actual snapshot is refreshed;
     * the predecessor is never updated. A database uniqueness race is translated to the stable
     * Task conflict contract.</p>
     *
     * @param actorEmail authenticated current-Leader email
     * @param projectId owning Project identifier
     * @param taskId Task identifier
     * @param expectedLatestForecastId client-observed latest forecast identifier
     * @param remainingMinutes replacement remaining effort in minutes
     * @param correctionReason mandatory correction reason
     * @return appended correction forecast view
     * @throws TaskNotFoundException when authorization or scope is invalid
     * @throws TaskConflictException when the expected forecast is stale or already superseded
     * @throws TaskValidationException when correction rules are violated
     */
    @Transactional
    public TaskRemainingEffortForecastView correctForecast(String actorEmail, long projectId, long taskId,
            long expectedLatestForecastId, Integer remainingMinutes, String correctionReason) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actor = requireActorMembership(access.project(), access.actor().userId());
        if (!Objects.equals(access.project().currentLeaderMembershipId(), actor.membershipId())) {
            throw new TaskNotFoundException();
        }
        Task task = requireLockedTask(projectId, taskId);
        requireUnfinished(task);
        TaskRemainingEffortForecast predecessor = forecasts
                .findByIdAndProjectIdAndTaskId(expectedLatestForecastId, projectId, taskId)
                .orElseThrow(() -> new TaskConflictException("Forecast changed; reload before correcting", null));
        if (forecasts.existsBySupersedesForecastId(predecessor.getId())
                || task.getAssigneeMembershipId() != predecessor.getIncomingMembershipId()
                || !Objects.equals(task.getAssignedAt(), predecessor.getAssignmentStartedAt())) {
            throw new TaskConflictException("Forecast changed; reload before correcting", null);
        }
        if (workLogs.existsByTaskIdAndProjectIdAndMembershipIdAndCreatedAtGreaterThanEqual(
                taskId, projectId, predecessor.getIncomingMembershipId(), predecessor.getAssignmentStartedAt())) {
            throw new TaskValidationException("Forecast correction is closed after incoming work begins");
        }
        if (remainingMinutes == null || remainingMinutes < 1 || remainingMinutes > 527040) {
            throw new TaskValidationException("Remaining effort must be between 1 and 527040 minutes");
        }
        String reason = correctionReason == null || correctionReason.isBlank()
                ? null : correctionReason.strip();
        if (reason == null) {
            throw new TaskValidationException("Correction reason is required");
        }
        if (reason.length() > 500) {
            throw new TaskValidationException("Correction reason must not exceed 500 characters");
        }
        Instant createdAt = clock.instant();
        TaskRemainingEffortForecast successor = TaskRemainingEffortForecast.correction(
                predecessor,
                actor.membershipId(),
                remainingMinutes,
                workLogs.sumMinutesByTaskIdAndProjectId(taskId, projectId),
                reason,
                createdAt);
        try {
            forecasts.saveAndFlush(successor);
        } catch (DataIntegrityViolationException exception) {
            throw new TaskConflictException("Forecast changed; reload before correcting", exception);
        }
        Map<Long, ProjectMemberView> members = projectMembers(access);
        return view(successor, task, members, false, true);
    }

    /**
     * Retains the programmatic reassignment overload for callers that do not have a browser snapshot.
     * Web forms must use the expected-version overload above.
     *
     * @param actorEmail authenticated current-Leader account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier
     * @param assigneeMembershipId eligible recipient membership
     * @return updated Task projection
     */
    @Transactional
    public TaskView reassign(
            String actorEmail, long projectId, long taskId, long assigneeMembershipId) {
        return reassign(actorEmail, projectId, taskId, null, assigneeMembershipId);
    }

    /**
     * Appends a comment to a current Task before Project completion.
     *
     * <p>The transaction locks the Project before the Task row. The owning Mentor or any active
     * member may comment; the persisted author is the authenticated user so later membership or
     * leadership changes do not alter history. A successful comment publishes an in-app-only
     * event to the current assignee and current Leader, excluding the author and deduplicating
     * when those roles resolve to the same account. A DONE Task may retain a non-actionable
     * historical or ineligible assignee; absence from the already locked active-member context
     * omits that recipient and action link without changing comment authorization or mutation.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param body required comment text
     * @return created append-only comment projection
     * @throws TaskNotFoundException when current authorization, lifecycle, or identifiers are invalid
     * @throws TaskValidationException when the normalized body is empty
     */
    @Transactional
    public TaskCommentView addComment(String actorEmail, long projectId, long taskId, String body) {
        String normalizedBody = requireCommentBody(body);
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        if ("COMPLETED".equals(access.project().status())) {
            throw new TaskNotFoundException();
        }
        boolean owningMentor = access.actor().userId() == access.project().mentorUserId();
        boolean activeMember = access.project().activeMembers().stream()
                .anyMatch(member -> member.userId() == access.actor().userId());
        if (!owningMentor && !activeMember) {
            throw new TaskNotFoundException();
        }
        Task task = requireLockedTask(projectId, taskId);
        List<Long> commentMembershipIds = new ArrayList<>(List.of(task.getAssigneeMembershipId()));
        if (access.project().currentLeaderMembershipId() != null) {
            commentMembershipIds.add(access.project().currentLeaderMembershipId());
        }
        List<NotificationRecipient> commentRecipients = commentNotificationRecipients(
                access.project(), access.actor().userId(), commentMembershipIds);

        TaskComment comment = new TaskComment(
                taskId, access.actor().userId(), normalizedBody, clock.instant());
        TaskCommentView result = view(comments.saveAndFlush(comment));
        publish(
                new NotificationEvent(
                        NotificationType.TASK_COMMENTED,
                        "COMMENTED",
                        "Task commented",
                        "A Task received a new comment."),
                new NotificationAction(taskAction(projectId, taskId), false),
                commentRecipients);
        return result;
    }

    /**
     * Records one dated effort row after serializing the Intern's combined daily budget.
     *
     * <p>The scalar Account email route is followed by the Project mutation context, which locks
     * every current-member Account/profile in ascending order and then the Project. The existing
     * Account work-window lock re-enters the already-held actor rows before Task and work-log rows
     * are locked. Retained Project membership intervals are then filtered in the server clock zone,
     * with both joined and closure dates inclusive, to derive the complete cross-Project total.</p>
     *
     * @param actorEmail authenticated current Task assignee email
     * @param projectId owning Project identifier
     * @param taskId current Task identifier
     * @param expectedTaskVersion client-observed Task version from the rendered form
     * @param workDate local effort date, evaluated against Account, Project, membership, and server-date bounds
     * @param minutes effort minutes in the inclusive range 1 through 1440
     * @param note optional non-blank note
     * @return persisted immutable work-log projection
     * @throws TaskNotFoundException when Project, Task, actor membership, or assignment is not authorized
     * @throws TaskValidationException when lifecycle/date/minute/note/daily-total rules fail
     */
    @Transactional
    public TaskWorkLogView addWorkLog(
            String actorEmail,
            long projectId,
            long taskId,
            Long expectedTaskVersion,
            LocalDate workDate,
            int minutes,
            String note) {
        validateWorkLogInput(workDate, minutes, note);
        long actorUserId = requireAccountId(actorEmail);
        TaskAccess access = requireMutationAccess(actorUserId, projectId);
        requireActiveProject(access.project());
        InternWorkWindow window = lockedWorkWindow(actorUserId, workDate);
        requireEligibleWorkWindow(window, workDate);
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), actorUserId);
        requireWorkDate(access.project(), actorMembership, workDate);
        Set<Long> membershipIds = membershipIdsForWorkDate(actorUserId, workDate);
        requireMembershipIncluded(membershipIds, actorMembership.membershipId());
        Task task = requireLockedTask(projectId, taskId);
        if (task.getAssigneeMembershipId() != actorMembership.membershipId()) {
            throw new TaskNotFoundException();
        }
        requireTaskVersion(task, expectedTaskVersion);
        requireDailyLimit(workLogs.sumMinutesByMembershipIdsAndWorkDate(membershipIds, workDate), minutes);

        TaskWorkLog log = new TaskWorkLog(
                projectId,
                taskId,
                actorMembership.membershipId(),
                workDate,
                minutes,
                note,
                clock.instant());
        return view(saveWorkLog(log));
    }

    /**
     * Retains the programmatic work-log overload for callers that do not have a browser snapshot.
     * Web forms must use the expected-task-version overload above.
     *
     * @param actorEmail authenticated current Task assignee email
     * @param projectId owning Project identifier
     * @param taskId current Task identifier
     * @param workDate local effort date
     * @param minutes effort minutes
     * @param note optional note
     * @return persisted work-log projection
     */
    @Transactional
    public TaskWorkLogView addWorkLog(
            String actorEmail,
            long projectId,
            long taskId,
            LocalDate workDate,
            int minutes,
            String note) {
        return addWorkLog(actorEmail, projectId, taskId, null, workDate, minutes, note);
    }

    /**
     * Corrects an existing work log while retaining its author, date, and creation instant.
     *
     * <p>Only the active author membership may correct its own row. A non-locking Project-scoped
     * candidate projection supplies the date needed for routing; the scalar Account route, locked
     * Project context, and Account work-window lock then precede the first managed work-log load.
     * Retained membership intervals are filtered in the server clock zone, including a membership's
     * closure date, before the replacement total is checked and persisted.</p>
     *
     * @param actorEmail authenticated author email
     * @param projectId owning Project identifier
     * @param workLogId work-log identifier within the Project
     * @param expectedTaskVersion client-observed Task version from the rendered form
     * @param expectedWorkLogVersion client-observed work-log version from the rendered form
     * @param minutes replacement effort in the inclusive range 1 through 1440
     * @param note replacement optional non-blank note
     * @return corrected immutable work-log projection
     * @throws TaskNotFoundException when Project, Task, author membership, or work-log scope is not authorized
     * @throws TaskValidationException when lifecycle/date/minute/note/daily-total rules fail
     */
    @Transactional
    public TaskWorkLogView correctWorkLog(
            String actorEmail,
            long projectId,
            long workLogId,
            Long expectedTaskVersion,
            Long expectedWorkLogVersion,
            int minutes,
            String note) {
        validateWorkLogValues(minutes, note);
        TaskWorkLogCandidate candidate = workLogs.findCandidateByIdAndProjectId(workLogId, projectId)
                .orElseThrow(TaskNotFoundException::new);
        long actorUserId = requireAccountId(actorEmail);
        TaskAccess access = requireMutationAccess(actorUserId, projectId);
        requireActiveProject(access.project());
        InternWorkWindow window = lockedWorkWindow(actorUserId, candidate.workDate());
        requireEligibleWorkWindow(window, candidate.workDate());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), actorUserId);
        requireWorkDate(access.project(), actorMembership, candidate.workDate());
        Set<Long> membershipIds = membershipIdsForWorkDate(actorUserId, candidate.workDate());
        TaskWorkLog log = workLogs.findLockedByIdAndProjectId(workLogId, projectId)
                .orElseThrow(TaskNotFoundException::new);
        requireSameCandidate(candidate, log, projectId, workLogId);
        requireMembershipIncluded(membershipIds, log.getMembershipId());
        if (log.getMembershipId() != actorMembership.membershipId()) {
            throw new TaskNotFoundException();
        }
        requireWorkLogVersion(log, expectedWorkLogVersion);
        Task task = requireLockedTask(projectId, log.getTaskId());
        requireTaskVersion(task, expectedTaskVersion);
        validateWorkLogInput(log.getWorkDate(), minutes, note);
        long currentMinutes = workLogs.sumMinutesByMembershipIdsAndWorkDate(membershipIds, log.getWorkDate());
        requireDailyLimit(currentMinutes - log.getMinutes(), minutes);
        log.correct(minutes, note, clock.instant());
        return view(saveWorkLog(log));
    }

    /**
     * Retains the programmatic correction overload for callers that do not have a browser snapshot.
     * Web forms must use both expected-version values in the overload above.
     *
     * @param actorEmail authenticated author email
     * @param projectId owning Project identifier
     * @param workLogId work-log identifier
     * @param minutes replacement effort
     * @param note replacement note
     * @return corrected work-log projection
     */
    @Transactional
    public TaskWorkLogView correctWorkLog(
            String actorEmail,
            long projectId,
            long workLogId,
            int minutes,
            String note) {
        return correctWorkLog(actorEmail, projectId, workLogId, null, null, minutes, note);
    }

    /**
     * Lists current Tasks and progress for an authorized Project reader.
     *
     * <p>Soft-deleted Tasks are excluded. Active members may read open Projects; former members may
     * read only a completed Project in which they historically participated. Empty current Task
     * sets produce empty completion percentage semantics. The create capability is server-derived.
     *
     * @param actorEmail authenticated account email
     * @param projectId Project identifier
     * @return visible Tasks, progress counts, and create capability
     * @throws TaskNotFoundException when the Project is outside the actor's authorized scope
     */
    @Transactional(readOnly = true)
    public TaskListView list(String actorEmail, long projectId) {
        TaskAccess access = requireReadableProject(actorEmail, projectId);
        Map<Long, ProjectMemberView> members = projectMembers(access);
        List<TaskView> projectTasks = tasks.findAllByProjectIdAndDeletedAtIsNullOrderById(projectId)
                .stream()
                .map(task -> view(task, requireAssigneeName(members, task.getAssigneeMembershipId())))
                .toList();
        return new TaskListView(
                projectTasks,
                TaskProgress.from(projectTasks.stream().map(TaskView::status).toList()),
                isOpen(access.project()) && activeMembership(access) != null);
    }

    /**
     * Loads one current Task, append-only comments, and server-derived action capabilities.
     *
     * <p>Status capability requires an ACTIVE Project and either the owning Mentor or the current
     * assignee. Comment capability requires an active member or owning Mentor before completion.
     * Historical completed-Project readers receive details with no mutation capability.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @return authorized detail projection
     * @throws TaskNotFoundException when scope or identifiers are invalid
     */
    @Transactional(readOnly = true)
    public TaskDetails details(String actorEmail, long projectId, long taskId) {
        TaskAccess access = requireReadableProject(actorEmail, projectId);
        ProjectTaskMemberView actorMembership = activeMembership(access);
        Task persistedTask = requireTask(projectId, taskId);
        TaskView task = view(
                persistedTask,
                requireAssigneeName(projectMembers(access), persistedTask.getAssigneeMembershipId()));
        Map<Long, ProjectMemberView> members = projectMembers(access);
        List<TaskCommentView> taskComments = comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(taskId)
                .stream()
                .map(TaskService::view)
                .toList();
        List<TaskWorkLogView> taskWorkLogs = workLogs
                .findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(taskId, projectId)
                .stream()
                .map(TaskService::view)
                .toList();
        boolean owningMentor = access.actor().userId() == access.project().mentorUserId();
        boolean canChangeStatus = "ACTIVE".equals(access.project().status())
                && (owningMentor
                        || actorMembership != null
                                && persistedTask.getAssigneeMembershipId() == actorMembership.membershipId());
        boolean canComment = !"COMPLETED".equals(access.project().status())
                && (access.actor().userId() == access.project().mentorUserId() || actorMembership != null);
        boolean unfinished = persistedTask.getStatus() != TaskStatus.DONE;
        boolean currentLeader = actorMembership != null
                && Objects.equals(access.project().currentLeaderMembershipId(), actorMembership.membershipId());
        boolean creatorOwnsCurrentAssignment = actorMembership != null
                && persistedTask.getCreatorMembershipId() == actorMembership.membershipId()
                && persistedTask.getAssigneeMembershipId() == actorMembership.membershipId();
        boolean canEdit = isOpen(access.project()) && unfinished && (currentLeader || creatorOwnsCurrentAssignment);
        boolean canDelete = canEdit;
        boolean canReassign = isOpen(access.project()) && unfinished && currentLeader;
        boolean canLogWork = "ACTIVE".equals(access.project().status())
                && actorMembership != null
                && persistedTask.getAssigneeMembershipId() == actorMembership.membershipId();
        TaskEffortPlanningView effortPlanning = effortPlanning(persistedTask, currentLeader,
                isOpen(access.project()),
                projectId, taskId);
        List<TaskRemainingEffortForecast> forecastRows = forecasts
                .findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(projectId, taskId);
        Set<Long> supersededForecastIds = forecastRows.stream()
                .map(TaskRemainingEffortForecast::getSupersedesForecastId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        List<TaskRemainingEffortForecastView> remainingEffortForecasts = forecastRows.stream()
                .map(forecast -> view(
                        forecast,
                        persistedTask,
                        members,
                        supersededForecastIds.contains(forecast.getId()),
                        taskWorkLogs))
                .toList();
        return new TaskDetails(
                task, taskComments, taskWorkLogs,
                actorMembership == null ? null : actorMembership.membershipId(),
                canChangeStatus, canComment,
                canEdit, canDelete, canReassign, canLogWork, effortPlanning, remainingEffortForecasts);
    }

    /**
     * Returns assignee options for an authorized Task create form.
     *
     * <p>A current Leader receives every active same-Project membership; another active member
     * receives only their own membership. Completed Projects and non-members are denied without
     * disclosing Project membership data.
     *
     * @param actorEmail authenticated account email
     * @param projectId Project identifier
     * @return authorized membership choices
     * @throws TaskNotFoundException when current authorization or lifecycle is invalid
     */
    @Transactional(readOnly = true)
    public List<TaskAssigneeChoice> assignmentChoices(String actorEmail, long projectId) {
        TaskAccess access = requireProjectAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        if (Objects.equals(access.project().currentLeaderMembershipId(), actorMembership.membershipId())) {
            return access.project().activeMembers().stream()
                    .filter(member -> !access.project().pendingExitMembershipIds().contains(member.membershipId()))
                    .map(member -> new TaskAssigneeChoice(member.membershipId(), member.displayName()))
                    .toList();
        }
        if (access.project().pendingExitMembershipIds().contains(actorMembership.membershipId())) {
            return List.of();
        }
        return List.of(new TaskAssigneeChoice(
                actorMembership.membershipId(), actorMembership.displayName()));
    }

    private TaskAccess requireProjectAccess(String actorEmail, long projectId) {
        try {
            ProjectActorView actor = projects.authenticatedActor(actorEmail);
            return new TaskAccess(actor, projects.taskContext(actor.userId(), projectId));
        } catch (ProjectAccessDeniedException | ProjectRuleViolationException exception) {
            throw new TaskNotFoundException();
        }
    }

    private TaskAccess requireMutationAccess(String actorEmail, long projectId) {
        return requireMutationAccess(requireAccountId(actorEmail), projectId);
    }

    private TaskAccess requireMutationAccess(long actorUserId, long projectId) {
        try {
            ProjectTaskContext project = projectMutations.taskMutationContext(actorUserId, projectId);
            String role = project.mentorUserId() == actorUserId ? "MENTOR" : "INTERN";
            return new TaskAccess(new ProjectActorView(actorUserId, role), project);
        } catch (ProjectAccessDeniedException | ProjectRuleViolationException exception) {
            throw new TaskNotFoundException();
        }
    }

    private long requireAccountId(String actorEmail) {
        try {
            return accounts.requireAccountIdByEmail(actorEmail);
        } catch (IllegalArgumentException exception) {
            throw new TaskNotFoundException();
        }
    }

    private TaskAccess requireReadableProject(String actorEmail, long projectId) {
        TaskAccess access = requireProjectAccess(actorEmail, projectId);
        boolean historicalIntern = "INTERN".equals(access.actor().role())
                && access.project().activeMembers().stream()
                        .noneMatch(member -> member.userId() == access.actor().userId());
        if (historicalIntern && !"COMPLETED".equals(access.project().status())) {
            throw new TaskNotFoundException();
        }
        return access;
    }

    private Map<Long, ProjectMemberView> projectMembers(TaskAccess access) {
        try {
            return projects.members(access.actor().userId(), access.project().projectId()).stream()
                    .collect(Collectors.toUnmodifiableMap(ProjectMemberView::membershipId, Function.identity()));
        } catch (ProjectAccessDeniedException | ProjectRuleViolationException exception) {
            throw new TaskNotFoundException();
        }
    }

    private static String requireAssigneeName(Map<Long, ProjectMemberView> members, long membershipId) {
        ProjectMemberView member = members.get(membershipId);
        if (member == null) {
            throw new TaskNotFoundException();
        }
        return member.displayName();
    }

    private String assigneeName(TaskAccess access, long membershipId) {
        return access.project().activeMembers().stream()
                .filter(member -> member.membershipId() == membershipId)
                .map(ProjectTaskMemberView::displayName)
                .findFirst()
                .orElseGet(() -> requireAssigneeName(projectMembers(access), membershipId));
    }

    private static ProjectTaskMemberView activeMembership(TaskAccess access) {
        return access.project().activeMembers().stream()
                .filter(member -> member.userId() == access.actor().userId())
                .findFirst()
                .orElse(null);
    }

    private Task requireTask(long projectId, long taskId) {
        return tasks.findByIdAndProjectIdAndDeletedAtIsNull(taskId, projectId)
                .orElseThrow(TaskNotFoundException::new);
    }

    private Task requireLockedTask(long projectId, long taskId) {
        return tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(taskId, projectId)
                .orElseThrow(TaskNotFoundException::new);
    }

    private static void requireOpenProject(ProjectTaskContext project) {
        if (!isOpen(project)) {
            throw new TaskNotFoundException();
        }
    }

    private static void requireActiveProject(ProjectTaskContext project) {
        if (!"ACTIVE".equals(project.status())) {
            throw new TaskNotFoundException();
        }
    }

    private InternWorkWindow lockedWorkWindow(long userId, LocalDate workDate) {
        try {
            return accounts.lockedInternWorkWindow(userId, workDate);
        } catch (IllegalArgumentException exception) {
            throw new TaskNotFoundException();
        }
    }

    private static void validateWorkLogInput(LocalDate workDate, int minutes, String note) {
        if (workDate == null) {
            throw new TaskValidationException("Work date is required");
        }
        validateWorkLogValues(minutes, note);
    }

    private static void validateWorkLogValues(int minutes, String note) {
        if (minutes < 1 || minutes > 1440) {
            throw new TaskValidationException("Work minutes must be between 1 and 1440");
        }
        if (note != null && note.isBlank()) {
            throw new TaskValidationException("Work-log note cannot be blank");
        }
    }

    private static void requireEligibleWorkWindow(InternWorkWindow window, LocalDate workDate) {
        if (!window.eligibleOn(workDate)) {
            throw new TaskValidationException("Intern is not eligible for that work date");
        }
    }

    private void requireWorkDate(
            ProjectTaskContext project, ProjectTaskMemberView actorMembership, LocalDate workDate) {
        LocalDate serverDate = LocalDate.now(clock);
        if (workDate.isAfter(serverDate)) {
            throw new TaskValidationException("Work date cannot be in the future");
        }
        if (workDate.isBefore(project.startDate()) || workDate.isAfter(project.endDate())) {
            throw new TaskValidationException("Work date must be within Project dates");
        }
        if (actorMembership.joinedAt() == null
                || workDate.isBefore(actorMembership.joinedAt().atZone(clock.getZone()).toLocalDate())) {
            throw new TaskValidationException("Work date is before the membership start");
        }
    }

    private Set<Long> membershipIdsForWorkDate(long actorUserId, LocalDate workDate) {
        try {
            Set<Long> membershipIds = projects.membershipIntervals(actorUserId).stream()
                    .filter(interval -> intervalCoversDate(interval, workDate))
                    .map(ProjectMembershipIntervalView::membershipId)
                    .collect(Collectors.toUnmodifiableSet());
            if (membershipIds.isEmpty()) {
                throw new TaskNotFoundException();
            }
            return membershipIds;
        } catch (ProjectAccessDeniedException | ProjectRuleViolationException exception) {
            throw new TaskNotFoundException();
        }
    }

    private boolean intervalCoversDate(ProjectMembershipIntervalView interval, LocalDate workDate) {
        if (interval.joinedAt() == null) {
            return false;
        }
        LocalDate joinedDate = interval.joinedAt().atZone(clock.getZone()).toLocalDate();
        LocalDate leftDate = interval.leftAt() == null
                ? null
                : interval.leftAt().atZone(clock.getZone()).toLocalDate();
        return !workDate.isBefore(joinedDate)
                && (leftDate == null || !workDate.isAfter(leftDate));
    }

    private static void requireMembershipIncluded(Set<Long> membershipIds, long membershipId) {
        if (!membershipIds.contains(membershipId)) {
            throw new TaskNotFoundException();
        }
    }

    private static void requireSameCandidate(
            TaskWorkLogCandidate candidate, TaskWorkLog locked, long projectId, long workLogId) {
        if (locked.getId() == null
                || locked.getId() != workLogId
                || locked.getProjectId() != projectId
                || candidate.id() != locked.getId()
                || candidate.projectId() != locked.getProjectId()
                || candidate.taskId() != locked.getTaskId()
                || candidate.membershipId() != locked.getMembershipId()
                || !Objects.equals(candidate.workDate(), locked.getWorkDate())) {
            throw new TaskNotFoundException();
        }
    }

    private static void requireDailyLimit(long currentMinutes, int additionalMinutes) {
        if (currentMinutes < 0 || currentMinutes + additionalMinutes > 1440) {
            throw new TaskValidationException("Combined daily work cannot exceed 1440 minutes");
        }
    }

    private static void requireUnfinished(Task task) {
        if (task.getStatus() == TaskStatus.DONE) {
            throw new TaskValidationException("Reopen the Task before reassignment or editing");
        }
    }

    private static void requireDefinitionMutationActor(
            ProjectTaskContext project, ProjectTaskMemberView actor, Task task) {
        boolean currentLeader = Objects.equals(project.currentLeaderMembershipId(), actor.membershipId());
        boolean creatorOwnsCurrentAssignment = task.getCreatorMembershipId() == actor.membershipId()
                && task.getAssigneeMembershipId() == actor.membershipId();
        if (!currentLeader && !creatorOwnsCurrentAssignment) {
            throw new TaskNotFoundException();
        }
    }

    private static boolean isOpen(ProjectTaskContext project) {
        return "PLANNED".equals(project.status()) || "ACTIVE".equals(project.status());
    }

    private static ProjectTaskMemberView requireActorMembership(ProjectTaskContext project, long userId) {
        return project.activeMembers().stream()
                .filter(member -> member.userId() == userId)
                .findFirst()
                .orElseThrow(TaskNotFoundException::new);
    }

    private static ProjectTaskMemberView requireAssigneeMembership(ProjectTaskContext project, long membershipId) {
        if (project.pendingExitMembershipIds().contains(membershipId)) {
            throw new TaskNotFoundException();
        }
        return project.activeMembers().stream()
                .filter(member -> member.membershipId() == membershipId)
                .findFirst()
                .orElseThrow(TaskNotFoundException::new);
    }

    private void validateDueDate(ProjectTaskContext project, LocalDate dueDate) {
        if (dueDate == null) {
            return;
        }
        if (dueDate.isBefore(project.startDate()) || dueDate.isAfter(project.endDate())) {
            throw new TaskValidationException("Due date must be within Project dates");
        }
        if (calendar.isGlobalDayOff(dueDate)) {
            throw new TaskValidationException("Due date cannot be a current global day off");
        }
    }

    private static void validateEstimate(Integer minutes) {
        if (minutes != null && (minutes < 1 || minutes > 527040)) {
            throw new TaskValidationException("Estimate must be between 1 and 527040 minutes");
        }
    }

    private static RemainingEffortForecastInput validateForecastInput(RemainingEffortForecastInput input) {
        if (input == null || input.remainingMinutes() == null
                || input.remainingMinutes() < 1 || input.remainingMinutes() > 527040) {
            throw new TaskValidationException("Remaining effort must be between 1 and 527040 minutes");
        }
        String note = input.note() == null || input.note().isBlank() ? null : input.note().strip();
        if (note != null && note.length() > 500) {
            throw new TaskValidationException("Forecast note must not exceed 500 characters");
        }
        return new RemainingEffortForecastInput(input.remainingMinutes(), note);
    }

    private TaskEffortPlanningView effortPlanning(Task task, boolean currentLeader, boolean projectOpen,
            long projectId, long taskId) {
        Integer estimate = task.getEstimatedMinutes();
        long actual = workLogs.sumMinutesByTaskIdAndProjectId(taskId, projectId);
        TaskVarianceState state = estimate == null ? TaskVarianceState.NOT_ESTIMATED
                : task.getStatus() == TaskStatus.DONE ? TaskVarianceState.VALUE : TaskVarianceState.PENDING;
        Long variance = state == TaskVarianceState.VALUE ? actual - estimate : null;
        return new TaskEffortPlanningView(estimate, actual, state, variance,
                projectOpen && currentLeader && isOpenTaskStatus(task)
                        && !workLogs.existsByTaskIdAndProjectId(taskId, projectId));
    }

    private static boolean isOpenTaskStatus(Task task) {
        return task.getStatus() != TaskStatus.DONE && !task.isDeleted();
    }

    private static TaskView view(Task task, String assigneeName) {
        return new TaskView(
                task.getId(),
                task.getProjectId(),
                task.getAssigneeMembershipId(),
                assigneeName,
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getCreatorMembershipId(),
                task.getAssignerMembershipId(),
                task.getAssignedAt(),
                task.getCreatedAt(),
                task.getVersion());
    }

    /**
     * Projects one forecast for the detail read model from the work-log snapshot already loaded
     * for that Task.
     *
     * <p>The correction-window check intentionally stays in memory here. The detail use case has
     * already loaded every retained log, so querying the repository once per forecast would both
     * duplicate work and make a history page's query count depend on its forecast count.</p>
     *
     * @param forecast persisted immutable forecast row
     * @param task current Task assignment snapshot
     * @param members authorized current and historical member names
     * @param superseded whether a retained successor points at this row
     * @param taskWorkLogs retained work-log snapshot loaded by the detail use case
     * @return forecast read model with a snapshot-derived correction state
     */
    private TaskRemainingEffortForecastView view(
            TaskRemainingEffortForecast forecast,
            Task task,
            Map<Long, ProjectMemberView> members,
            boolean superseded,
            List<TaskWorkLogView> taskWorkLogs) {
        boolean currentAssignment = task.getAssigneeMembershipId() == forecast.getIncomingMembershipId()
                && Objects.equals(task.getAssignedAt(), forecast.getAssignmentStartedAt());
        boolean correctionOpen = currentAssignment
                && !superseded
                && taskWorkLogs.stream()
                        .noneMatch(log -> log.membershipId() == forecast.getIncomingMembershipId()
                                && !log.createdAt().isBefore(forecast.getAssignmentStartedAt()));
        return view(forecast, task, members, superseded, correctionOpen);
    }

    /**
     * Projects a forecast after a command-side correction-window guard has succeeded.
     *
     * <p>Mutation callers must establish {@code correctionOpen} with the repository predicate
     * while holding their write transaction. The detail caller uses the overload above so it can
     * reuse its already-loaded work-log snapshot.</p>
     *
     * @param forecast persisted immutable forecast row
     * @param task current Task assignment snapshot
     * @param members authorized current and historical member names
     * @param superseded whether a retained successor points at this row
     * @param correctionOpen whether the caller has proven the correction window is open
     * @return forecast read model with server-derived assignment state
     */
    private TaskRemainingEffortForecastView view(
            TaskRemainingEffortForecast forecast,
            Task task,
            Map<Long, ProjectMemberView> members,
            boolean superseded,
            boolean correctionOpen) {
        ProjectMemberView incoming = members.get(forecast.getIncomingMembershipId());
        ProjectMemberView leader = members.get(forecast.getForecastingLeaderMembershipId());
        boolean currentAssignment = task.getAssigneeMembershipId() == forecast.getIncomingMembershipId()
                && Objects.equals(task.getAssignedAt(), forecast.getAssignmentStartedAt());
        return new TaskRemainingEffortForecastView(
                forecast.getId() == null ? 0L : forecast.getId(),
                forecast.getProjectId(), forecast.getTaskId(), forecast.getIncomingMembershipId(),
                incoming == null ? null : incoming.displayName(),
                forecast.getForecastingLeaderMembershipId(), leader == null ? null : leader.displayName(),
                forecast.getAssignmentStartedAt(), forecast.getRemainingMinutes(),
                forecast.getActualMinutesSnapshot(), forecast.getInitialNote(), forecast.getCreatedAt(),
                forecast.getCorrectionReason(), forecast.getSupersedesForecastId(), superseded,
                currentAssignment, currentAssignment && !superseded && correctionOpen);
    }

    private void publish(
            NotificationEvent event,
            NotificationAction action,
            List<NotificationRecipient> recipients) {
        notifications.publish(event, action, recipients);
    }

    private List<NotificationRecipient> notificationRecipients(
            ProjectTaskContext project,
            Long excludedUserId,
            List<Long> membershipIds) {
        return collectNotificationRecipients(project, excludedUserId, membershipIds, false);
    }

    private List<NotificationRecipient> commentNotificationRecipients(
            ProjectTaskContext project,
            Long excludedUserId,
            List<Long> membershipIds) {
        return collectNotificationRecipients(project, excludedUserId, membershipIds, true);
    }

    private List<NotificationRecipient> collectNotificationRecipients(
            ProjectTaskContext project,
            Long excludedUserId,
            List<Long> membershipIds,
            boolean omitMissingMembers) {
        Map<Long, NotificationRecipient> recipients = new LinkedHashMap<>();
        membershipIds.stream()
                .filter(Objects::nonNull)
                .map(membershipId -> activeMember(project, membershipId, omitMissingMembers))
                .filter(Objects::nonNull)
                .filter(member -> excludedUserId == null || member.userId() != excludedUserId)
                .forEach(member -> recipients.computeIfAbsent(
                        member.userId(), ignored -> notificationRecipient(member.userId())));
        return List.copyOf(recipients.values());
    }

    private ProjectTaskMemberView activeMember(
            ProjectTaskContext project,
            long membershipId,
            boolean omitMissingMember) {
        return project.activeMembers().stream()
                .filter(member -> member.membershipId() == membershipId)
                .findFirst()
                .orElseGet(() -> {
                    if (omitMissingMember) {
                        return null;
                    }
                    throw new TaskNotFoundException();
                });
    }

    private NotificationRecipient notificationRecipient(long userId) {
        try {
            AccountIdentity identity = accounts.requireIdentityById(userId);
            if (identity == null) {
                throw new TaskNotFoundException();
            }
            return new NotificationRecipient(identity.id(), identity.email());
        } catch (IllegalArgumentException exception) {
            throw new TaskNotFoundException();
        }
    }

    private static String taskAction(long projectId, Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new TaskNotFoundException();
        }
        return "/projects/%d/tasks/%d".formatted(projectId, taskId);
    }

    private static TaskCommentView view(TaskComment comment) {
        return new TaskCommentView(
                comment.getId(),
                comment.getTaskId(),
                comment.getAuthorUserId(),
                comment.getBody(),
                comment.getCreatedAt());
    }

    private static TaskWorkLogView view(TaskWorkLog log) {
        return new TaskWorkLogView(
                log.getId(),
                log.getProjectId(),
                log.getTaskId(),
                log.getMembershipId(),
                log.getWorkDate(),
                log.getMinutes(),
                log.getNote(),
                log.getCreatedAt(),
                log.getUpdatedAt(),
                log.getVersion());
    }

    private static String requireTitle(String title) {
        String trimmed = trimToNull(title);
        if (trimmed == null || trimmed.length() > 200) {
            throw new TaskValidationException("Title is required and must not exceed 200 characters");
        }
        return trimmed;
    }

    private Task saveTask(Task task) {
        try {
            return tasks.saveAndFlush(task);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new TaskConflictException("Task changed concurrently; reload before trying again", conflict);
        }
    }

    private TaskWorkLog saveWorkLog(TaskWorkLog log) {
        try {
            return workLogs.saveAndFlush(log);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new TaskConflictException("Task work log changed concurrently; reload before trying again", conflict);
        }
    }

    private static String requireCommentBody(String body) {
        String trimmed = trimToNull(body);
        if (trimmed == null) {
            throw new TaskValidationException("Comment body is required");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void requireTaskVersion(Task task, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion.longValue() != task.getVersion()) {
            throw new TaskConflictException("Task changed concurrently; reload before trying again", null);
        }
    }

    private static void requireWorkLogVersion(TaskWorkLog log, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion.longValue() != log.getVersion()) {
            throw new TaskConflictException(
                    "Task work log changed concurrently; reload before trying again", null);
        }
    }

    private record TaskAccess(ProjectActorView actor, ProjectTaskContext project) {}
}
