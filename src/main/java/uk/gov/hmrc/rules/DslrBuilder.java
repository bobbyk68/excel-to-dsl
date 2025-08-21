// v10
// v12
package uk.gov.hmrc.rules.build;

import uk.gov.hmrc.rules.templates.WhenTemplates;
import uk.gov.hmrc.rules.templates.ThenTemplates;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
// v13
// v14
package uk.gov.hmrc.rules.build;

import uk.gov.hmrc.rules.templates.WhenTemplates;
import uk.gov.hmrc.rules.templates.ThenTemplates;
import uk.gov.hmrc.rules.when.WhenPatternMatcher;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DslrBuilder {
    private static final List<String> ALL_DECL_TYPES = List.of("A","D","Y","Z","C","J","F");

    private final WhenTemplates whenTemplates;
    private final ThenTemplates thenTemplates;
    private final WhenPatternMatcher whenMatcher = new WhenPatternMatcher(); // NEW

    public DslrBuilder(WhenTemplates whenTemplates, ThenTemplates thenTemplates) {
        this.whenTemplates = Objects.requireNonNull(whenTemplates, "whenTemplates");
        this.thenTemplates = Objects.requireNonNull(thenTemplates, "thenTemplates");
    }

    public void buildDslr(List<RuleRow> rows, Path mainDsl, Path brDsl, Path outDslr) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("rows must not be null/empty");
        Objects.requireNonNull(mainDsl, "mainDsl");
        Objects.requireNonNull(brDsl, "brDsl");
        Objects.requireNonNull(outDslr, "outDslr");

        var ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(RuleRow::ruleName, Comparator.nullsLast(String::compareTo)));

        var dslr = new StringBuilder();
        writeHeader(dslr, ordered.size());

        var stagedCache = FileIO.fileReadAllLines(brDsl);

        for (RuleRow r : ordered) {
            validateRow(r);

            // 1) Translate Excel conditions -> DSL lines via WhenPatternEnum
            var translatedConds = whenMatcher.translateAll(r.conditions());

            // 2) Stage each translated LHS line into br.dsl if missing
            for (var c : translatedConds) {
                var normalized = normalizeCondition(c);
                ensureLhsExistsOrStage(normalized, mainDsl, brDsl, stagedCache);
            }

            // 3) Prepare bindings (include translated conditions for ${conditions})
            var bindings = effectiveBindings(r, translatedConds);

            // 4) Render WHEN/THEN (supports json arrays + prepend/append)
            var whenPre  = whenTemplates.prependLines(r.whenTemplateId(), bindings);
            var whenBody = whenTemplates.render(r.whenTemplateId(), bindings);
            var whenPost = whenTemplates.appendLines(r.whenTemplateId(), bindings);

            var thenPre  = thenTemplates.prependLines(r.thenTemplateId(), bindings);
            var thenBody = thenTemplates.render(r.thenTemplateId(), bindings);
            var thenPost = thenTemplates.appendLines(r.thenTemplateId(), bindings);

            // 5) Emit rule
            if (!isBlank(r.original())) {
                dslr.append("// source: ").append(r.original().replace("\n", " ")).append("\n");
            }
            dslr.append("rule \"%s\"\n".formatted(r.ruleName()));

            for (var ln : whenPre) dslr.append("    ").append(ln).append("\n");

            dslr.append("when\n");
            dslr.append("        ").append(whenBody).append("\n");
            for (var ln : whenPost) dslr.append("    ").append(ln).append("\n");

            dslr.append("then\n");
            for (var ln : thenPre) dslr.append("        ").append(ln).append("\n");
            dslr.append("        ").append(thenBody).append("\n");
            for (var ln : thenPost) dslr.append("        ").append(ln).append("\n");

            dslr.append("end\n\n");
        }

        FileIO.writeString(outDslr, dslr.toString());
    }

    private Map<String, Object> effectiveBindings(RuleRow r, List<String> translatedConds) {
        var map = new LinkedHashMap<>(Optional.ofNullable(r.bindings()).orElseGet(LinkedHashMap::new));
        map.putIfAbsent("procCats", nonNull(r.procCats()).trim());
        map.putIfAbsent("decTypes", nonNull(r.decTypes()).trim());
        map.putIfAbsent("decTypesExpanded", expandDecTypesToString(r.decTypes()));
        if (!map.containsKey("code") && !isBlank(r.errorCode())) map.put("code", r.errorCode());
        // ${conditions} is the translated + newline-joined text
        map.putIfAbsent("conditions", String.join("\n        ", translatedConds));
        map.putIfAbsent("ruleName", r.ruleName());
        return map;
    }

    private String expandDecTypesToString(String raw) {
        if (raw == null) return "";
        if ("ALL".equalsIgnoreCase(raw.trim())) return String.join(",", ALL_DECL_TYPES);
        var parts = Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return String.join(",", parts);
    }

    private String normalizeCondition(String c) { return c == null ? "" : c.trim().replaceAll("\\s+", " "); }
    private void writeHeader(StringBuilder sb, int count) {
        var ts = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        sb.append("""
            // --------------------------------------------------------------------
            // Generated by DslrBuilder v14 at %s
            // Rules: %d
            // --------------------------------------------------------------------

            """.formatted(ts, count));
    }
    private void validateRow(RuleRow r) {
        if (r == null) throw new IllegalArgumentException("RuleRow is null");
        if (isBlank(r.ruleName())) throw new IllegalArgumentException("ruleName is blank");
        if (r.conditions() == null || r.conditions().isEmpty()) {
            throw new IllegalArgumentException("conditions list is null/empty for rule: " + r.ruleName());
        }
        if (isBlank(r.whenTemplateId())) throw new IllegalArgumentException("whenTemplateId is blank");
        if (isBlank(r.thenTemplateId())) throw new IllegalArgumentException("thenTemplateId is blank");
    }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String nonNull(String s) { return s == null ? "" : s; }

    private void ensureLhsExistsOrStage(String lhsLine, Path mainDsl, Path brDsl, Set<String> stagedCache) {
        var normalized = lhsLine.trim();
        if (normalized.isEmpty()) return;
        if (FileIO.fileContainsLine(mainDsl, normalized)) return;
        if (!stagedCache.contains(normalized)) {
            FileIO.appendLine(brDsl, normalized);
            stagedCache.add(normalized);
        }
    }
}