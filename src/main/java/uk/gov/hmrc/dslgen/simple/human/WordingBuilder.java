package uk.gov.hmrc.dslgen.simple.human;

import java.util.Locale;

public final class WordingBuilder {

  public static String prettyWhenLhs(String before, String dottedType, String after) {
    if (dottedType == null || dottedType.isBlank()) {
      return cap((before == null ? "" : before) + " " + (after == null ? "" : after)).trim();
    }
    String root = rootOf(dottedType);
    String fieldPhrase = Humanize.dotPathToWords(tailOf(dottedType));

    String q = normaliseBefore(before);
    String comp = prettifyAfter(after);

    switch (q) {
      case "at least one":
        return "At least one " + Humanize.possessiveSingular(root) + " " + fieldPhrase + " " + comp;
      case "all":
        return "All " + Humanize.possessivePlural(root) + " " + fieldPhrase + " " + comp;
      default:
        return cap((before == null ? "" : before) + " " + Humanize.possessiveSingular(root) + " " + fieldPhrase + " " + comp);
    }
  }

  private static String prettifyAfter(String after) {
    if (after == null) return "";
    String a = after.trim();
    return switch (a) {
      case "equals" -> "equals {VAL}";
      case "must equals", "must equal" -> "must equal {VAL}";
      case "must not equal" -> "must not equal {VAL}";
      case "must is one of", "must be one of", "must be either" -> "must be one of {CSV}";
      case "must is not one of", "must not be one of" -> "must not be one of {CSV}";
      default -> a + " {VAL}";
    };
  }

  private static String normaliseBefore(String before) {
    if (before == null) return "";
    String b = before.trim().toLowerCase(Locale.ROOT);
    if (b.startsWith("there is at least one") || b.startsWith("at least one")) return "at least one";
    if (b.startsWith("all") || b.startsWith("they must all")) return "all";
    return before.trim();
  }

  private static String rootOf(String dotted) { int i = dotted.indexOf('.'); return i < 0 ? dotted : dotted.substring(0, i); }
  private static String tailOf(String dotted) { int i = dotted.indexOf('.'); return i < 0 ? "" : dotted.substring(i + 1); }
  private static String cap(String s) { return (s==null||s.isBlank())? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
