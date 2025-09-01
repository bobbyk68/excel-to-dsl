package uk.gov.hmrc.rules.core.match;

import java.util.*;
import java.util.regex.Matcher;

/** Iterates compiled regex atomics; caches hits by literal string */
public final class AtomicMatcher {
    private final List<CompiledAtomic> compiled;
    private final Map<String, AtomicHit> cache = new HashMap<>();

    public AtomicMatcher(List<CompiledAtomic> compiled) { this.compiled = compiled; }

    public AtomicHit matchAtomic(String literal) {
        AtomicHit cached = cache.get(literal);
        if (cached != null) return cached;

        for (CompiledAtomic ca : compiled) {
            Matcher m = ca.regex().matcher(literal);
            if (m.matches()) {
                List<String> groups = new ArrayList<>(m.groupCount());
                for (int i = 1; i <= m.groupCount(); i++) groups.add(m.group(i).trim());
                AtomicHit hit = new AtomicHit(ca.id(), groups);
                cache.put(literal, hit);
                return hit;
            }
        }
        throw new IllegalStateException("No atomic pattern matched literal: " + literal);
    }
}
