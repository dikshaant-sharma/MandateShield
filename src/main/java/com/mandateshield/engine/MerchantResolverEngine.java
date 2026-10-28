package com.mandateshield.engine;

import com.mandateshield.domain.MerchantPatternDocument;
import com.mandateshield.domain.RawTransactionDocument;
import com.mandateshield.domain.ResolutionTier;
import com.mandateshield.repository.MerchantPatternRepository;
import com.mandateshield.repository.RawTransactionRepository;
import com.mandateshield.service.GeminiMerchantEnrichmentService;
import com.mandateshield.service.SharedMerchantKnowledgeService;
import com.mandateshield.service.PineconeMerchantVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Core service that resolves a raw transaction descriptor across 3 layers:
 * <p>
 * Layer 1 (Tier 1): fast regex lookup against stored patterns and shared knowledge.
 * Layer 2 (Tier 2): Pinecone vector similarity search & heuristic extraction with single-letter normalization and fuzzy matching.
 * Layer 3 (Tier 3): Google Gemini LLM fallback for low-confidence or obfuscated descriptors.
 */
@Service
@Slf4j
public class MerchantResolverEngine {

    private final MerchantPatternRepository patternRepo;
    private final RawTransactionRepository transactionRepo;
    private final HeuristicExtractionEngine heuristicEngine;
    private final SharedMerchantKnowledgeService sharedKnowledge;
    private final PineconeMerchantVectorService pinecone;
    private final ObjectProvider<GeminiMerchantEnrichmentService> geminiProvider;

    /** Cache of compiled regex patterns to avoid recompilation on every request. */
    private final Map<String, Pattern> compiledPatternCache = new ConcurrentHashMap<>();
    /** Positive exact descriptor lookups are cached by account and descriptor. */
    private final Map<PatternKey, MerchantPatternDocument> exactPatternCache = new ConcurrentHashMap<>();

    @Autowired
    public MerchantResolverEngine(MerchantPatternRepository patternRepo,
                                  RawTransactionRepository transactionRepo,
                                  HeuristicExtractionEngine heuristicEngine,
                                  SharedMerchantKnowledgeService sharedKnowledge,
                                  PineconeMerchantVectorService pinecone,
                                  ObjectProvider<GeminiMerchantEnrichmentService> geminiProvider) {
        this.patternRepo = patternRepo;
        this.transactionRepo = transactionRepo;
        this.heuristicEngine = heuristicEngine;
        this.sharedKnowledge = sharedKnowledge;
        this.pinecone = pinecone;
        this.geminiProvider = geminiProvider;
    }

