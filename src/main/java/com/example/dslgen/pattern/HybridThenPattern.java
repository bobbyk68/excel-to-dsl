// HybridThenPattern.java
package com.example.dslgen.pattern;

import com.example.dslgen.builder.DslBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HybridThenPattern implements PatternRule {
    private final List<PatternDefinition> definitions = new ArrayList<>();

    public HybridThenPattern() {
        // Load from enum first
        for (ThenPatternEnum e : ThenPatternEnum.values()) {
            definitions.add(new PatternDefinition(
                Arrays.asList(e.getPatterns()),
                e.getLhs(),
                e.getRhs()
            ));
        }
        // Load from JSON file if available
        definitions.addAll(PatternLoader.loadFromJson("then-patterns.json"));
    }

    @Override
    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (PatternDefinition def : definitions) {
            DslBuilder.ParsedDsl parsed = def.tryMatch(line);
            if (parsed != null) return parsed;
        }
        return null;
    }
}
