package com.mandateshield.engine.discovery;

import com.mandateshield.domain.RawTransactionDocument;
import com.mandateshield.domain.SubscriptionDocument;
import com.mandateshield.domain.SubscriptionFrequency;
import com.mandateshield.domain.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/** Scores repeating payment intervals and preserves uncertain/annual candidates for user review. */
@Component
public class RecurringDiscoveryEngine {
    private static final List<Period> PERIODS = List.of(
            new Period(SubscriptionFrequency.DAILY, 3.0, 1, 5),
            new Period(SubscriptionFrequency.WEEKLY, 7.0, 5, 11),
            new Period(SubscriptionFrequency.BIWEEKLY, 14.0, 12, 21),
            new Period(SubscriptionFrequency.MONTHLY, 30.44, 22, 45),
            new Period(SubscriptionFrequency.BIMONTHLY, 60.88, 46, 75),
            new Period(SubscriptionFrequency.QUARTERLY, 91.31, 76, 128),
            new Period(SubscriptionFrequency.SEMIANNUAL, 182.62, 129, 240),
            // Include eleven-month renewals: billing dates commonly drift or renew early.
            new Period(SubscriptionFrequency.ANNUAL, 365.24, 241, 430));

    public SubscriptionDocument discover(Long userId, String merchantName,
                                          List<RawTransactionDocument> transactions,
                                          java.time.Instant lastUserActivity) {
        List<RawTransactionDocument> dated = transactions == null ? List.of() : transactions.stream()
                .filter(tx -> tx != null && tx.getTransactionDate() != null)
                .sorted(Comparator.comparing(RawTransactionDocument::getTransactionDate))
                .toList();
        if (dated.size() < 2) return null;
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < dated.size(); i++) {
            double days = Duration.between(dated.get(i - 1).getTransactionDate(), dated.get(i).getTransactionDate()).toHours() / 24.0;
            if (days > 0) intervals.add(days);
        }
        if (intervals.isEmpty()) return null;

        PeriodScore best = PERIODS.stream()
                .filter(period -> period.frequency != SubscriptionFrequency.DAILY || dated.size() >= 3)
                .map(period -> score(period, intervals))
                .max(Comparator.comparingDouble(PeriodScore::confidence)).orElse(null);
        SubscriptionFrequency frequency;
        double confidence;
        String evidence;
        if (best != null && best.confidence >= 0.48) {
            frequency = best.period.frequency;
            confidence = best.confidence;
            evidence = String.format(Locale.ROOT, "%d of %d payment gaps fit a %s pattern (median %.0f days).",
                    best.matches, intervals.size(), frequency.name().toLowerCase(Locale.ROOT), median(intervals));
        } else if (intervals.size() >= 2 && intervals.stream().allMatch(days -> days >= 14 && days <= 430)) {
            frequency = SubscriptionFrequency.IRREGULAR;
            double median = median(intervals);
            confidence = Math.min(0.72, 0.48 + Math.min(0.12, intervals.size() * 0.03));
            evidence = String.format(Locale.ROOT, "%d payments recur on a variable schedule (median gap %.0f days).",
                    dated.size(), median);
        } else return null;

        boolean tentative = dated.size() < 3 || confidence < 0.70;
        RawTransactionDocument latest = dated.getLast();
        return SubscriptionDocument.builder().userId(userId).merchantName(merchantName)
                .category(latest.getResolvedCategory()).currentAmount(latest.getAmount())
                .frequency(frequency).status(tentative ? SubscriptionStatus.TENTATIVE : SubscriptionStatus.ACTIVE)
                .paymentRail(latest.getPaymentRail()).lastPaymentDate(latest.getTransactionDate())
                .lastUserActivityDate(lastUserActivity).firstDetectedAt(java.time.Instant.now())
                .detectionConfidence(confidence).detectionEvidence(evidence)
                .averageIntervalDays(median(intervals)).build();
    }

    private PeriodScore score(Period period, List<Double> intervals) {
        int matches = 0;
        double error = 0;
        for (double gap : intervals) {
            if (gap < period.minDays || gap > period.maxDays) continue;
            matches++;
            error += Math.min(1.0, Math.abs(gap - period.days) / period.days);
        }
        if (matches == 0) return new PeriodScore(period, 0, 0);
        double coverage = (double) matches / intervals.size();
        double fit = 1.0 - (error / matches);
        // Two observed gaps are enough to show a repeatable pattern, while a single gap stays provisional.
        double support = Math.min(1.0, 0.70 + matches * 0.15);
        // Diminishingly penalize gaps outside the cadence: one duplicate, delayed, or imported charge
        // should lower confidence without erasing a pattern supported by multiple matching intervals.
        return new PeriodScore(period, Math.sqrt(coverage) * (0.55 + fit * 0.25) * support, matches);
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private record Period(SubscriptionFrequency frequency, double days, double minDays, double maxDays) {}
    private record PeriodScore(Period period, double confidence, int matches) {}
}
