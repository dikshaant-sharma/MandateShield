package com.mandateshield.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request payload for the resolver endpoint.
 * Only {@code rawDescriptor} is mandatory – the other fields are optional
 * and are stored for audit purposes.
 */
public record ResolveRequest(
        String rawDescriptor,
        Long userId,
        BigDecimal amount,
        String currency,
        Instant transactionDate
) {}
