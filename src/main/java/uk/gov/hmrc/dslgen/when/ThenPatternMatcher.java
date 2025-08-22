package uk.gov.hmrc.dslgen.when;

import java.util.*;

public final class ThenPatternMatcher implements DslBuilder.PatternMatcher {
    private final HybridPatternMatcher hybrid;

    public ThenPatternMatcher(HybridPatternMatcher hybrid) {
        this.hybrid = hybrid;
    }

    @Override
    public Optional<String> tryMatch(DslBuilder.RuleRow row) {
        return Optional.empty();
    }

    /** Use this in DslBuilder to get all THEN phrases for a row. */
    public List<String> collectAll(DslBuilder.RuleRow row) {
        return hybrid.thenPhrasesFor(row);
    }
}