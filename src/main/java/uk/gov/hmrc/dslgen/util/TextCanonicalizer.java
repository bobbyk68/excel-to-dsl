package uk.gov.hmrc.dslgen.util;

public final class TextCanonicalizer {
    private TextCanonicalizer() {}

    public static String canonical(String s) {
        if (s == null) return "";
        String x = s.replace('\u00A0',' ')                // non-breaking space
                    .replaceAll("[\\u200B-\\u200D\\uFEFF]", ""); // zero-widths
        x = x.trim().replaceAll("\\s+", " ");
        return x;
    }
}
