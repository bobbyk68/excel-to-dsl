package com.example.dslgen.matcher;

import com.example.dslgen.builder.DslBuilder.ParsedDsl;
import com.example.dslgen.pattern.PatternRule;
import com.example.dslgen.pattern.PatternDefinition;
import com.example.dslgen.pattern.PatternLoader;

import java.util.ArrayList;
import java.util.List;

public class PatternMatcher {

    private final List<PatternRule> rules = new ArrayList<>();

    public PatternMatcher(List<PatternDefinition> definitions) {
        for (PatternDefinition def : definitions) {
            rules.add(def::tryMatch);
        }
    }

    public ParsedDsl tryMatch(String line) {
        for (PatternRule rule : rules) {
            ParsedDsl result = rule.tryMatch(line);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static PatternMatcher fromJsonOrEnum(String resourcePath, List<PatternDefinition> enumDefs) {
        List<PatternDefinition> allDefs = new ArrayList<>(enumDefs);
        try {
            List<PatternDefinition> jsonDefs = PatternLoader.load(resourcePath);
            allDefs.addAll(jsonDefs);
        } catch (Exception e) {
            System.out.println("📂 No JSON found or failed to load from: " + resourcePath + " — falling back to enum only.");
        }
        return new PatternMatcher(allDefs);
    }
}
