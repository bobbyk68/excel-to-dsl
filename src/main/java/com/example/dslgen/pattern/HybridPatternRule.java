// HybridPatternRule.java
package com.example.dslgen.pattern;

import com.example.dslgen.builder.DslBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class HybridPatternRule implements PatternRule {

    private static final List<PatternDefinition> definitions = new ArrayList<>();

    static {
        // Load from enum first
        for (WhenPatternEnum e : WhenPatternEnum.values()) {
            definitions.add(new PatternDefinition(
                Arrays.asList(e.getPatterns()),
                e.getLhs(),
                e.getRhs()
            ));
        }

        // Load from JSON if present
        try {
            File file = new File("rules.json");
            if (file.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                List<PatternDefinition> jsonRules = mapper.readValue(file, new TypeReference<>() {});
                definitions.addAll(jsonRules);
            }
        } catch (Exception ex) {
            System.err.println("❌ Failed to load JSON rules: " + ex.getMessage());
        }
    }

    @Override
    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (PatternDefinition def : definitions) {
            for (Pattern pattern : def.patterns()) {
                var matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    String lhs = def.lhs();
                    String rhs = def.rhs();

                    // Replace {val1}, {val2} etc.
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        lhs = lhs.replace("{val" + i + "}", matcher.group(i));
                        rhs = rhs.replace("{val" + i + "}", matcher.group(i));
                    }

                    return new DslBuilder.ParsedDsl(lhs, rhs);
                }
            }
        }
        return null;
    }

    public record PatternDefinition(List<java.util.regex.Pattern> patterns, String lhs, String rhs) {}
}
