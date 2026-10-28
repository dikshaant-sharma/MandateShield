package com.mandateshield.repository;

import com.mandateshield.domain.RawTransactionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RawTransactionRepository extends MongoRepository<RawTransactionDocument, String> {
    List<RawTransactionDocument> findByUserId(Long userId);
    List<RawTransactionDocument> findByUserIdAndSyntheticTrue(Long userId);
    List<RawTransactionDocument> findByUserIdAndRawDescriptor(Long userId, String rawDescriptor);
    List<RawTransactionDocument> findByUserIdOrderByTransactionDateDesc(Long userId);
    java.util.Optional<RawTransactionDocument> findByIdAndUserId(String id, Long userId);
}
