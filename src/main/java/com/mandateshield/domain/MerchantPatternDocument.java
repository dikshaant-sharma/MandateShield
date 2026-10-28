package com.mandateshield.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "merchant_patterns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantPatternDocument {

    @Id
    private String id;

    @Indexed
    private String patternRegex;

    private String brandName;
    private String category;
    private Long userId;
    private String verificationSource;
    private Double confidenceScore;
    private Instant createdAt;
}
