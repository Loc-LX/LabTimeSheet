package com.lab.labtimesheet.feature.project.controller;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembersForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitWorkflowView;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Serves authenticated, server-rendered Project pages and binds Project mutation forms.
 *
 * <p>Project services remain the authority for ownership, membership, lifecycle, and
 * transactional validation. Known rule failures are returned to the originating safe view,
 * while authorization failures are left to {@code ProjectControllerAdvice} so identifiers are
 * not disclosed.
 */
@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectQueryService pages;
    private final ProjectService projects;
    private final AccountService accounts;
    private final Clock clock;

    /**
     * Lists only Projects visible to the authenticated actor and exposes Project creation only
     * to Mentors.
     *
     * @param principal authenticated user
     * @param model response model
     * @return the Project list view
     */
    @GetMapping
    public String list(Principal principal, Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        model.addAttribute("projects", pages.listVisible(actor.userId()));
        model.addAttribute("canCreateProject", "MENTOR".equals(actor.role()));
        return "projects/list";
    }

    /**
     * Opens the creation form for an authenticated Mentor.
     *
     * @param principal authenticated user
     * @param model response model
     * @return the Project creation view
     * @throws ProjectAccessDeniedException when the actor is not an active Mentor
     */
    @GetMapping("/new")
    public String createForm(Principal principal, Model model) {
        if (!"MENTOR".equals(pages.authenticatedActor(principal.getName()).role())) {
            throw new ProjectAccessDeniedException();
        }
        model.addAttribute("projectForm", new ProjectCreateForm());
        model.addAttribute("eligibleInternOptions", eligibleInternOptions());
        return "projects/form";
    }

    /**
     * Creates a Project or re-renders the form with retained safe input when validation fails.
     *
     * @param principal authenticated user
     * @param projectForm validated browser input
     * @param bindingResult binding and domain validation results
     * @param model response model used when validation fails
     * @return a redirect to the created Project, or the creation form on validation failure
     */
    @PostMapping
    public String create(
            Principal principal,
            @Valid @ModelAttribute("projectForm") ProjectCreateForm projectForm,
            BindingResult bindingResult,
            Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        if (!"MENTOR".equals(actor.role())) {
            throw new ProjectAccessDeniedException();
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("eligibleInternOptions", eligibleInternOptions());
            return "projects/form";
        }
        try {
            long projectId = projects.create(actor.userId(), projectForm.toCommand());
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            bindingResult.rejectValue(
                    "initialLeaderUserId", "project.initialLeader.ineligible", exception.getMessage());
            model.addAttribute("eligibleInternOptions", eligibleInternOptions());
            return "projects/form";
        }
    }

    /**
     * Renders an authorized Project detail without disclosing guessed identifiers.
     *
     * @param principal authenticated user
     * @param projectId requested Project identifier
     * @param model response model
     * @return the Project detail view
     */
    @GetMapping("/{projectId}")
    public String detail(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        model.addAttribute("project", pages.detail(actorId, projectId));
        model.addAttribute("exitReadiness", pages.exitReadiness(actorId, projectId));
        return "projects/detail";
    }

    /**
     * Lists only pending invitations addressed to the authenticated Intern.
     *
     * @param principal authenticated invitee
     * @param model response model
     * @return invitation inbox view
     */
    @GetMapping("/invitations")
    public String invitations(Principal principal, Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        model.addAttribute("invitations", pages.pendingInvitations(actor.userId()));
        return "projects/invitations";
    }

    /**
     * Routes a notification action to the authenticated invitation inbox; the POST response
     * boundary remains the only state-changing path.
     *
     * @return invitee inbox redirect
     */
    @GetMapping("/{projectId}/invitations/{invitationId}")
    public String invitationAction() {
        return "redirect:/projects/invitations";
    }

    /** Applies an accept/decline response through the invitation's locked producer boundary. */
    @PostMapping("/invitations/{invitationId}/respond")
    public String respondToInvitation(
            Principal principal,
            @PathVariable long invitationId,
            @RequestParam(defaultValue = "") String response,
            RedirectAttributes redirectAttributes) {
        return invitationMutation(redirectAttributes, () -> {
            projects.respondToInvitation(
                    actorId(principal),
                    invitationId,
                    requiredInvitationResponse(response));
            return null;
        });
    }

    /**
     * Renders production-bound invitation, exit, transfer, decision, completion, and retained
     * Project History controls for the exact authorized viewer.
     */
    @GetMapping("/{projectId}/workflows")
    public String workflows(Principal principal, @PathVariable long projectId, Model model) {
        populateWorkflowModel(principal, projectId, model);
        return "projects/workflows";
    }

    @PostMapping("/{projectId}/invitations")
    String issueInvitation(
            Principal principal,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "") String invitedInternUserId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "invitation",
                "invitedInternUserId", invitedInternUserId), () -> {
            projects.issueInvitation(
                    actorId(principal),
                    projectId,
                    requiredLong(invitedInternUserId, "Choose a valid Intern."));
            return null;
        });
    }

    @PostMapping("/{projectId}/invitations/{invitationId}/revoke")
    String revokeInvitation(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long invitationId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.revokeInvitation(actorId(principal), invitationId);
            return null;
        });
    }

    @PostMapping("/{projectId}/exits/removal")
    String requestRemoval(
            Principal principal,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "") String targetMembershipId,
            @RequestParam(defaultValue = "") String reason,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "removal",
                "targetMembershipId", targetMembershipId,
                "reason", reason), () -> {
            projects.requestMemberRemoval(
                    actorId(principal),
                    projectId,
                    requiredLong(targetMembershipId, "Choose a valid membership."),
                    reason);
            return null;
        });
    }

    @PostMapping("/{projectId}/exits/leave")
    String requestOwnLeave(
            Principal principal,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "") String reason,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "leave",
                "reason", reason), () -> {
            projects.requestOwnLeave(actorId(principal), projectId, reason);
            return null;
        });
    }

    @PostMapping("/{projectId}/exits/{requestId}/cancel")
    String cancelExit(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.cancelExit(actorId(principal), requestId);
            return null;
        });
    }

    @PostMapping("/{projectId}/exits/{requestId}/transfer")
    String transferExitTasks(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            @RequestParam(defaultValue = "") String sourceMembershipId,
            @RequestParam(required = false) Set<String> taskIds,
            @RequestParam(defaultValue = "") String recipientMembershipId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "transfer",
                "requestId", requestId,
                "sourceMembershipId", sourceMembershipId,
                "taskIds", taskIds == null ? Set.of() : Set.copyOf(taskIds),
                "recipientMembershipId", recipientMembershipId), () -> projects.transferTasks(
                actorId(principal),
                projectId,
                requestId,
                requiredLong(sourceMembershipId, "Choose a valid source membership."),
                requiredLongSet(taskIds, "Choose at least one valid Task."),
                requiredLong(recipientMembershipId, "Choose a valid recipient membership.")));
    }

    @PostMapping("/{projectId}/exits/{requestId}/approve")
    String approveExit(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "approve",
                "requestId", requestId,
                "note", note == null ? "" : note), () -> {
            projects.approveExit(actorId(principal), requestId, note);
            return null;
        });
    }

    @PostMapping("/{projectId}/exits/{requestId}/reject")
    String rejectExit(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "reject",
                "requestId", requestId,
                "note", note == null ? "" : note), () -> {
            projects.rejectExit(actorId(principal), requestId, note);
            return null;
        });
    }

    @PostMapping("/{projectId}/members/{membershipId}/remove")
    String directRemove(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long membershipId,
            @RequestParam(defaultValue = "") String replacementLeaderUserId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "direct-removal",
                "membershipId", membershipId,
                "replacementLeaderUserId", replacementLeaderUserId), () -> {
            projects.directRemoveMember(
                    actorId(principal),
                    projectId,
                    membershipId,
                    optionalLong(replacementLeaderUserId, "Choose a valid replacement Leader."));
            return null;
        });
    }

    @PostMapping("/{projectId}/complete")
    String complete(
            Principal principal,
            @PathVariable long projectId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.complete(actorId(principal), projectId);
            return null;
        });
    }

    /**
     * Activates a planned Project or re-renders its detail with a safe lifecycle error.
     *
     * @param principal authenticated user
     * @param projectId Project to activate
     * @param model response model used when activation is rejected
     * @return a detail redirect after success, or the detail view after a rule failure
     */
    @PostMapping("/{projectId}/activate")
    public String activate(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        try {
            projects.activate(actorId, projectId);
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            model.addAttribute("project", pages.detail(actorId, projectId));
            model.addAttribute("projectError", exception.getMessage());
            return "projects/detail";
        }
    }

    /**
     * Renders authorized current and historical membership intervals.
     *
     * @param principal authenticated user
     * @param projectId requested Project identifier
     * @param model response model
     * @return the membership history view
     */
    @GetMapping("/{projectId}/members")
    public String members(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        populateMembersModel(actorId, projectId, model);
        model.addAttribute("projectMembersForm", new ProjectMembersForm());
        return "projects/members";
    }

    /**
     * Adds all selected eligible Interns atomically or re-renders membership history with every
     * still-eligible selection retained and a count of unavailable choices.
     *
     * @param principal authenticated user
     * @param projectId owning Project identifier
     * @param membersForm validated Intern selection
     * @param bindingResult binding and domain validation results
     * @param model response model used on failure
     * @return a membership redirect after success, or the membership view on validation failure
     */
    @PostMapping("/{projectId}/members")
    public String addMembers(
            Principal principal,
            @PathVariable long projectId,
            @Valid @ModelAttribute("projectMembersForm") ProjectMembersForm membersForm,
            BindingResult bindingResult,
            Model model) {
        long actorId = actorId(principal);
        boolean rejectedByService = false;
        if (!bindingResult.hasErrors()) {
            try {
                projects.addMembers(actorId, projectId, membersForm.internUserIds());
                return "redirect:/projects/" + projectId + "/members";
            } catch (ProjectRuleViolationException exception) {
                rejectedByService = true;
                bindingResult.rejectValue(
                        "internUserIds", "project.members.ineligible", exception.getMessage());
            }
        }
        var refreshedOptions = populateMembersModel(actorId, projectId, model);
        if (rejectedByService) {
            Set<Long> refreshedIds = refreshedOptions.stream()
                    .map(EligibleInternOption::userId)
                    .collect(Collectors.toUnmodifiableSet());
            long unavailableSelectionCount = membersForm.internUserIds().stream()
                    .filter(userId -> !refreshedIds.contains(userId))
                    .count();
            model.addAttribute("unavailableSelectionCount", unavailableSelectionCount);
        }
        return "projects/members";
    }

    /**
     * Renders the authorized leadership-term history and an owner-only mutation form while the
     * Project is mutable.
     *
     * @param principal authenticated user
     * @param projectId requested Project identifier
     * @param model response model
     * @return the leadership history view
     */
    @GetMapping("/{projectId}/leadership")
    public String leadership(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        populateLeadershipModel(actorId, projectId, model);
        model.addAttribute("projectMemberForm", new ProjectMemberForm(null));
        return "projects/leadership";
    }

    /**
     * Appoints an eligible current member or re-renders leadership history with retained input.
     *
     * @param principal authenticated user
     * @param projectId owning Project identifier
     * @param memberForm validated replacement Leader selection
     * @param bindingResult binding and domain validation results
     * @param model response model used on failure
     * @return a leadership redirect after success, or the leadership view on validation failure
     */
    @PostMapping("/{projectId}/leadership")
    public String changeLeader(
            Principal principal,
            @PathVariable long projectId,
            @Valid @ModelAttribute("projectMemberForm") ProjectMemberForm memberForm,
            BindingResult bindingResult,
            Model model) {
        long actorId = actorId(principal);
        if (!bindingResult.hasErrors()) {
            try {
                projects.changeLeader(actorId, projectId, memberForm.internUserId());
                return "redirect:/projects/" + projectId + "/leadership";
            } catch (ProjectRuleViolationException exception) {
                bindingResult.rejectValue("internUserId", "project.leader.ineligible", exception.getMessage());
            }
        }
        populateLeadershipModel(actorId, projectId, model);
        return "projects/leadership";
    }

    private List<EligibleInternOption> populateMembersModel(long actorId, long projectId, Model model) {
        var project = pages.detail(actorId, projectId);
        var members = pages.members(actorId, projectId);
        model.addAttribute("project", project);
        model.addAttribute("members", members);
        if (project.canManage()) {
            Set<Long> currentMemberIds = members.stream()
                    .filter(member -> member.leftAt() == null)
                    .map(member -> member.internUserId())
                    .collect(Collectors.toUnmodifiableSet());
            var options = eligibleInternOptions().stream()
                    .filter(option -> !currentMemberIds.contains(option.userId()))
                    .toList();
            model.addAttribute("eligibleInternOptions", options);
            return options;
        }
        return List.of();
    }

    private void populateLeadershipModel(long actorId, long projectId, Model model) {
        var project = pages.detail(actorId, projectId);
        model.addAttribute("project", project);
        model.addAttribute("leadership", pages.leadership(actorId, projectId));
        if (project.canManage()) {
            Set<Long> replacementIds = pages.members(actorId, projectId).stream()
                    .filter(member -> member.leftAt() == null && !member.currentLeader())
                    .map(member -> member.internUserId())
                    .collect(Collectors.toUnmodifiableSet());
            model.addAttribute("eligibleInternOptions", eligibleInternOptions().stream()
                    .filter(option -> replacementIds.contains(option.userId()))
                    .toList());
        }
    }

    private void populateWorkflowModel(Principal principal, long projectId, Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        var project = pages.detail(actor.userId(), projectId);
        var members = pages.members(actor.userId(), projectId);
        var readiness = pages.exitReadiness(actor.userId(), projectId);
        var history = pages.history(actor.userId(), projectId);
        Map<Long, String> memberNames = members.stream().collect(Collectors.toUnmodifiableMap(
                member -> member.membershipId(),
                member -> member.displayName()));
        boolean currentLeader = members.stream().anyMatch(member -> member.currentLeader()
                && member.internUserId() == actor.userId());
        Set<Long> pendingTargets = readiness.stream()
                .map(item -> item.targetMembershipId())
                .collect(Collectors.toUnmodifiableSet());
        var currentMembers = members.stream().filter(member -> member.leftAt() == null).toList();
        var exitWorkflows = readiness.stream()
                .map(item -> new ProjectExitWorkflowView(
                        item.requestId(),
                        item.targetMembershipId(),
                        memberNames.getOrDefault(item.targetMembershipId(), "Membership " + item.targetMembershipId()),
                        item.targetIsCurrentLeader(),
                        item.unfinishedTaskCount(),
                        item.readyForApproval(),
                        currentLeader && !item.targetIsCurrentLeader() && item.unfinishedTaskCount() > 0))
                .toList();
        var unfinishedTasksByMembership = history.tasks().stream()
                .filter(task -> task.deletedAt() == null && task.status() != TaskStatus.DONE)
                .collect(Collectors.groupingBy(task -> task.assigneeMembershipId()));
        var currentMemberIds = currentMembers.stream()
                .map(member -> member.internUserId())
                .collect(Collectors.toUnmodifiableSet());
        var invitationOptions = currentLeader
                ? eligibleInternOptions().stream()
                        .filter(option -> !currentMemberIds.contains(option.userId()))
                        .toList()
                : List.<EligibleInternOption>of();
        var actorMembership = currentMembers.stream()
                .filter(member -> member.internUserId() == actor.userId())
                .findFirst()
                .orElse(null);
        Long currentLeadershipTermId = history.leadership().stream()
                .filter(term -> term.endedAt() == null)
                .map(term -> term.id())
                .findFirst()
                .orElse(null);
        var revocableInvitations = history.invitations().stream()
                .filter(invitation -> invitation.status().name().equals("PENDING"))
                .filter(invitation -> project.canManage()
                        || currentLeader && currentLeadershipTermId != null
                                && invitation.issuingLeadershipTermId() == currentLeadershipTermId)
                .toList();
        Set<Long> cancellableExitIds = actorMembership == null
                ? Set.of()
                : history.exitRequests().stream()
                        .filter(request -> request.status().name().equals("PENDING")
                                && request.requesterMembershipId() == actorMembership.membershipId())
                        .map(request -> request.id())
                        .collect(Collectors.toUnmodifiableSet());

        model.addAttribute("project", project);
        model.addAttribute("actor", actor);
        model.addAttribute("members", members);
        model.addAttribute("currentMembers", currentMembers);
        model.addAttribute("currentLeader", currentLeader);
        model.addAttribute("actorMembership", actorMembership);
        model.addAttribute("exitWorkflows", exitWorkflows);
        model.addAttribute("pendingTargetMembershipIds", pendingTargets);
        model.addAttribute("unfinishedTasksByMembership", unfinishedTasksByMembership);
        model.addAttribute("transferRecipients", currentMembers.stream()
                .filter(member -> !pendingTargets.contains(member.membershipId()))
                .toList());
        model.addAttribute("invitationOptions", invitationOptions);
        model.addAttribute("revocableInvitations", revocableInvitations);
        model.addAttribute("cancellableExitIds", cancellableExitIds);
        model.addAttribute("projectHistory", history);
    }

    private String workflowMutation(
            long projectId, RedirectAttributes redirectAttributes, Supplier<?> mutation) {
        return workflowMutation(projectId, redirectAttributes, Map.of(), mutation);
    }

    private String workflowMutation(
            long projectId,
            RedirectAttributes redirectAttributes,
            Map<String, ?> safeInput,
            Supplier<?> mutation) {
        try {
            mutation.get();
            redirectAttributes.addFlashAttribute("message", "Project workflow updated");
        } catch (ProjectRuleViolationException exception) {
            redirectAttributes.addFlashAttribute("projectError", exception.getMessage());
            if (!safeInput.isEmpty()) {
                redirectAttributes.addFlashAttribute("projectInput", safeInput);
            }
        }
        return "redirect:/projects/" + projectId + "/workflows";
    }

    private String invitationMutation(RedirectAttributes redirectAttributes, Supplier<?> mutation) {
        try {
            mutation.get();
            redirectAttributes.addFlashAttribute("message", "Invitation response saved");
        } catch (ProjectRuleViolationException exception) {
            redirectAttributes.addFlashAttribute("projectError", exception.getMessage());
        }
        return "redirect:/projects/invitations";
    }

    private static InvitationResponse requiredInvitationResponse(String value) {
        try {
            return InvitationResponse.valueOf(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new ProjectRuleViolationException("Choose a valid invitation response.");
        }
    }

    private static long requiredLong(String value, String errorMessage) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new ProjectRuleViolationException(errorMessage);
        }
    }

    private static Long optionalLong(String value, String errorMessage) {
        return value == null || value.isBlank() ? null : requiredLong(value, errorMessage);
    }

    private static Set<Long> requiredLongSet(Set<String> values, String errorMessage) {
        if (values == null || values.isEmpty()) {
            throw new ProjectRuleViolationException(errorMessage);
        }
        try {
            return values.stream()
                    .map(String::strip)
                    .map(Long::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException exception) {
            throw new ProjectRuleViolationException(errorMessage);
        }
    }

    private List<EligibleInternOption> eligibleInternOptions() {
        return accounts.eligibleInternOptions(LocalDate.now(clock));
    }

    private long actorId(Principal principal) {
        return pages.authenticatedUserId(principal.getName());
    }
}
