package io.github.thegoyalyogesh.qa.parabank.data;

import io.github.thegoyalyogesh.qa.core.excel.ExcelUtil;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes the ParaBank test-data workbook. All access goes through ExcelUtil (thread-safe writes). */
public final class TestDataRepository {

    public static final String USERS = "Users";
    public static final String LOGIN_NEGATIVE = "LoginNegative";

    /** {@code {U01}} in a cell means "the username of user U01". */
    private static final Pattern USER_REFERENCE = Pattern.compile("\\{(U\\d+)}");

    private final Path file;

    public TestDataRepository(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public List<ParaBankUser> users() {
        return ExcelUtil.readSheet(file, USERS).stream().map(ParaBankUser::fromRow).toList();
    }

    public List<ParaBankUser> registeredUsers() {
        return users().stream().filter(ParaBankUser::isRegistered).toList();
    }

    public ParaBankUser user(String userId) {
        return users().stream()
                .filter(user -> user.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No user '" + userId + "' in sheet " + USERS + " of " + file.toAbsolutePath()));
    }

    public LoginCase loginCase(String caseId) {
        Map<String, String> row = ExcelUtil.readSheet(file, LOGIN_NEGATIVE).stream()
                .filter(r -> caseId.equals(r.get("CaseId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No case '" + caseId + "' in sheet " + LOGIN_NEGATIVE + " of " + file.toAbsolutePath()));
        return new LoginCase(caseId,
                row.getOrDefault("Description", ""),
                resolve(row.getOrDefault("Username", "")),
                resolve(row.getOrDefault("Password", "")),
                row.getOrDefault("ExpectedError", ""));
    }

    String resolve(String cell) {
        Matcher matcher = USER_REFERENCE.matcher(cell.trim());
        if (!matcher.matches()) {
            return cell;
        }
        ParaBankUser user = user(matcher.group(1));
        if (!user.hasCredentials()) {
            throw new IllegalStateException("Placeholder " + cell + " needs user " + matcher.group(1)
                    + " to have credentials. Run the registration suite first.");
        }
        return user.username();
    }

    public void recordRegistration(RegistrationResult result, String browser) {
        ParaBankUser user = result.user();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Username", user.username());
        values.put("Password", user.password());
        values.put("Status", result.status().name());
        values.put("Acknowledgement", result.acknowledgement());
        values.put("CustomerId", user.customerId());
        values.put("AccountNumber", user.accountNumber());
        values.put("RegisteredAt", now());
        values.put("Browser", browser);
        ExcelUtil.updateRow(file, USERS, "UserId", user.userId(), values);
    }

    public void recordRegistrationFailure(String userId, String reason, String browser) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Status", RegistrationStatus.FAILED.name());
        values.put("Acknowledgement", shorten(reason));
        values.put("RegisteredAt", now());
        values.put("Browser", browser);
        ExcelUtil.updateRow(file, USERS, "UserId", userId, values);
    }

    public void recordLoginResult(String caseId, String result, String actualOutcome, String browser) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Result", result);
        values.put("ActualOutcome", shorten(actualOutcome));
        values.put("ExecutedAt", now());
        values.put("Browser", browser);
        ExcelUtil.updateRow(file, LOGIN_NEGATIVE, "CaseId", caseId, values);
    }

    private static String shorten(String text) {
        String single = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return single.length() > 200 ? single.substring(0, 200) + "…" : single;
    }

    private static String now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
