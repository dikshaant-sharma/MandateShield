package com.mandateshield.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Shared reference data only: stores a descriptor fingerprint, never a transaction or account identifier. */
@Document(collection = "shared_merchant_aliases")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MerchantAliasDocument {
    @Id private String id;
    @Indexed(unique = true) private String descriptorFingerprint;
    private String merchantName;
    private String category;
    private String verificationSource;
    private Instant confirmedAt;
}
