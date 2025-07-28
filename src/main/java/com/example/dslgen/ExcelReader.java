// ExcelReader.java
package com.example.dslgen;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.*;

public class ExcelReader {

    public static List<RuleRow> readRules(String filePath) throws Exception {
        List<RuleRow> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();
            int rowIndex = 0;

            while (iterator.hasNext()) {
                Row row = iterator.next();
                rowIndex++;
                if (rowIndex == 1) continue; // Skip header row
                if (row.getZeroHeight()) continue; // Skip hidden rows

                Cell col1 = row.getCell(0); // Description
                Cell col2 = row.getCell(1); // Rule ID
                Cell col3 = row.getCell(2); // Procedure Category
                Cell col4 = row.getCell(3); // Declaration Type
                Cell col5 = row.getCell(4); // Pseudocode

                if (isEmpty(col2) || isEmpty(col1) || isEmpty(col5)) continue;

                String name = col2.getStringCellValue().trim() + "_" + col1.getStringCellValue().trim().replace(" ", "_");
                String procCategory = col3 != null ? col3.getStringCellValue().trim() : "";
                String declType = col4 != null ? col4.getStringCellValue().trim() : "";
                String pseudocode = col5.getStringCellValue().trim();
                String[] lines = pseudocode.split("\n");

                List<String> conditions = new ArrayList<>();
                String errorMsg = "E000X";
                boolean foundIf = false;

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (!foundIf) {
                        if (line.toLowerCase().startsWith("if")) {
                            foundIf = true;
                            conditions.add(line);
                        }
                        continue;
                    }

                    if (line.matches("E\\d{4}")) {
                        errorMsg = line;
                        break;
                    }

                    // Keep "then ..." lines as conditions too
                    conditions.add(line);
                }

                rows.add(new RuleRow(name, procCategory, declType, conditions, errorMsg));
            }
        }

        return rows;
    }

    private static boolean isEmpty(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK || cell.toString().trim().isEmpty();
    }
}
