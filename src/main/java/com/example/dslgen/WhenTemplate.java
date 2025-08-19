package com.example.dslgen;

import uk.gov.h.model.RuleRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WhenTemplate {

    public static final class DslEntry {
        public final String lhs;
        public final String rhs;
        public DslEntry(String lhs, String rhs) { this.lhs = lhs; this.rhs = rhs; }
    }

    private final List<DslEntry> dsl;
    private final List<String> dslrPrepend;
    private final List<String> dslrAppend;

    public WhenTemplate(List<DslEntry> dsl, List<String> dslrPrepend, List<String> dslrAppend) {
        this.dsl = dsl == null ? List.of() : List.copyOf(dsl);
        this.dslrPrepend = dslrPrepend == null ? List.of() : List.copyOf(dslrPrepend);
        this.dslrAppend = dslrAppend == null ? List.of() : List.copyOf(dslrAppend);
    }

    /** Render [when] entries for the DSL dictionary with token substitution. */
    public Map<String, String> renderDsl(RuleRow row) {
        Map<String, String> out = new LinkedHashMap<>();
        for (DslEntry e : dsl) {
            out.put(applyTokens(e.lhs, row), applyTokens(e.rhs, row));
        }
        return out;
    }

    /** Lines injected at the top of each rule's DSLR 'when' block. */
    public List<String> renderDslrPrepend(RuleRow row) {
        return renderLines(dslrPrepend, row);
    }

    /** Lines injected at the end of each rule's DSLR 'when' block. */
    public List<String> renderDslrAppend(RuleRow row) {
        return renderLines(dslrAppend, row);
    }

    private static List<String> renderLines(List<String> lines, RuleRow row) {
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(applyTokens(l, row));
        return out;
    }

    private static String applyTokens(String s, RuleRow row) {
        if (s == null) return "";
        return s
            .replace("{errorCode}", nvl(row.getErrorCode()))
            .replace("{name}", nvl(row.getName()))
            .replace("{procedureCategory}", nvl(row.getProcedureCategory()))
            .replace("{declarationType}", nvl(row.getDeclarationType()));
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}