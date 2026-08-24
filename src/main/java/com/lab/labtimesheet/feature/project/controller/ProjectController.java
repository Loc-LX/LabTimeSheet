package com.lab.labtimesheet.feature.project.controller;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembersForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitWorkflowView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectListPage;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
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
 * <p>Project services remain the authority for ownership, membership, lifecycle, and
 * transactional validation. Known rule failures are returned to the originating safe view,
 * while authorization failures are left to {@code ProjectControllerAdvice} so identifiers are
 * not disclosed.
 */
// Điểm nhận HTTP request của toàn bộ màn hình Project.
// Controller chỉ lấy dữ liệu từ URL/form, gọi các Project service xử lý nghiệp vụ,
// rồi trả tên Thymeleaf view hoặc redirect về trình duyệt; không tự query database.
@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    // Spring inject các service cần cho phần hiển thị, thay đổi Project và đọc danh sách Intern.
    private final ProjectQueryService pages;
    private final ProjectService projects;
    private final AccountService accounts;
    private final Clock clock;

    /**
     * Lists only Projects visible to the authenticated actor and exposes Project creation only
     * to Mentors.
     *
     * @param principal authenticated user
     * @param page one-based bounded page number; values below one use the first page
     * @param model response model
     * @return the Project list view
     */
    // [Danh sách Project]
    // Luồng xử lý:
    // 1. Xác định tài khoản đang đăng nhập và role của họ.
    // 2. Chuẩn hóa số trang từ URL rồi lấy đúng các Project mà role đó được xem.
    // 3. Gửi danh sách, thông tin phân trang và quyền tạo Project sang giao diện.
    @GetMapping
    public String list(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        // Xác định người đang đăng nhập trước để danh sách chỉ chứa Project họ được phép xem.
        var actor = pages.authenticatedActor(principal.getName());
        // URL dùng số trang bắt đầu từ 1, còn Spring Pageable bắt đầu từ 0.
        int requestedPage = Math.max(page, 1);
        ProjectListPage projectPage = pages.listPage(
                actor.userId(), PageRequest.of(requestedPage - 1, 50));
        model.addAttribute("projects", projectPage.projects());
        model.addAttribute("projectPage", projectPage);
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
    // [Mở form tạo Project]
    // Luồng xử lý:
    // 1. Kiểm tra người mở form có phải Mentor hay không.
    // 2. Tạo form trống và tải danh sách Intern đang đủ điều kiện làm Leader ban đầu.
    @GetMapping("/new")
    public String createForm(Principal principal, Model model) {
        // Chỉ Mentor được nhìn thấy và gửi form tạo Project.
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
    // [Tạo Project mới]
    // Luồng xử lý:
    // 1. Chỉ Mentor được phép gửi form tạo Project.
    // 2. Nếu dữ liệu nhập sai, giữ form và danh sách lựa chọn để người dùng sửa.
    // 3. Nếu hợp lệ, giao cho ProjectService tạo Project; lỗi nghiệp vụ được trả về đúng ô Leader.
    @PostMapping
    public String create(
            Principal principal,
            @Valid @ModelAttribute("projectForm") ProjectCreateForm projectForm,
            BindingResult bindingResult,
            Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        // Kiểm tra quyền ở server, không dựa vào việc nút tạo Project có bị ẩn ở giao diện hay không.
        if (!"MENTOR".equals(actor.role())) {
            throw new ProjectAccessDeniedException();
        }
        if (bindingResult.hasErrors()) {
            // Khi dữ liệu form sai, tải lại các Intern hợp lệ để người dùng không phải bắt đầu lại.
            model.addAttribute("eligibleInternOptions", eligibleInternOptions());
            return "projects/form";
        }
        try {
            // Service thực hiện toàn bộ kiểm tra nghiệp vụ và trả về ID Project vừa tạo.
            long projectId = projects.create(actor.userId(), projectForm.toCommand());
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            // Gắn lỗi nghiệp vụ vào ô chọn Leader để hiển thị ngay trên form an toàn.
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
    // [Xem chi tiết Project]
    // Luồng xử lý:
    // 1. Lấy ID người đăng nhập.
    // 2. Query service tự kiểm tra quyền xem trước khi trả detail và trạng thái exit.
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
    // [Inbox lời mời Project]
    // Luồng xử lý:
    // 1. Xác định actor hiện tại.
    // 2. Chỉ lấy các invitation PENDING có invitee chính là actor đó.
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
    // [Điều hướng từ notification invitation]
    // Chỉ điều hướng về inbox; không thay đổi trạng thái invitation bằng GET request.
    @GetMapping("/{projectId}/invitations/{invitationId}")
    public String invitationAction() {
        return "redirect:/projects/invitations";
    }

    /** Applies an accept/decline response through the invitation's locked producer boundary. */
    // [Phản hồi lời mời Project]
    // Chuyển dữ liệu accept/decline đã chuẩn hóa sang service; service kiểm tra người được mời và lock state.
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
    // [Trang Workflows & History]
    // Luồng xử lý:
    // 1. Tạo một model gồm Project, member, invitation, exit, Task history và các quyền hiển thị.
    // 2. Render một trang workflow duy nhất; mọi thao tác thay đổi dữ liệu vẫn đi qua POST riêng.
    @GetMapping("/{projectId}/workflows")
    public String workflows(Principal principal, @PathVariable long projectId, Model model) {
        // Gom toàn bộ dữ liệu và quyền của màn workflow vào một snapshot trước khi render.
        populateWorkflowModel(principal, projectId, model);
        return "projects/workflows";
    }

    // [Gửi lời mời vào Project]
    // Tạo invitation từ dropdown; dữ liệu nhập được giữ lại trong flash nếu service từ chối.
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

    // [Thu hồi lời mời Project]
    // Revoke dùng ID Project và invitation cùng lúc để service chặn invitation thuộc Project khác.
    @PostMapping("/{projectId}/invitations/{invitationId}/revoke")
    String revokeInvitation(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long invitationId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.revokeInvitation(actorId(principal), projectId, invitationId);
            return null;
        });
    }

    // [Tạo yêu cầu loại thành viên]
    // Leader tạo yêu cầu loại một member; chưa đóng membership ở bước này.
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

    // [Tạo yêu cầu rời Project]
    // Member tự gửi yêu cầu rời Project; Mentor mới là người duyệt hoặc từ chối.
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

    // [Hủy yêu cầu exit]
    // Chỉ người đã tạo request mới có thể gọi endpoint hủy request này.
    @PostMapping("/{projectId}/exits/{requestId}/cancel")
    String cancelExit(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, () -> {
            projects.cancelExit(actorId(principal), projectId, requestId);
            return null;
        });
    }

    /**
     * Transfers one selected unfinished-Task batch from a pending exit target.
     *
     * <p>The browser submits one repeated {@code taskVersions} value per selected Task in the
     * {@code taskId:version} form. The complete pair set is validated at this trust boundary before
     * the immutable map is passed to the Project transaction; a missing, extra, duplicate, or
     * malformed pair cannot reach Task mutation.</p>
     *
     * @param principal authenticated current Leader
     * @param projectId owning Project encoded by the route
     * @param requestId pending exit request encoded by the route
     * @param sourceMembershipId pending-exit source membership input
     * @param taskIds selected Task identifiers
     * @param taskVersionPairs client-observed Task ID/version pairs
     * @param recipientMembershipId one eligible recipient membership input
     * @param redirectAttributes originating workflow flash state
     * @return workflow redirect after a successful or rejected Project rule operation
     */
    // [Chuyển Task trước khi duyệt exit]
    // Luồng xử lý:
    // 1. Giữ lại toàn bộ lựa chọn transfer để có thể hiển thị lại nếu thao tác bị từ chối.
    // 2. Kiểm tra Task ID, membership ID và cặp Task/version ngay tại HTTP boundary.
    // 3. Service thực hiện transfer theo transaction và optimistic locking.
    @PostMapping("/{projectId}/exits/{requestId}/transfer")
    String transferExitTasks(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long requestId,
            @RequestParam(defaultValue = "") String sourceMembershipId,
            @RequestParam(required = false) Set<String> taskIds,
            @RequestParam(name = "taskVersions", required = false) List<String> taskVersionPairs,
            @RequestParam(defaultValue = "") String recipientMembershipId,
            RedirectAttributes redirectAttributes) {
        return workflowMutation(projectId, redirectAttributes, Map.of(
                "kind", "transfer",
                "requestId", requestId,
                "sourceMembershipId", sourceMembershipId,
                "taskIds", taskIds == null ? Set.of() : Set.copyOf(taskIds),
                "taskVersions", taskVersionPairs == null ? List.of() : List.copyOf(taskVersionPairs),
                "recipientMembershipId", recipientMembershipId), () -> {
            // Kiểm tra toàn bộ cặp Task/version từ trình duyệt trước khi gửi sang transaction chuyển việc.
            Set<Long> selectedTaskIds = requiredLongSet(taskIds, "Choose at least one valid Task.");
            return projects.transferTasks(
                    actorId(principal),
                    projectId,
                    requestId,
                    requiredLong(sourceMembershipId, "Choose a valid source membership."),
                    selectedTaskIds,
                    requiredTaskVersions(selectedTaskIds, taskVersionPairs),
                    requiredLong(recipientMembershipId, "Choose a valid recipient membership."));
        });
    }

    // [Duyệt yêu cầu exit]
    // Mentor duyệt exit; service sẽ kiểm tra lại Leader replacement và Task chưa hoàn thành.
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
            projects.approveExit(actorId(principal), projectId, requestId, note);
            return null;
        });
    }

    // [Từ chối yêu cầu exit]
    // Mentor từ chối exit; member vẫn ở Project, chỉ trạng thái request thay đổi.
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
            projects.rejectExit(actorId(principal), projectId, requestId, note);
            return null;
        });
    }

    // [Mentor loại thành viên trực tiếp]
    // Mentor direct remove; service tự xử lý đổi Leader và chuyển Task nếu cần.
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

    // [Hoàn thành Project]
    // Complete chỉ được thực hiện khi service xác nhận mọi Task còn hiệu lực đã DONE.
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
    // [Kích hoạt Project]
    // Luồng xử lý:
    // 1. Yêu cầu service kích hoạt Project trong transaction.
    // 2. Nếu không thể kích hoạt, render lại detail cùng lý do để người dùng biết cần sửa gì.
    @PostMapping("/{projectId}/activate")
    public String activate(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        try {
            // Activation kiểm tra lại thành viên và Task ngay trong transaction để tránh kích hoạt dữ liệu cũ.
            projects.activate(actorId, projectId);
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            // Không redirect khi thất bại để lỗi lifecycle hiện trực tiếp ở trang Project hiện tại.
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
    // [Trang thành viên Project]
    // Luồng xử lý:
    // 1. Tải Project, member hiện tại/lịch sử và danh sách Intern có thể thêm.
    // 2. Form chọn nhiều Intern được tạo mới để giao diện gửi một batch duy nhất.
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
    // [Thêm nhiều thành viên vào Project]
    // Luồng xử lý:
    // 1. Gửi toàn bộ Intern được chọn sang service để thêm một cách nguyên tử.
    // 2. Nếu state đã đổi, tải lại lựa chọn hợp lệ và báo số lựa chọn không còn dùng được.
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
                // Thêm cả danh sách trong một transaction: hoặc tất cả được thêm, hoặc không ai được thêm.
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
            // Danh sách có thể đổi trong lúc người dùng chọn, nên tính lại số lựa chọn đã không còn dùng được.
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
    // [Trang lịch sử Leadership]
    // Luồng xử lý:
    // 1. Hiển thị các leadership term đã lưu.
    // 2. Chỉ owner đang quản lý Project mới nhận danh sách member đủ điều kiện thay Leader.
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
    // [Thay Leader của Project]
    // Luồng xử lý:
    // 1. Gửi Intern được chọn sang service để thay Leader.
    // 2. Nếu state không còn hợp lệ, giữ lựa chọn và render lại lịch sử leadership.
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
        // Detail và membership history đều đi qua query service để áp cùng một quyền xem Project.
        var project = pages.detail(actorId, projectId);
        var members = pages.members(actorId, projectId);
        model.addAttribute("project", project);
        model.addAttribute("members", members);
        if (project.canManage()) {
            // Không đưa thành viên hiện tại vào dropdown để tránh gửi lựa chọn trùng xuống service.
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
            // Leader mới phải là một thành viên hiện tại và không phải chính Leader đang giữ vai trò đó.
            Set<Long> replacementIds = pages.members(actorId, projectId).stream()
                    .filter(member -> member.leftAt() == null && !member.currentLeader())
                    .map(member -> member.internUserId())
                    .collect(Collectors.toUnmodifiableSet());
            model.addAttribute("eligibleInternOptions", eligibleInternOptions().stream()
                    .filter(option -> replacementIds.contains(option.userId()))
                    .toList());
        }
    }

    // [Chuẩn bị dữ liệu Workflows & History]
    // Luồng chuẩn bị dữ liệu cho trang Workflows & History:
    // 1. Lấy cùng một actor cho tất cả query để mọi phần trên trang dùng chung phạm vi quyền.
    // 2. Biến history/readiness thành các quyền UI: mời, revoke, transfer, cancel và quyết định exit.
    // 3. Đưa dữ liệu đã chuẩn hóa vào model; template không tự tính rule nghiệp vụ.
    private void populateWorkflowModel(Principal principal, long projectId, Model model) {
        // Dùng cùng actor cho toàn bộ dữ liệu để các phần trên một trang không lộ dữ liệu khác quyền.
        var actor = pages.authenticatedActor(principal.getName());
        var project = pages.detail(actor.userId(), projectId);
        var members = pages.members(actor.userId(), projectId);
        var readiness = pages.exitReadiness(actor.userId(), projectId);
        var history = pages.history(actor.userId(), projectId);
        // Template chỉ dùng username đã được chuẩn bị sẵn, không render raw membership ID.
        Map<Long, String> memberNames = members.stream().collect(Collectors.toUnmodifiableMap(
                member -> member.membershipId(),
                member -> history.usernamesByMembershipId().getOrDefault(
                        member.membershipId(), member.displayName())));
        boolean currentLeader = members.stream().anyMatch(member -> member.currentLeader()
                && member.internUserId() == actor.userId());
        // Các thành viên đang có yêu cầu rời Project sẽ không được chọn làm người nhận Task mới.
        Set<Long> pendingTargets = readiness.stream()
                .map(item -> item.targetMembershipId())
                .collect(Collectors.toUnmodifiableSet());
        var currentMembers = members.stream().filter(member -> member.leftAt() == null).toList();
        // Tra cứu thành viên theo membership ID để lấy riêng Username, Mã sinh viên và Gmail cho từng thẻ exit.
        Map<Long, ProjectMemberView> membersByMembershipId =
                members.stream().collect(Collectors.toUnmodifiableMap(
                        member -> member.membershipId(), member -> member));
        var exitWorkflows = readiness.stream()
                .map(item -> {
                    var targetMember = membersByMembershipId.get(item.targetMembershipId());
                    String targetUsername = targetMember == null
                            ? memberNames.getOrDefault(
                                    item.targetMembershipId(),
                                    "Membership " + item.targetMembershipId())
                            : targetMember.displayName();
                    String targetStudentCode = "—";
                    String targetEmail = "—";
                    if (targetMember != null) {
                        // AccountService là boundary duy nhất để lấy Gmail và Mã sinh viên; Project không đọc Account repository trực tiếp.
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
                            // Giữ thẻ exit hiển thị được nếu account lịch sử đã không còn tồn tại.
                        }
                    }
                    return new ProjectExitWorkflowView(
                            item.requestId(),
                            item.targetMembershipId(),
                            targetUsername,
                            targetStudentCode,
                            targetEmail,
                            item.targetIsCurrentLeader(),
                            item.unfinishedTaskCount(),
                            // Đây chỉ là điều kiện để hiện quyết định Mentor; service sẽ kiểm tra lại khi bấm nút.
                            item.readyForApproval(),
                            // Nút transfer chỉ hiện cho Leader hiện tại, không áp dụng khi chính Leader đang rời Project.
                            currentLeader && !item.targetIsCurrentLeader() && item.unfinishedTaskCount() > 0);
                })
                .toList();
        var unfinishedTasksByMembership = history.tasks().stream()
                // Task đã xoá và Task DONE không cần chuyển trước khi duyệt exit.
                .filter(task -> task.deletedAt() == null && task.status() != TaskStatus.DONE)
                .collect(Collectors.groupingBy(task -> task.assigneeMembershipId()));
        var currentMemberIds = currentMembers.stream()
                .map(member -> member.internUserId())
                .collect(Collectors.toUnmodifiableSet());
        var invitationOptions = currentLeader
                // Leader chỉ mời Intern chưa là thành viên của chính Project này.
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
                // Mentor được revoke mọi lời mời; Leader chỉ revoke lời mời của term hiện tại.
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
        // Drawer transfer chỉ cần map tên theo membership; tách map giúp fragment dùng được cả với dữ liệu test/list tối giản.
        model.addAttribute("projectHistoryUsernamesByMembershipId", history.usernamesByMembershipId());
    }

    // [Xử lý kết quả thao tác Workflow]
    // Bộ bọc chung cho các POST ở workflow:
    // 1. Gọi mutation từ ProjectService.
    // 2. Chỉ giữ lại input an toàn và thông báo lỗi nghiệp vụ.
    // 3. Luôn quay về workflow để tránh submit lại khi refresh trang.
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
            // Chỉ service mới thay đổi state; controller chỉ chuẩn hóa phản hồi cho cùng trang workflow.
            mutation.get();
            redirectAttributes.addFlashAttribute("message", "Project workflow updated");
        } catch (ProjectRuleViolationException exception) {
            // Chỉ giữ lại dữ liệu form an toàn, không đưa ID hoặc trạng thái nội bộ vào flash message.
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

    // [Kiểm tra version của Task transfer]
    // Chuyển danh sách "taskId:version" từ form thành map bất biến:
    // 1. Mỗi Task được chọn phải xuất hiện đúng một lần.
    // 2. Map kết quả phải có đúng toàn bộ Task ID người dùng đã chọn.
    // 3. Map này là cơ sở để service phát hiện thay đổi đồng thời.
    private static Map<Long, Long> requiredTaskVersions(
            Set<Long> taskIds, List<String> taskVersionPairs) {
        if (taskVersionPairs == null || taskVersionPairs.isEmpty()) {
            throw new ProjectRuleViolationException("Submit one version for every selected Task.");
        }
        Map<Long, Long> versions = new LinkedHashMap<>();
        for (String rawPair : taskVersionPairs) {
            // Mỗi Task phải có đúng một version để phát hiện Task bị người khác sửa song song.
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
            // Không cho phép thiếu version của bất kỳ Task nào trong lô được chọn.
            throw new ProjectRuleViolationException("Submit one version for every selected Task.");
        }
        return Map.copyOf(versions);
    }

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

    private List<EligibleInternOption> eligibleInternOptions() {
        // Eligibility phụ thuộc ngày hiện tại, vì vậy luôn lấy từ Account service thay vì cache ở controller.
        return accounts.eligibleInternOptions(LocalDate.now(clock));
    }

    private long actorId(Principal principal) {
        return pages.authenticatedUserId(principal.getName());
    }
}
