// v2
package uk.gov.hmrc.rules.when;

import java.util.ArrayList;
import java.util.List;

public final class WhenPatternMatcher {

    public List<String> translateAll(List<String> rawConditions) {
        var out = new ArrayList<String>();
        if (rawConditions == null) return out;

        for (var raw : rawConditions) {
            String normalized = raw == null ? "" : raw.trim();
            if (normalized.isEmpty()) continue;

            String translated = null;
            // Enum order = precedence
            for (var rule : uk.gov.hmrc.rules.when.WhenPatternEnum.values()) {
                var m = rule.tryMatch(normalized);
                if (m != null) {
                    translated = rule.render(m);
                    break;
                }
            }
            out.add(translated != null ? translated : normalized); // fallback to original
        }
        return out;
    }
}