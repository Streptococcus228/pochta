package com.pochta.web;

import com.pochta.dto.ParcelAdminDto;
import com.pochta.model.Parcel;
import com.pochta.model.User;
import com.pochta.service.ParcelService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ParcelService parcelService;

    public AdminController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping
    public String adminPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");

        if (user == null || !"ADMIN".equals(user.getRole())) {
            return "redirect:/login";
        }

        // Використовуємо DTO — це вирішує LazyInitializationException
        List<ParcelAdminDto> parcels = parcelService.getAllParcelsForAdmin();

        model.addAttribute("parcels", parcels);
        model.addAttribute("totalParcels", parcels.size());
        model.addAttribute("deliveredCount", parcels.stream()
                .filter(p -> "DELIVERED".equals(p.getStatus())).count());
        model.addAttribute("inTransitCount", parcels.stream()
                .filter(p -> "IN_TRANSIT".equals(p.getStatus())).count());

        return "admin";
    }

    @PostMapping("/reset")
    public String resetAllParcels(HttpSession session) {
        User user = (User) session.getAttribute("currentUser");

        if (user == null || !"ADMIN".equals(user.getRole())) {
            return "redirect:/login";
        }

        parcelService.resetAllParcels();
        return "redirect:/admin";
    }
}