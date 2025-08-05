package com.example.dslgen.matcher;

import com.example.dslgen.builder.DslBuilder;

public interface PatternMatcher {
    DslBuilder.ParsedDsl tryMatch(String line);
}
