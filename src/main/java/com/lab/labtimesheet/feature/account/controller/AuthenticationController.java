package com.lab.labtimesheet.feature.account.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders the project-owned form-login page used by Spring Security. */
@Controller
class AuthenticationController {
    @GetMapping("/login")
    String login() {
        return "accounts/login";
    }
}
