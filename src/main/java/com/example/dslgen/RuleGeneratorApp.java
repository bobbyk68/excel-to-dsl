package com.example.dslgen;

import com.example.dslgen.builder.DslBuilder;
import com.example.dslgen.pattern.HybridPatternMatcher;
import com.example.dslgen.pattern.PatternMatcher;
import com.example.dslgen.pattern.ThenPatternEnum;
import com.example.dslgen.pattern.WhenPatternEnum;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;

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

        // Group rules by prefix extracted from name (e.g., BR678)
        Map<String, List<RuleRow>> groupedRules = new HashMap<>();
        Pattern pattern = Pattern.compile("^(BR\\d+)"); // e.g., "BR678"

        for (RuleRow row : rules) {
            Matcher matcher = pattern.matcher(row.getName());
            if (matcher.find()) {
                String prefix = matcher.group(1);
                groupedRules.computeIfAbsent(prefix, k -> new ArrayList<>()).add(row);
            } else {
                System.err.println("⚠️ No rule prefix found in name: " + row.getName());
            }
        }

        // Create DSL and DSLR output per group
        PatternMatcher whenMatcher = new HybridPatternMatcher(List.of(WhenPatternEnum.values()));
        PatternMatcher thenMatcher = new HybridPatternMatcher(List.of(ThenPatternEnum.values()));
        DslBuilder builder = new DslBuilder(whenMatcher, thenMatcher);

        Files.createDirectories(Paths.get("output"));

        for (Map.Entry<String, List<RuleRow>> entry : groupedRules.entrySet()) {
            String prefix = entry.getKey();
            List<RuleRow> group = entry.getValue();

            DslBuilder.Result result = builder.build(group);

            try (FileWriter dsl = new FileWriter("output/" + prefix + "_rules.dsl");
                 FileWriter dslr = new FileWriter("output/" + prefix + "_rules.dslr")) {
                for (String line : result.dslLines) dsl.write(line + "\n");
                for (String line : result.dslrLines) dslr.write(line + "\n");
            }

            System.out.println("✅ Generated: " + prefix + "_rules.dsl and .dslr");
        }
    }
}
