package uk.gov.hmrc.dslgen.simple.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class PatternConfig {
  public static final class ComparatorRule {
    public final String name;
    public final List<String> synonyms;
    public final String lhsPretty;
    public final String rhsString;
    public final String rhsNumeric;
    public ComparatorRule(String name, List<String> synonyms, String lhsPretty, String rhsString, String rhsNumeric) {
      this.name = name; this.synonyms = synonyms; this.lhsPretty = lhsPretty; this.rhsString = rhsString; this.rhsNumeric = rhsNumeric;
    }
  }

  private final Map<String,String> quantifiers = new LinkedHashMap<>();
  private final List<ComparatorRule> comparators = new ArrayList<>();

  public static PatternConfig load(Path dir) throws IOException {
    PatternConfig cfg = new PatternConfig();
    if (dir == null || !Files.exists(dir)) return cfg;
    // quantifiers
    Path q = dir.resolve("quantifiers.properties");
    if (Files.exists(q)) {
      Properties p = new Properties();
      try (var in = Files.newInputStream(q)) { p.load(in); }
      for (String k : p.stringPropertyNames()) {
        cfg.quantifiers.put(k.trim().toLowerCase(Locale.ROOT), p.getProperty(k).trim().toUpperCase(Locale.ROOT));
      }
    }
    // comparators
    Path c = dir.resolve("comparators.csv");
    if (Files.exists(c)) {
      try (BufferedReader br = Files.newBufferedReader(c)) {
        String line;
        while ((line = br.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty() || line.startsWith("#")) continue;
          String[] parts = splitCsv(line);
          if (parts.length < 5) continue;
          String name = parts[0].trim();
          List<String> syns = Arrays.stream(parts[1].split("\\|"))
              .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toList());
          String lhsPretty = parts[2].trim();
          String rhsString = unquote(parts[3].trim());
          String rhsNumeric = unquote(parts[4].trim());
          cfg.comparators.add(new ComparatorRule(name, syns, lhsPretty, rhsString, rhsNumeric));
        }
      }
    }
    return cfg;
  }

  public String quantifierOf(String before) {
    if (before == null) return "OTHER";
    String b = before.trim().toLowerCase(Locale.ROOT);
    for (var e : quantifiers.entrySet()) {
      if (b.startsWith(e.getKey())) return e.getValue();
    }
    return "OTHER";
  }

  public ComparatorRule comparatorFor(String after) {
    if (after == null) return null;
    String a = after.trim().toLowerCase(Locale.ROOT);
    for (ComparatorRule r : comparators) {
      for (String s : r.synonyms) if (a.equals(s)) return r;
    }
    return null;
  }

  private static String[] splitCsv(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQ = false;
    for (int i=0;i<line.length();i++) {
      char ch = line.charAt(i);
      if (ch == '"') { inQ = !inQ; continue; }
      if (ch == ',' && !inQ) { out.add(cur.toString()); cur.setLength(0); continue; }
      cur.append(ch);
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  private static String unquote(String s) {
    String t = s;
    if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) t = t.substring(1, t.length()-1);
    return t;
  }
}
