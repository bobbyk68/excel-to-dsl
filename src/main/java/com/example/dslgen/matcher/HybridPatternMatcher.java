package uk.gov.h.pseudocode.matcher;

import uk.gov.h.pseudocode.pattern.PatternDefinition;
import uk.gov.h.pseudocode.pattern.loader.PatternLoader;
import uk.gov.h.pseudocode.pattern.matcher.PatternMatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HybridPatternMatcher implements PatternMatcher {

    private final List<PatternDefinition> definitions = new ArrayList<>();

    public HybridPatternMatcher(List<PatternDefinition> fromEnum) {
        this(fromEnum, null);
    }

    public HybridPatternMatcher(List<PatternDefinition> fromEnum, Path jsonPath) {
        definitions.addAll(fromEnum);

        if (jsonPath != null && Files.exists(jsonPath)) {
            try {
                List<PatternDefinition> fromJson = PatternLoader.load(jsonPath);
                definitions.addAll(fromJson);
                System.out.println("✅ Loaded " + fromJson.size() + " patterns from JSON");
            } catch (Exception e) {
                System.err.println("⚠️ Failed to load patterns from JSON: " + e.getMessage());
            }
        }
    }

    @Override
    public ParsedDsl tryMatch(String line) {
        for (PatternDefinition def : definitions) {
            ParsedDsl parsed = def.tryMatch(line);
            if (parsed != null) return parsed;
        }
        return null;
    }
}
