package com.mandateshield.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "raw_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawTransactionDocument {

    @Id
    private String id;

    private Long userId;
    private String rawDescriptor;
    private BigDecimal amount;
    private String currency;
    private Instant transactionDate;
    private String paymentRail;
    private String resolvedMerchantName;
    private String resolvedCategory;
    private Double confidenceScore;
    private ResolutionTier resolutionTier;
    private Instant createdAt;
    private Boolean trialTransaction;
    private Instant trialEndDate;
    private BigDecimal trialConversionAmount;
    private Boolean synthetic;
    private String expectedMerchant;
    private String expectedRecurringGroup;

    /**
     * Set defaults before persisting.
     */
    public static class RawTransactionDocumentBuilder {
        private Instant createdAt = Instant.now();
    }
}
