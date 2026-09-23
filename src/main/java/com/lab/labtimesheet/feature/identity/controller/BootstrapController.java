package com.lab.labtimesheet.feature.identity.controller;

import com.lab.labtimesheet.feature.identity.model.dto.BootstrapForm;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/** Renders and processes the one-time first-Admin installation form. */
@Controller
@RequestMapping("/bootstrap")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class  BootstrapController {
    private final BootstrapService bootstrap;

    @GetMapping
    String form(Model model) {
        requireOpen();
        if (!model.containsAttribute("bootstrapForm")) {
            model.addAttribute("bootstrapForm", new BootstrapForm());
        }
        return "bootstrap/form";
    }

    @PostMapping
    String create(@Valid @ModelAttribute("bootstrapForm") BootstrapForm form, BindingResult bindingResult) {
        requireOpen();
        if (bindingResult.hasErrors()) {
            form.setPassword(null);
            return "bootstrap/form";
        }
        try {
            if (bootstrap.bootstrap(form.getEmail(), form.getDisplayName(), form.getPassword())
                    == BootstrapService.BootstrapOutcome.CREATED) {
                return "redirect:/admin/smtp?onboarding";
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException validation) {
            bindingResult.reject("bootstrap.invalid", validation.getMessage());
            form.setPassword(null);
            return "bootstrap/form";
        }
    }

    private void requireOpen() {
        if (bootstrap.isInitialized()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
