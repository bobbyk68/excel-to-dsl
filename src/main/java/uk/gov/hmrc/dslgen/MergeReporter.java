package uk.gov.hmrc.dslgen;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Non-invasive reporting for your existing (working) pipeline.
 * Call MergeReporter.report(rows) before you run your own merge.
 * It DOES NOT modify the rows; it only prints a summary and/or returns entries.
 */
public final class MergeReporter {

    /** Generate and print a concise merge report to stdout. */
    public static List<Entry> report(List<RuleRow> rows) {
        if (rows == null || rows.isEmpty()) {
            System.out.println("No rows provided to MergeReporter.");
            return List.of();
        }

        // Group rows by the same composite key you now use (IF + cats + types)
        Map<String, List<RuleRow>> groups = new LinkedHashMap<>();
        for (RuleRow r : rows) {
            if (r == null) continue;
            String key = buildCompositeKey(r);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<Entry> out = new ArrayList<>();
        boolean any = false;

        for (Map.Entry<String, List<RuleRow>> e : groups.entrySet()) {
            List<RuleRow> bucket = e.getValue();
            if (bucket.size() <= 1) continue; // only report when duplicates exist

            any = true;

            // First row is the "kept" one (consistent with your working logic)
            RuleRow kept = bucket.get(0);
            String keptId = safe(kept.id());
            String ifText = safe(kept.ifCondition());

            // Collect all original THENs and all values across the bucket
            List<String> originalThens = new ArrayList<>();
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (RuleRow r : bucket) {
                String t = safe(r.thenCondition());
                originalThens.add(t);
                for (String v : extractValues(t)) {
                    if (!v.isBlank()) values.add(v);
                }
            }

            // Compute merged CSV and human-friendly merged THEN using the kept row's wording prefix
            String csv = String.join(",", values);
            String mergedThen = extractPrefix(safe(kept.thenCondition())) + csv;

            // Collect merged ids (everything except the first)
            List<String> mergedIds = new ArrayList<>();
            for (int i = 1; i < bucket.size(); i++) mergedIds.add(safe(bucket.get(i).id()));

            // Normalised cats/types for visibility
            List<String> cats = normalizedList(kept.procedureCategory());
            List<String> types = normalizedList(kept.declarationType());

            Entry entry = new Entry(keptId, ifText, cats, types, originalThens, mergedThen, mergedIds);
            out.add(entry);
        }

        // Print
        if (!any) {
            System.out.println("No merges performed.");
        } else {
            System.out.println("=== Merge Summary (by IF + cats + types) ===");
            for (Entry s : out) {
                System.out.println(s);
            }
        }

        return out;
    }

    // ========= helpers: SAME assumptions as your working code ================

    /** Composite key identical in spirit to your working composite logic. */
    private static String buildCompositeKey(RuleRow r) {
        return normalize(safe(r.ifCondition()))
            + "|" + joinNormalized(r.procedureCategory())
            + "|" + joinNormalized(r.declarationType());
    }

    private static String joinNormalized(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> items = new ArrayList<>();
        for (String s : list) if (s != null) items.add(s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " "));
        Collections.sort(items);
        return String.join(",", items);
    }

    private static String normalize(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
    private static String safe(String s) { return s == null ? "" : s; }

    /** Use the same simple extractor you said worked: values are tokens like GEN36, D05, C01, 0, etc. */
    private static List<String> extractValues(String thenText) {
        if (thenText == null || thenText.isBlank()) return List.of();
        Matcher m = Pattern.compile("\\b[A-Z0-9][A-Za-z0-9_-]*\\b")
                           .matcher(thenText.toUpperCase(Locale.ROOT));
        LinkedHashSet<String> out = new LinkedHashSet<>();
        while (m.find()) out.add(m.group());
        return new ArrayList<>(out); // distinct, original order
    }

    /** Keep the natural phrase before the value list, e.g., "must equals ", "equals ", "in ". */
    private static String extractPrefix(String rawThen) {
        if (rawThen == null) return "";
        String s = normalize(rawThen);
        String lower = s.toLowerCase(Locale.ROOT);

        String[] pivots = { " must equals ", " equals ", " greater than ", " less than ", " in ", " with ", " to " };
        int bestPos = -1, bestLen = 0;
        for (String p : pivots) {
            int pos = lower.lastIndexOf(p.trim());
            if (pos >= 0 && pos > bestPos) { bestPos = pos; bestLen = p.trim().length(); }
        }
        if (bestPos >= 0) {
            String head = s.substring(0, bestPos + bestLen).trim();
            return head.endsWith(" ") ? head : head + " ";
        }
        int last = s.lastIndexOf(' ');
        return (last > 0 ? s.substring(0, last) : s) + " ";
    }

    // ======================= DTO for consumers ===============================

    public static final class Entry {
        private final String keptId;
        private final String ifCondition;
        private final List<String> procedureCategory;
        private final List<String> declarationType;
        private final List<String> originalThens;
        private final String mergedThen;
        private final List<String> mergedIds;

        public Entry(String keptId,
                     String ifCondition,
                     List<String> procedureCategory,
                     List<String> declarationType,
                     List<String> originalThens,
                     String mergedThen,
                     List<String> mergedIds) {
            this.keptId = keptId;
            this.ifCondition = ifCondition;
            this.procedureCategory = procedureCategory;
            this.declarationType = declarationType;
            this.originalThens = originalThens;
            this.mergedThen = mergedThen;
            this.mergedIds = mergedIds;
        }

        public String keptId() { return keptId; }
        public String ifCondition() { return ifCondition; }
        public List<String> procedureCategory() { return procedureCategory; }
        public List<String> declarationType() { return declarationType; }
        public List<String> originalThens() { return originalThens; }
        public String mergedThen() { return mergedThen; }
        public List<String> mergedIds() { return mergedIds; }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Kept ID: ").append(keptId).append('\n');
            sb.append("IF: ").append(ifCondition).append('\n');
            sb.append("CATS: ").append(String.join(",", procedureCategory)).append('\n');
            sb.append("TYPES: ").append(String.join(",", declarationType)).append('\n');
            sb.append("  Originals (THEN):\n");
            for (String t : originalThens) sb.append("    - ").append(t).append('\n');
            sb.append("  Merged THEN: ").append(mergedThen).append('\n');
            sb.append("  Merged IDs: ").append(mergedIds.isEmpty() ? "(none)" : String.join(", ", mergedIds)).append('\n');
            return sb.toString();
        }
    }

    private MergeReporter() {}
}
