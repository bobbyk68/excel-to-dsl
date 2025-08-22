package uk.gov.hmrc.dslgen.when;

import java.util.*;

public final class WhenAssembler {
    private final WhenSnippetRegistry reg;

    public WhenAssembler(WhenSnippetRegistry reg) { this.reg = reg; }

    public List<String> assemblePhrases(List<String> requestedIds) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        EnumSet<Var> provided = EnumSet.noneOf(Var.class);
        Set<String> visiting = new HashSet<>();

        for (String id : requestedIds) {
            var s = reg.get(id);
            if (s == null) continue;
            resolve(s, ordered, provided, visiting);
        }
        return new ArrayList<>(ordered);
    }

    private void resolve(WhenSnippet s, LinkedHashSet<String> ordered,
                         EnumSet<Var> provided, Set<String> visiting) {
        if (!visiting.add(s.id)) return;
        for (Var need : s.requires) {
            if (!provided.contains(need)) {
                var prov = reg.providerOf(need).orElseThrow(() ->
                    new IllegalStateException("No provider for " + need + " required by " + s.id));
                resolve(prov, ordered, provided, visiting);
            }
        }
        if (ordered.add(s.phrase)) provided.addAll(s.provides);
        visiting.remove(s.id);
    }
}