package com.lab.labtimesheet.feature.task.service;

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
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
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
    private final ProjectQueryService projects;
    private final ProjectService projectMutations;
    private final CalendarApplicationService calendar;
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
     * Edits the current definition of an unfinished Task under the Project write lock.
     *
     * <p>The current Leader may edit any unfinished Task in the Project. A non-Leader creator may
     * edit only while that creator is still the current assignee. Stored creator, assignment, and
     * lifecycle attribution remain unchanged.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
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
        validateDueDate(access.project(), dueDate);
        task.updateDefinition(requireTitle(title), trimToNull(description), dueDate, clock.instant());
        return view(tasks.saveAndFlush(task), requireAssigneeName(
                projectMembers(access), task.getAssigneeMembershipId()));
    }

    /**
     * Soft-deletes an unfinished Task while retaining the row and deletion attribution.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @throws TaskNotFoundException when the actor or Task is outside the authorized mutation scope
     */
    @Transactional
    public void softDelete(String actorEmail, long projectId, long taskId) {
        TaskAccess access = requireMutationAccess(actorEmail, projectId);
        requireOpenProject(access.project());
        ProjectTaskMemberView actorMembership = requireActorMembership(
                access.project(), access.actor().userId());
        Task task = requireLockedTask(projectId, taskId);
        requireUnfinished(task);
        requireDefinitionMutationActor(access.project(), actorMembership, task);
        task.softDelete(actorMembership.membershipId(), clock.instant());
        tasks.saveAndFlush(task);
    }

    /**
     * Reassigns one unfinished Task to another eligible current Project member.
     *
     * <p>Only the current Leader may invoke this operation. The current Task status is retained;
     * a DONE Task must first be reopened by its current assignee through the fixed status graph.
     * Assignment actor/time change, while creator, comments, logs, and lifecycle timestamps stay
     * historical.
     *
     * @param actorEmail authenticated current-Leader account email
     * @param projectId owning Project identifier
     * @param taskId Task identifier within that Project
     * @param assigneeMembershipId eligible same-Project recipient membership
     * @return updated current Task projection
     * @throws TaskNotFoundException when authorization or recipient scope is invalid
     * @throws TaskValidationException when the Task is DONE or recipient is unchanged
     */
    @Transactional
    public TaskView reassign(
            String actorEmail, long projectId, long taskId, long assigneeMembershipId) {
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
        if (task.getAssigneeMembershipId() == recipient.membershipId()) {
            throw new TaskValidationException("Task is already assigned to that member");
        }
        task.reassign(recipient.membershipId(), actorMembership.membershipId(), clock.instant());
        return view(tasks.saveAndFlush(task), recipient.displayName());
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
     * <p>Status capability requires the ACTIVE Project's current assignee. Comment capability
     * requires an active member or owning Mentor before completion. Historical completed-Project
     * readers receive details with no mutation capability.
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
        boolean canChangeStatus = "ACTIVE".equals(access.project().status())
                && actorMembership != null
                && persistedTask.getAssigneeMembershipId() == actorMembership.membershipId();
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
        return new TaskDetails(
                task, taskComments, canChangeStatus, canComment,
                canEdit, canDelete, canReassign, canLogWork);
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
                task.getCreatedAt());
    }

    private static TaskCommentView view(TaskComment comment) {
        return new TaskCommentView(
                comment.getId(),
                comment.getTaskId(),
                comment.getAuthorUserId(),
                comment.getBody(),
                comment.getCreatedAt());
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

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record TaskAccess(ProjectActorView actor, ProjectTaskContext project) {}
}
