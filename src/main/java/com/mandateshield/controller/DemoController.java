package com.mandateshield.controller;

import com.mandateshield.service.SyntheticEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/demo") @RequiredArgsConstructor
public class DemoController {
    private final SyntheticEvaluationService evaluation;

    @PostMapping("/seed")
    public SyntheticEvaluationService.Evaluation seed(@AuthenticationPrincipal Long userId) {
        return evaluation.seedAndRun(userId);
    }

    @GetMapping("/evaluation")
    public SyntheticEvaluationService.Evaluation evaluation(@AuthenticationPrincipal Long userId) {
        return evaluation.evaluate(userId);
    }
}
