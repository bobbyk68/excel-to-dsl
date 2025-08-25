package uk.gov.hmrc.dslgen.when;

import uk.gov.hmrc.dslgen.RuleRow;
import uk.gov.hmrc.dslgen.support.DslTemplateService;
import uk.gov.hmrc.dslgen.support.DslTemplateService.TemplateMatch;
import uk.gov.hmrc.dslgen.support.PrePostConfig;
import uk.gov.hmrc.dslgen.support.Tokens;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class WhenPatternMatcher {
    private final DslTemplateService templates = new DslTemplateService();
    private final PrePostConfig prepost = new PrePostConfig();

    public List<String> collectAll(RuleRow row) {
        // 1) collect matches
        List<TemplateMatch> matches = new ArrayList<>();
        List<String> rawUnmatched = new ArrayList<>();
        for (String candidate : row.whenCandidates()) {
            templates.matchWhen(candidate).ifPresentOrElse(
                    matches::add,
                    () -> rawUnmatched.add(candidate)
            );
        }

        // 2) If you still want synthetic lines (comment out if you rely purely on prepend)
        // injectDerivedFromRow(row, matches);

        // 3) dependencies
        autoInjectMissingPrereqs(matches);

        // 4) order
        List<String> orderedDsl = orderByRequires(matches);

        // 5) hydrate ordered lines ({decTypes},{procCats},{param})
        orderedDsl = orderedDsl.stream().map(s -> hydrateFromRow(s, row)).toList();

        // 6) catch-all for truly unmatched
        for (String raw : rawUnmatched) writeCatchall("[when] " + raw + " =");

        // 7) NEW: hydrate pre/post as well
        List<String> hydratedPre  = hydrateList(prepost.whenPrepend(), row);
        List<String> hydratedPost = hydrateList(prepost.whenAppend(),  row);

        // 8) emit
        List<String> out = new ArrayList<>();
        out.addAll(hydratedPre);
        out.addAll(orderedDsl);
        out.addAll(hydratedPost);
        return out;
    }

    /** Derive matches from the row even without Excel triggers (optional). */
    private void injectDerivedFromRow(RuleRow row, List<TemplateMatch> matches) {
        boolean hasDeclType = matches.stream().anyMatch(m -> "DECL_TYPE".equals(m.id()));
        if (!hasDeclType && row.declarationTypes() != null && !row.declarationTypes().isEmpty()) {
            matches.add(new TemplateMatch(
                    "DECL_TYPE",
                    "$dec : Declaration( type in ({decTypes}) ) from $doc.declarations",
                    List.of("DOC")
            ));
        }
        boolean hasProcCat = matches.stream().anyMatch(m -> "PROC_CAT".equals(m.id()));
        if (!hasProcCat && row.procedureCategories() != null && !row.procedureCategories().isEmpty()) {
            matches.add(new TemplateMatch(
                    "PROC_CAT",
                    "$proc : Procedure( category in ({procCats}) ) from $doc.procedures",
                    List.of("DOC")
            ));
        }
    }

    private void autoInjectMissingPrereqs(List<TemplateMatch> matches) {
        Set<String> have = matches.stream().map(TemplateMatch::id).collect(Collectors.toCollection(LinkedHashSet::new));
        Deque<String> need = new ArrayDeque<>();
        for (TemplateMatch m : matches) for (String req : m.requires()) if (!have.contains(req)) need.add(req);

        while (!need.isEmpty()) {
            String reqId = need.pollFirst();
            if (have.contains(reqId)) continue;
            var defOpt = templates.findWhenById(reqId);
            if (defOpt.isEmpty()) continue;
            var def = defOpt.get();
            matches.add(new TemplateMatch(def.id(), def.dsl(), def.requires() == null ? List.of() : def.requires()));
            have.add(def.id());
            if (def.requires() != null) for (String up : def.requires()) if (!have.contains(up)) need.addLast(up);
        }
    }

    private List<String> orderByRequires(List<TemplateMatch> matches) {
        List<String> out = new ArrayList<>();
        Set<String> added = new HashSet<>();
        boolean progress; int guard = 0;
        do {
            progress = false;
            for (TemplateMatch m : matches) {
                if (added.contains(m.id())) continue;
                if (added.containsAll(m.requires())) {
                    out.add(m.dsl()); added.add(m.id()); progress = true;
                }
            }
            if (++guard > 1000) break;
        } while (progress);
        for (TemplateMatch m : matches) if (!added.contains(m.id())) { out.add(m.dsl()); added.add(m.id()); }
        return out;
    }

    private String hydrateFromRow(String dsl, RuleRow row) {
        if (dsl == null) return "";
        String out = dsl;
        if (out.contains("{decTypes}")) out = out.replace("{decTypes}", Tokens.quoteEachCsv(row.declarationTypes()));
        if (out.contains("{procCats}")) out = out.replace("{procCats}", Tokens.quoteEachCsv(row.procedureCategories()));
        if (out.contains("{param}"))    out = out.replace("{param}", row.param() == null ? "" : row.param());
        return out;
    }

    private List<String> hydrateList(List<String> lines, RuleRow row) {
        if (lines == null) return List.of();
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(hydrateFromRow(line, row));
        return out;
    }

    private void writeCatchall(String dslLine) {
        try {
            Path path = Path.of("target/catchall.dsl");
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, dslLine + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write catchall.dsl", e);
        }
    }
}
