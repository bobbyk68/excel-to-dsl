package com.example.dslgen.matcher;

import com.example.dslgen.builder.DslBuilder;
import com.example.dslgen.pattern.PatternRule;
import com.example.dslgen.pattern.loader.PatternDefinition;
import com.example.dslgen.pattern.loader.PatternLoader;
import com.example.dslgen.pattern.when.WhenPatternEnum;
import com.example.dslgen.pattern.then.ThenPatternEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HybridPatternMatcher implements PatternMatcher {

    private final List<PatternDefinition> definitions = new ArrayList<>();

    public HybridPatternMatcher(String type) {
        // Load enums first
        if ("when".equalsIgnoreCase(type)) {
            Arrays.stream(WhenPatternEnum.values())
                  .map(e -> new PatternDefinition(e.getPatterns(), e.getLhs(), e.getRhs()))
                  .forEach(definitions::add);
        } else if ("then".equalsIgnoreCase(type)) {
            Arrays.stream(ThenPatternEnum.values())
                  .map(e -> new PatternDefinition(e.getPatterns(), e.getLhs(), e.getRhs()))
                  .forEach(definitions::add);
        }

        // Load from JSON
        List<PatternDefinition> jsonDefs = PatternLoader.loadFromJson(type);
        if (jsonDefs != null) {
            definitions.addAll(jsonDefs);
        }
    }

    @Override
    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (PatternRule def : definitions) {
            DslBuilder.ParsedDsl parsed = def.tryMatch(line);
            if (parsed != null) return parsed;
        }
        return null;
    }
}
