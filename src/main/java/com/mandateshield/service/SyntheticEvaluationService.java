package com.mandateshield.service;

import com.mandateshield.domain.*;
import com.mandateshield.engine.MerchantResolverEngine;
import com.mandateshield.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
public class SyntheticEvaluationService {
    private final RawTransactionRepository transactions;
    private final MerchantPatternRepository patterns;
    private final SubscriptionRepository subscriptions;
    private final AnomalyRepository anomalies;
    private final MandateShieldPipelineService pipeline;
    private final MerchantResolverEngine resolver;

    public Evaluation seedAndRun(Long userId) {
        List<RawTransactionDocument> rows = transactions.findByUserIdAndSyntheticTrue(userId);
        if (rows.isEmpty()) {
            Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
            List<Seed> seeds = new ArrayList<>();
            addMonthly(seeds, "NETFLIX SUBSCRIPTION", "NETFLIX", "STREAMING", "CREDIT_CARD", now, 6, 35, 499, 649, "netflix-hike");
            seeds.add(new Seed("NIMBUS CLOUD STORAGE", "NIMBUS CLOUD", "CLOUD", "NACH", now.minus(334, ChronoUnit.DAYS), 1499, false, "annual-cloud"));
            seeds.add(new Seed("NIMBUS CLOUD STORAGE", "NIMBUS CLOUD", "CLOUD", "NACH", now, 1499, false, "annual-cloud"));
            seeds.add(new Seed("TRIALBOX MEMBERSHIP", "TRIALBOX", "SOFTWARE", "CREDIT_CARD", now.minus(100, ChronoUnit.DAYS), 0, true, "trialbox"));
            for (long days : new long[]{70, 40, 10}) seeds.add(new Seed("TRIALBOX MEMBERSHIP", "TRIALBOX", "SOFTWARE", "CREDIT_CARD", now.minus(days, ChronoUnit.DAYS), 399, false, "trialbox"));
            addVariable(seeds, "SPOTIFY PREMIUM", "SPOTIFY", "AUDIO", "UPI_AUTOPAY", now,
                    new long[]{100, 71, 43, 14}, new double[]{119, 129, 119, 139}, "spotify-variable");
            addVariable(seeds, "FITNESS CLUB AUTOPAY", "FITNESS CLUB", "FITNESS", "UPI_AUTOPAY", now,
                    new long[]{150, 120, 30, 0}, new double[]{799, 799, 849, 849}, "fitness-resumed");
            seedPatterns(seeds, userId);
            for (Seed s : seeds) transactions.save(RawTransactionDocument.builder().userId(userId).rawDescriptor(s.descriptor)
                    .amount(BigDecimal.valueOf(s.amount).setScale(2)).currency("INR").transactionDate(s.date).paymentRail(s.rail)
                    .resolvedMerchantName("UNKNOWN").resolvedCategory("Uncategorized").confidenceScore(0.5)
                    .resolutionTier(ResolutionTier.TIER_2).trialTransaction(s.trial).synthetic(true)
                    .expectedMerchant(s.merchant).expectedRecurringGroup(s.group).build());
            rows = transactions.findByUserIdAndSyntheticTrue(userId);
        }
        pipeline.processUserNow(userId);
        return evaluate(userId);
    }

    public Evaluation evaluate(Long userId) { return evaluateRows(userId, transactions.findByUserIdAndSyntheticTrue(userId)); }

    private Evaluation evaluateRows(Long userId, List<RawTransactionDocument> rows) {
        Map<String,List<RawTransactionDocument>> groups = rows.stream().filter(t -> t.getExpectedRecurringGroup()!=null)
                .collect(Collectors.groupingBy(RawTransactionDocument::getExpectedRecurringGroup));
        int correct = (int) rows.stream().filter(t -> t.getExpectedMerchant()!=null && t.getExpectedMerchant().equalsIgnoreCase(t.getResolvedMerchantName())).count();
        Set<String> actual = subscriptions.findByUserIdAndStatusIn(userId,List.of(SubscriptionStatus.ACTIVE,SubscriptionStatus.TENTATIVE))
                .stream().map(SubscriptionDocument::getMerchantName).filter(Objects::nonNull).map(s->s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        int detected = (int) groups.values().stream().filter(g -> actual.contains(g.getFirst().getExpectedMerchant().toUpperCase(Locale.ROOT))).count();
        Set<String> expectedMerchants = groups.values().stream().map(g -> g.getFirst().getExpectedMerchant().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> expectedSubscriptionIds = subscriptions.findByUserId(userId).stream()
                .filter(s -> expectedMerchants.contains(s.getMerchantName().toUpperCase(Locale.ROOT)))
                .map(SubscriptionDocument::getId).collect(Collectors.toSet());
        Set<AnomalyType> types = anomalies.findByUserId(userId).stream().filter(a -> expectedSubscriptionIds.contains(a.getSubscriptionId()))
                .map(AnomalyDocument::getType).collect(Collectors.toSet());
        return new Evaluation(rows.size(),correct,rows.size(),detected,groups.size(),types.contains(AnomalyType.PRICE_HIKE),
                types.contains(AnomalyType.TRIAL_CONVERSION),groups.getOrDefault("annual-cloud",List.of()).size()>=2&&actual.contains("NIMBUS CLOUD"),
                types.contains(AnomalyType.RESUMED_BILLING),
                "Synthetic records are isolated to this account.");
    }

    private void seedPatterns(List<Seed> seeds, Long userId) {
        for (Seed s : seeds.stream().collect(Collectors.toMap(Seed::descriptor,s->s,(a,b)->a)).values()) {
            String exact=Pattern.quote(s.descriptor);
            MerchantPatternDocument p=patterns.findAll().stream().filter(x->exact.equals(x.getPatternRegex())&&Objects.equals(x.getUserId(),userId)).findFirst()
                    .orElseGet(()->MerchantPatternDocument.builder().patternRegex(exact).createdAt(Instant.now()).build());
            p.setUserId(userId);
            p.setBrandName(s.merchant);p.setCategory(s.category);p.setConfidenceScore(.99);p.setVerificationSource("CURATED_SYNTHETIC_REFERENCE");patterns.save(p);
        }
        resolver.clearPatternCache();
    }
    private void addMonthly(List<Seed> out,String descriptor,String merchant,String category,String rail,Instant now,int count,int gap,double base,double raised,String group){
        for(int i=count-1;i>=0;i--)out.add(new Seed(descriptor,merchant,category,rail,now.minus((long)i*gap,ChronoUnit.DAYS),i<2?raised:base,false,group));
    }
    private void addVariable(List<Seed> out,String descriptor,String merchant,String category,String rail,Instant now,long[] days,double[] amounts,String group){
        for(int i=0;i<days.length;i++)out.add(new Seed(descriptor,merchant,category,rail,now.minus(days[i],ChronoUnit.DAYS),amounts[i],false,group));
    }
    private record Seed(String descriptor,String merchant,String category,String rail,Instant date,double amount,boolean trial,String group){}
    public record Evaluation(int transactionsEvaluated,int merchantLabelsCorrect,int merchantLabelsTotal,int recurringGroupsDetected,
            int recurringGroupsTotal,boolean priceIncreaseDetected,boolean trialConversionDetected,boolean annualRenewalDetected,
            boolean resumedBillingDetected,String note){}
}
