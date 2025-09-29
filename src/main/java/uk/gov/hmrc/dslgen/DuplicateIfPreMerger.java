package uk.gov.hmrc.rules;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, stable pre-merge by COMPOSITE KEY:
 *   key = normalize(ifCondition) + "|" + sortedUpper(procedureCategory) + "|" + sortedUpper(declarationType)
 *
 * - Keeps the FIRST RuleRow per key (stable order via LinkedHashMap)
 * - Collects values from THEN across duplicates
 * - Writes merged CSV back to the kept row
 * - Builds a readable combinedThenCondition as:  extractListPrefix(original THEN) + csv
 *
 * Assumes RuleRow has:
 *   - String  id()
 *   - String  ifCondition()
 *   - String  thenCondition()
 *   - List<String> procedureCategory()
 *   - List<String> declarationType()
 *   - void ensureMergedThenCodes()
 *   - List<String> mergedThenCodes()
 *   - void setMergedThenCodesCsv(String)
 *   - void setCombinedThenCondition(String)
 */
public final class DuplicateIfPreMerger {

    /**
     * Run this right after ExcelReader.read(...) and before collectAll(...).
     */
    public static List<RuleRow> mergeByCompositeKey(List<RuleRow> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        Map<String, RuleRow> keeperByKey = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> codesByKey = new HashMap<>();

        for (RuleRow row : rows) {
            if (row == null) continue;

            // Composite key = IF + CATS + TYPES (order-insensitive for lists)
            String key = buildCompositeKey(row);

            RuleRow keeper = keeperByKey.get(key);
            if (keeper == null) {
                // First time we see this key → keep THIS instance
                keeper = row;
                keeper.ensureMergedThenCodes();
                keeperByKey.put(key, keeper);
                codesByKey.put(key, new LinkedHashSet<>());
            }

            // Collect values from this row's THEN
            for (String val : extractThenCodes(safe(row.thenCondition()))) {
                if (!val.isBlank()) codesByKey.get(key).add(val);
            }
        }

        // Write merged CSV to kept rows; compose a readable THEN using the original prefix
        for (Map.Entry<String, RuleRow> e : keeperByKey.entrySet()) {
            RuleRow kept = e.getValue();

            List<String> merged = new ArrayList<>(codesByKey.getOrDefault(e.getKey(), new LinkedHashSet<>()));
            kept.mergedThenCodes().clear();
            kept.mergedThenCodes().addAll(merged);

            String csv = String.join(",", merged);              // e.g., "GEN36,GEN54,GEN89"
            kept.setMergedThenCodesCsv(csv);

            String prefix = extractListPrefix(safe(kept.thenCondition())); // e.g., "must equals "
            kept.setCombinedThenCondition(prefix + csv);
        }

        return new ArrayList<>(keeperByKey.values());
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    /** Build composite key: IF + normalized cats + normalized types. */
    private static String buildCompositeKey(RuleRow row) {
        String ifPart   = normalize(safe(row.ifCondition()));
        String catsPart = joinNormalized(row.procedureCategory());
        String typePart = joinNormalized(row.declarationType());
        return ifPart + "|" + catsPart + "|" + typePart;
    }

    /** Upper-case, trim, sort (order-insensitive) and join with comma. */
    private static String joinNormalized(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> copy = new ArrayList<>(list.size());
        for (String s : list) {
            if (s == null) continue;
            copy.add(s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " "));
        }
        Collections.sort(copy);
        return String.join(",", copy);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * Extract THEN “value” tokens (the version you said worked for you).
     * It finds all tokens starting with [A-Z0-9] followed by [A-Za-z0-9_-]*.
     * If you need to be stricter/looser later, tweak this one line.
     */
    private static List<String> extractThenCodes(String rawThen) {
        if (rawThen == null || rawThen.isBlank()) return List.of();
        Matcher m = Pattern.compile("\\b[A-Z0-9][A-Za-z0-9_-]*\\b")
                .matcher(rawThen.toUpperCase(Locale.ROOT));
        List<String> codes = new ArrayList<>();
        while (m.find()) codes.add(m.group());
        return codes;
    }

    /**
     * Keep the natural phrase before the value list in THEN, e.g.:
     * "must equals ", "equals ", "in ", "with ", "to ".
     * If not found, returns everything up to the last space + a space.
     */
    private static String extractListPrefix(String rawThen) {
        if (rawThen == null) return "";
        String s = normalize(rawThen);
        String lower = s.toLowerCase(Locale.ROOT);

        String[] pivots = { " must equals ", " equals ", " greater than ", " less than ", " in ", " with ", " to " };
        int bestPos = -1; int bestLen = 0;
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

    private DuplicateIfPreMerger() {}
}
