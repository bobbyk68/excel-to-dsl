
package com.example.dslgen;

import java.io.*;
import java.util.*;

public class DslBuilder {
    private final Set<String> dslLhsTracker = new HashSet<>();
    private final List<String> dslLines = new ArrayList<>();
    private final List<String> dslrLines = new ArrayList<>();
    private static final List<String> ALL_DECL_TYPES = List.of("A", "D", "Y", "Z", "C", "J", "F");

    public void addRule(String name, String procCat, String declType,
                        String dslrWhen, String dslrThen, String thenRhs) {
        String lhs1 = "the ProcedureCategory is " + procCat;
        String rhs1 = "declaration.getProcedureCategory().equals(\"" + procCat + "\")";

        if (!dslLhsTracker.contains(lhs1)) {
            dslLines.add("[when] " + lhs1 + " = " + rhs1);
            dslLhsTracker.add(lhs1);
        }

        if ("ALL".equalsIgnoreCase(declType)) {
            for (String t : ALL_DECL_TYPES) {
                String lhs = "and the DeclarationType is " + t;
                String rhs = "declaration.getType().equals(\"" + t + "\")";
                if (!dslLhsTracker.contains(lhs)) {
                    dslLines.add("[when] " + lhs + " = " + rhs);
                    dslLhsTracker.add(lhs);
                }
            }
        } else {
            String lhs = "and the DeclarationType is " + declType;
            String rhs = "declaration.getType().equals(\"" + declType + "\")";
            if (!dslLhsTracker.contains(lhs)) {
                dslLines.add("[when] " + lhs + " = " + rhs);
                dslLhsTracker.add(lhs);
            }
        }

        dslrLines.add("rule \"" + name + "\"");
        dslrLines.add("when");
        dslrLines.add("    " + lhs1);
        if (!"ALL".equalsIgnoreCase(declType)) {
            dslrLines.add("    and the DeclarationType is " + declType);
        } else {
            for (String t : ALL_DECL_TYPES) {
                dslrLines.add("    and the DeclarationType is " + t);
            }
        }
        dslrLines.add("    " + dslrWhen);
        dslrLines.add("then");
        if (!dslrThen.isEmpty()) {
            dslrLines.add("    " + dslrThen);
        }
        dslrLines.add("    " + thenRhs);
        dslrLines.add("end");
    }

    public void writeDslFile(String path) throws IOException {
        writeToFile(path, dslLines);
    }

    public void writeDslrFile(String path) throws IOException {
        writeToFile(path, dslrLines);
    }

    private void writeToFile(String path, List<String> lines) throws IOException {
        File outFile = new File(path);
        outFile.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}
