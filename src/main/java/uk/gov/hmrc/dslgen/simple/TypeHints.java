package uk.gov.hmrc.dslgen.simple;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class TypeHints {
  private final Map<String, String> map;
  private final boolean strict;
  private final Set<String> warned = new HashSet<>();

  private TypeHints(Map<String, String> map, boolean strict) {
    this.map = map; this.strict = strict;
  }

  static TypeHints load(Path propsFile, boolean strict) {
    Map<String,String> m = new HashMap<>();
    if (propsFile != null && Files.exists(propsFile)) {
      Properties p = new Properties();
      try (var in = Files.newInputStream(propsFile)) { p.load(in); }
      catch (IOException e) { throw new RuntimeException("Failed to read " + propsFile.toAbsolutePath(), e); }
      for (String k : p.stringPropertyNames()) m.put(k.trim(), p.getProperty(k).trim());
      System.out.println("Loaded type hints: " + m.size() + " from " + propsFile.toAbsolutePath());
    } else {
      System.out.println("No types.properties found at " + (propsFile == null ? "(null)" : propsFile.toAbsolutePath()) +
          " — defaulting to STRING/enum" + (strict ? " [STRICT]" : ""));
    }
    return new TypeHints(m, strict);
  }

  boolean isBigDecimal(String dottedPath) {
    if (dottedPath == null || dottedPath.isBlank()) return false;
    String v = map.get(dottedPath);
    if (v != null) return "BIGDECIMAL".equalsIgnoreCase(v);
    if (warned.add(dottedPath)) {
      String msg = "Type unknown for '" + dottedPath + "'. Defaulting to STRING/enum. " +
          "Mark numeric in types.properties:\n" + dottedPath + "=BIGDECIMAL";
      if (strict) throw new IllegalStateException("[STRICT TYPES] " + msg);
      System.err.println("WARN: " + msg);
    }
    return false;
  }
}
