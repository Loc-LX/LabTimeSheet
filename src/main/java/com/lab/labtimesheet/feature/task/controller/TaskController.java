package com.lab.labtimesheet.feature.task.controller;

import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.EditTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCorrection;
import com.lab.labtimesheet.feature.task.model.dto.TaskCreateForm;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskEditForm;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.service.TaskService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
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

/**
 * Serves authenticated Task list, create, detail, status, and comment pages.
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
        return renderDetails(authentication, projectId, taskId, model, null);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/status")
    String changeStatus(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam TaskStatus status) {
        taskService.changeStatus(authentication.getName(), projectId, taskId, status);
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/reassign")
    String reassign(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam long assigneeMembershipId) {
        taskService.reassign(
                authentication.getName(), projectId, taskId, assigneeMembershipId);
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/comments")
    String addComment(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam String body) {
        taskService.addComment(authentication.getName(), projectId, taskId, body);
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/work-logs")
    String logWork(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam LocalDate workDate,
            @RequestParam int minutes,
            @RequestParam(required = false) String note,
            Model model) {
        try {
            taskService.logWork(
                    authentication.getName(),
                    projectId,
                    taskId,
                    new LogWorkCommand(workDate, minutes, note));
        } catch (TaskValidationException exception) {
            model.addAttribute("workLogError", exception.getMessage());
            return renderDetails(authentication, projectId, taskId, model, "work-log");
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/work-logs/{logId}")
    String correctWorkLog(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @PathVariable long logId,
            @RequestParam int minutes,
            @RequestParam(required = false) String note,
            Model model) {
        try {
            taskService.correctWorkLog(
                    authentication.getName(),
                    projectId,
                    taskId,
                    logId,
                    new LogWorkCorrection(minutes, note));
        } catch (TaskValidationException exception) {
            model.addAttribute("workLogError", exception.getMessage());
            return renderDetails(authentication, projectId, taskId, model, "work-log");
        }
        return detailsRedirect(projectId, taskId);
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}/history")
    String historical(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            Model model) {
        TaskDetails details = taskService.historicalDetails(
                authentication.getName(), projectId, taskId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("details", details);
        return "tasks/detail";
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}/edit")
    String editForm(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            Model model) {
        TaskDetails details = taskService.details(authentication.getName(), projectId, taskId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("taskForm", new TaskEditForm(
                details.task().title(), details.task().description(), details.task().dueDate()));
        model.addAttribute("taskId", taskId);
        return "tasks/form";
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/edit")
    String edit(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @Valid @ModelAttribute("taskForm") TaskEditForm form,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", projectId);
            model.addAttribute("taskId", taskId);
            return "tasks/form";
        }
        try {
            taskService.edit(
                    authentication.getName(),
                    new EditTaskCommand(
                            projectId, taskId, form.title(), form.description(), form.dueDate()));
        } catch (TaskValidationException exception) {
            bindingResult.rejectValue("dueDate", "task.dueDate", exception.getMessage());
            model.addAttribute("projectId", projectId);
            model.addAttribute("taskId", taskId);
            return "tasks/form";
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/delete")
    String delete(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId) {
        taskService.softDelete(authentication.getName(), projectId, taskId);
        return "redirect:/projects/%d/tasks/%d/history".formatted(projectId, taskId);
    }

    private void populateForm(String actorEmail, long projectId, Model model) {
        model.addAttribute("projectId", projectId);
        model.addAttribute("assignees", taskService.assignmentChoices(actorEmail, projectId));
    }

    private String renderDetails(
            Authentication authentication,
            long projectId,
            long taskId,
            Model model,
            String focusedForm) {
        TaskDetails details = taskService.details(authentication.getName(), projectId, taskId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("details", details);
        model.addAttribute("statuses", Arrays.stream(TaskStatus.values())
                .filter(details.task().status()::canTransitionTo)
                .toList());
        if (focusedForm != null) {
            model.addAttribute("focusedForm", focusedForm);
        }
        if (details.canReassign()) {
            model.addAttribute("assignees", taskService.assignmentChoices(authentication.getName(), projectId));
        }
        return "tasks/detail";
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
