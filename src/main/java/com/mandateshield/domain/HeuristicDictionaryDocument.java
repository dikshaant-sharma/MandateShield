package com.mandateshield.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "heuristic_dictionary")
public class HeuristicDictionaryDocument {

    @Id
    private String id;
    
    private Set<String> gateways;
    private Set<String> stopwords;
    private Map<String, String> aliasMap;
    private Map<String, String> brandCategoryMap;

}
