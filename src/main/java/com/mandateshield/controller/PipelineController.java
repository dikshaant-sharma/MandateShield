package com.mandateshield.controller;

import com.mandateshield.service.MandateShieldPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;


@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineController {
    private final MandateShieldPipelineService pipelineService;

    @PostMapping("/run")
    public MandateShieldPipelineService.PipelineRunResult runPipeline(@AuthenticationPrincipal Long userId) {
        return pipelineService.processUser(userId).join();
    }
}
