package io.github.thegoyalyogesh.qa.core.excel;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Generic Excel (.xlsx) helper. Row 0 of every sheet is the header row; each data row is returned as a
 * map of header → cell text. All writes are serialised with one lock and saved via temp file + atomic move.
 */
public final class ExcelUtil {

    private static final ReentrantLock WRITE_LOCK = new ReentrantLock();
    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelUtil() {
    }

    /** Reads all non-blank data rows of a sheet. Column order is preserved. */
    public static List<Map<String, String>> readSheet(Path file, String sheetName) {
        try (Workbook workbook = open(file)) {
            Sheet sheet = requireSheet(workbook, sheetName, file);
            List<String> headers = headers(sheet);
            return IntStream.rangeClosed(1, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .filter(Objects::nonNull)
                    .map(row -> toMap(headers, row))
                    .filter(map -> map.values().stream().anyMatch(value -> !value.isBlank()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Excel file " + file, e);
        }
    }

    /** Creates (or replaces) one sheet; other sheets in an existing file are kept. */
    public static void writeSheet(Path file, String sheetName, List<String> headers,
                                  List<Map<String, String>> rows) {
        WRITE_LOCK.lock();
        try (Workbook workbook = Files.exists(file) ? open(file) : new XSSFWorkbook()) {
            int existing = workbook.getSheetIndex(sheetName);
            if (existing >= 0) {
                workbook.removeSheetAt(existing);
            }
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            IntStream.range(0, headers.size())
                    .forEach(col -> headerRow.createCell(col).setCellValue(headers.get(col)));
            IntStream.range(0, rows.size()).forEach(r -> {
                Row row = sheet.createRow(r + 1);
                IntStream.range(0, headers.size()).forEach(col ->
                        row.createCell(col).setCellValue(rows.get(r).getOrDefault(headers.get(col), "")));
            });
            save(workbook, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write Excel file " + file, e);
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    static Workbook open(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new XSSFWorkbook(in);
        }
    }

    static Sheet requireSheet(Workbook workbook, String sheetName, Path file) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet '" + sheetName + "' not found in " + file);
        }
        return sheet;
    }

    static List<String> headers(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new IllegalArgumentException("Sheet '" + sheet.getSheetName() + "' has no header row");
        }
        return IntStream.range(0, header.getLastCellNum())
                .mapToObj(col -> FORMATTER.formatCellValue(header.getCell(col)).trim())
                .toList();
    }

    static String cellText(Row row, int col) {
        return FORMATTER.formatCellValue(row.getCell(col)).trim();
    }

    static void save(Workbook workbook, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = parent.resolve(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temp)) {
            workbook.write(out);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Map<String, String> toMap(List<String> headers, Row row) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int col = 0; col < headers.size(); col++) {
            String header = headers.get(col);
            if (!header.isEmpty()) {
                values.put(header, cellText(row, col));
            }
        }
        return Collections.unmodifiableMap(values);
    }
}
