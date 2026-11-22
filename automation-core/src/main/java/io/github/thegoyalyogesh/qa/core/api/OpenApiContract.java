package io.github.thegoyalyogesh.qa.core.api;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import io.restassured.filter.Filter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An OpenAPI specification used as a test oracle. {@link #filter()} plugs into Rest Assured and fails the request
 * if the request or the response doesn't match the spec (paths, parameters, status, content type, JSON schema).
 * {@link #operations()} lists every operation, so a test can check that the client covers the whole API.
 * <p>
 * The spec is a local copy (classpath resource or file path), not the live URL: tests must not change because
 * the server's spec changed, and a changed spec should be a reviewed commit.
 */
public final class OpenApiContract {

    private final String location;
    private final OpenApiInteractionValidator validator;
    private final OpenAPI openApi;

    private OpenApiContract(String location, OpenApiInteractionValidator validator, OpenAPI openApi) {
        this.location = location;
        this.validator = validator;
        this.openApi = openApi;
    }

    /** {@code location}: classpath resource (e.g. {@code openapi/parabank-openapi.yaml}), file path or URL. */
    public static OpenApiContract load(String location) {
        OpenAPI openApi = new OpenAPIV3Parser().read(location);
        if (openApi == null || openApi.getPaths() == null) {
            throw new IllegalArgumentException("Cannot read OpenAPI spec: " + location);
        }
        OpenApiInteractionValidator validator = OpenApiInteractionValidator.createForSpecificationUrl(location).build();
        return new OpenApiContract(location, validator, openApi);
    }

    /** A new Rest Assured filter; a mismatch throws an exception listing every violation. */
    public Filter filter() {
        return new OpenApiValidationFilter(validator);
    }

    /** {@code "GET /accounts/{accountId}"}, sorted by path then method. */
    public SortedSet<String> operations() {
        SortedSet<String> operations = new TreeSet<>((a, b) -> {
            int byPath = a.substring(a.indexOf(' ') + 1).compareTo(b.substring(b.indexOf(' ') + 1));
            return byPath != 0 ? byPath : a.compareTo(b);
        });
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap().keySet()
                .forEach(method -> operations.add(method.name().toUpperCase(Locale.ROOT) + " " + path)));
        return operations;
    }

    @Override
    public String toString() {
        return "OpenApiContract[" + location + "]";
    }
}
