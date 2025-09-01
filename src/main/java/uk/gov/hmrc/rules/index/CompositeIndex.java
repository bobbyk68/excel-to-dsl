package uk.gov.hmrc.rules.index;

import uk.gov.hmrc.rules.core.model.Composite;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ordered-pair existence gate for composites (no metadata) */
public final class CompositeIndex {
    private final Set<String> pairs; // e.g., "A001|A002"

    private CompositeIndex(Set<String> pairs) { this.pairs = pairs; }

    public static CompositeIndex from(List<Composite> composites) {
        Set<String> s = new LinkedHashSet<>();
        for (Composite c : composites) {
            if (c.dependsOn() == null || c.dependsOn().size() != 2) {
                throw new IllegalStateException("Composite not a pair: " + c.id());
            }
            s.add(key(c.dependsOn().get(0), c.dependsOn().get(1)));
        }
        return new CompositeIndex(s);
    }

    public boolean contains(String pairKey) { return pairs.contains(pairKey); }

    public static String key(String leftId, String rightId) { return leftId + "|" + rightId; }
}
