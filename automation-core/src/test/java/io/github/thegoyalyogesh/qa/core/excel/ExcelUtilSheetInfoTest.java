package io.github.thegoyalyogesh.qa.core.excel;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelUtilSheetInfoTest {

    @Test
    public void listsSheetNamesInWorkbookOrderAndHeadersInColumnOrder() throws IOException {
        Path file = Files.createTempDirectory("info").resolve("w.xlsx");
        ExcelUtil.writeSheet(file, "Users", List.of("Id", "Name", "Empty"), List.of(Map.of("Id", "1", "Name", "Ann")));
        ExcelUtil.writeSheet(file, "Cases", List.of("CaseId"), List.of());

        assertThat(ExcelUtil.sheetNames(file)).containsExactly("Users", "Cases");
        assertThat(ExcelUtil.readHeaders(file, "Users")).containsExactly("Id", "Name", "Empty");
        assertThat(ExcelUtil.readHeaders(file, "Cases")).containsExactly("CaseId");
    }

    @Test
    public void unknownSheetGivesClearError() throws IOException {
        Path file = Files.createTempDirectory("info").resolve("w.xlsx");
        ExcelUtil.writeSheet(file, "Users", List.of("Id"), List.of());
        assertThatThrownBy(() -> ExcelUtil.readHeaders(file, "Nope")).hasMessageContaining("Nope");
    }
}
