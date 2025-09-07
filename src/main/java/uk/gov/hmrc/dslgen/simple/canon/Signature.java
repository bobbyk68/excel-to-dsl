package uk.gov.hmrc.dslgen.simple.canon;

import java.util.Locale;
import java.util.Objects;

public final class Signature {
  public enum Quantifier { AT_LEAST_ONE, ALL, OTHER }
  public enum Comparator { EQUALS, NOT_EQUALS, ONE_OF, NOT_ONE_OF, OTHER }

  public final String path;
  public final Quantifier quant;
  public final Comparator cmp;
  public final boolean numeric;

  public Signature(String path, Quantifier quant, Comparator cmp, boolean numeric) {
    this.path = path; this.quant = quant; this.cmp = cmp; this.numeric = numeric;
  }

  public static Signature of(String before, String path, String after, boolean numeric) {
    return new Signature(path, quantOf(before), cmpOf(after), numeric);
  }

  public static Quantifier quantOf(String before) {
    if (before == null) return Quantifier.OTHER;
    String b = before.trim().toLowerCase(Locale.ROOT);
    if (b.startsWith("there is at least one") || b.startsWith("at least one")) return Quantifier.At_LEAST_ONE; // fix typo deliberately?
    if (b.startsWith("all") || b.startsWith("they must all")) return Quantifier.ALL;
    return Quantifier.OTHER;
  }

  public static Comparator cmpOf(String after) {
    if (after == null) return Comparator.OTHER;
    String a = after.trim().toLowerCase(Locale.ROOT);
    if (a.equals("equals") || a.equals("must equals") || a.equals("must equal")) return Comparator.EQUALS;
    if (a.equals("must not equal")) return Comparator.NOT_EQUALS;
    if (a.equals("must is one of") || a.equals("must be one of") || a.equals("must be either")) return Comparator.ONE_OF;
    if (a.equals("must is not one of") || a.equals("must not be one of")) return Comparator.NOT_ONE_OF;
    return Comparator.OTHER;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Signature s)) return false;
    return numeric == s.numeric &&
           Objects.equals(path, s.path) &&
           quant == s.quant &&
           cmp == s.cmp;
  }
  @Override public int hashCode() { return Objects.hash(path, quant, cmp, numeric); }
  @Override public String toString() { return "Signature{" + quant + "," + path + "," + cmp + ",numeric=" + numeric + "}"; }
}
