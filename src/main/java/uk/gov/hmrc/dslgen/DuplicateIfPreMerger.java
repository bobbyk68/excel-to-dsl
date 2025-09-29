package uk.gov.hmrc.dslgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pre-merge step:
 *  - Groups rows by composite key: (IF condition + procedureCategory + declarationType)
 *  - Keeps the FIRST row per key (stable order thanks to LinkedHashMap)
 *  - Collects values from THEN across duplicates and writes merged CSV back to the kept row
 *  - Builds human-readable summaries (including kept id and merged ids)
 *
 * Assumptions about RuleRow (matching your current class):
 *  - id()                       : String
 *  - ifCondition()              : String     (raw/English IF)
 *  - thenCondition()            : String     (raw/English THEN)
 *  - declarationType()          : List<String>
 *  - procedureCategory()        : List<String>
 *  - ensureMergedThenCodes()    : void
 *  - mergedThenCodes()          : List<String>
 *  - setMergedThenCodes(List<String>) / setMergedThenCodesCsv(String) / mergedThenCodesCsv() : String
 *  - setCombinedThenCondition(String) / combinedThenCondition() : String
 */
public final class DuplicateIfPreMerger {

    /**
     * Run this right after ExcelReader.read(...) and before collectAll(...).
     *
     * @param rows all RuleRow objects read from Excel (not null; may be empty)
     * @return Result:
     *         - kept():     de-duplicated list (first occurrence per composite key), mutated with merged THEN data
     *         - removed():  duplicates dropped
     *         - summaries(): one summary per composite key that had duplicates (kept id + merged ids + THENs)
     */
    public static Result mergeByCompositeKey(List<RuleRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Result(List.of(), List.of(), List.of());
        }

        // --- CORE STATE -------------------------------------------------------
        // Keep FIRST row per composite key (insertion order preserved)
        Map<String, RuleRow> keeperByKey = new LinkedHashMap<>();
        // Distinct THEN codes per key (ordered by discovery)
        Map<String, LinkedHashSet<String>> codesByKey = new HashMap<>();
        // Original THEN strings encountered per key (for summary)
        Map<String, List<String>> originalThensByKey = new HashMap<>();
        // Occurrence count per key (to detect duplicates)
        Map<String, Integer> countByKey = new HashMap<>();
        // Track IDs merged into the kept row per key (not including the kept id)
        Map<String, List<String>> mergedIdsByKey = new HashMap<>();

        List<RuleRow> removed = new ArrayList<>();

        // === PASS 1: scan all rows, choose keeper, collect data ===============
        for (RuleRow row : rows) {
            if (row == null) continue;

            // Build composite key: IF + CATS + TYPES (normalized & order-insensitive for lists)
            final String key = buildCompositeKey(row);

            countByKey.merge(key, 1, Integer::sum);

            RuleRow keeper = keeperByKey.get(key);
            if (keeper == null) {
                // First time we see this key → keep THIS instance
                keeper = row;
                keeper.ensureMergedThenCodes();
                keeperByKey.put(key, keeper);
                codesByKey.put(key, new LinkedHashSet<>());
                mergedIdsByKey.put(key, new ArrayList<>()); // start empty list
            } else {
                // Duplicate for this key → mark as removed and store its id
                removed.add(row);
                mergedIdsByKey.get(key).add(safe(row.id()));
            }

            // Collect original THEN for summary
            originalThensByKey.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(safe(row.thenCondition()));

            // Collect code-like tokens from THEN for merging (handles alpha-numeric & numeric)
            for (String code : extractThenCodes(safe(row.thenCondition()))) {
                if (!code.isBlank()) {
                    codesByKey.get(key).add(code);
                }
            }
        }

        // === PASS 2: write merged data into keeper rows + build summaries =====
        List<MergeSummaryEntry> summaries = new ArrayList<>();

