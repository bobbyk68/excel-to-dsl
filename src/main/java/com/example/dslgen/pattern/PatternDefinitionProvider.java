package com.example.dslgen.pattern;

import java.util.ArrayList;
import java.util.List;

public class PatternDefinitionProvider {
    public static List<HybridPatternRule.PatternDefinition> loadAll(String resourceName, List<? extends PatternDefinitionSource> enumValues) {
        List<HybridPatternRule.PatternDefinition> all = new ArrayList<>();

        // Load from enums
        for (PatternDefinitionSource e : enumValues) {
            all.add(new HybridPatternRule.PatternDefinition(e.getPatterns(), e.getLhs(), e.getRhs()));
        }

        // Load from JSON file (if exists)
        List<HybridPatternRule.PatternDefinition> fromJson = com.example.dslgen.pattern.PatternLoader.loadFromJson(resourceName);
        if (fromJson != null) {
            all.addAll(fromJson);
        }

        return all;
    }
}
