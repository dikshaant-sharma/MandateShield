package com.mandateshield.dto;

import com.mandateshield.domain.SubscriptionDocument;
import com.mandateshield.domain.SubscriptionFrequency;
import com.mandateshield.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionDto(String id, String merchantName, String category, BigDecimal currentAmount,
                              SubscriptionFrequency frequency, SubscriptionStatus status, String paymentRail,
                              Instant lastPaymentDate, Instant lastUserActivityDate,
                              Double detectionConfidence, String detectionEvidence, Double averageIntervalDays,
                              BigDecimal estimatedAnnualAmount) {
    public static SubscriptionDto from(SubscriptionDocument document) {
        BigDecimal amount = document.getCurrentAmount() == null ? BigDecimal.ZERO : document.getCurrentAmount();
        BigDecimal annual = switch (document.getFrequency() == null ? SubscriptionFrequency.UNKNOWN : document.getFrequency()) {
            case DAILY -> amount.multiply(BigDecimal.valueOf(365));
            case WEEKLY -> amount.multiply(BigDecimal.valueOf(52));
            case BIWEEKLY -> amount.multiply(BigDecimal.valueOf(26));
            case MONTHLY -> amount.multiply(BigDecimal.valueOf(12));
            case BIMONTHLY -> amount.multiply(BigDecimal.valueOf(6));
            case QUARTERLY -> amount.multiply(BigDecimal.valueOf(4));
            case SEMIANNUAL -> amount.multiply(BigDecimal.valueOf(2));
            case ANNUAL -> amount;
            case IRREGULAR -> document.getAverageIntervalDays() == null || document.getAverageIntervalDays() <= 0
                    ? BigDecimal.ZERO : amount.multiply(BigDecimal.valueOf(365.0 / document.getAverageIntervalDays()));
            case UNKNOWN -> BigDecimal.ZERO;
        };
        return new SubscriptionDto(document.getId(), document.getMerchantName(), document.getCategory(),
                document.getCurrentAmount(), document.getFrequency(), document.getStatus(), document.getPaymentRail(),
                document.getLastPaymentDate(), document.getLastUserActivityDate(), document.getDetectionConfidence(),
                document.getDetectionEvidence(), document.getAverageIntervalDays(), annual.setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
