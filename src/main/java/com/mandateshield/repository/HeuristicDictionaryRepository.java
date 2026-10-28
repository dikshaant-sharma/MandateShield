package com.mandateshield.repository;

import com.mandateshield.domain.HeuristicDictionaryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeuristicDictionaryRepository extends MongoRepository<HeuristicDictionaryDocument, String> {
}
