package uk.gov.hmrc.dslgen;

import uk.gov.hmrc.dslgen.when.WhenPatternMatcher;

public class Pipeline {

    public java.util.List<RuleRow> run(String excelPath) {
        java.util.List<RuleRow> rows = excelReader.read(excelPath);

        DuplicateIfPreMerger.Result res = DuplicateIfPreMerger.mergeByRawIf(rows);

        // Optional: log removed rows for audit
        // for (RuleRow r : res.removed()) { log.warn("Removed dup IF: {}", r.ifCondition()); }

        // Only unique rows proceed; THEN side available via mergedThenCodesCsv()
        return whenPatternMatcher.collectAll(res.kept());
    }

    private final ExcelReader excelReader;
    private final WhenPatternMatcher whenPatternMatcher;

    public Pipeline(ExcelReader excelReader, WhenPatternMatcher whenPatternMatcher) {
        this.excelReader = excelReader;
        this.whenPatternMatcher = whenPatternMatcher;
    }

    // Compose IF & THEN as usual, but don't print their child/dash lines.
// Instead, fold the 4 lines (2 IF + 2 THEN) into: 1 header + 2 dashes (THEN dash is negated).
    private void emitAdditionalDocs_CoalescedAfterCompose(
            HyphenTwoPhaseComposer composer,
            ConstraintCase kase,
            AtomicHit ifHit,
            AtomicHit thenHit,
            RuleRow row,
            DslWriter dsl) {

        // True merge check
        var orig   = new java.util.LinkedHashSet<>(extractThenCodes(row.thenCondition()));
        var merged = new java.util.LinkedHashSet<>(row.mergedThenCodes());
        boolean hasMerged = row.mergeGroupSize() > 1 && !merged.equals(orig);
        String csv = row.mergedThenCodesCsv();

        // Buffer values per dash key and polarity (pos = IF, neg = THEN)
        java.util.Map<String, java.util.LinkedHashSet<String>> pos = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.LinkedHashSet<String>> neg = new java.util.LinkedHashMap<>();

        // IF side (positive)
        if (ifHit != null && ifHit.groupsList != null) {
            for (AtomicHit h : ifHit.groupsList) {
                if (!isAdditionalDocs(h.path())) continue;
                String key = dashKeyOf(h.path());
                String val = h.groups.isEmpty() ? "" : h.groups.get(h.groups.size() - 1);
                if (!val.isBlank()) pos.computeIfAbsent(key, k -> new java.util.LinkedHashSet<>()).add(val);
            }
        }

        // THEN side (always negated for your domain)
        if (thenHit != null && thenHit.groupsList != null) {
            for (AtomicHit h : thenHit.groupsList) {
                if (!isAdditionalDocs(h.path())) continue;
                String key = dashKeyOf(h.path());
                String val = h.groups.isEmpty() ? "" : h.groups.get(h.groups.size() - 1);
                if (hasMerged && csv != null && !csv.isBlank()) val = csv; // apply merged CSV
                if (!val.isBlank()) neg.computeIfAbsent(key, k -> new java.util.LinkedHashSet<>()).add(val);
            }
        }

        if (pos.isEmpty() && neg.isEmpty()) return; // nothing to fold

        // Emit once: header + positive dash + negative dash
        dsl.writeln("when");
        dsl.writeln("  Goods item with additional documents exists");

        // positive dashes
        for (var e : pos.entrySet()) {
            String mergedCsv = String.join(",", e.getValue());
            String dash = "with " + e.getKey().replace('_',' ') + " equals {1}";
            var res = composer.composeEnglish(HyphenTwoPhaseComposer.Role.IF, kase, dash, java.util.List.of(mergedCsv), null);
            dsl.writeln("  - " + res.english());
        }

        // negative dashes (from THEN)
        for (var e : neg.entrySet()) {
            String mergedCsv = String.join(",", e.getValue());
            String dash = "with " + e.getKey().replace('_',' ') + " equals {1}";
            String negDash = negateDashTemplate(dash);
            var res = composer.composeEnglish(HyphenTwoPhaseComposer.Role.IF, kase, negDash, java.util.List.of(mergedCsv), null);
            dsl.writeln("  - " + res.english());
        }

        // THEN header (your normal single line)
        if (thenHit != null) {
            var thenHeader = composer.composeEnglish(HyphenTwoPhaseComposer.Role.THEN, kase, thenHit.template, thenHit.groups, null);
            dsl.writeln("then");
            dsl.writeln("  " + thenHeader.english());
        }
    }

    AtomicHit ifHit   = matcher.matchIf(row.ifCondition());
    AtomicHit thenHit = matcher.matchThen(row.thenCondition());
    ConstraintCase kase = ConstraintCase.fromEnglish(row.thenCondition());

    // For additionalDocuments only:
    emitAdditionalDocs_CoalescedAfterCompose(composer, kase, ifHit, thenHit, row, dsl);

// For all other roots, keep your existing output path.


    private static boolean isAdditionalDocs(String path) {
        return path != null && path.startsWith("GoodsItem.additionalDocuments.");
    }
    private static String dashKeyOf(String path) { // leaf after last dot, e.g. "exception.code" or "type.code" -> "code"
        if (path == null) return "";
        int i = path.lastIndexOf('.');
        return i > 0 ? path.substring(i + 1) : path;
    }
    private static String negateDashTemplate(String dash) {
        if (dash == null) return "without {1}";
        String t = dash;
        t = t.replace(" equals ", " not equals ")
                .replace(" is in ",  " is not in ")
                .replace(" in ",     " not in ");
        if (t.toLowerCase().startsWith("with ")) t = "without " + t.substring(5);
        if (!t.toLowerCase().contains(" not ") && !t.toLowerCase().startsWith("without "))
            t = t.replace("{1}", "not {1}");
        return t;
    }

}
