package io.github.thegoyalyogesh.qa.core.report;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ScreenshotUtilTest {

    @Test
    public void savesUniqueSanitisedFilesPerCall() throws IOException {
        Path dir = Files.createTempDirectory("shots");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};

        Path first = ScreenshotUtil.save(png, dir, "Register customer U01: <fail>");
        Path second = ScreenshotUtil.save(png, dir, "Register customer U01: <fail>");

        assertThat(first).isNotEqualTo(second);
        assertThat(first.getFileName().toString())
                .startsWith("Register_customer_U01_fail_")
                .contains("_t" + Thread.currentThread().threadId() + "_")
                .endsWith(".png");
        assertThat(Files.readAllBytes(first)).isEqualTo(png);
    }
}
