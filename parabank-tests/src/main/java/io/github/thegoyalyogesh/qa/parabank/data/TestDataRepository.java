package io.github.thegoyalyogesh.qa.parabank.data;

import io.github.thegoyalyogesh.qa.core.excel.ExcelUtil;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;
import io.github.thegoyalyogesh.qa.parabank.model.TransferCase;
import io.github.thegoyalyogesh.qa.parabank.model.TransferType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes the ParaBank test-data workbook. All access goes through ExcelUtil (thread-safe writes). */
public final class TestDataRepository {

    public static final String USERS = "Users";
    public static final String LOGIN_NEGATIVE = "LoginNegative";
    public static final String TRANSFERS = "Transfers";

    /** {@code {U01}} in a cell means "the username of user U01". */
    private static final Pattern USER_REFERENCE = Pattern.compile("\\{(U\\d+)}");

    private final Path file;

    public TestDataRepository(Path file) {
        this.file = file;
    }

    /**
     * Repository on a working copy of the template. The template (tracked in git) is never written to.
     * First use copies it; an existing copy from an older version gets any sheet it is missing, and keeps its data.
     */
    public static TestDataRepository fromTemplate(Path workingFile, Path template) {
        try {
            if (Files.notExists(workingFile)) {
                Files.createDirectories(workingFile.toAbsolutePath().getParent());
                Files.copy(template, workingFile);
            } else {
                List<String> present = ExcelUtil.sheetNames(workingFile);
                ExcelUtil.sheetNames(template).stream()
                        .filter(sheet -> !present.contains(sheet))
                        .forEach(sheet -> ExcelUtil.writeSheet(workingFile, sheet,
                                ExcelUtil.readHeaders(template, sheet), ExcelUtil.readSheet(template, sheet)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + workingFile + " from " + template, e);
        }
        return new TestDataRepository(workingFile);
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

    public List<TransferCase> transferCases(TransferType type) {
        return ExcelUtil.readSheet(file, TRANSFERS).stream()
                .map(TestDataRepository::toTransferCase)
                .filter(transfer -> transfer.type() == type)
                .toList();
    }

    public TransferCase transferCase(String transferId) {
        return ExcelUtil.readSheet(file, TRANSFERS).stream()
                .filter(row -> transferId.equals(row.get("TransferId")))
                .map(TestDataRepository::toTransferCase)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No transfer '" + transferId + "' in sheet " + TRANSFERS + " of " + file.toAbsolutePath()));
    }

    private static TransferCase toTransferCase(Map<String, String> row) {
        return new TransferCase(
                row.get("TransferId"),
                TransferType.valueOf(row.get("Type").trim().toUpperCase(Locale.ROOT)),
                row.getOrDefault("FromUser", "").trim(),
                row.getOrDefault("ToUser", "").trim(),
                new BigDecimal(row.get("Amount").trim()),
                row.getOrDefault("Description", ""));
    }

    public void recordTransfer(String transferId, Map<String, String> values) {
        Map<String, String> all = new LinkedHashMap<>(values);
        all.put("ExecutedAt", now());
        ExcelUtil.updateRow(file, TRANSFERS, "TransferId", transferId, all);
    }

    private static String shorten(String text) {
        String single = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return single.length() > 200 ? single.substring(0, 200) + "…" : single;
    }

    private static String now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