    /**
     * Resolve the descriptor across Layer 1 (Regex), Layer 2 (Vector/Heuristic), Layer 3 (Gemini LLM) and persist the transaction.
     */
    public ResolveResult resolve(String rawDescriptor,
                                Long userId,
                                BigDecimal amount,
                                String currency,
                                Instant transactionDate) {

        log.info("Resolving descriptor: '{}'", rawDescriptor);

        // ------------------------------------------------------------------
        // Layer 1 (Tier 1) – regex match against shared knowledge & stored patterns
        // ------------------------------------------------------------------
        Optional<MerchantPatternDocument> tier1Match = sharedKnowledge.find(rawDescriptor).map(alias ->
                MerchantPatternDocument.builder().patternRegex("SHARED:" + alias.getDescriptorFingerprint())
                        .brandName(alias.getMerchantName()).category(alias.getCategory()).confidenceScore(0.98)
                        .verificationSource(alias.getVerificationSource()).build());
        if (tier1Match.isEmpty()) tier1Match = findPatternMatch(rawDescriptor, userId);
        if (tier1Match.isPresent()) {
            MerchantPatternDocument pat = tier1Match.get();
            log.info("Layer 1 (Tier 1) match for '{}' -> brand='{}'", rawDescriptor, pat.getBrandName());
            saveRawTransaction(userId, rawDescriptor, amount, currency, transactionDate,
                    pat.getBrandName(), pat.getCategory(), pat.getConfidenceScore(), ResolutionTier.TIER_1);
            return new ResolveResult(pat.getBrandName(), pat.getCategory(),
                    extractGateway(rawDescriptor), pat.getConfidenceScore(),
                    ResolutionTier.TIER_1, pat.getPatternRegex());
        }

        // ------------------------------------------------------------------
        // Layer 2 (Tier 2) – Pinecone Vector search
        // ------------------------------------------------------------------
        PineconeMerchantVectorService.Match vectorMatch = pinecone.find(rawDescriptor);
        if (vectorMatch != null) {
            String category = heuristicEngine.getBrandCategoryMap().getOrDefault(vectorMatch.merchant(), "Uncategorized");
            log.info("Layer 2 (Tier 2) Vector match for '{}' -> brand='{}', score={}", rawDescriptor, vectorMatch.merchant(), vectorMatch.score());
            saveRawTransaction(userId, rawDescriptor, amount, currency, transactionDate,
                    vectorMatch.merchant(), category, vectorMatch.score(), ResolutionTier.TIER_2);
            return new ResolveResult(vectorMatch.merchant(), category, extractGateway(rawDescriptor),
                    vectorMatch.score(), ResolutionTier.TIER_2, "PINECONE_VECTOR");
        }

        // ------------------------------------------------------------------
        // Layer 2 (Tier 2) – Heuristic extraction fallback (fuzzy + single letter concatenation)
        // ------------------------------------------------------------------
        log.info("Checking Layer 2 heuristic for '{}'", rawDescriptor);
        HeuristicExtractionEngine.HeuristicResolutionResult heur = heuristicEngine.resolve(rawDescriptor);
        if (!"UNKNOWN".equalsIgnoreCase(heur.brandName()) && heur.confidenceScore() >= 0.80) {
            log.info("Layer 2 (Tier 2) Heuristic match for '{}' -> brand='{}', confidence={}", rawDescriptor, heur.brandName(), heur.confidenceScore());
            saveRawTransaction(userId, rawDescriptor, amount, currency, transactionDate,
                    heur.brandName(), heur.category(), heur.confidenceScore(), heur.resolutionTier());
            pinecone.remember(rawDescriptor, heur.brandName(), heur.category());
            asyncWriteBackPattern(userId, rawDescriptor, heur.brandName(), heur.category());
            return new ResolveResult(heur.brandName(), heur.category(), heur.gateway(),
                    heur.confidenceScore(), heur.resolutionTier(), "HEURISTIC_RULE");
        }

        // ------------------------------------------------------------------
        // Layer 3 (Tier 3) – Google Gemini LLM fallback
        // ------------------------------------------------------------------
        log.info("No Tier 1/2 match found, invoking Layer 3 Gemini LLM for '{}'", rawDescriptor);
        GeminiMerchantEnrichmentService gemini = geminiProvider.getIfAvailable();
        if (gemini != null) {
            String geminiBrand = gemini.resolveSynchronous(rawDescriptor);
            if (geminiBrand != null && !"UNKNOWN".equalsIgnoreCase(geminiBrand)) {
                String category = heuristicEngine.getBrandCategoryMap().getOrDefault(geminiBrand, "Uncategorized");
                double confidence = 0.95;
                log.info("Layer 3 (Tier 3) Gemini LLM match for '{}' -> brand='{}'", rawDescriptor, geminiBrand);
                saveRawTransaction(userId, rawDescriptor, amount, currency, transactionDate,
                        geminiBrand, category, confidence, ResolutionTier.TIER_3);
                pinecone.remember(rawDescriptor, geminiBrand, category);
                asyncWriteBackPattern(userId, rawDescriptor, geminiBrand, category);
                return new ResolveResult(geminiBrand, category, extractGateway(rawDescriptor),
                        confidence, ResolutionTier.TIER_3, "GEMINI_LLM");
            }
        }

        // ------------------------------------------------------------------
        // Unresolved / UNKNOWN fallback
        // ------------------------------------------------------------------
        saveRawTransaction(userId, rawDescriptor, amount, currency, transactionDate,
                heur.brandName(), heur.category(), heur.confidenceScore(), heur.resolutionTier());
        return new ResolveResult(heur.brandName(), heur.category(), heur.gateway(),
                heur.confidenceScore(), heur.resolutionTier(), null);
    }

