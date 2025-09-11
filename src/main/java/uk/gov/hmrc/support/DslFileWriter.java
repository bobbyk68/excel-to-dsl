package uk.gov.hmrc.support;

public final class DslFileWriter {
    private final List<String> lines = new ArrayList<>();

    public void appendWhen(String lhs, String rhs) {
        lines.add(lhs + " = " + rhs);
    }
    public void appendThen(String lhs, String rhs) {
        lines.add(lhs + " = " + rhs);
    }
    public void writeTo(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}

package uk.gov.hmrc.rulegen.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
        import java.util.ArrayList;
import java.util.List;

/** Very small writer for .dsl mapping files. */
public final class DslFileWriter {
    private final List<String> lines = new ArrayList<>();

    /** Append a raw mapping line (already in the form: [when] X = Y). */
    public void append(String mappingLine) {
        if (mappingLine != null && !mappingLine.isBlank()) {
            lines.add(mappingLine);
        }
    }

    /** Convenience for [when] mappings. */
    public void appendWhen(String lhsWhenTemplate, String rhsDrl) {
        append(lhsWhenTemplate + " = " + rhsDrl);
    }

    /** Convenience for [then] mappings (optional, if you add them). */
    public void appendThen(String lhsThenTemplate, String rhsDrl) {
        append(lhsThenTemplate + " = " + rhsDrl);
    }

    /** Write out to a .dsl file. Subsequent calls overwrite the file. */
    public void writeTo(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Return all accumulated lines (handy for tests/logging). */
    public List<String> lines() { return List.copyOf(lines); }
}
