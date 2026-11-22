package io.github.thegoyalyogesh.qa.core.api;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/** Base for REST clients: one shared request specification (base URI, JSON) per API. */
public class ApiClient {

    private final RequestSpecification spec;

    public ApiClient(String baseUri) {
        this.spec = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setAccept(ContentType.JSON)
                .build();
    }

    public RequestSpecification spec() {
        return spec;
    }

    /** New request using the shared spec; request and response are logged only if a validation fails. */
    public RequestSpecification request() {
        return RestAssured.given().spec(spec).log().ifValidationFails();
    }
}
