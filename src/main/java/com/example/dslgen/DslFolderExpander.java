package com.example.rules.debug;

import org.drools.compiler.compiler.DSLTokenizedMappingFile;
import org.drools.compiler.lang.dsl.DefaultExpander;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class DslFolderExpander {

    private DslFolderExpander() {}

    /** Expands every .dslr in the folder using all .dsl mappings found there. */
    public static Map<Path, String> expandAll(Path folderPath) throws IOException {
        // Load all DSLs
        List<DSLTokenizedMappingFile> allDslMappings = Files.list(folderPath)
                .filter(p -> p.toString().endsWith(".dsl"))
                .map(DslFolderExpander::loadDsl)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Path, String> result = new LinkedHashMap<>();
        // Expand each DSLR with all mappings
        Files.list(folderPath)
                .filter(p -> p.toString().endsWith(".dslr"))
                .forEach(dslr -> {
                    try {
                        DefaultExpander expander = new DefaultExpander();
                        for (var mf : allDslMappings) expander.addDSLMapping(mf.getMapping());
                        String expanded = expander.expand(Files.readString(dslr));
                        result.put(dslr, expanded);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed expanding " + dslr, e);
                    }
                });

        return result;
    }

    private static DSLTokenizedMappingFile loadDsl(Path p) {
        try {
            DSLTokenizedMappingFile f = new DSLTokenizedMappingFile();
            f.parseAndLoad(Files.readAllLines(p));
            return f;
        } catch (Exception e) {
            System.err.println("Error loading DSL: " + p + " -> " + e.getMessage());
            return null;
        }
    }
}
