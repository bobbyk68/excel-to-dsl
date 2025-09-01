package uk.gov.hmrc.dslgen.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import uk.gov.hmrc.dslgen.model.RuleBook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the RuleBook object to a JSON file.
 */
public final class JsonWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonWriter() {}

    public static void write(RuleBook book, Path out) {
        try {
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            MAPPER.writeValue(out.toFile(), book);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing JSON to " + out, e);
        }
    }
}
