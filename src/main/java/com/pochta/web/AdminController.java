package com.pochta.web;

import com.pochta.model.Parcel;
import com.pochta.service.ParcelService;
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
    public String adminPage(Model model) {
        List<Parcel> allParcels = parcelService.getAllParcels();

        model.addAttribute("parcels", allParcels);
        model.addAttribute("totalParcels", allParcels.size());
        model.addAttribute("deliveredCount", allParcels.stream()
                .filter(p -> "DELIVERED".equals(p.getStatus().name())).count());
        model.addAttribute("inTransitCount", allParcels.stream()
                .filter(p -> "IN_TRANSIT".equals(p.getStatus().name())).count());

        return "admin";
    }

    @PostMapping("/reset")
    public String resetAllParcels() {
        parcelService.resetAllParcels();
        return "redirect:/admin";
    }
}