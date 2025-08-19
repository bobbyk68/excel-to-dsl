package uk.gov.h.builder;

import uk.gov.h.dsl.ThenTemplate;
import uk.gov.h.dsl.ThenTemplateLoader;
import uk.gov.h.dsl.WhenTemplate;
import uk.gov.h.dsl.WhenTemplateLoader;
import uk.gov.h.model.RuleRow;
import uk.gov.h.pattern.PatternMatcher;

import java.nio.file.Path;
import java.util.*;

/**
 * Combines PatternMatchers (dynamic per-rule) with JSON templates (static boilerplate).
 * - WHEN: template DSL + matched WHENs (deduped by LHS raw)
 * - THEN: matched THEN + template THEN lines (deduped)
 * - DSLR WHEN: template prepend + matched WHENs + template append
 */
public class DslBuilder {

    // Keep raw (for DSL dictionary) and rendered (for DSLR)
    public record ParsedDsl(String lhsRaw, String lhsRendered, String rhsRaw, String rhsRendered) {}

    public static class Result {
        public final List<String> dslLines  = new ArrayList<>();
        public final List<String> dslrLines = new ArrayList<>();
    }

    private final PatternMatcher whenMatcher;
    private final PatternMatcher thenMatcher;
    private final Path whenTemplatePath;
    private final Path thenTemplatePath;

    public DslBuilder(PatternMatcher whenMatcher,
                      PatternMatcher thenMatcher,
                      Path whenTemplatePath,
                      Path thenTemplatePath) {
        this.whenMatcher = whenMatcher;
        this.thenMatcher = thenMatcher;
        this.whenTemplatePath = whenTemplatePath;
        this.thenTemplatePath = thenTemplatePath;
    }

    /** Build a single rule: parse via matchers, merge with templates, output DSL + DSLR. */
    public Result build(RuleRow row) {
        Result out = new Result();

        // 0) Load templates
        WhenTemplate whenTpl = WhenTemplateLoader.load(
                whenTemplatePath != null ? whenTemplatePath : Path.of("config/when-template.json")
        );
        ThenTemplate thenTpl = ThenTemplateLoader.load(
                thenTemplatePath != null ? thenTemplatePath : Path.of("config/then-template.json")
        );

        // 1) Parse WHENs using the matcher
        List<ParsedDsl> whenParts = new ArrayList<>();
        for (String cond : row.getConditions()) {
            var p = whenMatcher.tryMatch(cond);
            if (p != null) whenParts.add(p);
        }

        // 2) Parse THEN (optional)
        ParsedDsl matchedThen = (thenMatcher != null && row.getAction() != null)
                ? thenMatcher.tryMatch(row.getAction())
                : null;

        // 3) DSL dictionary — start with template WHEN DSL, then add matched WHENs (dedupe by LHS raw)
        Map<String,String> whenDict = new LinkedHashMap<>(whenTpl.renderDsl(row));
        for (ParsedDsl p : whenParts) whenDict.putIfAbsent(p.lhsRaw(), p.rhsRaw());
        for (var e : whenDict.entrySet()) {
            out.dslLines.add("[when] " + e.getKey() + " = " + e.getValue());
        }
        if (matchedThen != null) {
            out.dslLines.add("[then] " + matchedThen.lhsRaw() + " = " + matchedThen.rhsRaw());
        }

        // 4) DSLR header
        out.dslrLines.add("rule \"" + row.getName() + "\"");
        out.dslrLines.add("when");

        // 5) DSLR WHEN: template prepend + matched WHENs + template append (deduped, stable)
        LinkedHashSet<String> dslrWhen = new LinkedHashSet<>();
        dslrWhen.addAll(whenTpl.renderDslrPrepend(row));
        for (ParsedDsl p : whenParts) dslrWhen.add(p.lhsRendered());
        dslrWhen.addAll(whenTpl.renderDslrAppend(row));
        for (String w : dslrWhen) out.dslrLines.add("    " + w);

        // 6) DSLR THEN: matched THEN + template THEN (deduped, stable)
        out.dslrLines.add("then");
        LinkedHashSet<String> thenLines = new LinkedHashSet<>();
        if (matchedThen != null && notBlank(matchedThen.rhsRendered())) {
            thenLines.add(matchedThen.rhsRendered());
        }
        for (String s : thenTpl.render(row)) {
            if (notBlank(s)) thenLines.add(s);
        }
        for (String t : thenLines) out.dslrLines.add("    " + t + ";");

        out.dslrLines.add("end");
        out.dslrLines.add("");
        return out;
    }

    private static boolean notBlank(String s){ return s != null && !s.isBlank(); }
}