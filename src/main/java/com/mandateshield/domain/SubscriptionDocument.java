package com.mandateshield.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "subscriptions")
@CompoundIndexes({
    @CompoundIndex(name = "user_status_idx", def = "{'userId': 1, 'status': 1}"),
    @CompoundIndex(name = "user_merchant_idx", def = "{'userId': 1, 'merchantName': 1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDocument {
    @Id
    private String id;
    private Long userId;
    private String merchantName;
    private String category;
    private BigDecimal currentAmount;
    private SubscriptionFrequency frequency;
    private SubscriptionStatus status;
    private String paymentRail;
    private Instant lastPaymentDate;
    private Instant lastUserActivityDate;
    private Instant firstDetectedAt;
    private Double detectionConfidence;
    private String detectionEvidence;
    private Double averageIntervalDays;
    private Boolean userReportedUnused;
}
