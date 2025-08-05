// RuleGeneratorApp.java
package com.example.dslgen;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class RuleGeneratorApp {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java RuleGeneratorApp <path-to-excel-or-csv>");
            return;
        }

        String inputPath = args[0];
        List<RuleRow> rules;

        if (inputPath.toLowerCase().endsWith(".csv")) {
            rules = CsvReader.readRules(inputPath);
        } else if (inputPath.toLowerCase().endsWith(".xlsx") || inputPath.toLowerCase().endsWith(".xlsm")) {
            rules = ExcelReader.readRules(inputPath);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + inputPath);
        }

        DslBuilder builder = new DslBuilder();
        DslBuilder.Result result = builder.build(rules);

        Files.createDirectories(Paths.get("output"));
        try (FileWriter dsl = new FileWriter("output/rules.dsl");
             FileWriter dslr = new FileWriter("output/rules.dslr")) {
            for (String line : result.dslLines) dsl.write(line + "\n");
            for (String line : result.dslrLines) dslr.write(line + "\n");
        }

        System.out.println("✅ DSL and DSLR generation completed.");
    }
}
