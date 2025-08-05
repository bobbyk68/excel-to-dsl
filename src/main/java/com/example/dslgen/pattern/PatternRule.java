// PatternRule.java (interface)
package com.example.dslgen.pattern;

import com.example.dslgen.builder.DslBuilder;

public interface PatternRule {
    DslBuilder.ParsedDsl tryMatch(String line);
}
