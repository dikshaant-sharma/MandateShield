package com.mandateshield.controller;

import com.mandateshield.domain.RawTransactionDocument;
import com.mandateshield.engine.MerchantResolverEngine;
import com.mandateshield.repository.RawTransactionRepository;
import com.mandateshield.service.GeminiMerchantEnrichmentService;
import com.mandateshield.service.SharedMerchantKnowledgeService;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.ZoneOffset;

@RestController @RequestMapping("/api/v1/transactions") @RequiredArgsConstructor
public class TransactionController {
    private final RawTransactionRepository transactions;
    private final MerchantResolverEngine resolver;
    private final GeminiMerchantEnrichmentService geminiEnrichment;
    private final SharedMerchantKnowledgeService sharedMerchantKnowledge;

    @GetMapping
    public java.util.List<TransactionView> list(@AuthenticationPrincipal Long userId) {
        return transactions.findByUserIdOrderByTransactionDateDesc(userId).stream().map(TransactionView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateTransaction request) {
        String descriptor = request.rawDescriptor() == null ? "" : request.rawDescriptor().trim();
        if (descriptor.isBlank()) throw new IllegalArgumentException("A transaction descriptor or merchant is required.");
        if (request.amount() == null) throw new IllegalArgumentException("A valid transaction amount is required.");
        BigDecimal amount = parseAmount(request.amount());
        boolean trialTransaction = Boolean.TRUE.equals(request.trialTransaction());
        if (amount.signum() < 0 || (amount.signum() == 0 && !trialTransaction))
            throw new IllegalArgumentException("Enter a positive amount, or mark a zero-value transaction as a trial.");
        Instant transactionDate = parseDate(request.transactionDate());
        String currency = request.currency() == null || request.currency().isBlank() ? "INR" : request.currency().trim().toUpperCase();
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter code.");
        RawTransactionDocument candidate = RawTransactionDocument.builder().userId(userId).rawDescriptor(descriptor)
                .amount(amount).currency(currency).transactionDate(transactionDate)
                .paymentRail(request.paymentRail() == null || request.paymentRail().isBlank() ? "OTHER" : request.paymentRail().trim().toUpperCase())
                .resolvedMerchantName(descriptor).resolvedCategory(request.category()).build();
        boolean suppliedMerchant = request.merchantName() != null && !request.merchantName().isBlank();
        MerchantResolverEngine.ResolveResult result = suppliedMerchant
                ? new MerchantResolverEngine.ResolveResult(request.merchantName().trim().toUpperCase(),
                    request.category() == null ? "Uncategorized" : request.category(), null, 0.99,
                    com.mandateshield.domain.ResolutionTier.TIER_1, null)
                : resolver.resolveExisting(candidate);
        candidate.setResolvedMerchantName(result.brandName() == null || result.brandName().isBlank() ? "UNKNOWN" : result.brandName());
        if (request.category() == null || request.category().isBlank()) candidate.setResolvedCategory(result.category());
        if ("OTHER".equals(candidate.getPaymentRail()) && result.gateway() != null) candidate.setPaymentRail(result.gateway());
        candidate.setConfidenceScore(result.confidenceScore());
        candidate.setResolutionTier(result.resolutionTier());
        candidate.setTrialTransaction(trialTransaction);
        if (trialTransaction && request.trialEndDate() != null && !request.trialEndDate().isBlank()) {
            candidate.setTrialEndDate(parseDateText(request.trialEndDate()));
            candidate.setTrialConversionAmount(request.trialConversionAmount() == null || request.trialConversionAmount().isNull()
                    || request.trialConversionAmount().asText().isBlank() ? null : parseAmount(request.trialConversionAmount()));
        }
        RawTransactionDocument saved = transactions.save(candidate);
        if (suppliedMerchant && Boolean.TRUE.equals(request.shareMerchant())) {
            sharedMerchantKnowledge.confirm(descriptor, result.brandName(), result.category());
            geminiEnrichment.synchronizeKnownTierOne(userId, descriptor, result.brandName(), result.confidenceScore());
        } else if (suppliedMerchant) {
            resolver.asyncWriteBackPattern(userId, descriptor, result.brandName(), result.category());
            geminiEnrichment.synchronizeKnownTierOne(userId, descriptor, result.brandName(), result.confidenceScore());
        } else if ("UNKNOWN".equalsIgnoreCase(result.brandName())) {
            geminiEnrichment.scheduleIfUnknown(userId, descriptor, result.confidenceScore(), result.brandName());
        } else if (result.resolutionTier() == com.mandateshield.domain.ResolutionTier.TIER_2) {
            resolver.asyncWriteBackPattern(userId, descriptor, result.brandName(), result.category());
        } else {
            geminiEnrichment.synchronizeKnownTierOne(userId, descriptor, result.brandName(), result.confidenceScore());
        }
        return TransactionView.from(saved);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public java.util.List<TransactionView> importTransactions(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody java.util.List<@Valid CreateTransaction> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 500)
            throw new IllegalArgumentException("Import between 1 and 500 transactions at a time.");
        return requests.stream().map(request -> create(userId, request)).toList();
    }

    private BigDecimal parseAmount(JsonNode node) {
        JsonNode value = node.isObject() && node.has("$numberDecimal") ? node.get("$numberDecimal") : node;
        if (value == null || (!value.isNumber() && !value.isTextual())) throw new IllegalArgumentException("A valid transaction amount is required.");
        try { return new BigDecimal(value.asText()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("A valid transaction amount is required."); }
    }

    private Instant parseDate(JsonNode node) {
        if (node == null || node.isNull()) throw new IllegalArgumentException("A transaction date is required.");
        if (node.isObject() && node.has("$date")) {
            JsonNode value = node.get("$date");
            if (value.isObject() && value.has("$numberLong")) return Instant.ofEpochMilli(Long.parseLong(value.get("$numberLong").asText()));
            node = value;
        }
        if (!node.isTextual()) throw new IllegalArgumentException("A valid ISO transaction date is required.");
        String value = node.asText();
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) {
            try { return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("A valid ISO transaction date is required."); }
        }
    }

    private Instant parseDateText(String value) {
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) {
            try { return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("A valid ISO trial end date is required."); }
        }
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable String id) {
        RawTransactionDocument tx = transactions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found."));
        transactions.delete(tx);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateTransaction(@JsonAlias({"merchant", "raw_descriptor", "rawDescriptor"}) @Size(max=250) String rawDescriptor,
                                    @JsonAlias({"merchant_name", "merchantName"}) @Size(max=80) String merchantName,
                                    @JsonAlias({"share_merchant", "shareMerchant"}) Boolean shareMerchant,
                                    @JsonAlias({"trial_transaction", "trialTransaction"}) Boolean trialTransaction,
                                    @JsonAlias({"trial_end_date", "trialEndDate"}) String trialEndDate,
                                    @JsonAlias({"trial_conversion_amount", "trialConversionAmount"}) JsonNode trialConversionAmount,
                                    JsonNode amount,
                                    String currency,
                                    @Size(max=60) String category,
                                    @JsonAlias("payment_rail") String paymentRail,
                                    @JsonAlias({"date", "transaction_date", "transactionDate"}) JsonNode transactionDate) {}
    public record TransactionView(String id, String merchant, String rawDescriptor, BigDecimal amount, String currency, String category,
                                  String paymentRail, LocalDate date, Double confidenceScore,
                                  com.mandateshield.domain.ResolutionTier resolutionTier) {
        static TransactionView from(RawTransactionDocument tx) {
            return new TransactionView(tx.getId(), tx.getResolvedMerchantName(), tx.getRawDescriptor(), tx.getAmount(), tx.getCurrency(),
                    tx.getResolvedCategory(), tx.getPaymentRail(), tx.getTransactionDate().atZone(ZoneOffset.UTC).toLocalDate(),
                    tx.getConfidenceScore(), tx.getResolutionTier());
        }
    }
}
