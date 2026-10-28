package com.mandateshield.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

@Document(collection = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {
    @Id
    private Long id;
    @Indexed(unique = true)
    private String email;
    private String passwordHash;
    private String fullName;
    private Instant lastUserActivityDate;
}
