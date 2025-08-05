package com.example.dslgen.pattern;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

package com.example.dslgen.pattern.loader;

import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class PatternLoader {

    public static List<PatternDefinition> load(Class<? extends PatternDefinitionSource> enumClass, String jsonPath) {
        List<PatternDefinition> definitions = new ArrayList<>();

        // 1. Load from enum
        if (enumClass.isEnum()) {
            for (PatternDefinitionSource e : enumClass.getEnumConstants()) {
                definitions.add(new PatternDefinition(
                        Arrays.asList(e.getPatterns()),
                        e.getLhs(),
                        e.getRhs()
                ));
            }
        }

        // 2. Load from JSON file if exists
        Path path = Paths.get(jsonPath);
        if (Files.exists(path)) {
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path))) {
                ObjectMapper mapper = new ObjectMapper();
                List<PatternDefinition> fromFile = mapper.readValue(reader, new TypeReference<>() {});
                definitions.addAll(fromFile);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load pattern definitions from: " + jsonPath, e);
            }
        }

        return definitions;
    }
}
