package com.mandateshield.repository;

import com.mandateshield.domain.AnomalyDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AnomalyRepository extends MongoRepository<AnomalyDocument, String> {
    List<AnomalyDocument> findByUserId(Long userId);
    List<AnomalyDocument> findBySubscriptionId(String subscriptionId);
    void deleteBySubscriptionId(String subscriptionId);
}
