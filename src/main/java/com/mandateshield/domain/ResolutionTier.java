package com.mandateshield.domain;

/**
 * Resolution tier indicating how the merchant was identified.
 * TIER_1: Direct regex pattern match (high confidence, fast).
 * TIER_2: Heuristic extraction fallback (moderate confidence, learned).
 * TIER_3: Manual/unresolved (low confidence or unknown).
 */
public enum ResolutionTier {
    TIER_1,
    TIER_2,
    TIER_3
}
