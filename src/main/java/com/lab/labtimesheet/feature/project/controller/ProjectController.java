package com.lab.labtimesheet.feature.project.controller;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembersForm;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
        model.addAttribute("project", pages.detail(actorId(principal), projectId));
        return "projects/detail";
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

    private List<EligibleInternOption> eligibleInternOptions() {
        return accounts.eligibleInternOptions(LocalDate.now(clock));
    }

    private long actorId(Principal principal) {
        return pages.authenticatedUserId(principal.getName());
    }
}