    /** Resolve and update an already-ingested Mongo document without inserting a duplicate. */
    public ResolveResult resolveExisting(RawTransactionDocument transaction) {
        if (transaction == null || transaction.getRawDescriptor() == null || transaction.getRawDescriptor().isBlank()) {
            throw new IllegalArgumentException("Raw transaction descriptor is required");
        }
        String descriptor = transaction.getRawDescriptor();

        // Layer 1 (Tier 1)
        Optional<MerchantPatternDocument> match = sharedKnowledge.find(descriptor)
                .map(alias -> MerchantPatternDocument.builder().patternRegex("SHARED:" + alias.getDescriptorFingerprint())
                        .brandName(alias.getMerchantName()).category(alias.getCategory()).confidenceScore(0.98)
                        .verificationSource(alias.getVerificationSource()).build());
        if (match.isEmpty()) match = findPatternMatch(descriptor, transaction.getUserId());
        if (match.isPresent()) {
            MerchantPatternDocument pattern = match.get();
            return new ResolveResult(pattern.getBrandName(), pattern.getCategory(), extractGateway(descriptor),
                    pattern.getConfidenceScore(), ResolutionTier.TIER_1, pattern.getPatternRegex());
        }

        // Layer 2 (Tier 2) Vector
        PineconeMerchantVectorService.Match vectorMatch = pinecone.find(descriptor);
        if (vectorMatch != null) {
            String category = heuristicEngine.getBrandCategoryMap().getOrDefault(vectorMatch.merchant(), "Uncategorized");
            return new ResolveResult(vectorMatch.merchant(), category, extractGateway(descriptor),
                    vectorMatch.score(), ResolutionTier.TIER_2, "PINECONE_VECTOR");
        }

        // Layer 2 (Tier 2) Heuristic
        HeuristicExtractionEngine.HeuristicResolutionResult result = heuristicEngine.resolve(descriptor);
        if (!"UNKNOWN".equalsIgnoreCase(result.brandName()) && result.confidenceScore() >= 0.80) {
            pinecone.remember(descriptor, result.brandName(), result.category());
            return new ResolveResult(result.brandName(), result.category(), result.gateway(),
                    result.confidenceScore(), result.resolutionTier(), "HEURISTIC_RULE");
        }

        // Layer 3 (Tier 3) Gemini LLM
        GeminiMerchantEnrichmentService gemini = geminiProvider.getIfAvailable();
        if (gemini != null) {
            String geminiBrand = gemini.resolveSynchronous(descriptor);
            if (geminiBrand != null && !"UNKNOWN".equalsIgnoreCase(geminiBrand)) {
                String category = heuristicEngine.getBrandCategoryMap().getOrDefault(geminiBrand, "Uncategorized");
                pinecone.remember(descriptor, geminiBrand, category);
                asyncWriteBackPattern(transaction.getUserId(), descriptor, geminiBrand, category);
                return new ResolveResult(geminiBrand, category, extractGateway(descriptor),
                        0.95, ResolutionTier.TIER_3, "GEMINI_LLM");
            }
        }

        return new ResolveResult(result.brandName(), result.category(), result.gateway(),
                result.confidenceScore(), result.resolutionTier(), null);
    }

    /** Regex lookup with compiled pattern cache for performance. */
    private Optional<MerchantPatternDocument> findPatternMatch(String rawDescriptor, Long userId) {
        if (rawDescriptor == null || rawDescriptor.isBlank()) return Optional.empty();
        MerchantPatternDocument exactCached = exactPatternCache.get(new PatternKey(userId, rawDescriptor));
        if (exactCached != null) return Optional.of(exactCached);
        List<MerchantPatternDocument> patterns = patternRepo.findAll().stream()
                .filter(pattern -> pattern.getUserId() == null || Objects.equals(pattern.getUserId(), userId))
                .toList();
        // Prefer an exact descriptor mapping over older heuristic substring patterns.
        Optional<MerchantPatternDocument> exact = patterns.stream()
                .filter(pat -> Pattern.quote(rawDescriptor).equals(pat.getPatternRegex()))
                .filter(pat -> pat.getBrandName() != null && !"UNKNOWN".equalsIgnoreCase(pat.getBrandName()))
                .findFirst();
        exact.ifPresent(pattern -> exactPatternCache.put(new PatternKey(userId, rawDescriptor), pattern));
        if (exact.isPresent()) return exact;
        for (MerchantPatternDocument pat : patterns) {
            if (pat.getPatternRegex() == null || pat.getPatternRegex().isBlank()) continue;
            if (pat.getBrandName() == null || "UNKNOWN".equalsIgnoreCase(pat.getBrandName())) continue;
            try {
                Pattern p = compiledPatternCache.computeIfAbsent(
                        pat.getPatternRegex(),
                        regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
                );
                if (p.matcher(rawDescriptor).matches()) {
                    return Optional.of(pat);
                }
            } catch (Exception e) {
                log.warn("Invalid regex pattern stored for brand {}: {}",
                        pat.getBrandName(), pat.getPatternRegex(), e);
            }
        }
        return Optional.empty();
    }

