package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.core.api.OpenApiContract;
import io.github.thegoyalyogesh.qa.parabank.api.ParaBankApi;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The client covers the whole OpenAPI contract, except the admin operations that must never be called. */
public class ApiCoverageTest {

    private static final OpenApiContract CONTRACT = OpenApiContract.load("openapi/parabank-openapi.yaml");

    @Test
    public void everyOperationIsImplementedOrDeliberatelyExcluded() {
        Set<String> notCovered = new TreeSet<>(CONTRACT.operations());
        notCovered.removeAll(ParaBankApi.OPERATIONS);
        notCovered.removeAll(ParaBankApi.EXCLUDED_OPERATIONS);
        assertThat(notCovered).as("operations in the spec without a client method").isEmpty();

        assertThat(CONTRACT.operations())
                .as("client operations must exist in the spec (typo in a path template?)")
                .containsAll(ParaBankApi.OPERATIONS)
                .containsAll(ParaBankApi.EXCLUDED_OPERATIONS);
        assertThat(ParaBankApi.OPERATIONS).doesNotContainAnyElementsOf(ParaBankApi.EXCLUDED_OPERATIONS);
    }

    /** Admin endpoints reset or reconfigure the shared public site: no code may reference them. */
    @Test
    public void noCodeReferencesTheAdminEndpoints() throws IOException {
        List<String> forbidden = List.of("cleanDB", "initializeDB", "setParameter", "JmsListener");
        Path self = Path.of("src/test/java/io/github/thegoyalyogesh/qa/parabank/unit/ApiCoverageTest.java");
        Path apiClass = Path.of("src/main/java/io/github/thegoyalyogesh/qa/parabank/api/ParaBankApi.java");
        List<String> offenders;
        try (Stream<Path> files = Stream.concat(Files.walk(Path.of("src/main")), Files.walk(Path.of("src/test")))) {
            offenders = files.filter(Files::isRegularFile)
                    .filter(file -> !file.toString().endsWith(".yaml"))           // the spec itself
                    .filter(file -> !file.equals(self) && !file.equals(apiClass)) // the exclusion list
                    .filter(file -> {
                        try {
                            String text = Files.readString(file);
                            return forbidden.stream().anyMatch(text::contains);
                        } catch (IOException e) {
                            return false; // binary file (e.g. xlsx)
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertThat(offenders).as("files referencing admin endpoints").isEmpty();
    }
}
