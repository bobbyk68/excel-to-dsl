// PseudocodeParser.java
package com.example.dslgen;

import com.example.dslgen.pattern.WhenPatternRule;
import com.example.dslgen.pattern.ThenPatternRule;

public class PseudocodeParser {

    public static class ParsedDsl {
        public final String whenLhs;
        public final String whenRhs;
        public final String thenLhs;
        public final String thenRhs;

        public ParsedDsl(String whenLhs, String whenRhs, String thenLhs, String thenRhs) {
            this.whenLhs = whenLhs;
            this.whenRhs = whenRhs;
            this.thenLhs = thenLhs;
            this.thenRhs = thenRhs;
        }
    }

    public static ParsedDsl parse(String condition) {
        String whenLhs = null;
        String whenRhs = null;
        String thenLhs = null;
        String thenRhs = null;

        for (WhenPatternRule rule : WhenPatternRule.values()) {
            if (rule.matches(condition)) {
                whenLhs = rule.getLhs(condition);
                whenRhs = rule.getRhs(condition);
                break;
            }
        }

        for (ThenPatternRule rule : ThenPatternRule.values()) {
            if (rule.matches(condition)) {
                thenLhs = rule.getLhs(condition);
                thenRhs = rule.getRhs(condition);
                break;
            }
        }

        if (whenLhs == null) {
            whenLhs = "unmatched WHEN: " + condition;
            whenRhs = "// TODO";
        }
        if (thenLhs == null) {
            thenLhs = "default THEN";
            thenRhs = "// default action";
        }

        return new ParsedDsl(whenLhs, whenRhs, thenLhs, thenRhs);
    }
}
