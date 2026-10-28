package com.mandateshield.controller;

import com.mandateshield.service.CancellationAndRevocationService;
import com.mandateshield.domain.SubscriptionDocument;
import com.mandateshield.domain.SubscriptionStatus;
import com.mandateshield.repository.SubscriptionRepository;
import com.mandateshield.repository.UserRepository;
import com.mandateshield.service.MandateShieldPipelineService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cancellation")
@RequiredArgsConstructor
public class CancellationController {

    private final CancellationAndRevocationService cancellationService;
    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final MandateShieldPipelineService pipeline;

    @GetMapping("/playbook")
    public ResponseEntity<?> getPlaybook(@RequestParam String rail, @RequestParam(required = false) String merchant) {
        return ResponseEntity.ok(cancellationService.getPlaybookDetails(rail, merchant));
    }

    @PostMapping("/generate-letter")
    public ResponseEntity<?> generateLetter(@AuthenticationPrincipal Long userId, @Valid @RequestBody RevocationRequest request) {
        String merchantName;
        if (request.subscriptionId() != null && !request.subscriptionId().isBlank()) {
            SubscriptionDocument subscription = subscriptions.findByIdAndUserId(request.subscriptionId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring payment not found."));
            merchantName = subscription.getMerchantName();
        } else if (request.merchantName() != null && !request.merchantName().isBlank()) {
            merchantName = request.merchantName().trim();
        } else {
            throw new IllegalArgumentException("Provide a recurring payment or merchant name for the notice.");
        }
        String userName = users.findById(userId).map(user -> user.getFullName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        String letter = cancellationService.generateRevocationLetter(
                userName,
                request.bankName(),
                merchantName,
                request.mandateRef()
        );
        return ResponseEntity.ok(Map.of("letter", letter));
    }

    @PostMapping("/{subscriptionId}/request")
    public ResponseEntity<?> requestCancellation(@AuthenticationPrincipal Long userId, @PathVariable String subscriptionId) {
        SubscriptionDocument subscription = subscriptions.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring payment not found."));
        subscription.setStatus(SubscriptionStatus.CANCELLATION_REQUESTED);
        subscriptions.save(subscription);
        return ResponseEntity.ok(Map.of("status", subscription.getStatus(), "subscriptionId", subscription.getId(),
                "message", "Marked as requested. Confirm with your bank or merchant to stop future charges."));
    }

    @PostMapping("/{subscriptionId}/confirm")
    public ResponseEntity<?> confirmCancellation(@AuthenticationPrincipal Long userId, @PathVariable String subscriptionId) {
        SubscriptionDocument subscription = subscriptions.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring payment not found."));
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscriptions.save(subscription);
        return ResponseEntity.ok(Map.of("status", subscription.getStatus(), "subscriptionId", subscription.getId()));
    }

    @PostMapping("/{subscriptionId}/report-unused")
    public ResponseEntity<?> reportUnused(@AuthenticationPrincipal Long userId, @PathVariable String subscriptionId) {
        SubscriptionDocument subscription = subscriptions.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring payment not found."));
        subscription.setUserReportedUnused(true);
        subscriptions.save(subscription);
        pipeline.processUserNow(userId);
        return ResponseEntity.ok(Map.of("subscriptionId", subscription.getId(), "message", "Your report is saved and included in this payment review."));
    }

    @PostMapping("/{subscriptionId}/confirm-activity")
    public ResponseEntity<?> confirmActivity(@AuthenticationPrincipal Long userId, @PathVariable String subscriptionId) {
        SubscriptionDocument subscription = subscriptions.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring payment not found."));
        subscription.setLastUserActivityDate(java.time.Instant.now());
        subscription.setUserReportedUnused(false);
        subscriptions.save(subscription);
        pipeline.processUserNow(userId);
        return ResponseEntity.ok(Map.of("subscriptionId", subscription.getId(), "lastUserActivityDate", subscription.getLastUserActivityDate()));
    }

    @PostMapping("/{subscriptionId}/revoke")
    public ResponseEntity<?> revoke(@AuthenticationPrincipal Long userId, @PathVariable String subscriptionId) {
        SubscriptionDocument subscription = subscriptions.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring payment not found."));
        subscription.setStatus(SubscriptionStatus.CANCELLATION_REQUESTED);
        subscriptions.save(subscription);
        return ResponseEntity.ok(Map.of("status", subscription.getStatus(), "subscriptionId", subscription.getId(),
                "message", "Cancellation request recorded; confirm with the provider before treating this as stopped."));
    }

    public record RevocationRequest(String subscriptionId, String merchantName,
                                    @NotBlank String bankName, @NotBlank String mandateRef) {}
}
