package uk.gov.hmrc.dslgen.simple;

import java.util.regex.*;

final class PhraseSplit {
  private static final Pattern SPLIT = Pattern.compile(
      "^(?<before>.*?)\\s+(?<type>[A-Z][A-Za-z]+(?:\\.[A-Za-z0-9_]+)+)\\s+(?<after>.+)$"
  );

  static Side split(String raw) {
    if (raw == null) return new Side("", "", "");
    String s = raw.trim();
    if (s.isEmpty()) return new Side("", "", "");
    Matcher m = SPLIT.matcher(s);
    if (m.matches()) {
      return new Side(m.group("before").trim(), m.group("type").trim(), m.group("after").trim());
    }
    Matcher r = Pattern.compile("([A-Z][A-Za-z]+(?:\\.[A-Za-z0-9_]+)+)").matcher(s);
    if (r.find()) {
      String type = r.group(1);
      return new Side(s.substring(0, r.start()).trim(), type, s.substring(r.end()).trim());
    }
    return new Side("", "", s);
  }

  record Side(String before, String type, String after) {}
}
