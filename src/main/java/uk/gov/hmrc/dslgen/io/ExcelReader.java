package uk.gov.hmrc.dslgen.io;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an Excel sheet with two columns (IF and THEN).
 */
public final class ExcelReader {

    private ExcelReader() {}

    /** Immutable row structure. */
    public record IfThenRow(int rowNumber, String ifBlock, String thenBlock) {}

    /**
     * Reads IF/THEN pairs from an Excel sheet.
     *
     * @param path       Path to XLSX file
     * @param sheetName  Sheet name (null to use first sheet)
     * @param sheetIndex Sheet index (null to use sheetName or 0)
     * @param headerRow  Row index (0-based) of header row
     * @param ifHeader   Header label for IF column (fallback = col0)
     * @param thenHeader Header label for THEN column (fallback = col1)
     */
    public static List<IfThenRow> readIfThenRows(
            Path path,
            String sheetName,
            Integer sheetIndex,
            int headerRow,
            String ifHeader,
            String thenHeader) {

        try (var in = Files.newInputStream(path);
             Workbook wb = new XSSFWorkbook(in)) {

            Sheet sheet = resolveSheet(wb, sheetName, sheetIndex);
            Row header = sheet.getRow(headerRow);

            int ifCol = -1, thenCol = -1;
            if (header != null) {
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    String h = readString(header.getCell(c));
                    if (ifHeader.equalsIgnoreCase(h.trim()))  ifCol = c;
                    if (thenHeader.equalsIgnoreCase(h.trim())) thenCol = c;
                }
            }

            // fallback if headers not found
            if (ifCol < 0) ifCol = 0;
            if (thenCol < 0) thenCol = 1;

            List<IfThenRow> out = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = headerRow + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String ifTxt = readString(row.getCell(ifCol));
                String thenTxt = readString(row.getCell(thenCol));
                if ((ifTxt == null || ifTxt.isBlank()) &&
                    (thenTxt == null || thenTxt.isBlank())) {
                    continue; // skip empty rows
                }
                out.add(new IfThenRow(r + 1, ifTxt, thenTxt)); // 1-based row number
            }
            return out;

        } catch (IOException e) {
            throw new RuntimeException("Error reading Excel file " + path, e);
        }
    }

    private static Sheet resolveSheet(Workbook wb, String sheetName, Integer sheetIndex) {
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet s = wb.getSheet(sheetName);
            if (s != null) return s;
        }
        if (sheetIndex != null) {
            return wb.getSheetAt(sheetIndex);
        }
        return wb.getSheetAt(0);
    }

    private static String readString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> Double.toString(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
