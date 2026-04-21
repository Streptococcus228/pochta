package com.pochta.web;

import com.pochta.dto.AdminDashboardDto;
import com.pochta.dto.ParcelAdminDto;
import com.pochta.model.User;
import com.pochta.service.BranchService;
import com.pochta.service.ParcelService;
import com.pochta.service.SimulatorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ParcelService parcelService;
    private final BranchService branchService;
    private final SimulatorService simulatorService;

    public AdminController(ParcelService parcelService, BranchService branchService, SimulatorService simulatorService) {
        this.parcelService = parcelService;
        this.branchService = branchService;
        this.simulatorService = simulatorService;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() != null && user.getRole().equalsIgnoreCase("ADMIN");
    }

    @GetMapping({"/admin", "/admin.html"})
    public String adminPage(@RequestParam(value = "search", required = false) String search, HttpSession session, Model model) {
        log.debug("Entering adminPage with search: {}", search);
        try {
            User user = (User) session.getAttribute("currentUser");

            if (!isAdmin(user)) {
                log.warn("Unauthorized access attempt to admin page");
                return "redirect:/login";
            }

            List<ParcelAdminDto> parcels = parcelService.getAllParcelsForAdmin(search);
            log.debug("Found {} parcels", parcels.size());

            List<ParcelAdminDto> createdParcels = parcels.stream()
                    .filter(p -> "CREATED".equalsIgnoreCase(p.getStatus()))
                    .toList();

            AdminDashboardDto stats = parcelService.getAdminDashboardStats();
            log.debug("Stats retrieved: labels size={}", stats.getLabels().size());

            model.addAttribute("parcels", parcels);
            model.addAttribute("createdParcels", createdParcels);
            model.addAttribute("totalParcels", parcels.size());
            model.addAttribute("stats", stats);
            model.addAttribute("search", search);
            model.addAttribute("branches", branchService.getAllBranches());
            model.addAttribute("currentSpeed", simulatorService.getSpeedMultiplier());

            return "admin";
        } catch (Exception e) {
            log.error("Error in adminPage", e);
            throw e;
        }
    }

    @PostMapping("/admin/branches")
    public String addBranch(@RequestParam String name,
                            @RequestParam int distance,
                            @RequestParam double latitude,
                            @RequestParam double longitude,
                            HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        if (!isAdmin(user)) return "redirect:/login";

        branchService.addBranch(name, distance, latitude, longitude);
        return "redirect:/admin";
    }

    @PostMapping("/admin/speed")
    public String setSpeed(@RequestParam int speed, HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        if (!isAdmin(user)) return "redirect:/login";

        simulatorService.setSpeedMultiplier(speed);
        return "redirect:/admin";
    }

    @PostMapping("/admin/start-trip")
    public String startTrip(@RequestParam("parcelIds") List<Long> parcelIds,
                           @RequestParam(value = "vehicleId", required = false) String vehicleId,
                           HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        if (!isAdmin(user)) return "redirect:/login";

        simulatorService.startManualTrip(parcelIds, vehicleId);
        return "redirect:/admin";
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