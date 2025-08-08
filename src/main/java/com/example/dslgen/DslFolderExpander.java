package com.example.dslgen;

import org.drools.compiler.compiler.DSLTokenizedMappingFile;
import org.drools.compiler.lang.dsl.DefaultExpander;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class DslFolderExpander {

    public static void main(String[] args) throws Exception {
        Path folderPath = Path.of("src/main/resources/rules"); // point this to your DSL/DSLR folder
        expandAll(folderPath);
    }

    public static void expandAll(Path folderPath) throws IOException {
        // 1. Load all DSL files into a list of mapping files
        List<DSLTokenizedMappingFile> allDslMappings = Files.list(folderPath)
                .filter(path -> path.toString().endsWith(".dsl"))
                .map(DslFolderExpander::loadDsl)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 2. For each DSLR file, expand and print DRL
        Files.list(folderPath)
                .filter(path -> path.toString().endsWith(".dslr"))
                .forEach(dslrPath -> expandDslr(allDslMappings, dslrPath));
    }

    private static DSLTokenizedMappingFile loadDsl(Path dslPath) {
        try {
            DSLTokenizedMappingFile dslFile = new DSLTokenizedMappingFile();
            dslFile.parseAndLoad(Files.readAllLines(dslPath));
            System.out.println("Loaded DSL: " + dslPath.getFileName());
            return dslFile;
        } catch (IOException e) {
            System.err.println("Error loading DSL: " + dslPath + " - " + e.getMessage());
            return null;
        }
    }

    private static void expandDslr(List<DSLTokenizedMappingFile> allDslMappings, Path dslrPath) {
        try {
            String dslrContent = Files.readString(dslrPath);

            DefaultExpander expander = new DefaultExpander();
            for (DSLTokenizedMappingFile mappingFile : allDslMappings) {
                expander.addDSLMapping(mappingFile.getMapping());
            }

            String expandedDrl = expander.expand(dslrContent);

            System.out.println("\n----- Generated DRL for " + dslrPath.getFileName() + " -----");
            System.out.println(expandedDrl);

        } catch (IOException e) {
            System.err.println("Error expanding DSLR: " + dslrPath + " - " + e.getMessage());
        }
    }
}
