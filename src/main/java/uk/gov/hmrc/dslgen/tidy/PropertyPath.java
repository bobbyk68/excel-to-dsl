package uk.gov.hmrc.dslgen.tidy;

package uk.gov.hmrc.rules.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to parse dotted property paths:
 *   "additionalDocument.specialProcedure.type.code"
 * and provide convenient accessors for root/middle/leaf segments.
 *
 * Optionally normalizes bracket keys: obj['k'] -> obj.k
 */
public final class PropertyPath {
    private static final Pattern BRACKET_KEY = Pattern.compile("\\[(?:'|\")?([A-Za-z0-9_\\-]+)(?:'|\")?\\]");
    private final List<String> segments;

    private PropertyPath(List<String> segments) {
        this.segments = List.copyOf(segments);
    }

    public static PropertyPath parse(String raw) {
        Objects.requireNonNull(raw, "path");
        String s = raw.trim();

        // normalize bracket keys: a['b'] -> a.b
        Matcher m = BRACKET_KEY.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "." + m.group(1));
        }
        m.appendTail(sb);
        s = sb.toString();

        // collapse multiple dots and trim
        s = s.replaceAll("\\s+", "")
                .replaceAll("\\.+", ".");

        if (s.startsWith(".")) s = s.substring(1);
        if (s.endsWith("."))   s = s.substring(0, s.length() - 1);

        if (s.isEmpty()) return new PropertyPath(List.of());

        String[] parts = s.split("\\.");
        List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) list.add(p);
        }
        return new PropertyPath(list);
    }

    public List<String> segments() { return segments; }
    public int size() { return segments.size(); }
    public boolean isEmpty() { return segments.isEmpty(); }

    public String root() { return isEmpty() ? "" : segments.get(0); }
    public String leaf() { return isEmpty() ? "" : segments.get(segments.size() - 1); }

    /** "Middle" = the segment between root and leaf; if multiple, returns the first middle (index 1). */
    public String middle() {
        return size() >= 3 ? segments.get(1) : "";
    }

    public Optional<String> segment(int index) {
        return (index >= 0 && index < size()) ? Optional.of(segments.get(index)) : Optional.empty();
    }

    /** Parent subpath without the last segment. */
    public String parent() {
        if (size() <= 1) return "";
        return String.join(".", segments.subList(0, size() - 1));
    }

    /** Subpath [from, to) */
    public String subpath(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0) fromInclusive = 0;
        if (toExclusive > size()) toExclusive = size();
        if (fromInclusive >= toExclusive) return "";
        return String.join(".", segments.subList(fromInclusive, toExclusive));
    }

    /** Case-insensitive search for a segment; returns index or -1. */
    public int find(String name) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    /** Pretty-case a segment for human DSL: "specialProcedure" -> "special procedure". */
    public static String humanize(String camelOrKebab) {
        if (camelOrKebab == null || camelOrKebab.isBlank()) return "";
        String s = camelOrKebab.replace('-', ' ');
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2");
        return capitalizeWords(s);
    }

    private static String capitalizeWords(String s) {
        String[] parts = s.trim().split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            parts[i] = parts[i].substring(0,1).toUpperCase(Locale.ROOT) + parts[i].substring(1);
        }
        return String.join(" ", parts);
    }

    @Override public String toString() { return String.join(".", segments); }
}
