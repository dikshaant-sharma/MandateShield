package com.mandateshield;

import com.mandateshield.domain.*;
import com.mandateshield.engine.HeuristicExtractionEngine;
import com.mandateshield.engine.MerchantResolverEngine;
import com.mandateshield.repository.*;
import com.mandateshield.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DescriptorMatchingAndFeaturesTest {

    private final HeuristicDictionaryRepository dictionaryRepo = Mockito.mock(HeuristicDictionaryRepository.class);

    private HeuristicExtractionEngine createEngine() {
        when(dictionaryRepo.findAll()).thenReturn(List.of());
        HeuristicExtractionEngine engine = new HeuristicExtractionEngine(dictionaryRepo);
        engine.init();
        return engine;
    }

    @Test
    @DisplayName("Tier 2 Heuristic: Resolves S_P_O_T_I_T_Y with single-letter concatenation and fuzzy matching to SPOTIFY")
    void testSpotityResolution() {
        HeuristicExtractionEngine engine = createEngine();
        var result = engine.resolve("S_P_O_T_I_T_Y");

        assertEquals("SPOTIFY", result.brandName(), "S_P_O_T_I_T_Y must resolve to SPOTIFY");
        assertEquals("Audio & Music", result.category());
        assertTrue(result.confidenceScore() >= 0.80, "Confidence score should be >= 0.80");
        assertEquals(ResolutionTier.TIER_2, result.resolutionTier());
    }

    @Test
    @DisplayName("Tier 2 Heuristic: Resolves S_P_O_T_I_F_Y with single-letter concatenation to SPOTIFY")
    void testSpotifySeparatedResolution() {
        HeuristicExtractionEngine engine = createEngine();
        var result = engine.resolve("S_P_O_T_I_F_Y");

        assertEquals("SPOTIFY", result.brandName(), "S_P_O_T_I_F_Y must resolve to SPOTIFY");
        assertEquals("Audio & Music", result.category());
        assertTrue(result.confidenceScore() >= 0.85);
    }

    @Test
    @DisplayName("Tier 2 Heuristic: Strips bank stopwords and resolves ACH_INB_S_P_O_T_I_F_Y_IN_9812 to SPOTIFY")
    void testBankDescriptorWithStopwords() {
        HeuristicExtractionEngine engine = createEngine();
        var result = engine.resolve("ACH_INB_S_P_O_T_I_F_Y_IN_9812");

        assertEquals("SPOTIFY", result.brandName());
        assertEquals("Audio & Music", result.category());
    }

    @Test
    @DisplayName("Tier 2 Heuristic: Detects gateway and resolves RAZORPAY*HOTSTAR_PREMIUM_BLR")
    void testGatewayAndAliasExtraction() {
        HeuristicExtractionEngine engine = createEngine();
        var result = engine.resolve("RAZORPAY*HOTSTAR_PREMIUM_BLR");

        assertEquals("DISNEY+", result.brandName());
        assertEquals("Entertainment", result.category());
    }

    @Test
    @DisplayName("Tier 2 Heuristic: Resolves SWIGGYONE and ZOMATO")
    void testDeliveryServices() {
        HeuristicExtractionEngine engine = createEngine();
        assertEquals("SWIGGY", engine.resolve("PAYU*SWIGGYONE_MANDATE_HYD").brandName());
        assertEquals("ZOMATO", engine.resolve("ZOMATO*GOLDMEMBERSHIP_GURGAON_IN").brandName());
    }

    @Test
    @DisplayName("Tier 1: MerchantResolverEngine matches stored exact pattern and returns TIER_1")
    void testTier1StoredPatternMatch() {
        MerchantPatternRepository patternRepo = Mockito.mock(MerchantPatternRepository.class);
        RawTransactionRepository txRepo = Mockito.mock(RawTransactionRepository.class);
        SharedMerchantKnowledgeService sharedKnowledge = Mockito.mock(SharedMerchantKnowledgeService.class);
        PineconeMerchantVectorService pinecone = Mockito.mock(PineconeMerchantVectorService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<GeminiMerchantEnrichmentService> geminiProvider = Mockito.mock(ObjectProvider.class);

        HeuristicExtractionEngine heuristic = createEngine();

        MerchantPatternDocument stored = MerchantPatternDocument.builder()
                .patternRegex("\\QNETFLIX SUBSCRIPTION\\E")
                .brandName("NETFLIX")
                .category("Entertainment")
                .confidenceScore(0.98)
                .build();

        when(sharedKnowledge.find(any())).thenReturn(Optional.empty());
        when(patternRepo.findAll()).thenReturn(List.of(stored));

        MerchantResolverEngine resolver = new MerchantResolverEngine(
                patternRepo, txRepo, heuristic, sharedKnowledge, pinecone, geminiProvider
        );

        var result = resolver.resolve("NETFLIX SUBSCRIPTION", 101L, BigDecimal.valueOf(499), "INR", Instant.now());
        assertEquals(ResolutionTier.TIER_1, result.resolutionTier());
        assertEquals("NETFLIX", result.brandName());
        assertEquals(0.98, result.confidenceScore());
    }

    @Test
    @DisplayName("Tier 2: Pinecone Vector Service match returns TIER_2 with score")
    void testTier2PineconeVectorMatch() {
        MerchantPatternRepository patternRepo = Mockito.mock(MerchantPatternRepository.class);
        RawTransactionRepository txRepo = Mockito.mock(RawTransactionRepository.class);
        SharedMerchantKnowledgeService sharedKnowledge = Mockito.mock(SharedMerchantKnowledgeService.class);
        PineconeMerchantVectorService pinecone = Mockito.mock(PineconeMerchantVectorService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<GeminiMerchantEnrichmentService> geminiProvider = Mockito.mock(ObjectProvider.class);

        HeuristicExtractionEngine heuristic = createEngine();

        when(sharedKnowledge.find(any())).thenReturn(Optional.empty());
        when(patternRepo.findAll()).thenReturn(List.of());
        when(pinecone.find("OBSCURE_STREAMING_SERVICE")).thenReturn(new PineconeMerchantVectorService.Match("SPOTIFY", 0.89));

        MerchantResolverEngine resolver = new MerchantResolverEngine(
                patternRepo, txRepo, heuristic, sharedKnowledge, pinecone, geminiProvider
        );

        var result = resolver.resolve("OBSCURE_STREAMING_SERVICE", 101L, BigDecimal.valueOf(119), "INR", Instant.now());
        assertEquals(ResolutionTier.TIER_2, result.resolutionTier());
        assertEquals("SPOTIFY", result.brandName());
        assertEquals(0.89, result.confidenceScore());
        assertEquals("PINECONE_VECTOR", result.matchedPattern());
    }

    @Test
    @DisplayName("Tier 3: Gemini LLM Fallback resolves unknown descriptor and returns TIER_3")
    void testTier3GeminiFallback() {
        MerchantPatternRepository patternRepo = Mockito.mock(MerchantPatternRepository.class);
        RawTransactionRepository txRepo = Mockito.mock(RawTransactionRepository.class);
        SharedMerchantKnowledgeService sharedKnowledge = Mockito.mock(SharedMerchantKnowledgeService.class);
        PineconeMerchantVectorService pinecone = Mockito.mock(PineconeMerchantVectorService.class);
        GeminiMerchantEnrichmentService geminiService = Mockito.mock(GeminiMerchantEnrichmentService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<GeminiMerchantEnrichmentService> geminiProvider = Mockito.mock(ObjectProvider.class);

        when(geminiProvider.getIfAvailable()).thenReturn(geminiService);
        when(geminiService.resolveSynchronous("12345 UNKNOWN DESCRIPTOR")).thenReturn("SPOTIFY");

        HeuristicExtractionEngine heuristic = createEngine();

        when(sharedKnowledge.find(any())).thenReturn(Optional.empty());
        when(patternRepo.findAll()).thenReturn(List.of());
        when(pinecone.find(any())).thenReturn(null);

        MerchantResolverEngine resolver = new MerchantResolverEngine(
                patternRepo, txRepo, heuristic, sharedKnowledge, pinecone, geminiProvider
        );

        var result = resolver.resolve("12345 UNKNOWN DESCRIPTOR", 101L, BigDecimal.valueOf(119), "INR", Instant.now());
        assertEquals(ResolutionTier.TIER_3, result.resolutionTier());
        assertEquals("SPOTIFY", result.brandName());
        assertEquals(0.95, result.confidenceScore());
        assertEquals("GEMINI_LLM", result.matchedPattern());
    }
}
