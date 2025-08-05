package com.example.dslgen.pattern.loader;

import java.util.ArrayList;
import java.util.List;

public class PatternDefinitionProvider {
    public static List<PatternDefinition> loadAll(String resourceName, List<? extends PatternDefinitionSource> enumValues) {
        List<PatternDefinition> all = new ArrayList<>();

        // Load from enums
        for (PatternDefinitionSource e : enumValues) {
            all.add(new PatternDefinition(e.getPatterns(), e.getLhs(), e.getRhs()));
        }

        // Load from JSON file (if exists)
        List<PatternDefinition> fromJson = PatternLoader.loadFromJson(resourceName);
        if (fromJson != null) {
            all.addAll(fromJson);
        }

        return all;
    }
}
