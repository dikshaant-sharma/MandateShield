package com.mandateshield.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mandateshield.domain.HeuristicDictionaryDocument;
import com.mandateshield.repository.HeuristicDictionaryRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;

/**
 * Implements the four-stage heuristic extraction pipeline (Tier 2).
 *
 * 1. Tokenise & standardise the raw descriptor (merging spaced/separated single characters).
 * 2. Detect and strip known payment-rail prefixes.
 * 3. Remove stop-words and numeric noise.
 * 4. Resolve the brand using exact alias, substring, Levenshtein fuzzy distance, or longest token.
 */
@Component
@Slf4j
@Getter
public class HeuristicExtractionEngine {

    private static final Map<String, String> COMMON_ALIASES = Map.ofEntries(
            Map.entry("NFLX", "NETFLIX"), Map.entry("NETFLIX", "NETFLIX"),
            Map.entry("AMZN", "AMAZON"), Map.entry("AMAZON", "AMAZON"),
            Map.entry("SPOTIFY", "SPOTIFY"), Map.entry("SPOTITY", "SPOTIFY"),
            Map.entry("DISNEY", "DISNEY+"), Map.entry("HOTSTAR", "DISNEY+"),
            Map.entry("HULU", "HULU"), Map.entry("AWS", "AWS"),
            Map.entry("APPLE", "APPLE"), Map.entry("ITUNES", "APPLE"),
            Map.entry("GOOGLE", "GOOGLE"), Map.entry("YOUTUBE", "YOUTUBE"),
            Map.entry("SWIGGY", "SWIGGY"), Map.entry("SWIGGYONE", "SWIGGY"),
            Map.entry("ZOMATO", "ZOMATO"), Map.entry("ADOBE", "ADOBE"),
            Map.entry("NOTION", "NOTION"), Map.entry("LINKEDIN", "LINKEDIN"),
            Map.entry("DROPBOX", "DROPBOX"), Map.entry("AUDIBLE", "AUDIBLE")
    );
    private static final Map<String, String> DEFAULT_CATEGORIES = Map.ofEntries(
            Map.entry("SPOTIFY", "Audio & Music"),
            Map.entry("NETFLIX", "Entertainment"),
            Map.entry("AMAZON", "E-Commerce"),
            Map.entry("DISNEY+", "Entertainment"),
            Map.entry("HULU", "Entertainment"),
            Map.entry("AWS", "Cloud Infrastructure"),
            Map.entry("APPLE", "Software & Services"),
            Map.entry("GOOGLE", "Digital Services"),
            Map.entry("YOUTUBE", "Streaming"),
            Map.entry("SWIGGY", "Food & Dining"),
            Map.entry("ZOMATO", "Food & Dining"),
            Map.entry("ADOBE", "Creative & Software"),
            Map.entry("NOTION", "Productivity"),
            Map.entry("LINKEDIN", "Professional Network"),
            Map.entry("DROPBOX", "Cloud Storage"),
            Map.entry("AUDIBLE", "Books & Audio")
    );
    private static final Set<String> DEFAULT_STOPWORDS = Set.of(
            "ACH", "INB", "NACH", "UPI", "POS", "ECOM", "BILL", "AUTOPAY", "SUB", "SUBSCR",
            "MANDATE", "DESCRIPTOR", "TXN", "PAYMENT", "TRANSFER", "PURCHASE", "DEBIT", "CREDIT",
            "CARD", "BANK", "REF", "NO", "LIMITED", "LTD", "PVT", "CORP", "INC", "UNKNOWN"
    );

    private final HeuristicDictionaryRepository dictionaryRepository;

    private Set<String> gateways;
    private Set<String> stopwords;
    private Map<String, String> aliasMap;
    private Map<String, String> brandCategoryMap;

    public HeuristicExtractionEngine(HeuristicDictionaryRepository dictionaryRepository) {
        this.dictionaryRepository = dictionaryRepository;
        this.gateways = new HashSet<>();
        this.stopwords = new HashSet<>();
        this.aliasMap = new HashMap<>();
        this.brandCategoryMap = new HashMap<>();
    }

    @PostConstruct
    public void init() {
        refreshDictionary();
    }

    public void refreshDictionary() {
        if (dictionaryRepository != null) {
            dictionaryRepository.findAll().stream().findFirst().ifPresent(dict -> {
                this.gateways = dict.getGateways() != null ? dict.getGateways() : new HashSet<>();
                this.stopwords = dict.getStopwords() != null ? dict.getStopwords() : new HashSet<>();
                this.aliasMap = dict.getAliasMap() != null ? dict.getAliasMap() : new HashMap<>();
                this.brandCategoryMap = dict.getBrandCategoryMap() != null ? dict.getBrandCategoryMap() : new HashMap<>();
                log.info("Loaded heuristic dictionary from MongoDB. Gateways: {}, Stopwords: {}", gateways.size(), stopwords.size());
            });
        }
        COMMON_ALIASES.forEach(aliasMap::putIfAbsent);
        DEFAULT_CATEGORIES.forEach(brandCategoryMap::putIfAbsent);
        stopwords.addAll(DEFAULT_STOPWORDS);
    }

