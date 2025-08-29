package uk.gov.hmrc.dslgen.core.index;// uk/gov/hmrc/rules/core/index/RuleGraphIndex.java
import uk.gov.hmrc.dslgen.core.model.Atomic;
import uk.gov.hmrc.dslgen.core.model.Bundle;
import uk.gov.hmrc.dslgen.core.model.Composite;
import uk.gov.hmrc.dslgen.core.model.*;
import java.util.*;
import static java.util.stream.Collectors.toMap;

public final class RuleGraphIndex {
    public final Map<String,String> patternToId;          // pattern → A###
    public final Map<String, Atomic> idToAtomic;           // A### → Atomic
    public final Set<String> pairSet;                     // "A###|A###" → exists

    private RuleGraphIndex(Map<String,String> p2i, Map<String,Atomic> i2a, Set<String> ps) {
        this.patternToId = p2i; this.idToAtomic = i2a; this.pairSet = ps;
    }

    public static RuleGraphIndex from(Bundle b) {
        Map<String,String> p2i = b.atomic().stream().collect(toMap(Atomic::pattern, Atomic::id));
        Map<String,Atomic> i2a = b.atomic().stream().collect(toMap(Atomic::id, a -> a));
        Set<String> pairs = new LinkedHashSet<>();
        for (Composite c : b.composite()) {
            var deps = c.dependsOn();
            if (deps.size() != 2) throw new IllegalStateException("Composite not pair: " + c.id());
            pairs.add(key(deps.get(0), deps.get(1)));
        }
        return new RuleGraphIndex(p2i, i2a, pairs);
    }

    public static String key(String leftId, String rightId) { return leftId + "|" + rightId; }
}
