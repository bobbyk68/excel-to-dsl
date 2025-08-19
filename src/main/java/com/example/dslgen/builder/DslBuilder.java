package uk.gov.h.builder;

import uk.gov.h.dsl.ThenTemplate;
import uk.gov.h.dsl.ThenTemplateLoader;
import uk.gov.h.model.RuleRow;

import java.nio.file.Path;
import java.util.*;

/**
 * Builds the shared DSL dictionary and per-rule DSLR, with NO hardcoded THENs.
 * - DSL: de-duped [when] (and optional [then] if a matcher THEN exists)
 * - DSLR: WHEN uses lhsRendered; THEN = (matcher THEN rhsRendered) + (template lines), de-duped, stable order
 */
public class DslBuilder {

    // Keep raw (with {val}) for DSL; rendered (substituted) for DSLR
    public record ParsedDsl(String lhsRaw, String lhsRendered, String rhsRaw, String rhsRendered) {}

    public static class Result {
        public final List<String> dslLines = new ArrayList<>();
        public final List<String> dslrLines = new ArrayList<>();
    }

    private final Path thenTemplatePath;

    public DslBuilder() { this(null); }
    public DslBuilder(Path thenTemplatePath) { this.thenTemplatePath = thenTemplatePath; }

    public Result buildRule(RuleRow row,
                            List<ParsedDsl> whenParts,
                            ParsedDsl matchedThen /* may be null */) {
        Result result = new Result();

        // --- DSL dictionary (WHEN only; de-dupe by LHS raw) ---
        Map<String, String> whenDict = new LinkedHashMap<>();
        for (ParsedDsl p : whenParts) {
            whenDict.putIfAbsent(p.lhsRaw(), p.rhsRaw());
        }
        for (var e : whenDict.entrySet()) {
            result.dslLines.add("[when] " + e.getKey() + " = " + e.getValue());
        }

        // Optional: expose THEN pattern in the DSL dictionary as well (still not hardcoded)
        if (matchedThen != null) {
            result.dslLines.add("[then] " + matchedThen.lhsRaw() + " = " + matchedThen.rhsRaw());
        }

        // --- DSLR assembly ---
        result.dslrLines.add("rule \"" + row.getName() + "\"");
        result.dslrLines.add("when");
        for (ParsedDsl p : whenParts) {
            // If you have a DRL-emitter, call it here instead of using English lhsRendered
            result.dslrLines.add("    " + p.lhsRendered());
        }
        result.dslrLines.add("then");

        // Combine matcher THEN + template THEN (de-duped, stable order)
        LinkedHashSet<String> thenLines = new LinkedHashSet<>();
        if (matchedThen != null && matchedThen.rhsRendered() != null && !matchedThen.rhsRendered().isBlank()) {
            thenLines.add(matchedThen.rhsRendered());
        }

        ThenTemplate tmpl = ThenTemplateLoader.load(
                thenTemplatePath != null ? thenTemplatePath : Path.of("config/then-template.json")
        );
        for (String rendered : tmpl.render(row)) {
            if (rendered != null && !rendered.isBlank()) thenLines.add(rendered);
        }

        for (String line : thenLines) {
            result.dslrLines.add("    " + line + ";");
        }
        result.dslrLines.add("end");
        result.dslrLines.add(""); // spacer between rules

        return result;
    }
}