    private void saveRawTransaction(Long userId, String rawDescriptor,
                                   BigDecimal amount, String currency,
                                   Instant txnDate, String brand, String category,
                                   Double confidence, ResolutionTier tier) {
        try {
            RawTransactionDocument tx = RawTransactionDocument.builder()
                    .userId(userId)
                    .rawDescriptor(rawDescriptor)
                    .amount(amount)
                    .currency(currency != null ? currency : "INR")
                    .transactionDate(txnDate != null ? txnDate : Instant.now())
                    .paymentRail(extractGateway(rawDescriptor))
                    .resolvedMerchantName(brand)
                    .resolvedCategory(category)
                    .confidenceScore(confidence)
                    .resolutionTier(tier)
                    .build();
            transactionRepo.save(tx);
            log.debug("Saved raw transaction for descriptor: '{}'", rawDescriptor);
        } catch (Exception e) {
            log.error("Failed to save raw transaction for '{}': {}", rawDescriptor, e.getMessage(), e);
        }
    }

    /** Extracts known gateway prefix if present, otherwise "UNKNOWN". */
    private String extractGateway(String raw) {
        String upper = raw.toUpperCase();
        for (String gw : heuristicEngine.getGateways()) {
            if (upper.startsWith(gw + "_") || upper.startsWith(gw + "/")
                    || upper.startsWith(gw + "-") || upper.startsWith(gw + " ")) {
                return gw;
            }
        }
        // Also check if gateway appears anywhere in the descriptor
        for (String gw : heuristicEngine.getGateways()) {
            if (upper.contains(gw)) {
                return gw;
            }
        }
        return "UNKNOWN";
    }

    /** Asynchronously persist a new pattern derived from a resolution. */
    @Async
    public void asyncWriteBackPattern(Long userId, String rawDescriptor, String brand, String category) {
        try {
            String regex = Pattern.quote(rawDescriptor);
            MerchantPatternDocument newPat = MerchantPatternDocument.builder()
                    .patternRegex(regex)
                    .brandName(brand)
                    .category(category)
                    .userId(userId)
                    .verificationSource("RESOLVER_AUTO_LEARNED")
                    .confidenceScore(0.88)
                    .createdAt(Instant.now())
                    .build();
            patternRepo.save(newPat);
            // Invalidate cache so new pattern is picked up
            compiledPatternCache.clear();
            exactPatternCache.clear();
            log.info("Learned new Tier 1 pattern: {} -> {}", regex, brand);
        } catch (Exception e) {
            log.error("Failed to write back pattern for '{}': {}", rawDescriptor, e.getMessage(), e);
        }
    }

    /** Expose all stored patterns (used by the controller). */
    public List<MerchantPatternDocument> getAllPatterns(Long userId) {
        return patternRepo.findAll().stream()
                .filter(pattern -> pattern.getUserId() == null || Objects.equals(pattern.getUserId(), userId))
                .toList();
    }

    /** Clear the compiled pattern cache (useful when patterns are updated externally). */
    public void clearPatternCache() {
        compiledPatternCache.clear();
        exactPatternCache.clear();
        log.info("Pattern cache cleared.");
    }

    /** DTO returned to the controller layer. */
    public record ResolveResult(
            String brandName,
            String category,
            String gateway,
            double confidenceScore,
            ResolutionTier resolutionTier,
            String matchedPattern) {}

    private record PatternKey(Long userId, String descriptor) {}
}
