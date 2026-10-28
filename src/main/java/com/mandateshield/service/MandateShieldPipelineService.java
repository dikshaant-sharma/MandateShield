package com.mandateshield.service;

import com.mandateshield.domain.*;
import com.mandateshield.engine.MerchantResolverEngine;
import com.mandateshield.engine.anomaly.AnomalyDetectionEngine;
import com.mandateshield.engine.discovery.RecurringDiscoveryEngine;
import com.mandateshield.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MandateShieldPipelineService {
    private final RawTransactionRepository transactionRepository;
    private final MerchantResolverEngine resolverEngine;
    private final RecurringDiscoveryEngine discoveryEngine;
    private final AnomalyDetectionEngine anomalyDetectionEngine;
    private final SubscriptionRepository subscriptionRepository;
    private final AnomalyRepository anomalyRepository;
    private final UserRepository userRepository;

    public MandateShieldPipelineService(
            RawTransactionRepository transactionRepository,
            MerchantResolverEngine resolverEngine,
            RecurringDiscoveryEngine discoveryEngine,
            AnomalyDetectionEngine anomalyDetectionEngine,
            SubscriptionRepository subscriptionRepository,
            AnomalyRepository anomalyRepository,
            UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.resolverEngine = resolverEngine;
        this.discoveryEngine = discoveryEngine;
        this.anomalyDetectionEngine = anomalyDetectionEngine;
        this.subscriptionRepository = subscriptionRepository;
        this.anomalyRepository = anomalyRepository;
        this.userRepository = userRepository;
    }

    @Async
    public CompletableFuture<PipelineRunResult> processUser(Long userId) {
        return CompletableFuture.completedFuture(processUserNow(userId));
    }

    /** Runs the same user-scoped pipeline on a caller-managed background thread. */
    public PipelineRunResult processUserNow(Long userId) {
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User " + userId + " was not found"));
        List<RawTransactionDocument> transactions = transactionRepository.findByUserId(userId);
        if (transactions.isEmpty()) return new PipelineRunResult(userId, 0, 0, 0);

        for (RawTransactionDocument tx : transactions) {
            if (tx.getRawDescriptor() == null || tx.getRawDescriptor().isBlank()) continue;
            MerchantResolverEngine.ResolveResult result = resolverEngine.resolveExisting(tx);
            // A low-confidence descriptor is being enriched in the background; don't save a stale snapshot.
            if ("UNKNOWN".equalsIgnoreCase(result.brandName()) && result.confidenceScore() <= 0.50) continue;
            if (result.brandName() != null && !result.brandName().isBlank()
                    && !"UNKNOWN".equalsIgnoreCase(result.brandName())) {
                tx.setResolvedMerchantName(result.brandName());
            }
            if ((tx.getResolvedCategory() == null || tx.getResolvedCategory().isBlank()
                    || "Uncategorized".equalsIgnoreCase(tx.getResolvedCategory()))
                    && result.category() != null && !result.category().isBlank()) {
                tx.setResolvedCategory(result.category());
            }
            // The user or raw import may provide a more reliable payment rail than descriptor heuristics.
            if ((tx.getPaymentRail() == null || tx.getPaymentRail().isBlank()
                    || "OTHER".equalsIgnoreCase(tx.getPaymentRail()) || "UNKNOWN".equalsIgnoreCase(tx.getPaymentRail()))
                    && result.gateway() != null && !"UNKNOWN".equalsIgnoreCase(result.gateway())) {
                tx.setPaymentRail(result.gateway());
            }
            tx.setConfidenceScore(result.confidenceScore());
            tx.setResolutionTier(result.resolutionTier());
            transactionRepository.save(tx);
        }

        Map<String, List<RawTransactionDocument>> grouped = transactions.stream()
                .filter(tx -> tx.getResolvedMerchantName() != null && !tx.getResolvedMerchantName().isBlank()
                        && !"UNKNOWN".equalsIgnoreCase(tx.getResolvedMerchantName()))
                .collect(Collectors.groupingBy(RawTransactionDocument::getResolvedMerchantName));
        int discovered = 0;
        int anomalyCount = 0;
        for (var entry : grouped.entrySet()) {
            SubscriptionDocument candidate = discoveryEngine.discover(userId, entry.getKey(), entry.getValue(), null);
            if (candidate == null) continue;
            SubscriptionDocument existing = subscriptionRepository.findByUserIdAndMerchantName(userId, entry.getKey()).orElse(null);
            if (existing != null) {
                candidate.setId(existing.getId());
                // A pipeline refresh must retain explicit user feedback attached to this commitment.
                candidate.setUserReportedUnused(existing.getUserReportedUnused());
                candidate.setLastUserActivityDate(existing.getLastUserActivityDate());
                candidate.setFirstDetectedAt(existing.getFirstDetectedAt() == null ? candidate.getFirstDetectedAt() : existing.getFirstDetectedAt());
                if (existing.getStatus() == SubscriptionStatus.CANCELLED
                        || existing.getStatus() == SubscriptionStatus.CANCELLATION_REQUESTED) candidate.setStatus(existing.getStatus());
            }
            SubscriptionDocument saved = subscriptionRepository.save(candidate);
            discovered++;
            anomalyRepository.deleteBySubscriptionId(saved.getId());
            List<AnomalyDocument> anomalies = anomalyDetectionEngine.detectAnomalies(saved, entry.getValue());
            if (!anomalies.isEmpty()) {
                anomalyRepository.saveAll(anomalies);
                anomalyCount += anomalies.size();
            }
        }
        log.info("Pipeline processed user {}: {} transactions, {} subscriptions, {} anomalies", userId, transactions.size(), discovered, anomalyCount);
        return new PipelineRunResult(userId, transactions.size(), discovered, anomalyCount);
    }

    public record PipelineRunResult(Long userId, int processedTransactions, int discoveredSubscriptions, int detectedAnomalies) {}
}
