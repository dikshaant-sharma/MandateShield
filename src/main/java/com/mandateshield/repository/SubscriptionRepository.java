package com.mandateshield.repository;

import com.mandateshield.domain.SubscriptionDocument;
import com.mandateshield.domain.SubscriptionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends MongoRepository<SubscriptionDocument, String> {
    List<SubscriptionDocument> findByUserId(Long userId);
    List<SubscriptionDocument> findByUserIdAndStatus(Long userId, SubscriptionStatus status);
    List<SubscriptionDocument> findByUserIdAndStatusIn(Long userId, List<SubscriptionStatus> statuses);
    Optional<SubscriptionDocument> findByUserIdAndMerchantName(Long userId, String merchantName);
    Optional<SubscriptionDocument> findByIdAndUserId(String id, Long userId);
}
