package com.lab.labtimesheet.feature.task.controller;

import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskCreateForm;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Locale;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Serves authenticated Task list, definition, reassignment, work-log, status, and comment pages.
 *
 * <p>The controller delegates record visibility and every mutation authorization decision to
 * {@link TaskService}. A denied or guessed Project/Task identifier therefore retains the service's
 * non-disclosing HTTP 404 contract. Bean and due-date validation failures return the create form;
 * successful mutations use redirects to prevent duplicate submissions.
 */
@Controller
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/projects/{projectId}/tasks")
    String list(Authentication authentication, @PathVariable long projectId, Model model) {
        TaskListView taskList = taskService.list(authentication.getName(), projectId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("taskList", taskList);
        model.addAttribute("progressLabel", progressLabel(taskList.progress()));
        return "tasks/list";
    }

    @GetMapping("/projects/{projectId}/tasks/new")
    String createForm(Authentication authentication, @PathVariable long projectId, Model model) {
        model.addAttribute("taskForm", new TaskCreateForm("", "", null, null));
        populateForm(authentication.getName(), projectId, model);
        return "tasks/form";
    }

    @PostMapping("/projects/{projectId}/tasks")
    String create(
            Authentication authentication,
            @PathVariable long projectId,
            @Valid @ModelAttribute("taskForm") TaskCreateForm form,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            populateForm(authentication.getName(), projectId, model);
            return "tasks/form";
        }
        TaskView task;
        try {
            task = taskService.create(
                    authentication.getName(),
                    new CreateTaskCommand(
                            projectId,
                            form.assigneeMembershipId(),
                            form.title(),
                            form.description(),
                            form.dueDate()));
        } catch (TaskValidationException exception) {
            bindingResult.rejectValue("dueDate", "task.dueDate", exception.getMessage());
            populateForm(authentication.getName(), projectId, model);
            return "tasks/form";
        }
        return "redirect:/projects/%d/tasks/%d".formatted(projectId, task.id());
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}")
    String details(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            Model model) {
        TaskDetails details = taskService.details(authentication.getName(), projectId, taskId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("details", details);
        model.addAttribute("statuses", Arrays.stream(TaskStatus.values())
                .filter(details.task().status()::canTransitionTo)
                .toList());
        if (details.canReassign()) {
            model.addAttribute("assignees", taskService.assignmentChoices(authentication.getName(), projectId));
        }
        return "tasks/detail";
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/edit")
    String edit(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.edit(authentication.getName(), projectId, taskId, title, description, dueDate);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskEditInput", Map.of(
                    "title", title,
                    "description", description == null ? "" : description,
                    "dueDate", dueDate == null ? "" : dueDate.toString()));
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/delete")
    String delete(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.softDelete(authentication.getName(), projectId, taskId);
            return "redirect:/projects/%d/tasks".formatted(projectId);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            return detailsRedirect(projectId, taskId);
        }
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/reassign")
    String reassign(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam long assigneeMembershipId,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.reassign(authentication.getName(), projectId, taskId, assigneeMembershipId);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskReassignInput", Map.of(
                    "assigneeMembershipId", assigneeMembershipId));
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/work-logs")
    String addWorkLog(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam int minutes,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.addWorkLog(authentication.getName(), projectId, taskId, workDate, minutes, note);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskWorkLogInput", Map.of(
                    "workDate", workDate.toString(),
                    "minutes", minutes,
                    "note", note == null ? "" : note));
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/work-logs/{workLogId}")
    String correctWorkLog(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @PathVariable long workLogId,
            @RequestParam int minutes,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.correctWorkLog(authentication.getName(), projectId, workLogId, minutes, note);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskWorkLogCorrectionInput", Map.of(
                    "workLogId", workLogId,
                    "minutes", minutes,
                    "note", note == null ? "" : note));
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/status")
    String changeStatus(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam TaskStatus status,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.changeStatus(authentication.getName(), projectId, taskId, status);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskStatusInput", Map.of("status", status.name()));
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/comments")
    String addComment(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam String body,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.addComment(authentication.getName(), projectId, taskId, body);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskCommentInput", Map.of("body", body));
        }
        return detailsRedirect(projectId, taskId);
    }

    private void populateForm(String actorEmail, long projectId, Model model) {
        model.addAttribute("projectId", projectId);
        model.addAttribute("assignees", taskService.assignmentChoices(actorEmail, projectId));
    }

    private static String detailsRedirect(long projectId, long taskId) {
        return "redirect:/projects/%d/tasks/%d".formatted(projectId, taskId);
    }

    private static String progressLabel(TaskProgress progress) {
        return progress.completionPercentage().isEmpty()
                ? "N/A"
                : String.format(Locale.ROOT, "%.1f%%", progress.completionPercentage().getAsDouble());
    }
}
