package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.parabank.data.UserRegistry;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class UserRegistryTest {

    @AfterMethod
    public void clear() {
        UserRegistry.clear();
    }

    private static ParaBankUser user(int i) {
        return ParaBankUser.fromRow(Map.of("UserId", "U%03d".formatted(i), "FirstName", "F" + i));
    }

    @Test
    public void parallelRegistrationsAreAllKeptAndSortedById() {
        IntStream.rangeClosed(1, 200).parallel().forEach(i -> UserRegistry.register(user(i)));

        assertThat(UserRegistry.size()).isEqualTo(200);
        assertThat(UserRegistry.all().get(0).userId()).isEqualTo("U001");
        assertThat(UserRegistry.all().get(199).userId()).isEqualTo("U200");
    }

    @Test
    public void findReturnsLatestVersionOfUser() {
        UserRegistry.register(user(1));
        UserRegistry.register(user(1).withCredentials("qa_new", "pw"));

        assertThat(UserRegistry.find("U001")).get().extracting(ParaBankUser::username).isEqualTo("qa_new");
        assertThat(UserRegistry.find("U999")).isEmpty();
    }
}
