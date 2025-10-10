// ─────────────────────────────────────────────────────────────────────────────
// DslFileWriter.java  (interface)
// ─────────────────────────────────────────────────────────────────────────────
public interface DslFileWriter {
    void whenLine(String text);
    void thenLine(String text);
    String getContent();   // optional convenience
}

// ─────────────────────────────────────────────────────────────────────────────
// SimpleDslFileWriter.java  (StringBuffer-backed implementation)
// ─────────────────────────────────────────────────────────────────────────────
public final class SimpleDslFileWriter implements DslFileWriter {

    private final StringBuffer buffer = new StringBuffer();
    private boolean inThenBlock = false;

    // helper constants for formatting
    private static final String NEWLINE = System.lineSeparator();

    @Override
    public void whenLine(String text) {
        // if we switched from [then] to [when], insert a separating newline
        if (inThenBlock) {
            buffer.append(NEWLINE);
            inThenBlock = false;
        }

        // add [when] header if not already present
        if (!startsWithWhenHeader()) {
            buffer.append("[when]").append(NEWLINE);
        }

        buffer.append(text).append(NEWLINE);
    }

    @Override
    public void thenLine(String text) {
        if (!inThenBlock) {
            buffer.append(NEWLINE).append("[then]").append(NEWLINE);
            inThenBlock = true;
        }

        buffer.append(text).append(NEWLINE);
    }

    @Override
    public String getContent() {
        return buffer.toString();
    }

    @Override
    public String toString() {
        return getContent();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helper: did we already start a [when] block?
    // ─────────────────────────────────────────────────────────────────────────
    private boolean startsWithWhenHeader() {
        // quick check if buffer already starts with "[when]"
        int len = buffer.length();
        return len >= 6 && buffer.indexOf("[when]") != -1;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Example usage (works with your emitters directly)
// ─────────────────────────────────────────────────────────────────────────────
public class DslWriterDemo {

    public static void main(String[] args) {

        SimpleDslFileWriter dsl = new SimpleDslFileWriter();

        // mimic Row 2 emitter output
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("    - with code equals \"C601\"");
        dsl.whenLine("Matching goods item with previous procedure exists");
        dsl.whenLine("    - with code not in \"00\",\"21\",\"51\",\"53\",\"54\",\"71\",\"78\"");
        dsl.thenLine("Emit BR675 validation error for requested and previous procedure");

        System.out.println("Generated DSL:");
        System.out.println("----------------------------------");
        System.out.println(dsl.getContent());
    }
}
