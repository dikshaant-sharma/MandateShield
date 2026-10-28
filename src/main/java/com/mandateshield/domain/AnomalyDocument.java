package com.mandateshield.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "anomalies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDocument {
    @Id
    private String id;
    private String subscriptionId;
    private Long userId;
    private AnomalyType type;
    private AnomalySeverity severity;
    private String description;
    private BigDecimal financialImpact;
    private Instant detectedAt;
}
