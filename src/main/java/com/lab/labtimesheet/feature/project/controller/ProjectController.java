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
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestType;
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
    // DispatcherServlet route: GET /projects -> method này vì class prefix là /projects và
    // @GetMapping không có suffix. Browser thường đến đây từ navigation hoặc từ redirect sau
    // một mutation; response cuối cùng là HTML view projects/list, không phải JSON.
    // Luồng xử lý:
    // 1. SecurityContext cung cấp Principal; controller đưa email vào ProjectQueryService để
    //    AccountService xác nhận account tồn tại, ACTIVE và lấy userId/role.
    // 2. page trên URL là one-based, còn PageRequest của Spring Data là zero-based; controller
    //    chuẩn hóa page rồi service query đúng phạm vi Admin/Mentor/Intern.
    // 3. QueryService đổi Entity thành DTO ProjectSummary, controller đưa DTO vào Model cùng
    //    quyền canCreateProject; Thymeleaf dùng Model đó để tạo link /projects/new.
    @GetMapping
    public String list(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        // principal.getName() là identity do Spring Security xác lập từ session, không phải giá trị
        // do browser gửi trong form. Vì vậy user không thể đổi actor bằng cách sửa parameter.
        var actor = pages.authenticatedActor(principal.getName());
        // URL dùng số trang bắt đầu từ 1, còn Spring Pageable bắt đầu từ 0; Math.max cũng ngăn
        // request page=0 hoặc page âm tạo Pageable bất hợp lệ.
        int requestedPage = Math.max(page, 1);
        // ProjectQueryService tiếp tục kiểm tra quyền và gọi ProjectRepository. Controller không
        // tự viết SQL và cũng không đưa toàn bộ Project aggregate vào template.
        ProjectListPage projectPage = pages.listPage(
                actor.userId(), PageRequest.of(requestedPage - 1, 50));
        // Model là request-scoped data passed from controller to Thymeleaf. addAttribute không save
        // dữ liệu; nó chỉ chuẩn bị biến cho lần render HTML hiện tại.
        model.addAttribute("projects", projectPage.projects());
        model.addAttribute("projectPage", projectPage);
        // Đây chỉ là hint để ẩn/hiện nút ở UI. POST /projects vẫn kiểm tra role lại ở server.
        model.addAttribute("canCreateProject", "MENTOR".equals(actor.role()));
        // Chuỗi này là logical view name; ThymeleafViewResolver tìm /templates/projects/list.html.
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
    // DispatcherServlet route: GET /projects/new -> method này. Mapping literal /new cụ thể hơn
    // mapping /{projectId}, nên chuỗi "new" không bị coi là một projectId.
    // Luồng xử lý:
    // 1. Filter chain đã xác thực session; pages.authenticatedActor tiếp tục đọc Account để
    //    xác nhận actor còn ACTIVE và có role MENTOR.
    // 2. Tạo một ProjectCreateForm rỗng trong Model với key projectForm. Đây là object phục vụ
    //    Thymeleaf binding của request GET, chưa liên quan đến database Project.
    // 3. Query Account-owned InternProfile projection để lấy các option Initial Leader hợp lệ.
    // 4. Đưa today vào Model để input date có min client-side; service vẫn kiểm tra lại ngày vì
    //    người dùng có thể bypass HTML bằng DevTools/Postman.
    // 5. Return projects/form; DispatcherServlet resolve template và trả HTTP 200 HTML.
    @GetMapping("/new")
    public String createForm(Principal principal, Model model) {
        // Đây là authorization ở application layer. Việc list.html ẩn nút Create không đủ an toàn
        // vì user vẫn có thể tự gõ GET /projects/new.
        if (!"MENTOR".equals(pages.authenticatedActor(principal.getName()).role())) {
            throw new ProjectAccessDeniedException();
        }
        // Constructor rỗng gán null cho name/description/startDate/endDate/initialLeaderUserId.
        // Thymeleaf sẽ lấy object này làm th:object="${projectForm}".
        model.addAttribute("projectForm", new ProjectCreateForm());
        // Helper gọi AccountService chứ không query trực tiếp từ Controller; options chỉ là dữ liệu
        // hiển thị, còn eligibility thực sự sẽ được recheck trong ProjectService khi POST.
        model.addAttribute("eligibleInternOptions", eligibleInternOptions());
        // LocalDate.now(clock) dùng business timezone Asia/Ho_Chi_Minh và tạo min="yyyy-MM-dd"
        // trong HTML input type=date.
        model.addAttribute("today", LocalDate.now(clock));
        // Logical view name -> Thymeleaf template /templates/projects/form.html.
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
    // DispatcherServlet route: POST /projects -> method này vì class prefix /projects kết hợp
    // @PostMapping không suffix. Browser gửi application/x-www-form-urlencoded gồm name,
    // description, startDate, endDate và initialLeaderUserId; CSRF token được Spring Security
    // kiểm tra trước khi DispatcherServlet gọi method.
    // Luồng xử lý:
    // 1. Spring MVC bind request parameters vào @ModelAttribute("projectForm") và convert String
    //    date/id thành LocalDate/Long; @Valid chạy Bean Validation; BindingResult nhận mọi lỗi.
    // 2. Controller xác nhận actor ACTIVE/Mentor một lần nữa, không tin role hoặc owner từ browser.
    // 3. Nếu BindingResult có lỗi, không gọi ProjectService: nạp lại option/today rồi render cùng
    //    view để th:field giữ input và th:errors/#fields hiển thị lỗi.
    // 4. Nếu form hợp lệ, form.toCommand() tạo command immutable; actor.userId() được truyền riêng
    //    làm owner, nên form không thể giả mạo Mentor sở hữu Project.
    // 5. ProjectService mở transaction, lock Account/Profile, recheck business rule, tạo Entity và
    //    save cascade qua ProjectRepository. Thành công trả ID, controller trả redirect để browser
    //    tạo GET /projects/{id} mới theo Post/Redirect/Get.
    // 6. ProjectRuleViolationException của creation flow được map vào field an toàn; AccessDenied
    //    đi lên ProjectControllerAdvice để trả response không tiết lộ resource.
    @PostMapping
    public String create(
            Principal principal,
            @Valid @ModelAttribute("projectForm") ProjectCreateForm projectForm,
            BindingResult bindingResult,
            Model model) {
        // HandlerAdapter của Spring MVC đã tạo projectForm và BindingResult trước khi vào body này.
        // BindingResult phải đứng ngay sau form parameter để Spring gắn đúng lỗi vào object projectForm.
        var actor = pages.authenticatedActor(principal.getName());
        // Kiểm tra quyền ở server, không dựa vào việc nút tạo Project có bị ẩn ở giao diện hay không.
        if (!"MENTOR".equals(actor.role())) {
            throw new ProjectAccessDeniedException();
        }
        if (bindingResult.hasErrors()) {
            // Có lỗi binding/Bean Validation thì ProjectService chưa được gọi và không có transaction
            // ghi Project. projectForm + BindingResult vẫn ở Model tự động, nên Thymeleaf có thể giữ
            // các giá trị đã nhập và render field-level/global errors.
            model.addAttribute("eligibleInternOptions", eligibleInternOptions());
            model.addAttribute("today", LocalDate.now(clock));
            return "projects/form";
        }
        try {
            // toCommand() chỉ chuyển Web DTO thành application command; nó không save và không gọi DB.
            // actor.userId() lấy từ authenticated Principal, còn initialLeaderUserId là giá trị client
            // gửi lên và sẽ được ProjectService lock/recheck trước khi được ghi vào membership.
            long projectId = projects.create(actor.userId(), projectForm.toCommand());
            // "redirect:" là tín hiệu cho Spring tạo RedirectView/HTTP 302 Location. Browser sau đó
            // gửi GET /projects/{projectId}, tránh refresh POST và hiển thị detail theo read flow mới.
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            // Service rule failure xảy ra sau binding nhưng trước commit hoặc trước khi mutation hoàn
            // tất. Message được gắn vào field an toàn cho form; không render stack trace.
            String field = exception.getMessage() != null
                    && exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("date")
                    ? "startDate" : "initialLeaderUserId";
            bindingResult.rejectValue(
                    field, "project.rule.violation", exception.getMessage());
            // Options phải được query lại vì mỗi request có Model riêng; danh sách có thể đã thay đổi
            // trong lúc user mở form. Input và BindingResult vẫn được giữ để render lại.
            model.addAttribute("eligibleInternOptions", eligibleInternOptions());
            model.addAttribute("today", LocalDate.now(clock));
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
        // DispatcherServlet đã convert path segment thành long trước khi gọi handler. Controller không tự query
        // repository: actorId và projectId được chuyển qua QueryService, nơi áp quyền và đổi Entity thành DTO.
        long actorId = actorId(principal);
        // Hai DTO cùng dùng một actor/project authorization boundary. Model chỉ là dữ liệu cho request render hiện tại;
        // return name là logical view, ViewResolver sẽ tìm projects/detail.html và Thymeleaf xử lý th:*.
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

    /** Renders invitation, exit, transfer, decision, and completion workflows for the authorized viewer. */
    // [Trang Workflows]
    // History được tách khỏi màn thao tác để người dùng không nhầm dữ liệu read-only với việc cần xử lý.
    @GetMapping("/{projectId}/workflows")
    public String workflows(Principal principal, @PathVariable long projectId, Model model) {
        populateWorkflowModel(principal, projectId, model);
        return "projects/workflows";
    }

    /** Renders the retained, read-only Project history for the exact authorized viewer. */
    // [Trang Project History]
    // Chỉ tải detail và retained history; trang này không nhận bất kỳ form mutation nào.
    @GetMapping("/{projectId}/history")
    public String history(Principal principal, @PathVariable long projectId, Model model) {
        // Đây là GET read-only sau khi DispatcherServlet match route literal /{projectId}/history. QueryService áp
        // cùng quyền xem trước khi lấy detail/history; controller không cho phép history bypass authorization.
        long actorId = actorId(principal);
        model.addAttribute("project", pages.detail(actorId, projectId));
        model.addAttribute("projectHistory", pages.history(actorId, projectId));
        return "projects/history";
    }

    // [Gửi lời mời vào Project]
    // Tạo invitation từ dropdown; dữ liệu nhập được giữ lại trong flash nếu service từ chối.
    @PostMapping("/{projectId}/invitations")
    String issueInvitation(
            Principal principal,
            @PathVariable long projectId,
            @RequestParam(name = "invitedInternUserId", required = false) List<String> invitedInternUserIds,
            RedirectAttributes redirectAttributes) {
        List<String> selected = invitedInternUserIds == null ? List.of() : List.copyOf(invitedInternUserIds);
        Map<String, Object> safeInput = selected.size() == 1
                ? Map.of("kind", "invitation", "invitedInternUserId", selected.getFirst())
                : Map.of("kind", "invitation", "invitedInternUserIds", selected);
        return workflowMutation(projectId, redirectAttributes, safeInput, () -> {
            List<Long> ids = selected.stream()
                    .map(value -> requiredLong(value, "Choose valid Interns."))
                    .toList();
            if (ids.size() == 1) {
                projects.issueInvitation(actorId(principal), projectId, ids.getFirst());
            } else {
                projects.issueInvitations(actorId(principal), projectId, ids);
            }
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
    // [Xóa Project PLANNED]
    // Nút xóa chỉ hiện cho Mentor sở hữu bản PLANNED, nhưng service vẫn kiểm tra lại để không tin UI.
    @PostMapping("/{projectId}/delete")
    public String delete(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        try {
            projects.delete(actorId, projectId);
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

    // [Chuẩn bị dữ liệu Workflows]
    // History snapshot vẫn được dùng nội bộ để resolve tên, pending invitation và Task cần transfer.
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
                            // Đây chỉ là điều kiện để hiện quyết định Mentor; service sẽ kiểm tra lại khi bấm nút.
                            item.readyForApproval(),
                            // Nút transfer chỉ hiện cho Leader hiện tại, không áp dụng khi chính Leader đang rời Project.
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
        model.addAttribute("memberLeaveWorkflows", memberLeaveWorkflows);
        model.addAttribute("leaderRemovalWorkflows", leaderRemovalWorkflows);
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
        // Đây là protocol chung cho các POST invitation/exit/Task workflow: SecurityFilterChain/DispatcherServlet
        // đã nhận request, handler đã parse path/form, rồi Supplier gọi ProjectService transaction. Controller không
        // tự mutate entity; nó chỉ quyết định cách đưa kết quả về UI.
        try {
            // Chỉ service mới thay đổi state; controller chỉ chuẩn hóa phản hồi cho cùng trang workflow.
            mutation.get();
            // Flash attribute sống qua đúng một redirect GET /workflows; vì vậy refresh trang không submit lại POST.
            redirectAttributes.addFlashAttribute("message", "Project workflow updated");
        } catch (ProjectRuleViolationException exception) {
            // Chỉ giữ lại dữ liệu form an toàn, không đưa ID hoặc trạng thái nội bộ vào flash message.
            redirectAttributes.addFlashAttribute("projectError", exception.getMessage());
            if (!safeInput.isEmpty()) {
                redirectAttributes.addFlashAttribute("projectInput", safeInput);
            }
        }
        // AccessDenied/exception ngoài rule không bị nuốt ở đây và sẽ đi lên ProjectControllerAdvice; rule đã biết
        // thì được flash rồi redirect để GET dựng lại toàn bộ Model/Thymeleaf state.
        return "redirect:/projects/" + projectId + "/workflows";
    }

    private String invitationMutation(RedirectAttributes redirectAttributes, Supplier<?> mutation) {
        // Invitation inbox dùng cùng PRG boundary nhưng đích là /projects/invitations thay vì workflow của một
        // Project. Supplier vẫn là nơi service mở transaction và kiểm tra invitee/locking.
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
        // Principal.getName() là định danh do SecurityContext cung cấp (trong app là email). QueryService/AccountService
        // đổi nó thành numeric Account ID để mọi Project service method dùng cùng một actor key; nếu account không còn
        // tồn tại/ACTIVE, boundary ném ProjectAccessDeniedException trước khi truy cập dữ liệu Project.
        return pages.authenticatedUserId(principal.getName());
    }
}
