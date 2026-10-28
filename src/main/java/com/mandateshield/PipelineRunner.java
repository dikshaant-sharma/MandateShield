package com.mandateshield;

import com.mandateshield.repository.UserRepository;
import com.mandateshield.service.MandateShieldPipelineService;
import com.mandateshield.service.ExecutiveDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mandateshield.pipeline.run-on-startup", havingValue = "true")
public class PipelineRunner implements CommandLineRunner {
    private final UserRepository userRepository;
    private final MandateShieldPipelineService pipelineService;
    private final ExecutiveDashboardService dashboardService;

    @Override
    public void run(String... args) {
        userRepository.findAll().forEach(user -> {
            try {
                var result = pipelineService.processUser(user.getId()).join();
                var dashboard = dashboardService.getDashboard(user.getId());
                log.info("Startup metrics userId={}: processed={}, subscriptions={}, anomalies={}, monthlySpend={}, annualizedWaste={}, potentialSavings={}",
                        result.userId(), result.processedTransactions(), dashboard.activeSubscriptionsCount(),
                        dashboard.flaggedAnomaliesCount(), dashboard.totalMonthlySpend(),
                        dashboard.totalAnnualizedWaste(), dashboard.potentialAnnualSavings());
            } catch (RuntimeException ex) {
                log.error("Startup pipeline failed for user {}", user.getId(), ex);
            }
        });
    }
}