        for (Map.Entry<String, RuleRow> e : keeperByKey.entrySet()) {
            final String key = e.getKey();
            final RuleRow kept = e.getValue();

            // Distinct THEN values merged (if any)
            LinkedHashSet<String> uniq = codesByKey.getOrDefault(key, new LinkedHashSet<>());
            List<String> mergedVals = new ArrayList<>(uniq);

            // Write merged values to the kept row
            kept.mergedThenCodes().clear();
            kept.mergedThenCodes().addAll(mergedVals);
            final String csv = String.join(",", mergedVals);
            kept.setMergedThenCodesCsv(csv);

            // Combined THEN: only build when there are >= 2 distinct values;
            // otherwise leave null so downstream emits the original THEN verbatim.
            if (mergedVals.size() >= 2) {
                final String prefix = extractListPrefix(safe(kept.thenCondition())); // e.g. "must equals "
                kept.setCombinedThenCondition(prefix + csv);
            } else {
                kept.setCombinedThenCondition(null);
            }

            // Build a summary ONLY if there were duplicates for this key
            final int occ = countByKey.getOrDefault(key, 1);
            if (occ > 1) {
                final List<String> originals = originalThensByKey.getOrDefault(key, List.of());
                final String finalThen = (kept.combinedThenCondition() != null && !kept.combinedThenCondition().isBlank())
                        ? kept.combinedThenCondition()
                        : kept.thenCondition(); // fallback: no real merge → show original THEN

                final String keptId = safe(kept.id());
                final List<String> mergedIds = mergedIdsByKey.getOrDefault(key, List.of());

                summaries.add(new MergeSummaryEntry(
                        keptId,
                        kept.ifCondition(),
                        normalizeListForReport(kept.procedureCategory()),
                        normalizeListForReport(kept.declarationType()),
                        originals,
                        finalThen,
                        new ArrayList<>(mergedIds)
                ));
            }
        }

