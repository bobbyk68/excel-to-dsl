package com.example.dslgen.pattern;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PatternLoader {
    public static List<PatternDefinition> loadFromEnum(PatternDefinitionProvider provider) {
        return provider.getPatternDefinitions();
    }

    public static List<PatternDefinition> loadFromJson(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return Collections.emptyList();

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(file, new TypeReference<List<PatternDefinition>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JSON: " + path, e);
        }
    }
}
