package com.pochta.web;

import com.pochta.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RedirectController {

    @GetMapping("/redirectAfterLogin")
    public String redirectAfterLogin(HttpSession session) {
        User user = (User) session.getAttribute("currentUser");

        if (user != null && "ADMIN".equals(user.getRole())) {
            return "redirect:/admin";
        }
        return "redirect:/";
    }
}