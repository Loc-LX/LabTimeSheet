package com.lab.labtimesheet.feature.task.controller;

import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.feature.task.model.dto.TaskCreateForm;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Locale;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
                            form.dueDate(),
                            form.estimatedMinutes()));
        } catch (TaskValidationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase(Locale.ROOT).contains("due date")) {
                bindingResult.rejectValue("dueDate", "task.dueDate", exception.getMessage());
            } else if (exception.getMessage() != null && exception.getMessage().toLowerCase(Locale.ROOT).contains("estimate")) {
                bindingResult.rejectValue("estimatedMinutes", "task.estimatedMinutes", exception.getMessage());
            } else {
                bindingResult.reject("task.invalid", exception.getMessage());
            }
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
            @RequestParam long expectedVersion,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String dueDate,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.edit(
                    authentication.getName(),
                    projectId,
                    taskId,
                    expectedVersion,
                    title,
                    description,
                    optionalDate(dueDate, "Enter a valid due date."));
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskEditInput", Map.of(
                    "title", title,
                    "description", description == null ? "" : description,
                    "dueDate", dueDate == null ? "" : dueDate));
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/estimate")
    String estimate(Authentication authentication, @PathVariable long projectId, @PathVariable long taskId,
            @RequestParam long expectedVersion, @RequestParam(required = false) Integer estimatedMinutes,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.estimate(authentication.getName(), projectId, taskId, expectedVersion, estimatedMinutes);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/delete")
    String delete(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam long expectedVersion,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.softDelete(authentication.getName(), projectId, taskId, expectedVersion);
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
            @RequestParam long expectedVersion,
            @RequestParam String assigneeMembershipId,
            @RequestParam(required = false) String remainingMinutes,
            @RequestParam(required = false) String forecastNote,
            RedirectAttributes redirectAttributes) {
        try {
            long recipient = requiredLong(assigneeMembershipId, "Choose a valid assignee.");
            Integer parsedRemaining = optionalInt(remainingMinutes, "Enter valid remaining effort minutes.");
            if (parsedRemaining == null && (forecastNote == null || forecastNote.isBlank())) {
                taskService.reassign(authentication.getName(), projectId, taskId, expectedVersion, recipient);
            } else {
                taskService.reassign(authentication.getName(), projectId, taskId, expectedVersion, recipient,
                        forecastInput(parsedRemaining, forecastNote));
            }
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskReassignInput", Map.of(
                    "assigneeMembershipId", assigneeMembershipId,
                    "remainingMinutes", remainingMinutes == null ? "" : remainingMinutes,
                    "forecastNote", forecastNote == null ? "" : forecastNote));
        }
        return detailsRedirect(projectId, taskId);
    }

    /**
     * Appends one Leader-authored correction to the forecast identified by the nested route.
     *
     * <p>The predecessor identifier is intentionally part of the URL so a browser replay cannot
     * silently overwrite whichever forecast happens to be latest. TaskService rechecks the
     * assignment context, incoming work boundary, current Leader, and append-only successor rule
     * while holding the Project/Task mutation locks.</p>
     *
     * @param authentication authenticated current Leader
     * @param projectId owning Project identifier
     * @param taskId unfinished Task identifier
     * @param forecastId expected predecessor forecast identifier
     * @param remainingMinutes replacement remaining effort text
     * @param reason mandatory correction reason
     * @param redirectAttributes safe validation flash state
     * @return detail redirect after success or validation rejection
     */
    @PostMapping("/projects/{projectId}/tasks/{taskId}/forecasts/{forecastId}/correct")
    String correctForecast(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @PathVariable long forecastId,
            @RequestParam(required = false) String remainingMinutes,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.correctForecast(
                    authentication.getName(),
                    projectId,
                    taskId,
                    forecastId,
                    optionalInt(remainingMinutes, "Enter valid remaining effort minutes."),
                    reason);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskForecastCorrectionInput", Map.of(
                    "forecastId", forecastId,
                    "remainingMinutes", remainingMinutes == null ? "" : remainingMinutes,
                    "reason", reason == null ? "" : reason));
        }
        return detailsRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/work-logs")
    String addWorkLog(
            Authentication authentication,
            @PathVariable long projectId,
            @PathVariable long taskId,
            @RequestParam long expectedTaskVersion,
            @RequestParam String workDate,
            @RequestParam String minutes,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.addWorkLog(
                    authentication.getName(),
                    projectId,
                    taskId,
                    expectedTaskVersion,
                    requiredDate(workDate, "Enter a valid work date and minutes."),
                    requiredInt(minutes, "Enter a valid work date and minutes."),
                    note);
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskWorkLogInput", Map.of(
                    "workDate", workDate,
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
            @RequestParam long expectedTaskVersion,
            @RequestParam long expectedWorkLogVersion,
            @RequestParam String minutes,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.correctWorkLog(
                    authentication.getName(),
                    projectId,
                    workLogId,
                    expectedTaskVersion,
                    expectedWorkLogVersion,
                    requiredInt(minutes, "Enter valid corrected minutes."),
                    note);
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
            @RequestParam long expectedVersion,
            @RequestParam String status,
            RedirectAttributes redirectAttributes) {
        try {
            taskService.changeStatus(
                    authentication.getName(),
                    projectId,
                    taskId,
                    expectedVersion,
                    requiredStatus(status));
        } catch (TaskValidationException exception) {
            redirectAttributes.addFlashAttribute("taskError", exception.getMessage());
            redirectAttributes.addFlashAttribute("taskStatusInput", Map.of("status", status));
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
        model.addAttribute("canSetEstimate", taskService.canSetEstimateOnCreate(actorEmail, projectId));
    }

    private static String detailsRedirect(long projectId, long taskId) {
        return "redirect:/projects/%d/tasks/%d".formatted(projectId, taskId);
    }

    private static LocalDate optionalDate(String value, String errorMessage) {
        return value == null || value.isBlank() ? null : requiredDate(value, errorMessage);
    }

    private static LocalDate requiredDate(String value, String errorMessage) {
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new TaskValidationException(errorMessage);
        }
    }

    private static int requiredInt(String value, String errorMessage) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            throw new TaskValidationException(errorMessage);
        }
    }

    private static Integer optionalInt(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredInt(value, errorMessage);
    }

    private static RemainingEffortForecastInput forecastInput(Integer remainingMinutes, String note) {
        return new RemainingEffortForecastInput(remainingMinutes, note);
    }

    private static long requiredLong(String value, String errorMessage) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new TaskValidationException(errorMessage);
        }
    }

    private static TaskStatus requiredStatus(String value) {
        try {
            return TaskStatus.valueOf(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new TaskValidationException("Choose a valid Task status.");
        }
    }

    private static String progressLabel(TaskProgress progress) {
        return progress.completionPercentage().isEmpty()
                ? "N/A"
                : String.format(Locale.ROOT, "%.1f%%", progress.completionPercentage().getAsDouble());
    }
}
