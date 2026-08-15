package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.model.ProjectInternEligibility;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes Project aggregate mutations under Spring-managed transactions.
 *
 * <p>Existing aggregates are pessimistically locked before mutation-time authorization and
 * lifecycle checks. Account and Task facts arrive through public feature services; Project never
 * imports their repositories or entities.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final AccountService accounts;
    private final ProjectQueryService queries;
    private final TaskQueryService taskQueries;
    private final Clock clock;

    /**
     * Creates the Project mutation service.
     *
     * @param projects Project aggregate repository
     * @param accounts public Account identity and eligibility boundary
     * @param queries DTO-only Project query boundary reused for locked Task context
     * @param taskQueries public Task activation-guard boundary
     * @param clock server clock supplying persisted mutation instants
     */
    public ProjectService(
            ProjectRepository projects,
            AccountService accounts,
            ProjectQueryService queries,
            TaskQueryService taskQueries,
            Clock clock) {
        this.projects = projects;
        this.accounts = accounts;
        this.queries = queries;
        this.taskQueries = taskQueries;
        this.clock = clock;
    }

    /**
     * Atomically creates a planned Mentor-owned Project, eligible initial membership, and first
     * leadership term. {@code saveAndFlush} exposes database invariant violations before commit.
     *
     * @param actorUserId authenticated active Mentor creating and owning the Project
     * @param command validated creation values
     * @return generated Project identifier
     * @throws ProjectAccessDeniedException when the actor is not an active Mentor
     * @throws com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException when
     *         dates, name, or initial-Leader eligibility violate the aggregate rules
     */
    @Transactional
    public long create(long actorUserId, ProjectCreateCommand command) {
        requireActiveMentor(actorUserId);
        var project = ProjectEntity.plan(
                actorUserId,
                command.name(),
                command.description(),
                command.startDate(),
                command.endDate(),
                eligibleIntern(command.initialLeaderUserId()),
                clock.instant());
        return projects.saveAndFlush(project).id();
    }

    /**
     * Adds one eligible Intern as a current member while holding the Project write lock.
     * The same Intern may belong to other Projects, but duplicate current membership in this
     * Project is rejected before flush.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId Project to update
     * @param internUserId Intern selected for direct addition
     */
    @Transactional
    public void addMember(long actorUserId, long projectId, long internUserId) {
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        project.addMember(actorUserId, eligibleIntern(internUserId), clock.instant());
        projects.flush();
    }

    /**
     * Replaces the current Leader with an eligible current member in one transaction.
     * The closed term is flushed before its replacement so PostgreSQL's immediate exclusion rule
     * observes exactly one current term; Task assignments are not changed.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId Project whose Leader changes
     * @param internUserId active same-Project replacement Intern
     */
    @Transactional
    public void changeLeader(long actorUserId, long projectId, long internUserId) {
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        var change = project.prepareLeaderChange(actorUserId, eligibleIntern(internUserId), clock.instant());

        // PostgreSQL rejects overlapping terms immediately. Flush the old term's
        // end before inserting its replacement; the transaction remains atomic.
        projects.flush();
        project.completeLeaderChange(actorUserId, change);
        projects.flush();
    }

    /**
     * Locks the Project and re-evaluates visibility, lifecycle, current leadership, and active
     * eligible memberships for a Task mutation. When called inside {@code TaskService}'s
     * transaction, the pessimistic lock remains held through the outer commit or rollback.
     *
     * @param actorUserId authenticated Task actor
     * @param projectId owning Project identifier
     * @return DTO-only locked mutation context
     * @throws ProjectAccessDeniedException for missing or unauthorized Projects
     */
    @Transactional
    public ProjectTaskContext taskMutationContext(long actorUserId, long projectId) {
        return queries.taskContext(actorUserId, lockedProject(projectId));
    }

    /**
     * Activates a planned Project while holding its write lock. Current Account eligibility and
     * Task-assignee validity are checked inside the same transaction; any failure leaves the
     * Project planned and preserves Tasks and interval history.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId planned Project to activate
     */
    @Transactional
    public void activate(long actorUserId, long projectId) {
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        var activeMemberships = project.memberships().stream()
                .filter(membership -> membership.isCurrent()
                        && accounts.isEligibleIntern(membership.internUserId()))
                .toList();
        var activeMembershipIds = activeMemberships.stream()
                .map(membership -> membership.id())
                .collect(Collectors.toUnmodifiableSet());
        Set<Long> activeInternUserIds = activeMemberships.stream()
                .map(membership -> membership.internUserId())
                .collect(Collectors.toUnmodifiableSet());
        var allTaskAssigneesAreCurrent = taskQueries.countCurrentTasksAssignedOutside(
                projectId, activeMembershipIds) == 0;

        project.activate(actorUserId, activeInternUserIds, allTaskAssigneesAreCurrent, clock.instant());
        projects.flush();
    }

    private ProjectEntity lockedProject(long projectId) {
        return projects.findLockedById(projectId).orElseThrow(ProjectAccessDeniedException::new);
    }

    private ProjectInternEligibility eligibleIntern(long userId) {
        return new ProjectInternEligibility(userId, accounts.isEligibleIntern(userId));
    }

    private void requireActiveMentor(long userId) {
        try {
            var identity = accounts.requireIdentityById(userId);
            if (!"MENTOR".equals(identity.role().name()) || !"ACTIVE".equals(identity.status().name())) {
                throw new ProjectAccessDeniedException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }
}
