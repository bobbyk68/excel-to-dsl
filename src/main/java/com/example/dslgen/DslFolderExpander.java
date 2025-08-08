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

    import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

    static String readStableString(Path path, int maxWaitMs) throws IOException, InterruptedException {
        long last = -1;
        int waited = 0;
        while (waited < maxWaitMs) {
            long size = Files.size(path);
            if (size == last) break;
            last = size;
            Thread.sleep(50);
            waited += 50;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

}
import org.drools.compiler.compiler.DSLTokenizedMappingFile;
import org.drools.compiler.lang.dsl.DefaultExpander;

import java.util.*;
        import java.nio.file.*;
        import static java.util.stream.Collectors.toList;

public final class DslFolderExpander {
    private DslFolderExpander() {}

    public static Map<Path,String> expandAll(Path folder) throws Exception {
        // fresh map each time
        Map<Path,String> out = new LinkedHashMap<>();

        // load all DSL mappings (Reader overload)
        List<DSLTokenizedMappingFile> mappings = Files.list(folder)
                .filter(p -> p.toString().endsWith(".dsl"))
                .map(p -> {
                    try (var r = Files.newBufferedReader(p)) {
                        var f = new DSLTokenizedMappingFile();
                        f.parseAndLoad(r);
                        return f;
                    } catch (Exception e) {
                        throw new RuntimeException("DSL load failed: "+p, e);
                    }
                }).collect(toList());

        // expand every DSLR
        Files.list(folder)
                .filter(p -> p.toString().endsWith(".dslr"))
                .forEach(p -> {
                    try {
                        var exp = new DefaultExpander();
                        for (var m : mappings) exp.addDSLMapping(m.getMapping());

                        // IMPORTANT: read full, stable content
                        String dslr = readStableString(p, 1000);
                        String drl = exp.expand(dslr);

                        out.put(p, drl);
                    } catch (Exception e) {
                        throw new RuntimeException("Expand failed: "+p, e);
                    }
                });

        return out;
    }
}
