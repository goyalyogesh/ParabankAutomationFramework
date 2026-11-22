package io.github.thegoyalyogesh.qa.parabank.tools;

import io.github.thegoyalyogesh.qa.core.excel.ExcelUtil;
import io.github.thegoyalyogesh.qa.parabank.data.TestDataRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Creates testdata/ParaBankData.xlsx with 10 customers (credentials left blank so they get generated)
 * and the negative login cases. Run from the repository root:
 * <pre>
 * mvn -q -pl parabank-tests test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *   -Dexec.mainClass=io.github.thegoyalyogesh.qa.parabank.tools.CreateTestDataWorkbook \
 *   -Dexec.classpathScope=test -Dexec.args=testdata/ParaBankData.xlsx [-Doverwrite=true]
 * </pre>
 */
public final class CreateTestDataWorkbook {

    static final List<String> USER_HEADERS = List.of("UserId", "FirstName", "LastName", "Street", "City", "State",
            "ZipCode", "Phone", "SSN", "Username", "Password", "Status", "Acknowledgement", "CustomerId",
            "AccountNumber", "RegisteredAt", "Browser");

    static final List<String> NEGATIVE_HEADERS = List.of("CaseId", "Description", "Username", "Password",
            "ExpectedError", "Result", "ActualOutcome", "ExecutedAt", "Browser");

    private static final String[][] PEOPLE = {
            {"Aarav", "Sharma", "12 Park Ave", "Austin", "TX", "73301"},
            {"Priya", "Patel", "48 Lake St", "Seattle", "WA", "98101"},
            {"Liam", "Johnson", "7 Beacon St", "Boston", "MA", "02108"},
            {"Sofia", "Garcia", "301 Pine Rd", "Denver", "CO", "80202"},
            {"Noah", "Williams", "95 State St", "Chicago", "IL", "60601"},
            {"Ananya", "Iyer", "18 Ocean Dr", "Miami", "FL", "33101"},
            {"Mateo", "Rossi", "220 Market St", "San Francisco", "CA", "94105"},
            {"Emma", "Brown", "64 Elm St", "Portland", "OR", "97201"},
            {"Kabir", "Mehta", "5 River Rd", "Phoenix", "AZ", "85001"},
            {"Olivia", "Davis", "410 Main St", "Atlanta", "GA", "30301"}};

    private static final String NOT_VERIFIED = "The username and password could not be verified.";
    private static final String ENTER_BOTH = "Please enter a username and password.";

    private CreateTestDataWorkbook() {
    }

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "testdata/ParaBankData.xlsx");
        if (Files.exists(file)) {
            if (!Boolean.getBoolean("overwrite")) {
                System.out.println("Workbook already exists (use -Doverwrite=true to replace): " + file.toAbsolutePath());
                return;
            }
            Files.delete(file);
        }
        ExcelUtil.writeSheet(file, TestDataRepository.USERS, USER_HEADERS, users());
        ExcelUtil.writeSheet(file, TestDataRepository.LOGIN_NEGATIVE, NEGATIVE_HEADERS, negativeCases());
        System.out.println("Created " + file.toAbsolutePath());
    }

    static List<Map<String, String>> users() {
        return IntStream.range(0, PEOPLE.length).mapToObj(i -> {
            String[] p = PEOPLE[i];
            int n = i + 1;
            Map<String, String> row = new LinkedHashMap<>();
            row.put("UserId", "U%02d".formatted(n));
            row.put("FirstName", p[0]);
            row.put("LastName", p[1]);
            row.put("Street", p[2]);
            row.put("City", p[3]);
            row.put("State", p[4]);
            row.put("ZipCode", p[5]);
            row.put("Phone", "555-201-%04d".formatted(1000 + n));
            row.put("SSN", "5%02d-11-%04d".formatted(n, 1000 + n));
            return row;
        }).toList();
    }

    static List<Map<String, String>> negativeCases() {
        return List.of(
                negative("N01", "Wrong password for a registered customer", "{U01}", "WrongPass99", NOT_VERIFIED),
                negative("N02", "Unknown username", "qa_nobody_000", "Whatever123", NOT_VERIFIED),
                negative("N03", "Empty username", "", "Secret123", ENTER_BOTH),
                negative("N04", "Empty password", "{U01}", "", ENTER_BOTH),
                negative("N05", "Empty username and password", "", "", ENTER_BOTH),
                negative("N06", "SQL-injection-like input", "' OR '1'='1", "' OR '1'='1", ""),
                negative("N07", "300-character username and password", "a".repeat(300), "a".repeat(300), ""));
    }

    private static Map<String, String> negative(String id, String description, String username, String password,
                                                String expectedError) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("CaseId", id);
        row.put("Description", description);
        row.put("Username", username);
        row.put("Password", password);
        row.put("ExpectedError", expectedError);
        return row;
    }
}
