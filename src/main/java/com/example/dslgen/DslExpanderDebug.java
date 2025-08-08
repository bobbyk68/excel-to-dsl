package com.example.dslgen;

import org.drools.compiler.compiler.DSLTokenizedMappingFile;
import org.drools.compiler.compiler.DSLMappingEntry;
import org.drools.compiler.lang.dsl.DefaultExpander;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DslExpanderDebug {

    public static void main(String[] args) throws Exception {
        Path dslPath = Path.of("src/main/resources/rules/your-rules.dsl");
        Path dslrPath = Path.of("src/main/resources/rules/your-rules.dslr");

        // Read DSL file
        List<String> dslLines = Files.readAllLines(dslPath);
        DSLTokenizedMappingFile dslFile = new DSLTokenizedMappingFile();
        dslFile.parseAndLoad(dslLines);

        // Read DSLR file
        String dslrContent = Files.readString(dslrPath);

        // Expand DSLR to DRL
        DefaultExpander expander = new DefaultExpander();
        expander.addDSLMapping(dslFile.getMapping());

        String expandedDrl = expander.expand(dslrContent);

        // Print the generated DRL
        System.out.println("----- Generated DRL -----");
        System.out.println(expandedDrl);
    }
}
