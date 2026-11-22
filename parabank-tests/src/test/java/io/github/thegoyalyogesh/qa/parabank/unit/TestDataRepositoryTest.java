package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.core.excel.ExcelUtil;
import io.github.thegoyalyogesh.qa.parabank.data.TestDataRepository;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;
import io.github.thegoyalyogesh.qa.parabank.model.TransferCase;
import io.github.thegoyalyogesh.qa.parabank.model.TransferType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestDataRepositoryTest {

    private Path file;
    private TestDataRepository repository;

    @BeforeMethod
    public void setUp() throws IOException {
        file = Files.createTempDirectory("repo").resolve("data.xlsx");
        ExcelUtil.writeSheet(file, TestDataRepository.USERS,
                List.of("UserId", "FirstName", "LastName", "Username", "Password", "Status"),
                List.of(Map.of("UserId", "U01", "FirstName", "Ann", "LastName", "Lee",
                                "Username", "qa_ann", "Password", "Pw1", "Status", "REGISTERED"),
                        Map.of("UserId", "U02", "FirstName", "Bob", "LastName", "Ray")));
        ExcelUtil.writeSheet(file, TestDataRepository.LOGIN_NEGATIVE,
                List.of("CaseId", "Description", "Username", "Password", "ExpectedError"),
                List.of(Map.of("CaseId", "N01", "Description", "Wrong password", "Username", "{U01}",
                                "Password", "bad", "ExpectedError", "could not be verified"),
                        Map.of("CaseId", "N02", "Username", "{U02}", "Password", "x")));
        ExcelUtil.writeSheet(file, TestDataRepository.TRANSFERS,
                List.of("TransferId", "Type", "FromUser", "ToUser", "Amount", "Description", "Result", "Confirmation", "ExecutedAt"),
                List.of(Map.of("TransferId", "T01", "Type", "OWN", "FromUser", "U01", "Amount", "25", "Description", "to savings"),
                        Map.of("TransferId", "C01", "Type", "chain", "FromUser", "U01", "ToUser", "U02", "Amount", "12.5"),
                        Map.of("TransferId", "C02", "Type", "CHAIN", "FromUser", "U02", "ToUser", "U01", "Amount", "2")));
        repository = new TestDataRepository(file);
    }

    @Test
    public void readsUsersAndRegisteredSubset() {
        assertThat(repository.users()).extracting(ParaBankUser::userId).containsExactly("U01", "U02");
        assertThat(repository.registeredUsers()).extracting(ParaBankUser::userId).containsExactly("U01");
        assertThat(repository.user("U02").fullName()).isEqualTo("Bob Ray");
    }

    @Test
    public void unknownUserGivesClearError() {
        assertThatThrownBy(() -> repository.user("U99")).hasMessageContaining("U99");
    }

    @Test
    public void loginCaseResolvesUserPlaceholder() {
        LoginCase loginCase = repository.loginCase("N01");
        assertThat(loginCase.username()).isEqualTo("qa_ann");
        assertThat(loginCase.expectedError()).isEqualTo("could not be verified");
    }

    @Test
    public void placeholderForUserWithoutCredentialsExplainsWhatToDo() {
        assertThatThrownBy(() -> repository.loginCase("N02"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("U02")
                .hasMessageContaining("registration");
    }

    @Test
    public void recordRegistrationWritesBackToTheUserRow() {
        ParaBankUser user = repository.user("U02").withCredentials("qa_bob", "Pw2")
                .withRegistration("REGISTERED", "123", "456");
        repository.recordRegistration(new RegistrationResult(user, RegistrationStatus.REGISTERED, "created"), "CHROME");

        ParaBankUser saved = repository.user("U02");
        assertThat(saved.username()).isEqualTo("qa_bob");
        assertThat(saved.status()).isEqualTo("REGISTERED");
        assertThat(saved.customerId()).isEqualTo("123");
        assertThat(saved.accountNumber()).isEqualTo("456");
    }

    @Test
    public void recordLoginResultWritesBackToTheCaseRow() {
        repository.recordLoginResult("N01", "FAIL", "LOGGED_IN", "FIREFOX");

        Map<String, String> row = ExcelUtil.readSheet(file, TestDataRepository.LOGIN_NEGATIVE).get(0);
        assertThat(row).containsEntry("Result", "FAIL").containsEntry("ActualOutcome", "LOGGED_IN")
                .containsEntry("Browser", "FIREFOX");
    }

    @Test
    public void readsTransferCasesByTypeInSheetOrder() {
        assertThat(repository.transferCases(TransferType.CHAIN)).extracting(TransferCase::transferId)
                .containsExactly("C01", "C02");
        TransferCase own = repository.transferCase("T01");
        assertThat(own.type()).isEqualTo(TransferType.OWN);
        assertThat(own.amount()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(own.toUserId()).isEmpty();
    }

    @Test
    public void recordTransferWritesResultAndTimestamp() {
        repository.recordTransfer("C02", Map.of("Result", "PASS", "Confirmation", "done"));
        Map<String, String> row = ExcelUtil.readSheet(file, TestDataRepository.TRANSFERS).get(2);
        assertThat(row).containsEntry("Result", "PASS").containsEntry("Confirmation", "done");
        assertThat(row.get("ExecutedAt")).isNotBlank();
    }

    @Test
    public void fromTemplateCopiesOnFirstUseAndAddsSheetsMissingFromAnOlderCopy() throws IOException {
        Path dir = Files.createTempDirectory("tpl");
        Path working = dir.resolve("work.xlsx");
        TestDataRepository.fromTemplate(working, file);
        assertThat(ExcelUtil.sheetNames(working)).containsExactlyElementsOf(ExcelUtil.sheetNames(file));

        Path older = dir.resolve("older.xlsx");
        ExcelUtil.writeSheet(older, TestDataRepository.USERS, List.of("UserId", "Username"),
                List.of(Map.of("UserId", "U01", "Username", "keep_me")));
        TestDataRepository.fromTemplate(older, file);
        assertThat(ExcelUtil.sheetNames(older)).contains(TestDataRepository.TRANSFERS, TestDataRepository.LOGIN_NEGATIVE);
        assertThat(ExcelUtil.readSheet(older, TestDataRepository.USERS).get(0)).containsEntry("Username", "keep_me");
    }
}
