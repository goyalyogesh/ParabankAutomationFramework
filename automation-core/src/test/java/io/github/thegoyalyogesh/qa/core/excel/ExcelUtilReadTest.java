package io.github.thegoyalyogesh.qa.core.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelUtilReadTest {

    private Path file;

    @BeforeMethod
    public void setUp() throws IOException {
        file = Files.createTempDirectory("excel-read").resolve("data.xlsx");
    }

    @Test
    public void writeSheetThenReadSheetReturnsRowsKeyedByHeader() {
        ExcelUtil.writeSheet(file, "Users", List.of("UserId", "FirstName"),
                List.of(Map.of("UserId", "U01", "FirstName", "Ann"),
                        Map.of("UserId", "U02", "FirstName", "Bob")));

        List<Map<String, String>> rows = ExcelUtil.readSheet(file, "Users");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("UserId", "U01").containsEntry("FirstName", "Ann");
        assertThat(rows.get(1).keySet()).containsExactly("UserId", "FirstName");
    }

    @Test
    public void blankRowsAreSkipped() {
        ExcelUtil.writeSheet(file, "Users", List.of("Name"),
                List.of(Map.of("Name", "A"), Map.of("Name", ""), Map.of("Name", "B")));

        assertThat(ExcelUtil.readSheet(file, "Users"))
                .extracting(row -> row.get("Name"))
                .containsExactly("A", "B");
    }

    @Test
    public void numericCellsAreReadAsDisplayedText() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = wb.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Zip");
            sheet.createRow(1).createCell(0).setCellValue(90210);
            wb.write(out);
        }

        assertThat(ExcelUtil.readSheet(file, "Data").get(0)).containsEntry("Zip", "90210");
    }

    @Test
    public void writeSheetKeepsOtherSheets() {
        ExcelUtil.writeSheet(file, "A", List.of("x"), List.of(Map.of("x", "1")));
        ExcelUtil.writeSheet(file, "B", List.of("y"), List.of(Map.of("y", "2")));

        assertThat(ExcelUtil.readSheet(file, "A").get(0)).containsEntry("x", "1");
        assertThat(ExcelUtil.readSheet(file, "B").get(0)).containsEntry("y", "2");
    }

    @Test
    public void missingSheetThrowsWithSheetName() {
        ExcelUtil.writeSheet(file, "Users", List.of("Name"), List.of(Map.of("Name", "A")));

        assertThatThrownBy(() -> ExcelUtil.readSheet(file, "Nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nope");
    }
}
