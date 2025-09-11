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
