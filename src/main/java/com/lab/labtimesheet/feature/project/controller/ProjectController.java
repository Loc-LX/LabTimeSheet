package com.lab.labtimesheet.feature.project.controller;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberForm;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembersForm;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
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
 * Phục vụ các trang Project hiển thị phía máy chủ cho người dùng đã xác thực và gắn dữ liệu vào biểu
 * mẫu thay đổi Project.
 *
 * <p>Service Project vẫn là nơi quyết định về sở hữu, thành viên, vòng đời và kiểm tra trong
 * transaction. Lỗi nghiệp vụ đã biết được trả về view an toàn ban đầu, còn lỗi phân quyền được
 * chuyển cho {@code ProjectControllerAdvice} để không tiết lộ mã tài nguyên.
 *
 * <p>Truy vết mã UI Project: {@code I1-PRJ-01} tạo Project, {@code I1-PRJ-02} thêm member,
 * {@code I1-PRJ-03} đổi Leader, {@code I1-PRJ-04} kích hoạt, {@code I1-PRJ-05} hiển thị các
 * trang Project; {@code I2-PRJ-01}–{@code I2-PRJ-05} và {@code I2-PRJ-06}
 * giữ lịch sử, handoff/xóa Leader an toàn và đọc lịch sử hoàn tất.
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
     * [I1-PRJ-01, I1-PRJ-05, I2-PRJ-06] Chỉ liệt kê Project mà người thực hiện đã xác thực được xem và chỉ hiển thị chức năng tạo
     * Project cho Mentor.
     *
     * @param principal người dùng đã xác thực
     * @param model model của phản hồi
     * @return view danh sách Project
     */
    @GetMapping
    public String list(Principal principal, Model model) {
        var actor = pages.authenticatedActor(principal.getName());
        model.addAttribute("projects", pages.listVisible(actor.userId()));
        model.addAttribute("canCreateProject", "MENTOR".equals(actor.role()));
        return "projects/list";
    }

    /**
     * [I1-PRJ-01, I1-PRJ-05] Mở biểu mẫu tạo Project cho Mentor đã xác thực.
     *
     * @param principal người dùng đã xác thực
     * @param model model của phản hồi
     * @return view tạo Project
     * @throws ProjectAccessDeniedException khi người thực hiện không phải Mentor đang hoạt động
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
     * [I1-PRJ-01, I1-PRJ-05] Tạo Project hoặc hiển thị lại biểu mẫu với dữ liệu an toàn đã nhập khi kiểm tra thất bại.
     *
     * @param principal người dùng đã xác thực
     * @param projectForm dữ liệu biểu mẫu trên trình duyệt sau khi gắn dữ liệu
     * @param bindingResult kết quả gắn dữ liệu và kiểm tra nghiệp vụ
     * @param model model của phản hồi khi kiểm tra thất bại
     * @return redirect tới Project vừa tạo hoặc view biểu mẫu khi kiểm tra thất bại
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
     * [I1-PRJ-05, I2-PRJ-06] Hiển thị chi tiết Project đã phân quyền mà không tiết lộ mã được đoán ngẫu nhiên.
     *
     * @param principal người dùng đã xác thực
     * @param projectId mã Project được yêu cầu
     * @param model model của phản hồi
     * @return view chi tiết Project
     */
    @GetMapping("/{projectId}")
    public String detail(Principal principal, @PathVariable long projectId, Model model) {
        model.addAttribute("project", pages.detail(actorId(principal), projectId));
        return "projects/detail";
    }

    /**
     * [I1-PRJ-04, I1-PRJ-05] Kích hoạt Project Planned hoặc hiển thị lại chi tiết với lỗi vòng đời an toàn.
     *
     * @param principal người dùng đã xác thực
     * @param projectId mã Project cần kích hoạt
     * @param model model của phản hồi khi kích hoạt bị từ chối
     * @return redirect về chi tiết khi thành công hoặc view chi tiết khi vi phạm quy tắc
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
     * [I1-PRJ-02, I1-PRJ-05, I2-PRJ-06] Hiển thị các membership đang hoạt động; lịch sử được mở
     * qua nút History ở trang riêng.
     *
     * @param principal người dùng đã xác thực
     * @param projectId mã Project được yêu cầu
     * @param model model của phản hồi
     * @return view danh sách thành viên hiện tại
     */
    @GetMapping("/{projectId}/members")
    public String members(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        populateMembersModel(actorId, projectId, model);
        model.addAttribute("projectMembersForm", new ProjectMembersForm());
        return "projects/members";
    }

    /** [I2-PRJ-06] Hiển thị read-only toàn bộ lịch sử vào/ra của membership Project. */
    @GetMapping("/{projectId}/members/history")
    public String memberHistory(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        model.addAttribute("project", pages.detail(actorId, projectId));
        // Lịch sử riêng được hiển thị từ mới đến cũ; danh sách active không dùng thứ tự này.
        model.addAttribute("members", pages.members(actorId, projectId).stream()
                .sorted(Comparator.comparing(ProjectMemberView::joinedAt)
                        .reversed()
                        .thenComparing(ProjectMemberView::membershipId, Comparator.reverseOrder()))
                .toList());
        return "projects/members-history";
    }

    /**
     * [I1-PRJ-02, I1-PRJ-05] Nguyên tử thêm toàn bộ Intern đủ điều kiện đã chọn hoặc hiển thị lại lịch sử thành viên với
     * các lựa chọn vẫn còn đủ điều kiện và số lựa chọn không còn khả dụng.
     *
     * @param principal người dùng đã xác thực
     * @param projectId mã Project sở hữu
     * @param membersForm lựa chọn Intern sau khi gắn dữ liệu và kiểm tra
     * @param bindingResult kết quả gắn dữ liệu và kiểm tra nghiệp vụ
     * @param model model của phản hồi khi thất bại
     * @return redirect về thành viên khi thành công hoặc view thành viên khi kiểm tra thất bại
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
     * [I2-PRJ-04] Chuyển Task chưa hoàn thành sang Leader hiện tại rồi đóng membership member
     * thường; khi lỗi thì nạp lại trang cùng thông báo và không tự ý đổi dữ liệu biểu mẫu thêm member.
     *
     * @param principal Mentor đang đăng nhập
     * @param projectId mã Project
     * @param internUserId mã Intern hiện tại cần loại
     * @param model model dùng khi hiển thị lỗi nghiệp vụ
     * @return redirect về lịch sử member khi thành công hoặc view member khi thất bại
     */
    @PostMapping("/{projectId}/members/{internUserId}/remove")
    public String removeMember(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long internUserId,
            Model model) {
        long actorId = actorId(principal);
        try {
            projects.removeMember(actorId, projectId, internUserId);
            return "redirect:/projects/" + projectId + "/members";
        } catch (ProjectRuleViolationException exception) {
            populateMembersModel(actorId, projectId, model);
            model.addAttribute("projectMembersForm", new ProjectMembersForm());
            model.addAttribute("memberRemovalError", exception.getMessage());
            return "projects/members";
        }
    }

    /**
     * [I2-PRJ-05] Hoàn tất Project sau khi service đã kiểm tra toàn bộ Task và đóng các interval.
     * Lỗi nghiệp vụ được hiển thị lại ở trang chi tiết, còn quyền sở hữu vẫn được kiểm tra trong
     * ProjectService.
     *
     * @param principal Mentor đang đăng nhập
     * @param projectId mã Project cần hoàn tất
     * @param model model hiển thị lỗi
     * @return redirect về chi tiết hoặc view chi tiết khi bị từ chối
     */
    @PostMapping("/{projectId}/complete")
    public String complete(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        try {
            projects.complete(actorId, projectId);
            return "redirect:/projects/" + projectId;
        } catch (ProjectRuleViolationException exception) {
            model.addAttribute("project", pages.detail(actorId, projectId));
            model.addAttribute("projectError", exception.getMessage());
            return "projects/detail";
        }
    }

    /**
     * [I1-PRJ-03, I1-PRJ-05, I2-PRJ-01, I2-PRJ-06] Hiển thị Leader hiện tại và biểu mẫu thay đổi
     * Leader chỉ dành cho Mentor sở hữu khi Project còn cho phép thay đổi. Lịch sử nhiệm kỳ được
     * mở qua nút History ở trang riêng.
     *
     * @param principal người dùng đã xác thực
     * @param projectId mã Project được yêu cầu
     * @param model model dùng để hiển thị phản hồi
     * @return view Leader hiện tại
     */
    @GetMapping("/{projectId}/leadership")
    public String leadership(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        var currentTermId = populateLeadershipModel(actorId, projectId, model);
        model.addAttribute("projectMemberForm", new ProjectMemberForm(null, currentTermId));
        return "projects/leadership";
    }

    /** [I2-PRJ-06] Hiển thị read-only toàn bộ lịch sử các nhiệm kỳ Leader của Project. */
    @GetMapping("/{projectId}/leadership/history")
    public String leadershipHistory(Principal principal, @PathVariable long projectId, Model model) {
        long actorId = actorId(principal);
        model.addAttribute("project", pages.detail(actorId, projectId));
        // Bảo đảm bản ghi mới nhất đứng trước ngay tại trang History, độc lập với dữ liệu từ service.
        model.addAttribute("leadership", pages.leadership(actorId, projectId).stream()
                .sorted(Comparator.comparing(ProjectLeadershipTermView::startedAt)
                        .reversed()
                        .thenComparing(ProjectLeadershipTermView::id, Comparator.reverseOrder()))
                .toList());
        return "projects/leadership-history";
    }

    /**
     * [I1-PRJ-03, I1-PRJ-05, I2-PRJ-01, I2-PRJ-02] Bổ nhiệm một thành viên hiện tại đủ điều kiện làm Leader mới; nếu thất bại thì hiển thị lại
     * lịch sử Leader cùng dữ liệu người dùng đã nhập.
     *
     * @param principal người dùng đã xác thực
     * @param projectId mã Project sở hữu
     * @param memberForm lựa chọn Leader thay thế sau khi gắn dữ liệu và kiểm tra
     * @param bindingResult kết quả gắn dữ liệu và kiểm tra nghiệp vụ
     * @param model model dùng khi xử lý thất bại
     * @return redirect về lịch sử Leader khi thành công, hoặc view lịch sử khi kiểm tra thất bại
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
                projects.changeLeader(
                        actorId,
                        projectId,
                        memberForm.expectedLeadershipTermId(),
                        memberForm.internUserId());
                return "redirect:/projects/" + projectId + "/leadership";
            } catch (ProjectRuleViolationException exception) {
                bindingResult.rejectValue("internUserId", "project.leader.ineligible", exception.getMessage());
            }
        }
        populateLeadershipModel(actorId, projectId, model);
        return "projects/leadership";
    }

    /**
     * [I2-PRJ-03] Thay Leader trước rồi đóng membership Leader cũ trong một transaction; chỉ
     * Mentor sở hữu mới đi qua được phân quyền ở ProjectService.
     *
     * @param principal người dùng đã xác thực
     * @param projectId mã Project cần thay Leader và xóa membership cũ
     * @param memberForm replacement cùng token nhiệm kỳ hiện tại
     * @param bindingResult lỗi binding/validation của form
     * @param model model dùng khi hiển thị lại form lỗi
     * @return redirect sau khi thành công hoặc leadership view khi thất bại
     */
    @PostMapping("/{projectId}/leadership/remove")
    public String removeLeader(
            Principal principal,
            @PathVariable long projectId,
            @Valid @ModelAttribute("projectMemberForm") ProjectMemberForm memberForm,
            BindingResult bindingResult,
            Model model) {
        long actorId = actorId(principal);
        if (!bindingResult.hasErrors()) {
            try {
                projects.removeLeader(
                        actorId,
                        projectId,
                        memberForm.expectedLeadershipTermId(),
                        memberForm.internUserId());
                return "redirect:/projects/" + projectId + "/leadership";
            } catch (ProjectRuleViolationException exception) {
                bindingResult.rejectValue("internUserId", "project.leader.removal", exception.getMessage());
            }
        }
        populateLeadershipModel(actorId, projectId, model);
        return "projects/leadership";
    }

    /** Nạp chi tiết, membership hiện tại và danh sách Intern còn có thể thêm vào model. */
    private List<EligibleInternOption> populateMembersModel(long actorId, long projectId, Model model) {
        var project = pages.detail(actorId, projectId);
        var members = pages.members(actorId, projectId).stream()
                .filter(member -> member.leftAt() == null)
                .toList();
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

    /**
     * Nạp Leader hiện tại và trả về mã nhiệm kỳ dùng làm token chống ghi đè từ form cũ. Project đã
     * hoàn tất cố ý không trả token vì không hiển thị biểu mẫu thay đổi.
     */
    private Long populateLeadershipModel(long actorId, long projectId, Model model) {
        var project = pages.detail(actorId, projectId);
        var leadership = pages.leadership(actorId, projectId).stream()
                .filter(term -> term.endedAt() == null)
                .toList();
        model.addAttribute("project", project);
        model.addAttribute("leadership", leadership);
        if (project.canManage()) {
            Set<Long> replacementIds = pages.members(actorId, projectId).stream()
                    .filter(member -> member.leftAt() == null && !member.currentLeader())
                    .map(member -> member.internUserId())
                    .collect(Collectors.toUnmodifiableSet());
            model.addAttribute("eligibleInternOptions", eligibleInternOptions().stream()
                    .filter(option -> replacementIds.contains(option.userId()))
                    .toList());
        }
        return leadership.stream()
                .filter(term -> term.endedAt() == null)
                .map(term -> term.id())
                .findFirst()
                .orElse(null);
    }

    /** Lấy danh sách Intern hiện đủ điều kiện tại ngày hiện tại của server. */
    private List<EligibleInternOption> eligibleInternOptions() {
        return accounts.eligibleInternOptions(LocalDate.now(clock));
    }

    /** Đổi danh tính đăng nhập trong yêu cầu thành mã Account dùng ở service Project. */
    private long actorId(Principal principal) {
        return pages.authenticatedUserId(principal.getName());
    }
}
