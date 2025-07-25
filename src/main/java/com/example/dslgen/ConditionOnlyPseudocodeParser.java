
package com.example.dslgen;

public class ConditionOnlyPseudocodeParser {
    public static class ParsedDsl {
        public final String dslrWhen;
        public final String dslrThen;
        public final String thenRhs;

        public ParsedDsl(String dslrWhen, String dslrThen, String thenRhs) {
            this.dslrWhen = dslrWhen;
            this.dslrThen = dslrThen;
            this.thenRhs = thenRhs;
        }
    }

    public static ParsedDsl parse(String logicLine, String errorCode) {
        String dslrWhen = logicLine.toLowerCase();
        String dslrThen = "";
        String thenRhs = "System.out.println(\"ERROR: " + errorCode + "\");";
        return new ParsedDsl(dslrWhen, dslrThen, thenRhs);
    }
}
