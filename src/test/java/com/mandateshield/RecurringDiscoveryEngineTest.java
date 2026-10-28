package com.mandateshield;

import com.mandateshield.domain.RawTransactionDocument;
import com.mandateshield.domain.SubscriptionFrequency;
import com.mandateshield.domain.SubscriptionStatus;
import com.mandateshield.engine.discovery.RecurringDiscoveryEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurringDiscoveryEngineTest {
    private final RecurringDiscoveryEngine engine = new RecurringDiscoveryEngine();

    @Test
    void monthlyPatternSurvivesAnIsolatedEarlyCharge() {
        Instant today = Instant.parse("2026-10-28T00:00:00Z");
        List<RawTransactionDocument> transactions = List.of(100L, 71L, 43L, 27L, 14L).stream()
                .map(daysAgo -> RawTransactionDocument.builder()
                        .transactionDate(today.minus(daysAgo, ChronoUnit.DAYS))
                        .amount(BigDecimal.valueOf(119))
                        .build())
                .toList();

        var subscription = engine.discover(7L, "SPOTIFY", transactions, null);

        assertNotNull(subscription, "Two matching monthly gaps should preserve the recurring candidate");
        assertEquals(SubscriptionFrequency.MONTHLY, subscription.getFrequency());
        assertEquals(SubscriptionStatus.TENTATIVE, subscription.getStatus(), "Outlier gaps should reduce confidence and keep review provisional");
    }
}
