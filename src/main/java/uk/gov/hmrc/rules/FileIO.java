package uk.gov.hmrc.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Stream;

final class FileIO {
    static boolean fileContainsLine(Path file, String exactLine) {
        if (!Files.exists(file)) return false;
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.anyMatch(l -> l.trim().equals(exactLine.trim()));
        } catch (IOException e) {
            throw new RuntimeException("Read failed: " + file, e);
        }
    }

    static void appendLine(Path file, String line) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    Files.exists(file) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException("Append failed: " + file, e);
        }
    }

    static void writeString(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Write failed: " + file, e);
        }
    }

    private FileIO() {}
}