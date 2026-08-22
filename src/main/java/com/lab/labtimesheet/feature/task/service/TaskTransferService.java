package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskConflictException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes Project-owned atomic Task redistribution operations.
 *
 * <p>The Project feature supplies a locked DTO context and performs Project authorization. This
 * service rechecks current Leader/recipient/Task state in that locked transaction, locks selected
 * Task rows in identifier order, validates every row before mutation, and flushes one atomic batch.
 * It never imports Project persistence types.
 */
@Service
@RequiredArgsConstructor
public class TaskTransferService {

    private static final Set<TaskStatus> UNFINISHED = Set.of(
            TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED);

    private final TaskRepository tasks;
    private final Clock clock;
    private final AccountService accounts;
    private final NotificationService notifications;

    /**
     * Transfers a selected batch from one source membership to one eligible recipient.
     *
     * <p>Every selected row must still be unfinished and assigned to the source. A pending-exit
     * source remains eligible for redistribution, but a pending-exit recipient is rejected. Any
     * stale or unauthorized row aborts the transaction before a mutation is flushed. Each changed
     * Task then publishes one designated in-app and ordinary-email event to its previous and new
     * assignee while the same transaction remains active.
     *
     * @param project locked Project task context supplied by the Project service
     * @param actorMembershipId current Leader membership performing the batch
     * @param sourceMembershipId pending-exit or direct-removal source membership
     * @param taskIds selected Task identifiers in the locked Project
     * @param recipientMembershipId eligible same-Project recipient membership
     * @return atomic transfer count and recipient
     * @throws TaskNotFoundException when the context, Leader, source, or recipient is invalid
     * @throws TaskValidationException when selection is empty, duplicate, done, or stale
     */
    @Transactional
    public TaskTransferResult transferBatch(
            ProjectTaskContext project,
            long actorMembershipId,
            long sourceMembershipId,
            Set<Long> taskIds,
            long recipientMembershipId) {
        requireOpenProject(project);
        requireLeader(project, actorMembershipId);
        ProjectTaskMemberView source = requireSource(project, sourceMembershipId);
        ProjectTaskMemberView recipient = requireRecipient(project, recipientMembershipId);
        requireDifferentMemberships(sourceMembershipId, recipient.membershipId());
        List<NotificationRecipient> notificationRecipients = List.of(
                notificationRecipient(source), notificationRecipient(recipient));
        if (taskIds == null || taskIds.isEmpty() || taskIds.stream().anyMatch(Objects::isNull)) {
            throw new TaskValidationException("Select at least one unfinished Task");
        }

        List<Task> selected = new ArrayList<>();
        taskIds.stream().sorted().forEach(id -> selected.add(tasks
                .findLockedByIdAndProjectIdAndDeletedAtIsNull(id, project.projectId())
                .orElseThrow(TaskNotFoundException::new)));
        selected.forEach(task -> {
            if (!UNFINISHED.contains(task.getStatus())) {
                throw new TaskValidationException("Only unfinished Tasks can be transferred");
            }
            if (task.getAssigneeMembershipId() != sourceMembershipId) {
                throw new TaskValidationException("One or more selected Tasks changed assignment");
            }
        });

        Instant assignedAt = clock.instant();
        selected.forEach(task -> task.reassign(recipient.membershipId(), actorMembershipId, assignedAt));
        try {
            tasks.saveAllAndFlush(selected);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new TaskConflictException("Task changed concurrently; reload before trying again", conflict);
        }
        selected.forEach(task -> notifications.publish(
                new NotificationEvent(
                        NotificationType.TASK_REASSIGNED,
                        "REASSIGNED",
                        "Task reassigned",
                        "A Task assignment changed."),
                new NotificationAction(taskAction(project.projectId(), task.getId()), false),
                notificationRecipients));
        return new TaskTransferResult(selected.size(), recipient.membershipId());
    }

    /**
     * Transfers every unfinished Task from one source membership atomically.
     *
     * <p>This is the guarded direct-removal shortcut. The Project caller must supply its locked
     * authorization context and a replacement recipient; no completed Task is touched.
     *
     * @param project locked Project task context supplied by the Project service
     * @param actorMembershipId authorized Leader or guarded Project-operation actor
     * @param sourceMembershipId membership being removed
     * @param recipientMembershipId eligible recipient membership
     * @return atomic count and recipient
     */
    @Transactional
    public TaskTransferResult transferAllUnfinished(
            ProjectTaskContext project,
            long actorMembershipId,
            long sourceMembershipId,
            long recipientMembershipId) {
        requireOpenProject(project);
        requireLeader(project, actorMembershipId);
        requireSource(project, sourceMembershipId);
        ProjectTaskMemberView recipient = requireRecipient(project, recipientMembershipId);
        requireDifferentMemberships(sourceMembershipId, recipient.membershipId());
        List<Long> taskIds = tasks.findIdsByProjectIdAndAssigneeMembershipIdAndStatusInAndDeletedAtIsNullOrderById(
                project.projectId(), sourceMembershipId, UNFINISHED);
        if (taskIds.isEmpty()) {
            return new TaskTransferResult(0, recipient.membershipId());
        }
        return transferBatch(project, actorMembershipId, sourceMembershipId,
                Set.copyOf(taskIds), recipient.membershipId());
    }

    /**
     * Counts unfinished current Tasks assigned to a source membership.
     *
     * <p>Project authorization and membership eligibility belong to the caller. This narrow read
     * is intentionally reusable by pending-exit readiness and direct-removal orchestration.
     *
     * @param projectId owning Project identifier
     * @param sourceMembershipId source membership identifier
     * @return unfinished non-deleted Task count
     */
    @Transactional(readOnly = true)
    public long unfinishedCount(long projectId, long sourceMembershipId) {
        return tasks.countByProjectIdAndAssigneeMembershipIdAndStatusInAndDeletedAtIsNull(
                projectId, sourceMembershipId, UNFINISHED);
    }

    private static void requireOpenProject(ProjectTaskContext project) {
        if (project == null || !("PLANNED".equals(project.status()) || "ACTIVE".equals(project.status()))) {
            throw new TaskNotFoundException();
        }
    }

    private static void requireLeader(ProjectTaskContext project, long actorMembershipId) {
        if (!Objects.equals(project.currentLeaderMembershipId(), actorMembershipId)) {
            throw new TaskNotFoundException();
        }
    }

    private static ProjectTaskMemberView requireSource(ProjectTaskContext project, long sourceMembershipId) {
        return project.activeMembers().stream()
                .filter(member -> member.membershipId() == sourceMembershipId)
                .findFirst()
                .orElseThrow(TaskNotFoundException::new);
    }

    private static ProjectTaskMemberView requireRecipient(
            ProjectTaskContext project, long recipientMembershipId) {
        if (project.pendingExitMembershipIds().contains(recipientMembershipId)) {
            throw new TaskNotFoundException();
        }
        return project.activeMembers().stream()
                .filter(member -> member.membershipId() == recipientMembershipId)
                .findFirst()
                .orElseThrow(TaskNotFoundException::new);
    }

    private static void requireDifferentMemberships(long sourceMembershipId, long recipientMembershipId) {
        if (sourceMembershipId == recipientMembershipId) {
            throw new TaskValidationException("Transfer recipient must differ from the source member");
        }
    }

    private NotificationRecipient notificationRecipient(ProjectTaskMemberView member) {
        try {
            AccountIdentity identity = accounts.requireIdentityById(member.userId());
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
}
