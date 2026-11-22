package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ParaBankUserTest {

    private static final Map<String, String> ROW = Map.ofEntries(
            Map.entry("UserId", "U01"), Map.entry("FirstName", "Aarav"), Map.entry("LastName", "Sharma"),
            Map.entry("Street", "12 Park Ave"), Map.entry("City", "Austin"), Map.entry("State", "TX"),
            Map.entry("ZipCode", "73301"), Map.entry("Phone", "555-201-1001"), Map.entry("SSN", "501-11-1001"),
            Map.entry("Username", ""), Map.entry("Password", " "), Map.entry("Status", ""));

    @Test
    public void fromRowMapsColumnsAndTreatsMissingAsBlank() {
        ParaBankUser user = ParaBankUser.fromRow(ROW);

        assertThat(user.userId()).isEqualTo("U01");
        assertThat(user.fullName()).isEqualTo("Aarav Sharma");
        assertThat(user.customerId()).isEmpty();
        assertThat(user.hasCredentials()).isFalse();
        assertThat(user.isRegistered()).isFalse();
    }

    @Test
    public void withMethodsReturnNewCopies() {
        ParaBankUser original = ParaBankUser.fromRow(ROW);
        ParaBankUser withCreds = original.withCredentials("qa_1", "Pw1234abcd");
        ParaBankUser registered = withCreds.withRegistration("REGISTERED", "15431", "19893");

        assertThat(original.hasCredentials()).isFalse();
        assertThat(withCreds.hasCredentials()).isTrue();
        assertThat(registered.isRegistered()).isTrue();
        assertThat(registered.customerId()).isEqualTo("15431");
        assertThat(registered.accountNumber()).isEqualTo("19893");
        assertThat(registered.username()).isEqualTo("qa_1");
    }

    @Test
    public void existingStatusAlsoCountsAsRegistered() {
        assertThat(ParaBankUser.fromRow(ROW).withRegistration("EXISTING", "1", "2").isRegistered()).isTrue();
        assertThat(ParaBankUser.fromRow(ROW).withRegistration("FAILED", "", "").isRegistered()).isFalse();
    }
}