    /**
     * Runs the full heuristic pipeline and returns a {@link HeuristicResolutionResult}.
     */
    public HeuristicResolutionResult resolve(String rawDescriptor) {
        if (rawDescriptor == null || rawDescriptor.isBlank()) {
            return new HeuristicResolutionResult("UNKNOWN", "Uncategorized", null, 0.0,
                    com.mandateshield.domain.ResolutionTier.TIER_2);
        }
        log.debug("Heuristic extraction start for raw: {}", rawDescriptor);

        // 1. Standardise & tokenise (including single-letter separator merging)
        List<String> tokens = standardiseAndTokenise(rawDescriptor);
        log.trace("Tokens after standardisation: {}", tokens);

        // 2. Detect gateway (metadata)
        String gateway = detectGateway(tokens);
        if (gateway != null) tokens.remove(gateway);
        log.trace("Detected gateway: {}", gateway);

        // 3. Remove stop-words & pure numbers
        List<String> cleaned = tokens.stream()
                .filter(t -> !stopwords.contains(t))
                .filter(t -> !t.matches("\\d+"))
                .collect(Collectors.toCollection(ArrayList::new));
        log.trace("Tokens after stop-word & numeric removal: {}", cleaned);

        // 4. Brand extraction
        BrandMatch brandMatch = extractBrand(cleaned);
        String brand = brandMatch.brand();
        String category = brandCategoryMap.getOrDefault(brand, "Uncategorized");
        double confidence = brand.equals("UNKNOWN") ? 0.50 : brandMatch.confidence();

        log.info("Heuristic resolved -> brand='{}', category='{}', gateway='{}', confidence={}", brand, category, gateway, confidence);

        return new HeuristicResolutionResult(
                brand,
                category,
                gateway,
                confidence,
                com.mandateshield.domain.ResolutionTier.TIER_2
        );
    }

    /** Convert delimiters to spaces, upper-case, split on whitespace and merge consecutive single-letter tokens. */
    private List<String> standardiseAndTokenise(String raw) {
        String cleaned = raw.toUpperCase(Locale.ROOT)
                .replaceAll("[*_\\-./#@|:;,(){}\\[\\]]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        List<String> rawTokens = Arrays.asList(cleaned.split(" "));
        List<String> merged = new ArrayList<>();
        StringBuilder singleCharSeq = new StringBuilder();
        for (String token : rawTokens) {
            if (token.length() == 1 && Character.isLetterOrDigit(token.charAt(0))) {
                singleCharSeq.append(token);
            } else {
                if (singleCharSeq.length() > 0) {
                    merged.add(singleCharSeq.toString());
                    singleCharSeq.setLength(0);
                }
                if (!token.isBlank()) {
                    merged.add(token);
                }
            }
        }
        if (singleCharSeq.length() > 0) {
            merged.add(singleCharSeq.toString());
        }
        return merged;
    }

    /** Return the first gateway token that appears in the list, or null. */
    private String detectGateway(List<String> tokens) {
        for (String token : tokens) {
            if (gateways.contains(token)) {
                return token;
            }
        }
        return null;
    }

    /** Alias lookup -> fuzzy distance matching -> longest-token fallback. */
    private BrandMatch extractBrand(List<String> tokens) {
        // Pass 1: exact alias match
        for (String token : tokens) {
            String alias = aliasMap.get(token);
            if (alias != null) return new BrandMatch(alias, 0.88);
        }
        // Pass 2: partial substring match (token contains alias key or vice versa)
        for (String token : tokens) {
            for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
                String key = entry.getKey();
                if (token.contains(key) || (key.length() >= 4 && token.length() >= 4 && key.contains(token))) {
                    return new BrandMatch(entry.getValue(), 0.85);
                }
            }
        }
        // Pass 3: fuzzy matching via Levenshtein distance (e.g., SPOTITY -> SPOTIFY)
        for (String token : tokens) {
            if (token.length() >= 4) {
                for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
                    String key = entry.getKey();
                    if (Math.abs(token.length() - key.length()) <= 1) {
                        int dist = levenshteinDistance(token, key);
                        if (dist <= (key.length() >= 6 ? 2 : 1)) {
                            return new BrandMatch(entry.getValue(), 0.83);
                        }
                    }
                }
            }
        }
        // Pass 4: fallback to longest token that is at least 3 chars
        String fallback = tokens.stream()
                .filter(t -> t.length() >= 3)
                .max(Comparator.comparingInt(String::length))
                .orElse("UNKNOWN");
        return new BrandMatch(fallback, fallback.equals("UNKNOWN") ? 0.50 : 0.70);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) costs[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }

    private record BrandMatch(String brand, double confidence) {}

    /** Record that carries the heuristic result. */
    public record HeuristicResolutionResult(
            String brandName,
            String category,
            String gateway,
            double confidenceScore,
            com.mandateshield.domain.ResolutionTier resolutionTier) {}
}
