package com.example.dslgen.pattern;

import com.example.dslgen.builder.DslBuilder;
import com.example.dslgen.pattern.loader.PatternDefinition;

import java.util.ArrayList;
import java.util.List;

public class PatternMatcher {

    private final List<PatternDefinition> definitions;

    public PatternMatcher(List<PatternDefinition> definitions) {
        this.definitions = definitions;
    }

    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (PatternDefinition def : definitions) {
            DslBuilder.ParsedDsl parsed = def.tryMatch(line);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    public List<PatternDefinition> getDefinitions() {
        return definitions;
    }

    public List<DslBuilder.ParsedDsl> tryMatchAll(String line) {
        List<DslBuilder.ParsedDsl> matches = new ArrayList<>();
        for (PatternDefinition def : definitions) {
            DslBuilder.ParsedDsl parsed = def.tryMatch(line);
            if (parsed != null) {
                matches.add(parsed);
            }
        }
        return matches;
    }
}
