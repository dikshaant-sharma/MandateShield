package com.mandateshield.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardKpiResponse(
        Long userId,
        int activeSubscriptionsCount,
        int flaggedAnomaliesCount,
        BigDecimal totalMonthlySpend,
        BigDecimal totalAnnualizedWaste,
        BigDecimal potentialAnnualSavings,
        BigDecimal estimatedAnnualRecurringSpend,
        List<SubscriptionDto> activeSubscriptions,
        List<AnomalyDto> detectedAnomalies,
        List<SubscriptionDto> tentativeSubscriptions,
        List<TrialConversionAlert> trialConversionAlerts
) {}
