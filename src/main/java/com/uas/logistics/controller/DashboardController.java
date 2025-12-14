package com.uas.logistics.controller;

import com.uas.logistics.service.GeneticAlgorithmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    @Autowired
    private GeneticAlgorithmService gaService;

    @GetMapping("/")
    public String landing() { return "home"; }

    @GetMapping("/customer")
    public String customerPage(Model model) {
        gaService.generateMap();
        model.addAttribute("mapUrl", "/map.html");
        model.addAttribute("myContainers", gaService.globalManifest);
        model.addAttribute("destinations", gaService.getAvailableDestinations());
        return "customer";
    }

    @GetMapping("/admin")
    public String adminPage(Model model) { 
        model.addAttribute("manifest", gaService.globalManifest);
        model.addAttribute("currentShip", gaService.currentShip);
        model.addAttribute("currentOrigin", gaService.currentOriginIdx);
        return "admin"; 
    }

    // 1. SAVE CONFIG (Hanya Simpan, Tidak Run)
    @PostMapping("/admin/config/save")
    public String saveConfig(
            @RequestParam("shipId") String shipId,
            @RequestParam("shipName") String shipName,
            @RequestParam("maxTonnage") double maxTonnage,
            @RequestParam("stacks") int stacks,
            @RequestParam("stackHeight") int stackHeight,
            @RequestParam("originIdx") int originIdx,
            @RequestParam(value = "secureStacks", required = false) String secureStacks,
            @RequestParam(value = "stackCapacities", required = false) String stackCapacities,
            Model model) {
        
        gaService.updateShipConfig(shipId, shipName, maxTonnage, stacks, stackHeight, secureStacks, stackCapacities, originIdx);
        return "redirect:/admin";
    }

    // 2. RUN OPTIMIZATION (Pakai Config yang sudah di-Save)
    @PostMapping("/admin/optimize")
    public String optimize(Model model) {
        gaService.generateMap();
        GeneticAlgorithmService.SimulationResult result = gaService.runOptimization();
        
        model.addAttribute("result", result);
        model.addAttribute("mapUrl", "/map.html");
        model.addAttribute("manifest", gaService.globalManifest);
        model.addAttribute("currentShip", gaService.currentShip);
        model.addAttribute("currentOrigin", gaService.currentOriginIdx);
        return "admin";
    }
    
    @PostMapping("/customer/submit")
    public String submitCustomer(
            @RequestParam("weight") double weight, 
            @RequestParam("destination") String destination,
            @RequestParam("type") String type,
            Model model) {
        
        gaService.addContainerToManifest(weight, type.contains("Special"), destination, "Customer");
        
        model.addAttribute("successMessage", "Cargo added successfully!");
        model.addAttribute("myContainers", gaService.globalManifest);
        model.addAttribute("mapUrl", "/map.html");
        model.addAttribute("destinations", gaService.getAvailableDestinations());
        return "customer";
    }
    
    @PostMapping("/admin/reset")
    public String resetAdmin(Model model) {
        gaService.clearManifest();
        return "redirect:/admin";
    }

    // FITUR UNTUK DELETE PER ITEM (ADMIN ONLY) 
    @PostMapping("/admin/delete")
    public String deleteContainer(@RequestParam("id") int id) {
        gaService.removeContainerById(id);
        return "redirect:/admin"; // Refresh halaman admin setelah hapus
    }
}