        return new Result(new ArrayList<>(keeperByKey.values()), removed, summaries);
    }

    // ========================================================================
    // =========================== KEY BUILDING ================================
    // ========================================================================

    /**
     * Composite merge key = IF + normalized(procedureCategory) + normalized(declarationType)
     * - IF is trimmed and internal whitespace collapsed
     * - CATS / TYPES are uppercased, trimmed, deduped, sorted for stable equality (order-insensitive)
     */
    private static String buildCompositeKey(RuleRow row) {
        String ifPart   = normalize(safe(row.ifCondition()));
        String catsPart = joinNormalizedList(row.procedureCategory());
        String typePart = joinNormalizedList(row.declarationType());
        return ifPart + " | CATS=" + catsPart + " | TYPES=" + typePart;
    }

    private static String joinNormalizedList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        // Uppercase, trim, dedupe, sort
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : list) {
            String t = normalizeToken(s);
            if (!t.isBlank()) set.add(t);
        }
        // Sort for order-insensitive equality
        List<String> sorted = new ArrayList<>(set);
        sorted.sort(String::compareTo);
        return String.join(",", sorted);
    }

    private static String normalizeToken(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // ========================================================================
    // ============================ THEN PARSING ===============================
    // ========================================================================

    /**
     * One-pass code extractor that matches:
     *  - Alpha-numeric codes (e.g., GEN36, D05, VG1, ABC-123, A1_B2)
     *  - Pure numbers (e.g., 0, 10, 999)
     *
     * It does NOT match normal words ("at", "least", etc.) because we uppercase
     * and require the token to start with [A-Z0-9] and then contain only [A-Z0-9_-].
     */
// Extract all value tokens from a THEN string.
// Strategy:
//   1) Slice to the "tail" after the last pivot (equals/greater than/less than/in/with/to/:)
//   2) Split the tail on commas / "and" / "or"
//   3) Clean and filter tokens to keep only codes/numbers; drop normal words.
    private static java.util.List<String> extractThenCodes(String rawThen) {
        if (rawThen == null || rawThen.isBlank()) return java.util.List.of();

        // --- 1) Slice to tail after last pivot ---
        String s = rawThen.trim();
        String upper = s.toUpperCase(java.util.Locale.ROOT);

        // Regex: capture everything AFTER the last pivot into group(1)
        java.util.regex.Pattern tailPat = java.util.regex.Pattern.compile(
                ".*(?:\\bEQUALS\\b|\\bGREATER\\s+THAN\\b|\\bLESS\\s+THAN\\b|\\bONLY\\s+IN\\b|\\bIN\\b|\\bWITH\\b|\\bTO\\b|:)\\s*(.+)$",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher tm = tailPat.matcher(upper);
        String tail = tm.matches() ? s.substring(s.length() - (upper.length() - tm.group(1).length())).trim()
                : s; // if no pivot, fall back to whole string

        // strip surrounding parentheses in the tail
        if (tail.startsWith("(") && tail.endsWith(")") && tail.length() > 2) {
            tail = tail.substring(1, tail.length() - 1).trim();
        }

        // --- 2) Normalise separators and split ---
        String norm = tail
                .replace('，', ',')                 // full-width comma
                .replace(';', ',')                  // semicolons → comma
                .replaceAll("(?i)\\s+(AND|OR)\\s+", ",") // 'and'/'or' → comma
                .replaceAll("\\s+", " ")            // collapse spaces
                .trim();

        String[] parts = norm.split("\\s*,\\s*");

        // --- 3) Clean + filter tokens ---
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        java.util.Set<String> stop = java.util.Set.of(
                "AT","IS","OF","ONE","ONLY","IN","WITH","TO","MUST","EQUALS","GREATER","LESS","THAN","AND","OR"
        );

        for (String part : parts) {
            String tok = part.trim();
            if (tok.isEmpty()) continue;

            // Remove surrounding quotes and stray punctuation
            if ((tok.startsWith("'") && tok.endsWith("'")) || (tok.startsWith("\"") && tok.endsWith("\""))) {
                tok = tok.substring(1, tok.length() - 1).trim();
            }
            // Keep only A-Z / 0-9 / _ / - inside the token
            tok = tok.replaceAll("[^A-Za-z0-9_-]", "");

            String up = tok.toUpperCase(java.util.Locale.ROOT);
            if (up.isEmpty() || stop.contains(up)) continue;

            // Accept if:
            //  - pure number, or
            //  - letters+at least one digit (e.g., C01, N934, GEN36), or
            //  - 1–2 letters only (e.g., V, CX)
            boolean ok =
                    up.matches("\\d+") ||
                            up.matches("[A-Z]+\\d[A-Z0-9_-]*") ||
                            up.matches("[A-Z]{1,2}");

            if (ok) out.add(up);
        }

        return new java.util.ArrayList<>(out); // distinct, in original order
    }


    /**
     * Preserve the natural phrase before the value-list in THEN, e.g.:
     *  "must equals ", "in ", "with ", "to "
     * If not found, returns everything up to the last space, plus a space.
     */
    private static String extractListPrefix(String rawThen) {
        final String s = normalize(safe(rawThen));
        final String lower = s.toLowerCase();

        final String[] pivots = { " must equals ", " equals ", " only in ", " in ", " with ", " to ", ":" };

        int bestPos = -1;
        int bestLen = 0;
        for (String p : pivots) {
            int pos = lower.lastIndexOf(p.trim());
            if (pos >= 0 && pos > bestPos) {
                bestPos = pos;
                bestLen = p.equals(":") ? 1 : p.trim().length();
            }
        }

        if (bestPos >= 0) {
            String head = s.substring(0, bestPos + bestLen).trim();
            return head.endsWith(" ") ? head : head + " ";
        }

        int last = s.lastIndexOf(' ');
        return (last > 0 ? s.substring(0, last) : s) + " ";
    }

    // ========================================================================
    // ============================ RESULT TYPES ===============================
    // ========================================================================

    /**
     * Result of the pre-merge step.
     */
    public static final class Result {
        private final List<RuleRow> kept;                 // first-per-key rows (mutated with merged THEN)
        private final List<RuleRow> removed;              // duplicates dropped
        private final List<MergeSummaryEntry> summaries;  // summaries for keys that had duplicates

        public Result(List<RuleRow> kept, List<RuleRow> removed, List<MergeSummaryEntry> summaries) {
            this.kept = kept;
            this.removed = removed;
            this.summaries = summaries;
        }

        public List<RuleRow> kept() { return kept; }
        public List<RuleRow> removed() { return removed; }
        public List<MergeSummaryEntry> summaries() { return summaries; }
    }

    /**
     * Summary entry for one composite key that had duplicates.
     * Includes kept id, IF, CATS, TYPES, all original THENs, final merged THEN, and merged ids.
     */
    public static final class MergeSummaryEntry {
        private final String keptId;                 // e.g., "BR675_..."
        private final String ifCondition;            // IF text
        private final List<String> procedureCategory;// normalized cats (sorted, deduped)
        private final List<String> declarationType;  // normalized types (sorted, deduped)
        private final List<String> originalThens;    // original THENs encountered
        private final String mergedThen;             // final THEN used
        private final List<String> mergedIds;        // ids merged into keptId

        public MergeSummaryEntry(String keptId,
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

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Kept ID: ").append(keptId).append('\n');
            sb.append("IF: ").append(ifCondition).append('\n');
            sb.append("CATS: ").append(String.join(",", procedureCategory)).append('\n');
            sb.append("TYPES: ").append(String.join(",", declarationType)).append('\n');
            sb.append("  Originals (THEN):\n");
            for (String t : originalThens) {
                sb.append("    - ").append(t).append('\n');
            }
            sb.append("  Merged THEN: ").append(mergedThen).append('\n');
            if (!mergedIds.isEmpty()) {
                sb.append("  Merged IDs: ").append(String.join(", ", mergedIds)).append('\n');
            } else {
                sb.append("  Merged IDs: (none)\n");
            }
            return sb.toString();
        }
    }

    // ========================================================================
    // ============================== REPORT HELPERS ===========================
    // ========================================================================

    private static List<String> normalizeListForReport(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : list) {
            String t = normalizeToken(s);
            if (!t.isBlank()) set.add(t);
        }
        List<String> out = new ArrayList<>(set);
        out.sort(String::compareTo);
        return out;
    }

    private DuplicateIfPreMerger() {}
}
