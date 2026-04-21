package com.pochta.web;

import com.pochta.dto.ParcelHistoryDto;
import com.pochta.model.User;
import com.pochta.service.AuthService;
import com.pochta.service.BranchService;
import com.pochta.service.ParcelService;
import com.pochta.service.PricingService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    private final AuthService authService;
    private final ParcelService parcelService;
    private final PricingService pricingService;
    private final BranchService branchService;

    public WebController(AuthService authService, ParcelService parcelService, PricingService pricingService, BranchService branchService) {
        this.authService = authService;
        this.parcelService = parcelService;
        this.pricingService = pricingService;
        this.branchService = branchService;
    }

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");
        if (user != null) model.addAttribute("currentUser", user);
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session) {

        var userOpt = authService.login(username, password);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            session.setAttribute("currentUser", user);

            // Важливо! Перекидаємо на спеціальний редирект
            return "redirect:/redirectAfterLogin";
        }

        return "redirect:/login?error=true";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String fullName,
                           @RequestParam String email,
                           HttpSession session) {

        User user = authService.register(username, password, fullName, email);
        if (user != null) {
            session.setAttribute("currentUser", user);
            return "redirect:/create";
        }
        return "redirect:/register?error=true";
    }

    @GetMapping("/create")
    public String createPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";
        model.addAttribute("currentUser", user);
        model.addAttribute("branches", branchService.getAllBranches());
        return "create";
    }

    @PostMapping("/create")
    public String createParcel(@RequestParam String fromBranch,
                               @RequestParam String toBranch,
                               @RequestParam double weight,
                               HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        parcelService.createParcel(user.getId(), fromBranch, toBranch, weight);
        return "redirect:/history";
    }

    @GetMapping("/history")
    public String historyPage(HttpSession session, Model model) {
        log.info("Accessing /history page");
        User user = (User) session.getAttribute("currentUser");
        if (user == null) {
            log.warn("User not found in session, redirecting to login");
            return "redirect:/login";
        }

        try {
            model.addAttribute("currentUser", user);
            model.addAttribute("branches", branchService.getAllBranches());
            log.info("History page loaded successfully for user: {}", user.getUsername());
            return "history";
        } catch (Exception e) {
            log.error("Error loading history page for user: {}", user.getUsername(), e);
            return "redirect:/?error=history";
        }
    }

    @GetMapping("/api/history")
    @ResponseBody
    public List<ParcelHistoryDto> getUserHistory(HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null) return List.of();
        return parcelService.getParcelsHistoryByUserId(user.getId());
    }

    @GetMapping("/api/calculate-price")
    @ResponseBody
    public Map<String, Object> calculatePrice(@RequestParam String from,
                                              @RequestParam String to,
                                              @RequestParam double weight) {
        double cost = pricingService.calculateCost(from, to, weight);
        return Map.of("cost", cost);
    }

    @PostMapping("/parcels/delete/{id}")
    public String deleteParcel(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        if (user != null) {
            parcelService.deleteParcel(id);
        }
        return "redirect:/history";
    }
}