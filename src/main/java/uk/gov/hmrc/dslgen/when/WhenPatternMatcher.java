package uk.gov.hmrc.dslgen.when;

import uk.gov.hmrc.dslgen.RuleRow;
import uk.gov.hmrc.dslgen.dict.DslValidator;
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
    // top of class
    private final DslValidator validator;

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

        // 2) dependencies
        autoInjectMissingPrereqs(matches);

        // 3) order + flatten lines
        List<String> orderedDsl = orderByRequires(matches);

        // 4) hydrate row tokens on WHEN lines
        orderedDsl = orderedDsl.stream().map(s -> hydrateFromRow(s, row)).toList();

        // 5) catch-all
        for (String raw : rawUnmatched) writeCatchall("[when] " + raw + " =");

        // 6) hydrate pre/post and emit
        List<String> hydratedPre  = hydrateList(prepost.whenPrepend(), row);
        List<String> hydratedPost = hydrateList(prepost.whenAppend(),  row);

        List<String> out = new ArrayList<>();
        out.addAll(hydratedPre);
        out.addAll(orderedDsl);
        out.addAll(hydratedPost);
        return out;
    }

    /** Auto-inject parents referenced by 'requires' if missing. */
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
            // No regex match here -> take effective lines as-is (tokens hydrated later)
            matches.add(new TemplateMatch(
                    def.getId(),
                    def.effectiveDslLines(),
                    def.getRequires() == null ? List.of() : def.getRequires()
            ));
            have.add(def.getId());

            if (def.getRequires() != null) {
                for (String up : def.getRequires()) if (!have.contains(up)) need.addLast(up);
            }
        }
    }

    /** Order by requirements; emit ALL lines of each match when ready. */
    private List<String> orderByRequires(List<TemplateMatch> matches) {
        List<String> out = new ArrayList<>();
        Set<String> added = new HashSet<>();
        boolean progress; int guard = 0;

        do {
            progress = false;
            for (TemplateMatch m : matches) {
                if (added.contains(m.id())) continue;
                if (added.containsAll(m.requires())) {
                    out.addAll(m.dslLines());
                    added.add(m.id());
                    progress = true;
                }
            }
            if (++guard > 1000) break;
        } while (progress);

        // Emit any leftovers (cycles / unresolved deps)
        for (TemplateMatch m : matches) {
            if (!added.contains(m.id())) {
                out.addAll(m.dslLines());
                added.add(m.id());
            }
        }
        return out;
    }

    private List<String> hydrateList(List<String> lines, RuleRow row) {
        if (lines == null) return List.of();
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(hydrateFromRow(line, row));
        return out;
    }

    private String hydrateFromRow(String dsl, RuleRow row) {
        if (dsl == null) return "";
        String out = dsl;
        if (out.contains("{decTypes}"))
            out = out.replace("{decTypes}", Tokens.quoteEachCsv(row.declarationTypes()));
        if (out.contains("{procCats}"))
            out = out.replace("{procCats}", Tokens.quoteEachCsv(row.procedureCategories()));
        if (out.contains("{param}"))
            out = out.replace("{param}", row.param() == null ? "" : row.param());
        return out;
    }

    private void writeCatchall(String dslLine) {
        try {
            Path path = Path.of("target/catchall.dsl");
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, dslLine + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write catchall.dsl", e);
        }
    }
}

package uk.gov.hmrc.rules.core;

import uk.gov.hmrc.rules.core.match.AtomicHit;
import uk.gov.hmrc.rules.core.match.AtomicMatcher;
import uk.gov.hmrc.rules.core.model.RuleRow;
import uk.gov.hmrc.rules.emit.DslBuilder;
import uk.gov.hmrc.rules.index.CompositeIndex;

import java.util.List;

public final class WhenPatternMatcher {
    private final AtomicMatcher matcher;
    private final CompositeIndex compositeIndex;
    private final DslBuilder dsl;

    public WhenPatternMatcher(AtomicMatcher matcher, CompositeIndex compositeIndex, DslBuilder dsl) {
        this.matcher = matcher;
        this.compositeIndex = compositeIndex;
        this.dsl = dsl;
    }

    /** Existing contract preserved: returns a DSLR string */
    public String collectAll(List<RuleRow> rows) {
        StringBuilder out = new StringBuilder();
        for (RuleRow row : rows) {
            // Excel literals (with concrete values)
            String leftLiteral  = row.ifCondition();
            String rightLiteral = row.thenCondition();

            // Regex match → atomic ids
            AtomicHit left  = matcher.matchAtomic(leftLiteral);
            AtomicHit right = matcher.matchAtomic(rightLiteral);

            // Confirm composite ordered pair exists (pair is just “found” gate)
            String pairKey = left.id() + "|" + right.id();
            if (!compositeIndex.contains(pairKey)) {
                throw new IllegalStateException("Composite not found for pair " + pairKey + " (row " + row + ")");
            }

            // Render when/then using literals; annotations taken from RuleRow
            dsl.appendRule(out, row, leftLiteral, rightLiteral);
        }
        return out.toString();
    }
}
