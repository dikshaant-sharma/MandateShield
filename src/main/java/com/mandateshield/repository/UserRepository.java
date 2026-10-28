package com.mandateshield.repository;

import com.mandateshield.domain.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<UserDocument, Long> {
    Optional<UserDocument> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
