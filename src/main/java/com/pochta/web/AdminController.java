package com.pochta.web;

import com.pochta.dto.ParcelAdminDto;
import com.pochta.model.User;
import com.pochta.service.ParcelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
public class AdminController {

    private final ParcelService parcelService;

    public AdminController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() != null && user.getRole().equalsIgnoreCase("ADMIN");
    }

    @GetMapping({"/admin", "/admin.html"})
    public String adminPage(HttpSession session, Model model) {

        User user = (User) session.getAttribute("currentUser");

        if (!isAdmin(user)) {
            return "redirect:/login";
        }

        List<ParcelAdminDto> parcels = parcelService.getAllParcelsForAdmin();

        long deliveredCount = parcels.stream()
                .filter(p -> "DELIVERED".equalsIgnoreCase(p.getStatus()))
                .count();

        long inTransitCount = parcels.stream()
                .filter(p -> "IN_TRANSIT".equalsIgnoreCase(p.getStatus()))
                .count();

        model.addAttribute("parcels", parcels);
        model.addAttribute("totalParcels", parcels.size());
        model.addAttribute("deliveredCount", deliveredCount);
        model.addAttribute("inTransitCount", inTransitCount);

        return "admin";
    }

    @PostMapping({"/admin/reset", "/admin.html/reset"})
    public String resetAllParcels(HttpSession session, HttpServletRequest request) {

        User user = (User) session.getAttribute("currentUser");

        if (!isAdmin(user)) {
            return "redirect:/login";
        }

        parcelService.resetAllParcels();

        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/admin.html")) {
            return "redirect:/admin.html";
        }
        return "redirect:/admin";
    }
}