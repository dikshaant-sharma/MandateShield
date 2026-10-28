package com.mandateshield.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pinecone adapter for merchant aliases. Character n-gram vectors keep embeddings local and free. */
@Service
@Slf4j
public class PineconeMerchantVectorService {
    private static final int DIMENSIONS = 384;
    private static final Pattern HOST = Pattern.compile("\\\"host\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MATCH = Pattern.compile("\\\"score\\\"\\s*:\\s*([0-9.]+).*?\\\"merchant\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build();
    private final String apiKey;
    private final String indexName;
    private volatile String indexHost;

    public PineconeMerchantVectorService(@Value("${mandateshield.pinecone.api-key:}") String apiKey,
                                         @Value("${mandateshield.pinecone.index-name:mandateshield-merchants}") String indexName,
                                         @Value("${mandateshield.pinecone.index-host:}") String indexHost) {
        this.apiKey = apiKey;
        this.indexName = indexName;
        this.indexHost = indexHost == null || indexHost.isBlank() ? null : indexHost.replaceFirst("^https?://", "").replaceAll("/$", "");
    }

    @PostConstruct
    public void seedInitialVectors() {
        if (apiKey == null || apiKey.isBlank()) return;
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                Map<String, String> seeds = Map.of(
                        "SPOTIFY PREMIUM STREAMING INDIA", "SPOTIFY",
                        "NETFLIX DIGITAL SUBSCRIPTION MUMBAI", "NETFLIX",
                        "AMAZON PRIME MARKETPLACE", "AMAZON",
                        "SWIGGY ONE MEMBERSHIP", "SWIGGY",
                        "ZOMATO GOLD DINING DELIVERY", "ZOMATO",
                        "HOTSTAR DISNEY PLUS PREMIUM", "DISNEY+",
                        "ADOBE CREATIVE CLOUD SYSTEMS", "ADOBE",
                        "GOOGLE YOUTUBE PREMIUM", "YOUTUBE",
                        "APPLE SERVICES ITUNES BILL", "APPLE",
                        "NOTION WORKSPACE PLUS", "NOTION"
                );
                for (Map.Entry<String, String> entry : seeds.entrySet()) {
                    remember(entry.getKey(), entry.getValue(), "Subscription");
                }
                log.info("Pinecone vector index seeded with common merchant descriptors.");
            } catch (Exception e) {
                log.debug("Pinecone seeding non-blocking note: {}", e.getMessage());
            }
        }, "pinecone-seed").start();
    }

    public Match find(String descriptor) {
        try {
            String host = resolveHost();
            if (host == null) return null;
            String body = "{\"vector\":" + vectorJson(descriptor) + ",\"topK\":1,\"includeMetadata\":true,\"namespace\":\"mandateshield-merchants\"}";
            HttpResponse<String> response = send("https://" + host + "/query", "POST", body);
            if (response.statusCode() / 100 != 2) return null;
            Matcher matcher = MATCH.matcher(response.body());
            if (!matcher.find()) return null;
            double score = Double.parseDouble(matcher.group(1));
            return score >= 0.70 ? new Match(matcher.group(2), score) : null;
        } catch (Exception e) {
            log.debug("Pinecone lookup unavailable; using local merchant resolver: {}", e.getMessage());
            return null;
        }
    }

    public void remember(String descriptor, String merchant, String category) {
        try {
            String host = resolveHost();
            if (host == null) return;
            String id = fingerprint(descriptor);
            String metadata = "{\"merchant\":\"" + json(merchant) + "\",\"category\":\"" + json(category == null ? "Uncategorized" : category) + "\"}";
            send("https://" + host + "/vectors/upsert", "POST", "{\"namespace\":\"mandateshield-merchants\",\"vectors\":[{\"id\":\"" + id + "\",\"values\":" + vectorJson(descriptor) + ",\"metadata\":" + metadata + "}]}");
        } catch (Exception e) {
            log.debug("Pinecone upsert unavailable: {}", e.getMessage());
        }
    }

    private String resolveHost() throws Exception {
        if (apiKey == null || apiKey.isBlank()) return null;
        if (indexHost != null) return indexHost;
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.pinecone.io/indexes"))
                .timeout(Duration.ofMillis(1200)).header("Api-Key", apiKey)
                .header("X-Pinecone-Api-Version", "2026-07").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) return null;
        String body = response.body();
        Matcher name = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"" + Pattern.quote(indexName) + "\\\"").matcher(body);
        if (!name.find()) return null;
        String candidate = body.substring(name.start());
        Matcher matcher = HOST.matcher(candidate);
        if (!matcher.find()) return null;
        indexHost = matcher.group(1);
        return indexHost;
    }

    private HttpResponse<String> send(String url, String method, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(1200))
                .header("Api-Key", apiKey).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String vectorJson(String text) {
        double[] vector = new double[DIMENSIONS];
        String cleanLetters = mergeSingleLetters(text.toUpperCase(Locale.ROOT));
        String normalized = "  " + cleanLetters.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + "  ";
        for (int n = 2; n <= 4; n++) {
            for (int i = 0; i <= normalized.length() - n; i++) {
                String gram = normalized.substring(i, i + n);
                int hash = gram.hashCode();
                int bucket = Math.floorMod(hash, DIMENSIONS);
                vector[bucket] += (hash & 0x40000000) == 0 ? 1 : -1;
            }
        }
        double norm = Math.sqrt(java.util.Arrays.stream(vector).map(v -> v * v).sum());
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) out.append(',');
            out.append(norm == 0 ? 0 : vector[i] / norm);
        }
        return out.append(']').toString();
    }

    private String mergeSingleLetters(String raw) {
        String cleaned = raw.replaceAll("[*_\\-./#@|:;,(){}\\[\\]]+", " ").replaceAll("\\s+", " ").trim();
        List<String> rawTokens = Arrays.asList(cleaned.split(" "));
        StringBuilder merged = new StringBuilder();
        StringBuilder singleCharSeq = new StringBuilder();
        for (String token : rawTokens) {
            if (token.length() == 1 && Character.isLetterOrDigit(token.charAt(0))) {
                singleCharSeq.append(token);
            } else {
                if (singleCharSeq.length() > 0) {
                    if (merged.length() > 0) merged.append(" ");
                    merged.append(singleCharSeq);
                    singleCharSeq.setLength(0);
                }
                if (!token.isBlank()) {
                    if (merged.length() > 0) merged.append(" ");
                    merged.append(token);
                }
            }
        }
        if (singleCharSeq.length() > 0) {
            if (merged.length() > 0) merged.append(" ");
            merged.append(singleCharSeq);
        }
        return merged.toString();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private String fingerprint(String descriptor) throws NoSuchAlgorithmException {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest((apiKey + "\u0000" + descriptor.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(bytes);
    }

    public record Match(String merchant, double score) {}
}
