package com.example.dslgen.pattern.loader;

import com.example.dslgen.pattern.HybridPatternRule;
import com.example.dslgen.pattern.PatternRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class PatternLoader {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Main method to call: loads JSON rules and returns them as PatternRule implementations
    public static List<PatternRule> loadFromJson(String path) {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            List<HybridPatternRule.PatternDefinition> rules = objectMapper.readValue(in, new TypeReference<>() {});
            return Collections.unmodifiableList(rules);  // defensively return unmodifiable list
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load pattern definitions from JSON: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Optional legacy alias
    public static List<PatternRule> load(String path) {
        return loadFromJson(path);
    }
}
