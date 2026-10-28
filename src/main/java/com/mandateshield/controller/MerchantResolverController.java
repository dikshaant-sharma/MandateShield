package com.mandateshield.controller;

import com.mandateshield.engine.MerchantResolverEngine;
import com.mandateshield.service.GeminiMerchantEnrichmentService;
import com.mandateshield.engine.MerchantResolverEngine.ResolveResult;
import com.mandateshield.dto.ResolveResponse;
import com.mandateshield.dto.ResolveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/resolver")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Merchant Resolver", description = "Resolve raw transaction descriptors to merchant identities")
public class MerchantResolverController {

    private final MerchantResolverEngine resolverEngine;
    private final GeminiMerchantEnrichmentService geminiEnrichment;

    @PostMapping("/resolve")
    @Operation(summary = "Resolve a raw transaction descriptor",
               description = "Takes a raw bank/payment descriptor and resolves it to a known merchant brand using Tier 1 (regex) and Tier 2 (heuristic) resolution.")
    public ResponseEntity<ResolveResponse> resolve(@AuthenticationPrincipal Long userId, @RequestBody ResolveRequest request) {
        if (request.rawDescriptor() == null || request.rawDescriptor().isBlank()) {
            throw new IllegalArgumentException("rawDescriptor must be provided");
        }

        ResolveResult result = resolverEngine.resolve(
                request.rawDescriptor(),
                userId,
                request.amount(),
                request.currency(),
                request.transactionDate()
        );
        geminiEnrichment.scheduleIfUnknown(userId, request.rawDescriptor(), result.confidenceScore(), result.brandName());
        if (result.resolutionTier() == com.mandateshield.domain.ResolutionTier.TIER_1) {
            geminiEnrichment.synchronizeKnownTierOne(userId, request.rawDescriptor(), result.brandName(), result.confidenceScore());
        }
        ResolveResponse response = new ResolveResponse(
                result.brandName(),
                result.category(),
                result.gateway(),
                result.confidenceScore(),
                result.resolutionTier(),
                result.matchedPattern()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/patterns")
    @Operation(summary = "List all merchant patterns",
               description = "Returns all stored merchant patterns including auto-learned ones.")
    public ResponseEntity<?> listPatterns(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(resolverEngine.getAllPatterns(userId));
    }

    @PostMapping("/patterns/cache/clear")
    @Operation(summary = "Clear the pattern cache",
               description = "Invalidates the compiled regex cache. Use after manually updating patterns in the database.")
    public ResponseEntity<?> clearCache() {
        resolverEngine.clearPatternCache();
        return ResponseEntity.ok(Map.of("status", "cache_cleared"));
    }
}
