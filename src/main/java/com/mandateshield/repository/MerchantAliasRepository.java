package com.mandateshield.repository;

import com.mandateshield.domain.MerchantAliasDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MerchantAliasRepository extends MongoRepository<MerchantAliasDocument, String> {
    Optional<MerchantAliasDocument> findByDescriptorFingerprint(String descriptorFingerprint);
}
