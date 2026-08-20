package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.EditTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCorrection;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.WorkLogView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes authorized Task creation, status, comment, and current/historical read use cases.
 *
 * <p>Project identity, lifecycle, ownership, leadership, and membership are obtained through
 * Project service DTOs. Mutations lock and re-evaluate the Project first; status/comment mutations
 * then lock the Task row, preserving the Project-to-Task lock order. Access failures are translated
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
    private final AccountService accounts;
    private final Clock clock;

    /**
     * Creates a TODO Task in a PLANNED or ACTIVE Project.
     *
     * <p>The Project is write-locked before membership and lifecycle checks. A current Leader may
     * choose any active same-Project member; another active member may choose only themselves.
     * Creator, assigner, and assignment time are stored from authenticated current context. An
     * optional due date must be within Project dates and not a current global day off.
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
        validateDueDate(access.project(), command.dueDate());

        Task task = new Task(
                access.project().projectId(),
                assignee.membershipId(),
                title,
                trimToNull(command.description()),
                command.dueDate(),
                actorMembership.membershipId(),
                clock.instant());
        return view(tasks.saveAndFlush(task), assignee.displayName());
    }

    /**
     * Changes an ACTIVE Project Task through one fixed workflow edge.
     *
     * <p>The transaction locks the Project before the Task row and permits only the current
     * assignee membership to mutate status. Neither leadership nor a global role substitutes for
     * assignment authority.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param target requested next status
     * @return updated Task projection
     * @throws TaskNotFoundException when scope, lifecycle, assignment, or identifiers are invalid
     * @throws TaskValidationException when the requested status edge is forbidden
     */
    @Transactional
    public TaskView changeStatus(String actorEmail, long projectId, long taskId, TaskStatus target) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        if (!"ACTIVE".equals(access.project().status())) {
            throw new TaskNotFoundException();
        }
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        Task task = requireLockedTask(projectId, taskId);
        if (task.getAssigneeMembershipId() != actorMembership.membershipId()) {
            throw new TaskNotFoundException();
        }
        if (!task.getStatus().canTransitionTo(target)) {
            throw new TaskValidationException("Task status transition is not allowed");
        }
        task.changeStatus(target, clock.instant());
        return view(tasks.saveAndFlush(task), actorMembership.displayName());
    }

    /**
     * Reassigns an unfinished Task to another active same-Project member by the current Leader.
     *
     * <p>The transaction locks the Project before the Task row. Only the current Leader may
     * reassign. Reassignment updates the current assignee, assignment actor, and assignment time
     * while preserving creator attribution, status, comments, and work logs. A {@code DONE} Task
     * must first be reopened by its current assignee.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param newAssigneeMembershipId active same-Project replacement membership
     * @return reassigned Task projection
     * @throws TaskNotFoundException when scope, lifecycle, Leader, or identifiers are invalid
     * @throws TaskValidationException when the Task is DONE
     */
    @Transactional
    public TaskView reassign(
            String actorEmail, long projectId, long taskId, long newAssigneeMembershipId) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        if (!"ACTIVE".equals(access.project().status())) {
            throw new TaskNotFoundException();
        }
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        if (access.project().currentLeaderMembershipId() == null
                || actorMembership.membershipId() != access.project().currentLeaderMembershipId()) {
            throw new TaskNotFoundException();
        }
        ProjectTaskMemberView newAssignee = requireAssigneeMembership(
                access.project(), newAssigneeMembershipId);
        Task task = requireLockedTask(projectId, taskId);
        try {
            task.reassign(newAssignee.membershipId(), actorMembership.membershipId(), clock.instant());
        } catch (IllegalArgumentException exception) {
            throw new TaskValidationException(exception.getMessage());
        }
        return view(tasks.saveAndFlush(task), newAssignee.displayName());
    }

    /**
     * Edits the definition of an unfinished Task under Leader or self-Task creator authority.
     *
     * <p>The transaction locks the Project before the Task row. The current Leader may edit any
     * unfinished Task; a non-Leader creator may edit only while the Task is unfinished and still
     * assigned to that same creating membership. Creator, assigner, assignment time, status,
     * comments, and work logs are untouched. A changed due date must satisfy the Project/calendar
     * rules of {@link #validateDueDate}.
     *
     * @param actorEmail authenticated account email
     * @param command requested Project, Task, and edited definition fields
     * @return edited Task projection
     * @throws TaskNotFoundException when scope, lifecycle, definition authority, or identifiers fail
     * @throws TaskValidationException when title or due date violates a business rule
     */
    @Transactional
    public TaskView edit(String actorEmail, EditTaskCommand command) {
        String title = requireTitle(command.title());
        TaskAccess access = requireMutationAccess(actorEmail, command.projectId());
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        Task task = requireLockedTask(command.projectId(), command.taskId());
        requireDefinitionControl(access.project(), task, actorMembership);
        validateDueDate(access.project(), command.dueDate());

        task.edit(title, trimToNull(command.description()), command.dueDate(), clock.instant());
        return view(
                tasks.saveAndFlush(task),
                requireAssigneeName(projectMembers(access), task.getAssigneeMembershipId()));
    }

    /**
     * Soft-deletes an unfinished Task under Leader or self-Task creator authority.
     *
     * <p>The transaction locks the Project before the Task row and then records the deleting actor
     * and server time. The row is retained historically, excluded from progress and normal lists,
     * and its creator attribution, comments, and work logs remain. A {@code DONE} Task must be
     * reopened before deletion.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @return soft-deleted Task projection for the historical confirmation view
     * @throws TaskNotFoundException when scope, lifecycle, definition authority, or identifiers fail
     * @throws TaskValidationException when the Task is DONE or already deleted
     */
    @Transactional
    public TaskView softDelete(String actorEmail, long projectId, long taskId) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        Task task = requireLockedTask(projectId, taskId);
        requireDefinitionControl(access.project(), task, actorMembership);
        try {
            task.softDelete(actorMembership.membershipId(), clock.instant());
        } catch (IllegalArgumentException exception) {
            throw new TaskValidationException(exception.getMessage());
        }
        return view(
                tasks.saveAndFlush(task),
                requireAssigneeName(projectMembers(access), task.getAssigneeMembershipId()));
    }

    /**
     * Loads one Task historically, including soft-deleted rows, under Leader or self-Task creator
     * inspection authority.
     *
     * <p>The current Leader or the Task's self-Task creator may inspect a soft-deleted Task with its
     * append-only comments and dated work logs; every mutation capability is false in the returned
     * projection. This is the read path that makes soft-deleted Tasks "queryable historically"
     * without exposing them to normal lists or progress.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @return authorized historical detail projection including soft-deleted Tasks
     * @throws TaskNotFoundException when scope, inspection authority, or identifiers fail
     */
    @Transactional(readOnly = true)
    public TaskDetails historicalDetails(String actorEmail, long projectId, long taskId) {
        TaskAccess access = requireReadableProject(actorEmail, projectId);
        ProjectTaskMemberView actorMembership = activeMembership(access);
        Task persistedTask = tasks.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(TaskNotFoundException::new);
        boolean leader = access.project().currentLeaderMembershipId() != null
                && actorMembership != null
                && actorMembership.membershipId() == access.project().currentLeaderMembershipId();
        boolean selfCreator = actorMembership != null
                && persistedTask.getCreatorMembershipId() == actorMembership.membershipId();
        if (!leader && !selfCreator) {
            throw new TaskNotFoundException();
        }
        TaskView task = view(
                persistedTask,
                requireAssigneeName(projectMembers(access), persistedTask.getAssigneeMembershipId()));
        List<TaskCommentView> taskComments = comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(taskId)
                .stream()
                .map(TaskService::view)
                .toList();
        List<WorkLogView> taskWorkLogs = workLogs.findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(taskId, projectId)
                .stream()
                .map(log -> view(log, projectMembers(access)))
                .toList();
        boolean deleted = persistedTask.getDeletedAt() != null;
        return new TaskDetails(
                task, taskComments, taskWorkLogs,
                false, false, false, false, false, false, deleted, null);
    }

    private static void requireDefinitionControl(
            ProjectTaskContext project, Task task, ProjectTaskMemberView actorMembership) {
        if (task.getStatus() == TaskStatus.DONE) {
            throw new TaskNotFoundException();
        }
        boolean leader = project.currentLeaderMembershipId() != null
                && actorMembership.membershipId() == project.currentLeaderMembershipId();
        boolean selfCreator = task.getCreatorMembershipId() == actorMembership.membershipId()
                && task.getAssigneeMembershipId() == actorMembership.membershipId();
        if (!leader && !selfCreator) {
            throw new TaskNotFoundException();
        }
    }

    /**
     * Appends a comment to a current Task before Project completion.
     *
     * <p>The transaction locks the Project before the Task row. The owning Mentor or any active
     * member may comment; the persisted author is the authenticated user so later membership or
     * leadership changes do not alter history.
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
        requireLockedTask(projectId, taskId);

        TaskComment comment = new TaskComment(
                taskId, access.actor().userId(), normalizedBody, clock.instant());
        return view(comments.saveAndFlush(comment));
    }

    /**
     * Records one dated effort entry on a Task by its current assignee in an ACTIVE Project.
     *
     * <p>The transaction locks the Project before the Task row. Only the current assignee may log
     * work, and the persisted membership is the assignee's own stable interval so later
     * reassignment does not move attribution. Work date must not be in the future, must fall within
     * Project dates, and must fall within the logging member's membership interval. Global days off
     * do not block Task work logs and no attendance row is created or implied.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param command dated effort inputs
     * @return created work log projection
     * @throws TaskNotFoundException when scope, lifecycle, or assignment is invalid
     * @throws TaskValidationException when minutes, note, or work date violates a business rule
     */
    @Transactional
    public WorkLogView logWork(String actorEmail, long projectId, long taskId, LogWorkCommand command) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        if (!"ACTIVE".equals(access.project().status())) {
            throw new TaskNotFoundException();
        }
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        Task task = requireLockedTask(projectId, taskId);
        if (task.getAssigneeMembershipId() != actorMembership.membershipId()) {
            throw new TaskNotFoundException();
        }
        int minutes = requireWorkMinutes(command.minutes());
        String note = requireOptionalNote(command.note());
        LocalDate workDate = requireLogWorkDate(access, command.workDate(), actorMembership.membershipId());
        requireDailyTotal(access, workDate, minutes, 0);

        TaskWorkLog log = new TaskWorkLog(
                projectId,
                taskId,
                actorMembership.membershipId(),
                workDate,
                minutes,
                note,
                clock.instant());
        return view(workLogs.saveAndFlush(log), projectMembers(access));
    }

    /**
     * Lets the log author correct their own work log while the Project is active and they remain a
     * member, even if the Task was reassigned.
     *
     * <p>The transaction locks the Project before the Task row and then the work log row. The author
     * is the membership stored on the log, not the current assignee, so a former assignee may fix
     * attribution-stable history. Only minutes and the optional note are corrected; the work date is
     * immutable after creation. No other user may edit the log.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param logId work log identifier within that Task
     * @param correction corrected effort inputs
     * @return corrected work log projection
     * @throws TaskNotFoundException when scope, lifecycle, author, or identifiers are invalid
     * @throws TaskValidationException when corrected minutes or note violates a business rule
     */
    @Transactional
    public WorkLogView correctWorkLog(
            String actorEmail, long projectId, long taskId, long logId, LogWorkCorrection correction) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        if (!"ACTIVE".equals(access.project().status())) {
            throw new TaskNotFoundException();
        }
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        requireLockedTask(projectId, taskId);
        TaskWorkLog log = workLogs.findLockedByIdAndTaskIdAndProjectId(logId, taskId, projectId)
                .orElseThrow(TaskNotFoundException::new);
        if (log.getMembershipId() != actorMembership.membershipId()) {
            throw new TaskNotFoundException();
        }
        int minutes = requireWorkMinutes(correction.minutes());
        String note = requireOptionalNote(correction.note());
        requireDailyTotal(access, log.getWorkDate(), minutes, log.getMinutes());
        log.correct(minutes, note, clock.instant());
        return view(workLogs.saveAndFlush(log), projectMembers(access));
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
     * Loads one current Task, append-only comments, dated work logs, and server-derived action
     * capabilities.
     *
     * <p>Status and work-log capability require the ACTIVE Project's current assignee. Comment
     * capability requires an active member or owning Mentor before completion. The actor's current
     * membership interval enables the template to offer author-only log corrections. Historical
     * completed-Project readers receive details with no mutation capability.
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
        List<TaskCommentView> taskComments = comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(taskId)
                .stream()
                .map(TaskService::view)
                .toList();
        List<WorkLogView> taskWorkLogs = workLogs.findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(taskId, projectId)
                .stream()
                .map(log -> view(log, projectMembers(access)))
                .toList();
        boolean canChangeStatus = "ACTIVE".equals(access.project().status())
                && actorMembership != null
                && persistedTask.getAssigneeMembershipId() == actorMembership.membershipId();
        boolean canComment = !"COMPLETED".equals(access.project().status())
                && (access.actor().userId() == access.project().mentorUserId() || actorMembership != null);
        boolean canLogWork = canChangeStatus;
        boolean canReassign = "ACTIVE".equals(access.project().status())
                && persistedTask.getStatus() != TaskStatus.DONE
                && access.project().currentLeaderMembershipId() != null
                && actorMembership != null
                && actorMembership.membershipId() == access.project().currentLeaderMembershipId();
        boolean canDefine = isOpen(access.project())
                && actorMembership != null
                && persistedTask.getStatus() != TaskStatus.DONE
                && (Objects.equals(access.project().currentLeaderMembershipId(), actorMembership.membershipId())
                        || (persistedTask.getCreatorMembershipId() == actorMembership.membershipId()
                                && persistedTask.getAssigneeMembershipId() == actorMembership.membershipId()));
        Long actorMembershipId = actorMembership == null ? null : actorMembership.membershipId();
        return new TaskDetails(
                task, taskComments, taskWorkLogs,
                canChangeStatus, canComment, canLogWork, canReassign,
                canDefine, canDefine, false, actorMembershipId);
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
                    .map(member -> new TaskAssigneeChoice(member.membershipId(), member.displayName()))
                    .toList();
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
        try {
            ProjectActorView actor = projects.authenticatedActor(actorEmail);
            return new TaskAccess(actor, projectMutations.taskMutationContext(actor.userId(), projectId));
        } catch (ProjectAccessDeniedException | ProjectRuleViolationException exception) {
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
                task.getDeletedAt());
    }

    private static TaskCommentView view(TaskComment comment) {
        return new TaskCommentView(
                comment.getId(),
                comment.getTaskId(),
                comment.getAuthorUserId(),
                comment.getBody(),
                comment.getCreatedAt());
    }

    private static WorkLogView view(TaskWorkLog log, Map<Long, ProjectMemberView> members) {
        ProjectMemberView author = members.get(log.getMembershipId());
        if (author == null) {
            throw new TaskNotFoundException();
        }
        return new WorkLogView(
                log.getId(),
                log.getProjectId(),
                log.getTaskId(),
                log.getMembershipId(),
                author.displayName(),
                log.getWorkDate(),
                log.getMinutes(),
                log.getNote(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }

    private static String requireTitle(String title) {
        String trimmed = trimToNull(title);
        if (trimmed == null || trimmed.length() > 200) {
            throw new TaskValidationException("Title is required and must not exceed 200 characters");
        }
        return trimmed;
    }

    private static String requireCommentBody(String body) {
        String trimmed = trimToNull(body);
        if (trimmed == null) {
            throw new TaskValidationException("Comment body is required");
        }
        return trimmed;
    }

    private static int requireWorkMinutes(int minutes) {
        if (minutes < 1 || minutes > 1440) {
            throw new TaskValidationException("Work minutes must be between 1 and 1440");
        }
        return minutes;
    }

    private static String requireOptionalNote(String note) {
        String trimmed = trimToNull(note);
        if (note != null && trimmed == null) {
            throw new TaskValidationException("Work log note cannot be blank");
        }
        return trimmed;
    }

    private void requireDailyTotal(TaskAccess access, LocalDate workDate, int requestedMinutes, int replacedMinutes) {
        accounts.lockInternProfileForDailyWork(access.actor().userId());
        int currentTotal = workLogs.sumMinutesByMembershipIdsAndWorkDate(
                projects.membershipIdsForIntern(access.actor().userId()), workDate);
        int projectedTotal = currentTotal - replacedMinutes + requestedMinutes;
        if (projectedTotal > 1440) {
            throw new TaskValidationException(
                    "Daily work minutes across all Projects cannot exceed 1440");
        }
    }

    private LocalDate requireLogWorkDate(TaskAccess access, LocalDate workDate, long membershipId) {
        if (workDate == null) {
            throw new TaskValidationException("Work date is required");
        }
        if (workDate.isAfter(LocalDate.now(clock))) {
            throw new TaskValidationException("Work date cannot be in the future");
        }
        if (workDate.isBefore(access.project().startDate()) || workDate.isAfter(access.project().endDate())) {
            throw new TaskValidationException("Work date must be within Project dates");
        }
        ProjectMemberView membership = projectMembers(access).get(membershipId);
        if (membership == null) {
            throw new TaskNotFoundException();
        }
        LocalDate joinedDate = membership.joinedAt().atZone(clock.getZone()).toLocalDate();
        if (workDate.isBefore(joinedDate)) {
            throw new TaskValidationException("Work date must be within membership interval");
        }
        if (membership.leftAt() != null
                && workDate.isAfter(membership.leftAt().atZone(clock.getZone()).toLocalDate())) {
            throw new TaskValidationException("Work date must be within membership interval");
        }
        return workDate;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record TaskAccess(ProjectActorView actor, ProjectTaskContext project) {}
}
