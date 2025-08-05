package com.example.dslgen.pattern;

import java.util.List;

public interface PatternDefinitionSource {
    List<String> getPatterns();
    String getLhs();
    String getRhs();
}
