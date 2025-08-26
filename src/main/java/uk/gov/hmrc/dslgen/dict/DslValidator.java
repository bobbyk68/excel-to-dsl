package uk.gov.hmrc.dslgen.dict;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validates generated lines against the indexed .dsl dictionary. */
public class DslValidator {

    private final Set<String> knownWhenRhs; // compare against RHS code from [when] entries

    public DslValidator(DslIndex index) {
        this.knownWhenRhs = index.getWhenRhs();
    }

    /** Return all lines not found in the known RHS set (normalized). */
    public List<String> findUnknownWhenRhs(List<String> emittedWhenLines) {
        List<String> unknown = new ArrayList<>();
        for (String line : emittedWhenLines) {
            String norm = normalize(line);
            if (!knownWhenRhs.contains(norm)) {
                unknown.add(line); // keep original for reporting/catchall
            }
        }
        return unknown;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
