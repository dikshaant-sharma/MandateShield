package com.mandateshield.dto;

import com.mandateshield.domain.AnomalyDocument;
import com.mandateshield.domain.AnomalySeverity;
import com.mandateshield.domain.AnomalyType;

import java.math.BigDecimal;
import java.time.Instant;

public record AnomalyDto(String id, String subscriptionId, AnomalyType type, AnomalySeverity severity,
                         String description, BigDecimal financialImpact, Instant detectedAt) {
    public static AnomalyDto from(AnomalyDocument document) {
        return new AnomalyDto(document.getId(), document.getSubscriptionId(), document.getType(),
                document.getSeverity(), document.getDescription(), document.getFinancialImpact(), document.getDetectedAt());
    }
}
