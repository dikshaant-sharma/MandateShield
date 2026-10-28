package com.mandateshield.service;

import com.mandateshield.domain.MerchantAliasDocument;
import com.mandateshield.repository.MerchantAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Mongo-backed global alias dictionary with a Redis-like hot cache. Opt-in confirmations only. */
@Service
@RequiredArgsConstructor
public class SharedMerchantKnowledgeService {
    private final MerchantAliasRepository repository;
    private final ConcurrentHashMap<String, MerchantAliasDocument> cache = new ConcurrentHashMap<>();

    public Optional<MerchantAliasDocument> find(String rawDescriptor) {
        String fingerprint = fingerprint(rawDescriptor);
        if (fingerprint == null) return Optional.empty();
        MerchantAliasDocument cached = cache.get(fingerprint);
        if (cached != null) return Optional.of(cached);
        return repository.findByDescriptorFingerprint(fingerprint).map(alias -> {
            cache.putIfAbsent(fingerprint, alias);
            return alias;
        });
    }

    public MerchantAliasDocument confirm(String rawDescriptor, String merchantName, String category) {
        String fingerprint = fingerprint(rawDescriptor);
        if (fingerprint == null) throw new IllegalArgumentException("A descriptor is required to save a shared merchant match.");
        if (merchantName == null || merchantName.isBlank() || merchantName.length() > 80)
            throw new IllegalArgumentException("Enter a valid merchant name.");
        MerchantAliasDocument alias = repository.findByDescriptorFingerprint(fingerprint)
                .orElseGet(() -> MerchantAliasDocument.builder().descriptorFingerprint(fingerprint).build());
        if (alias.getMerchantName() != null && !alias.getMerchantName().equalsIgnoreCase(merchantName.trim()))
            throw new IllegalArgumentException("This shared descriptor is already matched to a different merchant. It has not been overwritten.");
        alias.setMerchantName(merchantName.trim());
        alias.setCategory(category == null || category.isBlank() ? "Uncategorized" : category.trim());
        alias.setVerificationSource("USER_CONFIRMED");
        alias.setConfirmedAt(Instant.now());
        alias = repository.save(alias);
        cache.put(fingerprint, alias);
        return alias;
    }

    private String fingerprint(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) return null;
        // Keep merchant words, remove volatile account/reference numbers and separators.
        String normalized = descriptor.toUpperCase(Locale.ROOT)
                .replaceAll("\\b\\d{2,}\\b", " ")
                .replaceAll("[^A-Z]+", " ").trim().replaceAll("\\s+", " ");
        if (normalized.length() < 3) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}
