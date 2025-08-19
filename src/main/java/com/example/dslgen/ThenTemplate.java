package com.example.dslgen;

import uk.gov.h.model.RuleRow;
import java.util.*;

/** Simple token-substitution template for THEN lines. */
public final class ThenTemplate {
    private final List<String> lines;

    public ThenTemplate(List<String> lines) {
        this.lines = (lines == null) ? List.of() : List.copyOf(lines);
    }

    /** Render lines by replacing tokens from the RuleRow (e.g., {errorCode}, {name}). */
    public List<String> render(RuleRow row) {
        Map<String, String> tokens = Map.of(
            "{errorCode}", nvl(row.getErrorCode()),
            "{name}", nvl(row.getName()),
            "{procedureCategory}", nvl(row.getProcedureCategory()),
            "{declarationType}", nvl(row.getDeclarationType())
        );
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            String s = l;
            for (var e : tokens.entrySet()) s = s.replace(e.getKey(), e.getValue());
            out.add(s);
        }
        return out;
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
