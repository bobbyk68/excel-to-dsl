// ExcelToDslGenerator.java
package com.example.dslgen;

import com.example.dslgen.builder.DslBuilder;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExcelToDslGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java ExcelToDslGenerator <path-to-excel>");
            return;
        }

        String excelPath = args[0];
        List<RuleRow> rules = ExcelReader.readRules(excelPath);

        DslBuilder builder = new DslBuilder();
        DslBuilder.Result result = builder.build(rules);

        Files.createDirectories(Paths.get("output"));
        try (FileWriter dsl = new FileWriter("output/rules.dsl");
             FileWriter dslr = new FileWriter("output/rules.dslr")) {
            for (String line : result.dslLines) dsl.write(line + "\n");
            for (String line : result.dslrLines) dslr.write(line + "\n");
        }

        System.out.println("DSL and DSLR generation completed.");
    }
}