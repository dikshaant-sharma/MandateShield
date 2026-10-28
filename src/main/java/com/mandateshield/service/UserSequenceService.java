package com.mandateshield.service;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import com.mandateshield.repository.UserRepository;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class UserSequenceService {
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    public UserSequenceService(MongoTemplate mongoTemplate, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
    }

    private static final String SEQUENCE_ID = "user_ids";

    public long nextUserId() {
        Sequence sequence = mongoTemplate.findById(SEQUENCE_ID, Sequence.class);
        long safeStart = Math.max(System.currentTimeMillis(), userRepository.findAll().stream()
                .mapToLong(user -> user.getId() == null ? 0 : user.getId()).max().orElse(0));
        if (sequence == null) {
            try {
                mongoTemplate.insert(new Sequence(SEQUENCE_ID, safeStart));
            } catch (DuplicateKeyException concurrentInitialization) {
                // Another application instance initialized the shared sequence first.
            }
        } else if (sequence.getValue() < safeStart) {
            mongoTemplate.updateFirst(new Query(where("_id").is(SEQUENCE_ID)),
                    new Update().max("value", safeStart), Sequence.class);
        }
        sequence = mongoTemplate.findAndModify(new Query(where("_id").is(SEQUENCE_ID)),
                new Update().inc("value", 1), FindAndModifyOptions.options().returnNew(true), Sequence.class);
        return sequence.getValue();
    }
    public static class Sequence {
        @org.springframework.data.annotation.Id private String id;
        private long value;
        public Sequence() {}
        public Sequence(String id, long value) { this.id = id; this.value = value; }
        public long getValue() { return value; }
    }
}
