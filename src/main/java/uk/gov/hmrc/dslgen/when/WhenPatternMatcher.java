package uk.gov.hmrc.dslgen.when;

import uk.gov.hmrc.dslgen.RuleRow;
import uk.gov.hmrc.dslgen.support.DslTemplateService;
import uk.gov.hmrc.dslgen.support.DslTemplateService.TemplateDef;
import uk.gov.hmrc.dslgen.support.DslTemplateService.TemplateMatch;
import uk.gov.hmrc.dslgen.support.PrePostConfig;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class WhenPatternMatcher {
    private final DslTemplateService templates = new DslTemplateService();
    private final PrePostConfig prepost = new PrePostConfig();

    public List<String> collectAll(RuleRow row) {
        // 1) Collect matches from Excel phrases
        List<TemplateMatch> matches = new ArrayList<>();
        List<String> rawUnmatched = new ArrayList<>();

        for (String candidate : row.whenCandidates()) {
            templates.matchWhen(candidate).ifPresentOrElse(
                    matches::add,
                    () -> rawUnmatched.add(candidate)
            );
        }

        // 2) Auto-inject any missing prerequisites (recursively)
        autoInjectMissingPrereqs(matches);

        // 3) Order by requires (topological-ish)
        List<String> orderedDsl = orderByRequires(matches);

        // 4) Emit + catch-all records for truly unmatched phrases
        for (String raw : rawUnmatched) {
            writeCatchall("[when] " + raw + " =");
        }

        // 5) Wrap with pre/post and return
        List<String> out = new ArrayList<>();
        out.addAll(prepost.whenPrepend());
        out.addAll(orderedDsl);
        out.addAll(prepost.whenAppend());
        return out;
    }

    /** Inject required parents by ID if they weren't matched from Excel. */
    private void autoInjectMissingPrereqs(List<TemplateMatch> matches) {
        // Build current id set
        Set<String> have = matches.stream().map(TemplateMatch::id).collect(Collectors.toCollection(LinkedHashSet::new));
        // Queue all missing required ids
        Deque<String> need = new ArrayDeque<>();
        matches.forEach(m -> m.requires().forEach(req -> { if (!have.contains(req)) need.add(req); }));

        // BFS over requirements; inject as needed
        while (!need.isEmpty()) {
            String reqId = need.pollFirst();
            if (have.contains(reqId)) continue;

            Optional<TemplateDef> defOpt = templates.findWhenById(reqId);
            if (defOpt.isEmpty()) {
                // No such template in JSON; we cannot inject. Leave to leftovers.
                continue;
            }

            TemplateDef def = defOpt.get();
            matches.add(new TemplateMatch(def.id(), def.dsl(), def.requires()));
            have.add(def.id());

            // Ensure we also satisfy this injected node's own prereqs
            for (String up : def.requires()) {
                if (!have.contains(up)) need.addLast(up);
            }
        }
    }

    /** Simple dependency resolver: emit when all 'requires' are satisfied. */
    private List<String> orderByRequires(List<TemplateMatch> matches) {
        List<String> out = new ArrayList<>();
        Set<String> added = new HashSet<>();

        boolean progress;
        int guard = 0;
        do {
            progress = false;
            for (TemplateMatch m : matches) {
                if (added.contains(m.id())) continue;
                if (added.containsAll(m.requires())) {
                    out.add(m.dsl());
                    added.add(m.id());
                    progress = true;
                }
            }
            if (++guard > 1000) break; // safety guard
        } while (progress);

        // Leftovers (unmet/cyclic) — still emit so rules remain visible
        for (TemplateMatch m : matches) {
            if (!added.contains(m.id())) {
                out.add(m.dsl());
                added.add(m.id());
            }
        }
        return out;
    }

    private void writeCatchall(String dslLine) {
        try {
            Path path = Path.of("target/catchall.dsl");
            Files.createDirectories(path.getParent());
            Files.writeString(path, dslLine + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write catchall.dsl", e);
        }
    }
}
