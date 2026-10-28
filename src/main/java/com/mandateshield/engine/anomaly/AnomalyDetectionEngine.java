package com.mandateshield.engine.anomaly;

import com.mandateshield.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class AnomalyDetectionEngine {
    private static final BigDecimal MICRO_CHARGE_LIMIT = new BigDecimal("5.00");

    public List<AnomalyDocument> detectAnomalies(SubscriptionDocument subscription, List<RawTransactionDocument> rawTransactions) {
        if (subscription == null || rawTransactions == null || rawTransactions.isEmpty()) return List.of();
        List<RawTransactionDocument> transactions = rawTransactions.stream()
                .filter(tx -> tx != null && tx.getTransactionDate() != null)
                .sorted(Comparator.comparing(RawTransactionDocument::getTransactionDate))
                .toList();
        List<AnomalyDocument> anomalies = new ArrayList<>();
        checkAnnualRenewal(subscription, transactions).ifPresent(anomalies::add);
        checkResumedBilling(subscription, transactions).ifPresent(anomalies::add);
        checkPriceHike(subscription, transactions).ifPresent(anomalies::add);
        checkFreeTrialFlip(subscription, transactions).ifPresent(anomalies::add);
        checkUnusedSubscription(subscription).ifPresent(anomalies::add);
        return anomalies;
    }

    private Optional<AnomalyDocument> checkPriceHike(SubscriptionDocument sub, List<RawTransactionDocument> txs) {
        if (txs.size() < 3) return Optional.empty();
        BigDecimal latest = txs.getLast().getAmount();
        List<BigDecimal> history = txs.subList(0, txs.size() - 1).stream().map(RawTransactionDocument::getAmount)
                .filter(java.util.Objects::nonNull).sorted().toList();
        if (latest == null || history.size() < 2) return Optional.empty();
        BigDecimal typical = history.size() % 2 == 1 ? history.get(history.size() / 2)
                : history.get(history.size() / 2 - 1).add(history.get(history.size() / 2)).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        if (typical.signum() <= 0 || latest.compareTo(typical.multiply(new BigDecimal("1.05"))) < 0) return Optional.empty();
        BigDecimal priorMaximum = history.getLast();
        long priorElevatedCount = history.stream().filter(amount -> amount.compareTo(typical.multiply(new BigDecimal("1.05"))) >= 0).count();
        boolean newStepUp = latest.compareTo(priorMaximum.multiply(new BigDecimal("1.05"))) >= 0;
        boolean sustainedStepUp = priorElevatedCount > 0;
        if (!newStepUp && !sustainedStepUp) return Optional.empty();
        BigDecimal annualDelta = annualize(sub, latest.subtract(typical));
        return Optional.of(anomaly(sub, AnomalyType.PRICE_HIKE, AnomalySeverity.MEDIUM,
                "Latest charge " + latest + " is above the previous typical charge of " + typical + " ("
                        + latest.subtract(typical).multiply(BigDecimal.valueOf(100)).divide(typical, 0, java.math.RoundingMode.HALF_UP)
                        + "% higher). A 5% or larger step-up is flagged for review.", annualDelta));
    }

    private Optional<AnomalyDocument> checkAnnualRenewal(SubscriptionDocument sub, List<RawTransactionDocument> txs) {
        if (sub.getFrequency() != SubscriptionFrequency.ANNUAL || txs.size() < 2) return Optional.empty();
        BigDecimal amount = txs.getLast().getAmount();
        if (amount == null) return Optional.empty();
        return Optional.of(anomaly(sub, AnomalyType.ANNUAL_RENEWAL, AnomalySeverity.MEDIUM,
                "An annual renewal pattern is active; the next charge may be a full year of service at once.", amount));
    }

    private Optional<AnomalyDocument> checkResumedBilling(SubscriptionDocument sub, List<RawTransactionDocument> txs) {
        if (txs.size() < 3 || sub.getFrequency() == SubscriptionFrequency.ANNUAL) return Optional.empty();
        double baseline = sub.getAverageIntervalDays() == null ? 0 : sub.getAverageIntervalDays();
        if (baseline <= 0) {
            List<Double> gaps = new ArrayList<>();
            for (int i = 1; i < txs.size(); i++) gaps.add(Duration.between(
                    txs.get(i - 1).getTransactionDate(), txs.get(i).getTransactionDate()).toHours() / 24.0);
            baseline = gaps.stream().filter(gap -> gap > 0 && gap < 75).mapToDouble(Double::doubleValue)
                    .sorted().skip(Math.max(0, (gaps.size() - 1) / 2)).findFirst().orElse(0);
        }
        if (baseline <= 0) return Optional.empty();
        for (int i = 1; i < txs.size(); i++) {
            double gap = Duration.between(txs.get(i - 1).getTransactionDate(), txs.get(i).getTransactionDate()).toHours() / 24.0;
            if (gap < Math.max(75, baseline * 2.5)) continue;
            BigDecimal amount = txs.get(i).getAmount();
            if (amount == null) continue;
            return Optional.of(anomaly(sub, AnomalyType.RESUMED_BILLING, AnomalySeverity.MEDIUM,
                    "Billing resumed after a gap of " + Math.round(gap) + " days; review whether you still want this mandate.", annualize(sub, amount)));
        }
        return Optional.empty();
    }

    private Optional<AnomalyDocument> checkFreeTrialFlip(SubscriptionDocument sub, List<RawTransactionDocument> txs) {
        if (txs.size() < 2) return Optional.empty();
        boolean markedTrial = txs.stream().limit(txs.size() - 1L).anyMatch(tx -> Boolean.TRUE.equals(tx.getTrialTransaction()));
        BigDecimal latest = txs.getLast().getAmount();
        if (!markedTrial || latest == null || latest.compareTo(MICRO_CHARGE_LIMIT) <= 0) return Optional.empty();
        return Optional.of(anomaly(sub, AnomalyType.TRIAL_CONVERSION, AnomalySeverity.HIGH,
                "You marked an earlier payment as a trial; a later full-price charge followed it.", annualize(sub, latest)));
    }

    private Optional<AnomalyDocument> checkUnusedSubscription(SubscriptionDocument sub) {
        Instant observationStart = sub.getFirstDetectedAt() != null ? sub.getFirstDetectedAt() : sub.getLastPaymentDate();
        boolean noRecentConfirmation = sub.getLastUserActivityDate() == null
                ? observationStart != null && observationStart.isBefore(Instant.now().minus(Duration.ofDays(90)))
                : sub.getLastUserActivityDate().isBefore(Instant.now().minus(Duration.ofDays(90)));
        if (!Boolean.TRUE.equals(sub.getUserReportedUnused()) && !noRecentConfirmation) return Optional.empty();
        BigDecimal impact = annualize(sub, sub.getCurrentAmount() == null ? BigDecimal.ZERO : sub.getCurrentAmount());
        return Optional.of(anomaly(sub, AnomalyType.UNUSED_SUBSCRIPTION, AnomalySeverity.HIGH,
                Boolean.TRUE.equals(sub.getUserReportedUnused())
                        ? "You reported that you stopped using this service while its recurring charge remains active."
                        : "No usage confirmation has been recorded for over 90 days while this recurring charge remains active.", impact));
    }

    private BigDecimal annualize(SubscriptionDocument sub, BigDecimal amount) {
        return switch (sub.getFrequency() == null ? SubscriptionFrequency.UNKNOWN : sub.getFrequency()) {
            case DAILY -> amount.multiply(BigDecimal.valueOf(365));
            case WEEKLY -> amount.multiply(BigDecimal.valueOf(52));
            case BIWEEKLY -> amount.multiply(BigDecimal.valueOf(26));
            case MONTHLY -> amount.multiply(BigDecimal.valueOf(12));
            case BIMONTHLY -> amount.multiply(BigDecimal.valueOf(6));
            case QUARTERLY -> amount.multiply(BigDecimal.valueOf(4));
            case SEMIANNUAL -> amount.multiply(BigDecimal.valueOf(2));
            case ANNUAL -> amount;
            case IRREGULAR -> sub.getAverageIntervalDays() == null || sub.getAverageIntervalDays() <= 0
                    ? amount.multiply(BigDecimal.valueOf(12)) : amount.multiply(BigDecimal.valueOf(365.0 / sub.getAverageIntervalDays()));
            case UNKNOWN -> amount.multiply(BigDecimal.valueOf(12));
        };
    }

    private AnomalyDocument anomaly(SubscriptionDocument sub, AnomalyType type, AnomalySeverity severity,
                                   String description, BigDecimal impact) {
        return AnomalyDocument.builder().subscriptionId(sub.getId()).userId(sub.getUserId())
                .type(type).severity(severity).description(description).financialImpact(impact)
                .detectedAt(Instant.now()).build();
    }
}
