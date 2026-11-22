package io.github.thegoyalyogesh.qa.core.retry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Reads Cucumber's {@code json:} plugin output. A scenario passed only if every hook and step passed. */
public final class CucumberJsonReader {

    private CucumberJsonReader() {
    }

    /** All scenarios from every {@code *.json} file in the directory (one file per runner); none if it doesn't exist. */
    public static List<ScenarioResult> readDirectory(Path directory) {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .flatMap(file -> read(file).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + directory, e);
        }
    }

    public static List<ScenarioResult> read(Path file) {
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        if (json.isBlank()) {
            return List.of(); // a runner that ran nothing can leave an empty file
        }
        List<ScenarioResult> results = new ArrayList<>();
        for (JsonElement featureElement : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject feature = featureElement.getAsJsonObject();
            String uri = feature.get("uri").getAsString();
            String featureName = feature.get("name").getAsString();
            for (JsonElement element : array(feature, "elements")) {
                JsonObject scenario = element.getAsJsonObject();
                if (!"scenario".equals(scenario.get("type").getAsString())) {
                    continue; // backgrounds are reported separately; their steps are part of each scenario run
                }
                Set<String> tags = stream(array(scenario, "tags"))
                        .map(tag -> tag.getAsJsonObject().get("name").getAsString())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                boolean passed = Stream.of("before", "steps", "after")
                        .flatMap(section -> stream(array(scenario, section)))
                        .map(item -> item.getAsJsonObject().getAsJsonObject("result").get("status").getAsString())
                        .allMatch("passed"::equals);
                results.add(new ScenarioResult(uri + ":" + scenario.get("line").getAsInt(), featureName,
                        scenario.get("name").getAsString(), tags, passed));
            }
        }
        return results;
    }

    private static JsonArray array(JsonObject object, String member) {
        return object.has(member) ? object.getAsJsonArray(member) : new JsonArray();
    }

    private static Stream<JsonElement> stream(JsonArray array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
