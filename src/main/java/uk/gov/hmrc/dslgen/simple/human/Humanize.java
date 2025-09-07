package uk.gov.hmrc.dslgen.simple.human;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Humanize {
  private static final Pattern CAMEL = Pattern.compile("(?<=[a-z])(?=[A-Z])");

  public static String dotPathToWords(String tail) {
    if (tail == null || tail.isBlank()) return "";
    String[] parts = tail.split("\\.");
    return Arrays.stream(parts)
        .map(Humanize::camelToWords)
        .map(String::toLowerCase)
        .collect(Collectors.joining(" "));
  }

  public static String camelToWords(String s) {
    if (s == null || s.isBlank()) return "";
    return String.join(" ", CAMEL.split(s));
  }

  public static String classToWords(String cls) {
    String words = camelToWords(cls).toLowerCase(Locale.ROOT);
    return words.substring(0,1).toUpperCase(Locale.ROOT) + words.substring(1);
  }

  public static String possessiveSingular(String rootClass) {
    String noun = classToWords(rootClass);
    return noun + (noun.endsWith("s") ? "'" : "’s");
  }

  public static String possessivePlural(String rootClass) {
    String base = classToWords(rootClass);
    String plural = base.toLowerCase(Locale.ROOT).endsWith("y")
        ? base.substring(0, base.length()-1) + "ies"
        : base + "s";
    return plural + (plural.endsWith("s") ? "'" : "’s");
  }
}
