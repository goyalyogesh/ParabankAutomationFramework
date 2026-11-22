package io.github.thegoyalyogesh.qa.core.excel;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelUtilUpdateTest {

    private Path file;

    @BeforeMethod
    public void setUp() throws IOException {
        file = Files.createTempDirectory("excel-update").resolve("data.xlsx");
        List<Map<String, String>> rows = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> Map.of("UserId", "U" + i, "Status", ""))
                .toList();
        ExcelUtil.writeSheet(file, "Users", List.of("UserId", "Status"), rows);
    }

    @Test
    public void updatesExistingColumnOfMatchingRow() {
        ExcelUtil.updateRow(file, "Users", "UserId", "U3", Map.of("Status", "REGISTERED"));

        Map<String, String> row = ExcelUtil.readSheet(file, "Users").get(2);
        assertThat(row).containsEntry("UserId", "U3").containsEntry("Status", "REGISTERED");
    }

    @Test
    public void addsMissingColumnToHeader() {
        ExcelUtil.updateRow(file, "Users", "UserId", "U1", Map.of("AccountNumber", "13344"));

        List<Map<String, String>> rows = ExcelUtil.readSheet(file, "Users");
        assertThat(rows.get(0)).containsEntry("AccountNumber", "13344");
        assertThat(rows.get(1)).containsEntry("AccountNumber", "");
    }

    @Test
    public void unknownKeyValueThrows() {
        assertThatThrownBy(() -> ExcelUtil.updateRow(file, "Users", "UserId", "U99", Map.of("Status", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("U99");
    }

    @Test
    public void unknownKeyColumnThrows() {
        assertThatThrownBy(() -> ExcelUtil.updateRow(file, "Users", "Nope", "U1", Map.of("Status", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nope");
    }

    @Test
    public void concurrentUpdatesAreAllPersisted() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<?>> futures = IntStream.rangeClosed(1, 10)
                    .<Future<?>>mapToObj(i -> pool.submit(() ->
                            ExcelUtil.updateRow(file, "Users", "UserId", "U" + i, Map.of("Status", "DONE-" + i))))
                    .toList();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(ExcelUtil.readSheet(file, "Users"))
                .extracting(row -> row.get("Status"))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj(i -> "DONE-" + i).toList());
    }
}
