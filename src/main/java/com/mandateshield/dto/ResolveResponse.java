package com.mandateshield.dto;

import com.mandateshield.domain.ResolutionTier;

/**
 * Response payload for the resolver endpoint.
 */
public record ResolveResponse(
        String brandName,
        String category,
        String gateway,
        double confidenceScore,
        ResolutionTier resolutionTier,
        String matchedPattern
) {}
