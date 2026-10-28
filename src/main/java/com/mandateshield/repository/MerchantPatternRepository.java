package com.mandateshield.repository;

import com.mandateshield.domain.MerchantPatternDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for merchant pattern documents. The custom regex search is performed
 * via {@link org.springframework.data.mongodb.core.MongoTemplate} inside the
 * resolver service, but we expose a convenient method for potential use.
 */
@Repository
public interface MerchantPatternRepository extends MongoRepository<MerchantPatternDocument, String> {
    Optional<MerchantPatternDocument> findFirstByPatternRegexRegex(String rawDescriptor);
}
