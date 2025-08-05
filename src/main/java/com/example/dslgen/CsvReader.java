package com.example.dslgen;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class CsvReader {

    public static List<RuleRow> readRules(String filePath) throws Exception {
        List<RuleRow> rules = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int rowIndex = 0;

            while ((line = reader.readLine()) != null) {
                rowIndex++;
                if (rowIndex == 1) continue; // skip header

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 6) continue;

                String name = cols.get(0).trim();
                String procedureCategory = stripQuotes(cols.get(1).trim());
                String declarationType = stripQuotes(cols.get(2).trim());

                List<String> conditions = new ArrayList<>();
                for (int i = 3; i < cols.size() - 1; i++) {
                    String cond = cols.get(i).trim();
                    if (!cond.isEmpty()) {
                        // Normalize everything as "if"
                        cond = cond.replaceFirst("^(then|if)\\s*", "if ");
                        conditions.add(cond.replace("\n", " "));
                    }
                }

                String errorCode = cols.get(cols.size() - 1).trim();

                rules.add(new RuleRow(name, procedureCategory, declarationType, conditions, errorCode));
            }
        }

        return rules;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        boolean insideQuote = false;
        StringBuilder sb = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '\"') {
                insideQuote = !insideQuote;
            } else if (c == ',' && !insideQuote) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString()); // Add last token
        return tokens;
    }

    private static String stripQuotes(String input) {
        if (input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1).trim();
        }
        return input;
    }
}
