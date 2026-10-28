package com.mandateshield.service;

import com.mandateshield.domain.AnomalyDocument;
import com.mandateshield.domain.SubscriptionDocument;
import com.mandateshield.domain.SubscriptionFrequency;
import com.mandateshield.domain.SubscriptionStatus;
import com.mandateshield.dto.AnomalyDto;
import com.mandateshield.dto.DashboardKpiResponse;
import com.mandateshield.dto.SubscriptionDto;
import com.mandateshield.repository.AnomalyRepository;
import com.mandateshield.repository.SubscriptionRepository;
import com.mandateshield.repository.RawTransactionRepository;
import com.mandateshield.domain.RawTransactionDocument;
import com.mandateshield.dto.TrialConversionAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutiveDashboardService {
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private final SubscriptionRepository subscriptionRepository;
    private final AnomalyRepository anomalyRepository;
    private final RawTransactionRepository transactionRepository;

    public DashboardKpiResponse getDashboard(Long userId) {
        var commitments = subscriptionRepository.findByUserIdAndStatusIn(userId,
                java.util.List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TENTATIVE, SubscriptionStatus.CANCELLATION_REQUESTED));
        var active = commitments.stream().filter(s -> s.getStatus() != SubscriptionStatus.TENTATIVE).toList();
        var tentative = commitments.stream().filter(s -> s.getStatus() == SubscriptionStatus.TENTATIVE).toList();
        var anomalies = anomalyRepository.findByUserId(userId);
        var now = java.time.Instant.now();
        var trialAlerts = transactionRepository.findByUserId(userId).stream()
                .filter(tx -> Boolean.TRUE.equals(tx.getTrialTransaction()) && tx.getTrialEndDate() != null
                        && !tx.getTrialEndDate().isBefore(now) && !tx.getTrialEndDate().isAfter(now.plusSeconds(48 * 3600L)))
                .map(tx -> new TrialConversionAlert(tx.getResolvedMerchantName(), tx.getTrialEndDate(), tx.getTrialConversionAmount()))
                .toList();
        BigDecimal monthlySpend = active.stream().map(this::monthlyAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedAnnualSpend = monthlySpend.multiply(TWELVE);
        Set<String> activeIds = active.stream().map(SubscriptionDocument::getId).collect(Collectors.toSet());
        BigDecimal annualizedWaste = anomalies.stream().filter(a -> activeIds.contains(a.getSubscriptionId())).collect(Collectors.toMap(AnomalyDocument::getSubscriptionId,
                        anomaly -> anomaly.getFinancialImpact() == null ? BigDecimal.ZERO : anomaly.getFinancialImpact(),
                        BigDecimal::max)).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(estimatedAnnualSpend);
        return new DashboardKpiResponse(userId, (int) active.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).count(), anomalies.size(), monthlySpend,
                annualizedWaste, annualizedWaste, estimatedAnnualSpend,
                active.stream().map(SubscriptionDto::from).toList(),
                anomalies.stream().map(AnomalyDto::from).toList(),
                tentative.stream().map(SubscriptionDto::from).toList(), trialAlerts);
    }

    private BigDecimal monthlyAmount(SubscriptionDocument subscription) {
        BigDecimal amount = subscription.getCurrentAmount();
        SubscriptionFrequency frequency = subscription.getFrequency();
        if (amount == null || frequency == null) return BigDecimal.ZERO;
        return switch (frequency) {
            case DAILY -> amount.multiply(BigDecimal.valueOf(365)).divide(TWELVE, 2, RoundingMode.HALF_UP);
            case WEEKLY -> amount.multiply(BigDecimal.valueOf(52)).divide(TWELVE, 2, RoundingMode.HALF_UP);
            case BIWEEKLY -> amount.multiply(BigDecimal.valueOf(26)).divide(TWELVE, 2, RoundingMode.HALF_UP);
            case MONTHLY -> amount;
            case BIMONTHLY -> amount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            case QUARTERLY -> amount.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case SEMIANNUAL -> amount.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
            case ANNUAL -> amount.divide(TWELVE, 2, RoundingMode.HALF_UP);
            case IRREGULAR -> subscription.getAverageIntervalDays() == null || subscription.getAverageIntervalDays() <= 0
                    ? BigDecimal.ZERO : amount.multiply(BigDecimal.valueOf(365.0 / subscription.getAverageIntervalDays())).divide(TWELVE, 2, RoundingMode.HALF_UP);
            case UNKNOWN -> BigDecimal.ZERO;
        };
    }
}
