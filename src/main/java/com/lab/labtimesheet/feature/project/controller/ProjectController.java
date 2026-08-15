package com.lab.labtimesheet.feature.project.controller;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberForm;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import jakarta.validation.Valid;
import java.security.Principal;
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
        return "projects/form";
    }

    /**
     * Creates a Project or re-renders the form with retained safe input when validation fails.
     *
     * @param principal authenticated user
     * @param projectForm validated browser input
     * @param bindingResult binding and domain validation results
     * @return a redirect to the created Project, or the creation form on validation failure
     */
    @PostMapping
    public String create(
            Principal principal,
            @Valid @ModelAttribute("projectForm") ProjectCreateForm projectForm,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "projects/form";
        }
        try {
            long projectId = projects.create(actorId(principal), projectForm.toCommand());
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            bindingResult.rejectValue(
                    "initialLeaderUserId", "project.initialLeader.ineligible", exception.getMessage());
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
        model.addAttribute("project", pages.detail(actorId, projectId));
        model.addAttribute("members", pages.members(actorId, projectId));
        model.addAttribute("projectMemberForm", new ProjectMemberForm(null));
        return "projects/members";
    }

    /**
     * Adds an eligible Intern or re-renders membership history with the submitted identifier
     * and a safe validation message.
     *
     * @param principal authenticated user
     * @param projectId owning Project identifier
     * @param memberForm validated Intern selection
     * @param bindingResult binding and domain validation results
     * @param model response model used on failure
     * @return a membership redirect after success, or the membership view on validation failure
     */
    @PostMapping("/{projectId}/members")
    public String addMember(
            Principal principal,
            @PathVariable long projectId,
            @Valid @ModelAttribute("projectMemberForm") ProjectMemberForm memberForm,
            BindingResult bindingResult,
            Model model) {
        long actorId = actorId(principal);
        if (!bindingResult.hasErrors()) {
            try {
                projects.addMember(actorId, projectId, memberForm.internUserId());
                return "redirect:/projects/" + projectId + "/members";
            } catch (ProjectRuleViolationException exception) {
                bindingResult.rejectValue("internUserId", "project.member.ineligible", exception.getMessage());
            }
        }
        model.addAttribute("project", pages.detail(actorId, projectId));
        model.addAttribute("members", pages.members(actorId, projectId));
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
        model.addAttribute("project", pages.detail(actorId, projectId));
        model.addAttribute("leadership", pages.leadership(actorId, projectId));
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
        model.addAttribute("project", pages.detail(actorId, projectId));
        model.addAttribute("leadership", pages.leadership(actorId, projectId));
        return "projects/leadership";
    }

    private long actorId(Principal principal) {
        return pages.authenticatedUserId(principal.getName());
    }
}
