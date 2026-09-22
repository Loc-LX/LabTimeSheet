package com.lab.labtimesheet.feature.project.controller;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestType;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitWorkflowView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectListPage;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembersForm;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
 * <p>Every method here is reached through Spring MVC's {@code DispatcherServlet} after the
 * servlet filter chain has handled the HTTP session, authentication, authorization, and (for
 * state-changing requests) CSRF checks. The controller is therefore the HTTP boundary: it turns
 * the authenticated {@link Principal}, path variables, query parameters, and form fields into
 * feature-service calls, then turns a logical view name or redirect into the HTTP response
 * rendered by Thymeleaf.</p>
 *
 * <p>Project services remain the authority for ownership, membership, lifecycle, and
 * transactional validation. Known rule failures are returned to the originating safe view,
 * while authorization failures are left to {@code ProjectControllerAdvice} so identifiers are
 * not disclosed.</p>
 */
// Cổng HTTP Project. Tìm cụm: Ctrl+F "===" (LIST | CREATE | DETAIL | INVITATION | WORKFLOWS | ADD MEMBER | CHANGE LEADER | ACTIVATE | DELETE).
@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectQueryService pages;   // đọc dữ liệu hiển thị
    private final ProjectService projects;     // ghi/thay đổi dữ liệu project
    private final AccountService accounts;     // lấy danh sách intern hợp lệ
    private final Clock clock;                 // ngày hiện tại (validate ngày bắt đầu)

    /**
     * Lists only Projects visible to the authenticated actor and exposes Project creation only
     * to Mentors.
     *
     * @param principal authenticated user
     * @param page one-based bounded page number; values below one use the first page
     * @param model response model
     * @return the Project list view
     */
    // === LIST PROJECT | GET /projects ===
    // Chức năng: bảng project theo role + phân trang; Mentor thấy nút Create.
    @GetMapping
    public String list(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        var actor = pages.authenticatedActor(principal.getName()); // → Query: ProjectQueryService.authenticatedActor
        int requestedPage = Math.max(page, 1);
        ProjectListPage projectPage = pages.listPage( // → Query: ProjectQueryService.listPage → Repo: ProjectRepository
                actor.userId(), PageRequest.of(requestedPage - 1, 50));
        model.addAttribute("projects", projectPage.projects());       // → list.html: bảng
        model.addAttribute("projectPage", projectPage);             // → list.html: Previous/Next
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
    // === CREATE PROJECT | GET /projects/new ===
    // Chức năng (Controller): Mentor mở form — nạp form rỗng + dropdown Intern; không ghi DB.
    @GetMapping("/new")
    public String createForm(Principal principal, Model model) {
        if (!"MENTOR".equals(pages.authenticatedActor(principal.getName()).role())) { // → Query: ProjectQueryService.authenticatedActor
            throw new ProjectAccessDeniedException();
        }
        model.addAttribute("projectForm", new ProjectCreateForm());
        model.addAttribute("eligibleInternOptions", eligibleInternOptions()); // → Service: AccountService (helper bên dưới)
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
    // === CREATE PROJECT | POST /projects ===
    // Chức năng (Controller): bind form → validate → gọi Service tạo project → redirect hoặc render lại form lỗi.
    @PostMapping
    public String create(
            Principal principal,
            @Valid @ModelAttribute("projectForm") ProjectCreateForm projectForm,
            BindingResult bindingResult,
            Model model) {
        var actor = pages.authenticatedActor(principal.getName()); // → Query: ProjectQueryService.authenticatedActor
        if (!"MENTOR".equals(actor.role())) {
            throw new ProjectAccessDeniedException();
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("eligibleInternOptions", eligibleInternOptions());
            return "projects/form";
        }
        try {
            long projectId = projects.create(actor.userId(), projectForm.toCommand()); // → Service: ProjectService.create
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            String field = exception.getMessage() != null
                    && exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("date")
                    ? "startDate" : "initialLeaderUserId";
            bindingResult.rejectValue(
                    field, "project.rule.violation", exception.getMessage());
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
    // === DETAIL PROJECT | GET /projects/{id} ===
    // Chức năng: tổng quan project; nút Activate/Delete trên detail.html gọi POST riêng bên dưới.
    @GetMapping("/{projectId}")
    public String detail(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        model.addAttribute("project", pages.detail(actorId, projectId)); // → Query: ProjectQueryService.detail
        model.addAttribute("exitReadiness", pages.exitReadiness(actorId, projectId)); // → Query: có exit đang chờ không
        return "projects/detail";
    }

    /**
     * Lists only pending invitations addressed to the authenticated Intern.
     *
     * @param principal authenticated invitee
     * @param model response model
     * @return invitation inbox view
     */
    // === INVITATION INBOX | GET /projects/invitations ===
    // Chức năng: Intern xem lời mời PENDING; Accept/Decline dùng POST respond bên dưới.
    @GetMapping("/invitations")
    public String invitations(Principal principal, Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        model.addAttribute("invitations", pages.pendingInvitations(actor.userId())); // → Query: ProjectQueryService
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
        // Link email/thông báo → chỉ redirect inbox, không đổi DB (Accept/Decline dùng POST)
        return "redirect:/projects/invitations";
    }

    /** Applies an accept/decline response through the invitation's locked producer boundary. */
    // === INVITATION RESPOND | POST /projects/invitations/{id}/respond ===
    // Chức năng: Intern chấp nhận/từ chối lời mời → thêm membership hoặc đóng invitation.
    @PostMapping("/invitations/{invitationId}/respond")
    public String respondToInvitation(
            Principal principal,
            @PathVariable long invitationId,
            @RequestParam(defaultValue = "") String response,
            RedirectAttributes redirectAttributes) {
        return invitationMutation(redirectAttributes, () -> {
            projects.respondToInvitation( // → Service: ProjectService.respondToInvitation
                    actorId(principal),
                    invitationId,
                    requiredInvitationResponse(response));
            return null;
        });
    }

    /** Renders invitation, exit, transfer, decision, and completion workflows for the authorized viewer. */
    // === WORKFLOWS | GET /projects/{id}/workflows ===
    // Chức năng: gom exit queue, mời Intern, chuyển task, duyệt/từ chối — mọi POST workflow ở block bên dưới.
    @GetMapping("/{projectId}/workflows")
    public String workflows(Principal principal, @PathVariable long projectId, Model model) {
        populateWorkflowModel(principal, projectId, model); // helper: nạp toàn bộ Model cho workflows.html
        return "projects/workflows";
    }

    /** Renders the retained, read-only Project history for the exact authorized viewer. */
    // === HISTORY | GET /projects/{id}/history ===
    // Chức năng: chỉ đọc — membership, Leader, invitation, exit, task (không ghi DB).
    @GetMapping("/{projectId}/history")
    public String history(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        model.addAttribute("project", pages.detail(actorId, projectId)); // → Query
        model.addAttribute("projectHistory", pages.history(actorId, projectId)); // → Query
        return "projects/history";
    }

    // === WORKFLOWS | POST mời Intern ===
    // Chức năng: Leader (hoặc Mentor trên workflows) gửi lời mời tham gia project.
    @PostMapping("/{projectId}/invitations")
    String issueInvitation(
            Principal principal,
            @PathVariable long projectId,
            @RequestParam(name = "invitedInternUserId", required = false) List<String> invitedInternUserIds,
            RedirectAttributes redirectAttributes) {
        // workflows.html: parse Intern ID từ form (1 hoặc nhiều checkbox)
        List<String> selected = invitedInternUserIds == null ? List.of() : List.copyOf(invitedInternUserIds);
        Map<String, Object> safeInput = selected.size() == 1
                ? Map.of("kind", "invitation", "invitedInternUserId", selected.getFirst())
                : Map.of("kind", "invitation", "invitedInternUserIds", selected);
        return workflowMutation(projectId, redirectAttributes, safeInput, () -> {
            List<Long> ids = selected.stream()
                    .map(value -> requiredLong(value, "Choose valid Interns."))
                    .toList();
            if (ids.size() == 1) {
                projects.issueInvitation(actorId(principal), projectId, ids.getFirst()); // → Service
            } else {
                projects.issueInvitations(actorId(principal), projectId, ids); // → Service
            }
            return null;
        });
    }

    // === WORKFLOWS | POST thu hồi lời mời ===
    @PostMapping("/{projectId}/invitations/{invitationId}/revoke")
    String revokeInvitation(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long invitationId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.revokeInvitation(actorId(principal), projectId, invitationId); // → Service
            return null;
        });
    }

    // === WORKFLOWS | POST Leader đề xuất loại member ===
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
            projects.requestMemberRemoval( // → Service
                    actorId(principal),
                    projectId,
                    requiredLong(targetMembershipId, "Choose a valid membership."),
                    reason);
            return null;
        });
    }

    // === WORKFLOWS | POST Intern xin rời ===
    @PostMapping("/{projectId}/exits/leave")
    String requestOwnLeave(
            Principal principal,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "") String reason,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "leave",
                "reason", reason), () -> {
            projects.requestOwnLeave(actorId(principal), projectId, reason); // → Service
            return null;
        });
    }

    // === WORKFLOWS | POST hủy yêu cầu rời (người gửi) ===
    @PostMapping("/{projectId}/exits/{requestId}/cancel")
    String cancelExit(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.cancelExit(actorId(principal), projectId, requestId); // → Service
            return null;
        });
    }

    /**
     * Transfers one selected unfinished-Task batch from a pending exit target.
     *
     * <p>The browser submits one repeated {@code taskVersions} value per selected Task in the
     * {@code taskId:version} form. The complete pair set is validated at this trust boundary before
     * the immutable map is passed to the Project transaction; a missing, extra, duplicate, or
     * malformed pair cannot reach Task mutation. Worked rows may additionally submit repeated
     * {@code forecastInputs} values in the {@code taskId:remaining[:note]} form; unworked rows
     * deliberately omit that value.</p>
     *
     * @param principal authenticated current Leader
     * @param projectId owning Project encoded by the route
     * @param requestId pending exit request encoded by the route
     * @param sourceMembershipId pending-exit source membership input
     * @param taskIds selected Task identifiers
     * @param taskVersionPairs client-observed Task ID/version pairs
     * @param forecastInputPairs optional worked-Task ID/remaining/note pairs
     * @param recipientMembershipId one eligible recipient membership input
     * @param redirectAttributes originating workflow flash state
     * @return workflow redirect after a successful or rejected Project rule operation
     */
    // === WORKFLOWS | POST Leader chuyển task trước khi Mentor duyệt exit ===
    @PostMapping("/{projectId}/exits/{requestId}/transfer")
    String transferExitTasks(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            @RequestParam(defaultValue = "") String sourceMembershipId,
            @RequestParam(required = false) Set<String> taskIds,
            @RequestParam(name = "taskVersions", required = false) List<String> taskVersionPairs,
            @RequestParam(name = "forecastInputs", required = false) List<String> forecastInputPairs,
            @RequestParam(defaultValue = "") String recipientMembershipId,
            RedirectAttributes redirectAttributes) {
        // workflows.html: gom input form chuyển task (taskIds, version, người nhận)
        Map<String, Object> safeTransferInput = new LinkedHashMap<>();
        safeTransferInput.put("kind", "transfer");
        safeTransferInput.put("requestId", requestId);
        safeTransferInput.put("sourceMembershipId", sourceMembershipId);
        safeTransferInput.put("taskIds", taskIds == null ? Set.of() : Set.copyOf(taskIds));
        safeTransferInput.put("taskVersions", taskVersionPairs == null
                ? List.of() : List.copyOf(taskVersionPairs));
        if (forecastInputPairs != null && !forecastInputPairs.isEmpty()) {
            safeTransferInput.put("forecastInputs", List.copyOf(forecastInputPairs));
        }
        safeTransferInput.put("recipientMembershipId", recipientMembershipId);
        return workflowMutation(projectId, redirectAttributes, Map.copyOf(safeTransferInput), () -> {
            Set<Long> selectedTaskIds = requiredLongSet(taskIds, "Choose at least one valid Task.");
            long actorUserId = actorId(principal);
            long sourceId = requiredLong(sourceMembershipId, "Choose a valid source membership.");
            Map<Long, Long> expectedVersions = requiredTaskVersions(selectedTaskIds, taskVersionPairs);
            long recipientId = requiredLong(recipientMembershipId, "Choose a valid recipient membership.");
            if (forecastInputPairs == null || forecastInputPairs.isEmpty()) {
                return projects.transferTasks( // → Service → TaskTransferService
                        actorUserId,
                        projectId,
                        requestId,
                        sourceId,
                        selectedTaskIds,
                        expectedVersions,
                        recipientId);
            }
            return projects.transferTasks( // → Service (kèm dự báo nỗ lực)
                    actorUserId,
                    projectId,
                    requestId,
                    sourceId,
                    selectedTaskIds,
                    expectedVersions,
                    requiredForecastInputs(selectedTaskIds, forecastInputPairs),
                    recipientId);
        });
    }

    // === WORKFLOWS | POST Mentor duyệt yêu cầu rời ===
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
            projects.approveExit(actorId(principal), projectId, requestId, note); // → Service
            return null;
        });
    }

    // === WORKFLOWS | POST Mentor từ chối yêu cầu rời ===
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
            projects.rejectExit(actorId(principal), projectId, requestId, note); // → Service
            return null;
        });
    }

    // === WORKFLOWS | POST Mentor loại member trực tiếp ===
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
            projects.directRemoveMember( // → Service
                    actorId(principal),
                    projectId,
                    membershipId,
                    optionalLong(replacementLeaderUserId, "Choose a valid replacement Leader."));
            return null;
        });
    }

    // === WORKFLOWS | POST hoàn thành project ===
    @PostMapping("/{projectId}/complete")
    String complete(
            Principal principal,
            @PathVariable long projectId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.complete(actorId(principal), projectId); // → Service
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
    // === ACTIVATE PROJECT | POST /projects/{id}/activate ===
    // Chức năng: PLANNED → ACTIVE (nút trên detail.html).
    @PostMapping("/{projectId}/activate")
    public String activate(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        try {
            projects.activate(actorId, projectId); // → Service
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            model.addAttribute("project", pages.detail(actorId, projectId));
            model.addAttribute("projectError", exception.getMessage());
            return "projects/detail";
        }
    }

    /**
     * Deletes a planned Project draft and returns to the authorized Project list.
     *
     * <p>The service rechecks ownership and lifecycle under the Project lock. A concurrent
     * activation therefore turns this into the same safe detail error used by other lifecycle
     * mutations instead of allowing an ACTIVE Project to be deleted.</p>
     *
     * @param principal authenticated Mentor
     * @param projectId Project to delete
     * @param model response model used when deletion is rejected
     * @return a list redirect after success, or the detail view after a rule failure
     */
    // === DELETE PROJECT | POST /projects/{id}/delete ===
    // Chức năng: xóa bản nháp PLANNED (nút trên detail.html).
    @PostMapping("/{projectId}/delete")
    public String delete(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        try {
            projects.delete(actorId, projectId); // → Service
            return "redirect:/projects";
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
    // === ADD MEMBER | GET tab Members ===
    // Chức năng: hiển thị bảng thành viên + dropdown Intern (Mentor thêm người).
    public String members(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        populateMembersModel(actorId, projectId, model); // xử lí lấy danh sách member hiện tại và dropdown Intern (lọc người đã trong project).
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
    // === ADD MEMBER | POST thêm Intern ===
    // Chức năng: validate checkbox → gọi Service thêm membership → redirect hoặc render lại form lỗi.
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
                projects.addMembers(actorId, projectId, membersForm.internUserIds()); // → Service: ProjectService.addMembers
                return "redirect:/projects/" + projectId + "/members";
            } catch (ProjectRuleViolationException exception) {
                rejectedByService = true;
                bindingResult.rejectValue(
                        "internUserIds", "project.members.ineligible", exception.getMessage());
            }
        }
        // Lỗi → nạp lại members.html, giữ selection + đếm Intern không còn đủ điều kiện
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
    // === CHANGE LEADER | GET tab Leadership ===
    // Chức năng: lịch sử kỳ Leader + dropdown đổi Leader (Mentor).
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
    // === CHANGE LEADER | POST đổi Leader ===
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
                projects.changeLeader(actorId, projectId, memberForm.internUserId()); // → Service
                return "redirect:/projects/" + projectId + "/leadership";
            } catch (ProjectRuleViolationException exception) {
                bindingResult.rejectValue("internUserId", "project.leader.ineligible", exception.getMessage());
            }
        }
        populateLeadershipModel(actorId, projectId, model);
        return "projects/leadership";
    }

    // === CONTROLLER HELPERS | nạp Model ===
    // populateMembersModel: project + bảng member + dropdown Intern (lọc người đã trong project).
    private List<EligibleInternOption> populateMembersModel(long actorId, long projectId, Model model) {
        var project = pages.detail(actorId, projectId); // → Query: header + canManage (Mentor owner, project chưa COMPLETED)
        var members = pages.members(actorId, projectId); // → Query: toàn bộ lịch sử membership (current + đã rời)
        model.addAttribute("project", project);
        model.addAttribute("members", members);
        if (project.canManage()) { // chỉ Mentor owner mới thấy form thêm member
            // Lọc Intern đủ điều kiện nhưng chưa là thành viên hiện tại → dropdown thêm người
            Set<Long> currentMemberIds = members.stream()
                    .filter(member -> member.leftAt() == null) // chỉ member đang còn trong project (chưa leftAt)
                    .map(member -> member.internUserId())
                    .collect(Collectors.toUnmodifiableSet());
            var options = eligibleInternOptions().stream() // → AccountService → Repo JPQL, điều kiện:
                    // role INTERN + account ACTIVE + internship ACTIVE + hôm nay nằm trong [startDate, endDate]
                    .filter(option -> !currentMemberIds.contains(option.userId())) // loại người đã là member hiện tại
                    .toList();
            model.addAttribute("eligibleInternOptions", options);
            return options;
        }
        return List.of(); // Intern/Leader chỉ xem bảng, không có dropdown
    }

    // populateLeadershipModel: project + bảng kỳ Leader + dropdown thành viên (trừ Leader hiện tại).
    private void populateLeadershipModel(long actorId, long projectId, Model model) {
        var project = pages.detail(actorId, projectId); // → Query
        model.addAttribute("project", project);
        model.addAttribute("leadership", pages.leadership(actorId, projectId)); // → Query
        if (project.canManage()) {
            // Dropdown: thành viên đang ở lại nhưng không phải Leader hiện tại
            Set<Long> replacementIds = pages.members(actorId, projectId).stream()
                    .filter(member -> member.leftAt() == null && !member.currentLeader())
                    .map(member -> member.internUserId())
                    .collect(Collectors.toUnmodifiableSet());
            model.addAttribute("eligibleInternOptions", eligibleInternOptions().stream()
                    .filter(option -> replacementIds.contains(option.userId()))
                    .toList());
        }
    }

    // populateWorkflowModel: gom mọi data cho workflows.html (exit, invitation, transfer task...).
    private void populateWorkflowModel(Principal principal, long projectId, Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        var project = pages.detail(actor.userId(), projectId);
        var members = pages.members(actor.userId(), projectId);
        var readiness = pages.exitReadiness(actor.userId(), projectId); // hàng chờ exit Mentor duyệt
        var history = pages.history(actor.userId(), projectId);
        Map<Long, String> memberNames = members.stream().collect(Collectors.toUnmodifiableMap(
                member -> member.membershipId(),
                member -> history.usernamesByMembershipId().getOrDefault(
                        member.membershipId(), member.displayName())));
        boolean currentLeader = members.stream().anyMatch(member -> member.currentLeader()
                && member.internUserId() == actor.userId());
        Set<Long> pendingTargets = readiness.stream()
                .map(item -> item.targetMembershipId())
                .collect(Collectors.toUnmodifiableSet());
        var currentMembers = members.stream().filter(member -> member.leftAt() == null).toList();
        Map<Long, ProjectMemberView> membersByMembershipId =
                members.stream().collect(Collectors.toUnmodifiableMap(
                        member -> member.membershipId(), member -> member));
        var exitWorkflows = readiness.stream()
                .map(item -> {
                    // Gắn tên/email Intern đang chờ rời + số task chưa DONE
                    var targetMember = membersByMembershipId.get(item.targetMembershipId());
                    String targetUsername = targetMember == null
                            ? memberNames.getOrDefault(
                                    item.targetMembershipId(),
                                    "Membership " + item.targetMembershipId())
                            : targetMember.displayName();
                    String targetStudentCode = "—";
                    String targetEmail = "—";
                    if (targetMember != null) {
                        try {
                            var targetIdentity = accounts.requireIdentityById(targetMember.internUserId());
                            if (targetIdentity != null) {
                                targetEmail = targetIdentity.email() == null
                                        ? "—" : targetIdentity.email();
                                var studentCode = accounts.studentCodeByUserId(targetMember.internUserId());
                                targetStudentCode = studentCode == null
                                        ? "—" : studentCode.orElse("—");
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    return new ProjectExitWorkflowView(
                            item.requestId(),
                            item.targetMembershipId(),
                            history.exitRequests().stream()
                                    .filter(request -> request.id() == item.requestId())
                                    .map(request -> request.requestType())
                                    .findFirst()
                                    .orElse(ProjectExitRequestType.MEMBER_LEAVE),
                            history.exitRequests().stream()
                                    .filter(request -> request.id() == item.requestId())
                                    .map(request -> request.reasonText())
                                    .findFirst()
                                    .orElse("—"),
                            targetUsername,
                            targetStudentCode,
                            targetEmail,
                            item.targetIsCurrentLeader(),
                            item.unfinishedTaskCount(),
                            item.readyForApproval(),
                            currentLeader && !item.targetIsCurrentLeader() && item.unfinishedTaskCount() > 0);
                })
                .toList();
        var memberLeaveWorkflows = exitWorkflows.stream()
                .filter(workflow -> workflow.requestType() == ProjectExitRequestType.MEMBER_LEAVE)
                .toList();
        var leaderRemovalWorkflows = exitWorkflows.stream()
                .filter(workflow -> workflow.requestType() == ProjectExitRequestType.LEADER_REMOVAL)
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
                : List.<EligibleInternOption>of(); // → workflows.html: dropdown mời Intern mới
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
                .toList(); // → workflows.html: nút Revoke lời mời
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
        model.addAttribute("exitWorkflows", exitWorkflows);           // → workflows.html: bảng exit
        model.addAttribute("memberLeaveWorkflows", memberLeaveWorkflows);
        model.addAttribute("leaderRemovalWorkflows", leaderRemovalWorkflows);
        model.addAttribute("pendingTargetMembershipIds", pendingTargets);
        model.addAttribute("unfinishedTasksByMembership", unfinishedTasksByMembership); // form chuyển task
        model.addAttribute("transferRecipients", currentMembers.stream()
                .filter(member -> !pendingTargets.contains(member.membershipId()))
                .toList());
        model.addAttribute("invitationOptions", invitationOptions);
        model.addAttribute("revocableInvitations", revocableInvitations);
        model.addAttribute("cancellableExitIds", cancellableExitIds); // Intern hủy yêu cầu rời của mình
        model.addAttribute("projectHistory", history);
        model.addAttribute("projectHistoryUsernamesByMembershipId", history.usernamesByMembershipId());
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
            mutation.get(); // chạy ProjectService (ghi DB)
            redirectAttributes.addFlashAttribute("message", "Project workflow updated");
        } catch (ProjectRuleViolationException | TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("projectError", exception.getMessage());
            if (!safeInput.isEmpty()) {
                redirectAttributes.addFlashAttribute("projectInput", safeInput); // giữ form khi lỗi
            }
        }
        return "redirect:/projects/" + projectId + "/workflows"; // luôn quay lại workflows.html
    }

    // invitationMutation: bọc POST respond invitation — redirect /invitations.
    private String invitationMutation(RedirectAttributes redirectAttributes, Supplier<?> mutation) {
        try {
            mutation.get();
            redirectAttributes.addFlashAttribute("message", "Invitation response saved");
        } catch (ProjectRuleViolationException exception) {
            redirectAttributes.addFlashAttribute("projectError", exception.getMessage());
        }
        return "redirect:/projects/invitations"; // quay lại invitations.html
    }

    // === CONTROLLER HELPERS | parse form ===
    private static InvitationResponse requiredInvitationResponse(String value) {
        try {
            return InvitationResponse.valueOf(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new ProjectRuleViolationException("Choose a valid invitation response.");
        }
    }

    // Kiểm tra ID số bắt buộc từ form.
    private static long requiredLong(String value, String errorMessage) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new ProjectRuleViolationException(errorMessage);
        }
    }

    // Kiểm tra ID số tùy chọn từ form.
    private static Long optionalLong(String value, String errorMessage) {
        return value == null || value.isBlank() ? null : requiredLong(value, errorMessage);
    }

    // Kiểm tra tập ID số bắt buộc từ form.
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

    // Kiểm tra mỗi task được chọn có đúng một version (phát hiện sửa song song).
    private static Map<Long, Long> requiredTaskVersions(
            Set<Long> taskIds, List<String> taskVersionPairs) {
        if (taskVersionPairs == null || taskVersionPairs.isEmpty()) {
            throw new ProjectRuleViolationException("Submit one version for every selected Task.");
        }
        Map<Long, Long> versions = new LinkedHashMap<>();
        for (String rawPair : taskVersionPairs) {
            String[] parts = rawPair == null ? new String[0] : rawPair.strip().split(":", -1);
            if (parts.length != 2) {
                throw new ProjectRuleViolationException("Submit valid Task ID/version pairs.");
            }
            long taskId = parseTransferValue(parts[0], true);
            long version = parseTransferValue(parts[1], false);
            if (!taskIds.contains(taskId) || versions.putIfAbsent(taskId, version) != null) {
                throw new ProjectRuleViolationException("Submit one version for every selected Task.");
            }
        }
        if (!versions.keySet().equals(taskIds)) {
            throw new ProjectRuleViolationException("Submit one version for every selected Task.");
        }
        return Map.copyOf(versions);
    }

    /**
     * Parses the optional worked-Task forecast form values at the HTTP boundary.
     *
     * <p>Each value is {@code taskId:remainingMinutes[:note]}; splitting at most three parts
     * keeps colons in a human note intact. The selected-ID and duplicate checks happen before the
     * immutable map crosses into ProjectService. The service still owns the worked/unworked rule
     * because only the locked database state can tell whether a selected Task already has work.</p>
     */
    private static Map<Long, RemainingEffortForecastInput> requiredForecastInputs(
            Set<Long> taskIds, List<String> forecastInputPairs) {
        if (forecastInputPairs == null || forecastInputPairs.isEmpty()) {
            return Map.of();
        }
        Map<Long, RemainingEffortForecastInput> forecasts = new LinkedHashMap<>();
        for (String rawPair : forecastInputPairs) {
            String[] parts = rawPair == null ? new String[0] : rawPair.strip().split(":", 3);
            if (parts.length < 2 || parts.length > 3) {
                throw new ProjectRuleViolationException("Submit valid Task forecast pairs.");
            }
            long taskId = parseTransferValue(parts[0], true);
            int remainingMinutes;
            try {
                remainingMinutes = Integer.parseInt(parts[1].strip());
            } catch (NumberFormatException | NullPointerException exception) {
                throw new ProjectRuleViolationException("Submit valid Task forecast pairs.");
            }
            if (remainingMinutes < 1 || remainingMinutes > 527040
                    || !taskIds.contains(taskId)
                    || forecasts.containsKey(taskId)) {
                throw new ProjectRuleViolationException("Submit one forecast for each worked Task.");
            }
            String note = parts.length == 3 ? parts[2].strip() : null;
            forecasts.put(taskId, new RemainingEffortForecastInput(
                    remainingMinutes, note == null || note.isBlank() ? null : note));
        }
        return Map.copyOf(forecasts);
    }

    // Kiểm tra giá trị taskId/version từ form.
    private static long parseTransferValue(String value, boolean taskId) {
        try {
            long parsed = Long.parseLong(value.strip());
            if ((taskId && parsed <= 0) || (!taskId && parsed < 0)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ProjectRuleViolationException("Submit valid Task ID/version pairs.");
        }
    }

    // === CREATE PROJECT | dropdown Intern ===
    // Chức năng: lấy list Intern đủ điều kiện cho form (GET) và khi POST lỗi cần render lại form.
    private List<EligibleInternOption> eligibleInternOptions() {
        return accounts.eligibleInternOptions(LocalDate.now(clock)); // → Service: AccountService → Repo: InternProfileRepository
    }

    // Lấy ID người đăng nhập từ tài khoản đã xác thực.
    private long actorId(Principal principal) {
        return pages.authenticatedUserId(principal.getName());
    }
}
