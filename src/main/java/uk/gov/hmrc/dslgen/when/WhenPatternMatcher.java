package uk.gov.hmrc.dslgen.when;

import java.util.*;

public final class WhenPatternMatcher implements DslBuilder.PatternMatcher {
    private final HybridPatternMatcher hybrid;

    public WhenPatternMatcher(HybridPatternMatcher hybrid) {
        this.hybrid = hybrid;
    }

    @Override
    public Optional<String> tryMatch(DslBuilder.RuleRow row) {
        // Old PatternMatcher API is single-result, but Hybrid can give many.
        // Simplest: return empty here, and expose collectAll().
        return Optional.empty();
    }

    /** Use this in DslBuilder to get all WHEN phrases for a row. */
    public List<String> collectAll(DslBuilder.RuleRow row) {
        return hybrid.whenPhrasesFor(row);
    }
}