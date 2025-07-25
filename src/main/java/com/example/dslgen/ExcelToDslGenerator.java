
package com.example.dslgen;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class ExcelToDslGenerator {
    private static final List<String> ALL_DECL_TYPES = List.of("A", "D", "Y", "Z", "C", "J", "F");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java -jar excel-to-dsl.jar <path-to-xlsm>");
            System.exit(1);
        }
        generateFrom(args[0]);
    }

    public static void generateFrom(String excelPath) throws Exception {
        InputStream inp = new FileInputStream(excelPath);
        Workbook workbook = new XSSFWorkbook(inp);
        Sheet sheet = workbook.getSheetAt(0);

        DslBuilder builder = new DslBuilder();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String col1 = getCell(row, 0);
            String col2 = getCell(row, 1);
            String col3 = getCell(row, 2);
            String procCat = getCell(row, 3);
            String declType = getCell(row, 4);

            if (col1.isEmpty() && col2.isEmpty() && col3.isEmpty()) continue;

            String ruleName = col2 + "_" + col1;
            String pseudo = col3;

            String[] lines = pseudo.split("\\R");
            String logicLine = lines[0].trim();
            String errorCode = lines.length > 1 ? lines[1].trim() : "UNKNOWN";

            var parsed = ConditionOnlyPseudocodeParser.parse(logicLine, errorCode);
            builder.addRule(ruleName, procCat, declType, parsed.dslrWhen, parsed.dslrThen, parsed.thenRhs);
        }

        builder.writeDslFile("generated-output/output.dsl");
        builder.writeDslrFile("generated-output/output.dslr");
    }

    private static String getCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        return cell == null ? "" : cell.toString().trim();
    }
}
