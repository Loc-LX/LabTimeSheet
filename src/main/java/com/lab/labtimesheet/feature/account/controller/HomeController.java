package com.lab.labtimesheet.feature.account.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Maps the authenticated application root to the shared role-aware dashboard. */
@Controller
class HomeController {
    @GetMapping("/")
    String home() {
        return "redirect:/dashboard";
    }
}
