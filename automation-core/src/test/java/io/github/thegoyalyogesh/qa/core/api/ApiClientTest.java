package io.github.thegoyalyogesh.qa.core.api;

import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiClientTest {

    @Test
    public void specCarriesBaseUriAndJsonAccept() {
        ApiClient client = new ApiClient("https://example.test/api");
        QueryableRequestSpecification spec = SpecificationQuerier.query(client.spec());

        assertThat(spec.getBaseUri()).isEqualTo("https://example.test/api");
        assertThat(spec.getHeaders().getValue("Accept")).contains("application/json");
    }
}
