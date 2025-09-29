package uk.gov.hmrc.rules;

import uk.gov.hmrc.dslgen.RuleRow;

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

    static void debugGroupForId(List<RuleRow> rows, String targetId) {
        java.util.function.Function<RuleRow,String> keyFn = r ->
                normalize(safe(r.ifCondition())) + "|" +
                        joinNormalized(r.procedureCategory()) + "|" +
                        joinNormalized(r.declarationType());

        String targetKey = null;
        for (RuleRow r : rows) {
            if (r != null && targetId.equals(safe(r.id()))) {
                targetKey = keyFn.apply(r);
                break;
            }
        }
        if (targetKey == null) {
            System.out.println("Target id " + targetId + " not found.");
            return;
        }

        System.out.println("== Group for id " + targetId + " ==");
        System.out.println("KEY=[" + targetKey + "]");
        int i = 0;
        for (RuleRow r : rows) {
            if (r == null) continue;
            String k = keyFn.apply(r);
            if (targetKey.equals(k)) {
                System.out.println(" [" + (++i) + "] id=" + r.id());
                System.out.println("     IF(raw):  [" + safe(r.ifCondition()) + "]");
                System.out.println("     IF(norm): [" + normalize(safe(r.ifCondition())) + "]");
                System.out.println("     CATS:     " + joinNormalized(r.procedureCategory()));
                System.out.println("     TYPES:    " + joinNormalized(r.declarationType()));
                System.out.println("     THEN:     [" + safe(r.thenCondition()) + "]");
            }
        }
    }

    static String joinNormalized(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "";
        java.util.List<String> copy = new java.util.ArrayList<>();
        for (String s : list) if (s != null) copy.add(s.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+"," "));
        java.util.Collections.sort(copy); // remove sort if order must matter
        return String.join(",", copy);
    }
    static String normalize(String s){ return s == null ? "" : s.trim().replaceAll("\\s+"," "); }
    static String safe(String s){ return s == null ? "" : s; }

    private DuplicateIfPreMerger() {}
}
