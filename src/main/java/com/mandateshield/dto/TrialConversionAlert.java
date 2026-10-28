package com.mandateshield.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TrialConversionAlert(String merchantName, Instant trialEndDate, BigDecimal expectedAmount) {}
