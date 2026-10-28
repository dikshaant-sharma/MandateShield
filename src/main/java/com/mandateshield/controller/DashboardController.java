package com.mandateshield.controller;

import com.mandateshield.dto.DashboardKpiResponse;
import com.mandateshield.service.ExecutiveDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final ExecutiveDashboardService dashboardService;

    @GetMapping
    public DashboardKpiResponse getDashboard(@AuthenticationPrincipal Long userId) {
        return dashboardService.getDashboard(userId);
    }
}
