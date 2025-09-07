package uk.gov.hmrc.dslgen.simple;

public final class OperatorLibrary {

  public static String rhsFor(String before, String path, String after, boolean numeric) {
    String cls = classOf(path);
    String field = fieldPathOf(path);

    String b = (before == null ? "" : before.trim().toLowerCase());
    String a = (after == null ? "" : after.trim().toLowerCase());

    boolean atLeastOne = b.startsWith("there is at least one") || b.startsWith("at least one");
    boolean all        = b.startsWith("all") || b.startsWith("they must all");

    if (a.equals("equals") || a.equals("must equal") || a.equals("must equals")) {
      if (atLeastOne) {
        return numeric
          ? "exists " + cls + "( " + field + " != null, eval(" + field + ".compareTo(new java.math.BigDecimal(\"{VAL}\")) == 0) )"
          : "exists " + cls + "( " + field + " == \"{VAL}\" )";
      }
      if (all) {
        return numeric
          ? "Number( intValue == 0 ) from accumulate( " + cls +
            "( " + field + " != null, eval(" + field + ".compareTo(new java.math.BigDecimal(\"{VAL}\")) != 0) ), count(1) )"
          : "not " + cls + "( " + field + " != \"{VAL}\" )";
      }
    }

    if (a.equals("must not equal")) {
      return numeric
        ? "not " + cls + "( " + field + " != null, eval(" + field + ".compareTo(new java.math.BigDecimal(\"{VAL}\")) == 0) )"
        : "not " + cls + "( " + field + " == \"{VAL}\" )";
    }

    if (a.equals("must is one of") || a.equals("must be one of") || a.equals("must be either")) {
      return numeric
        ? "Number( intValue == 0 ) from accumulate( " + cls +
          "( " + field + " != null, eval( !uk.gov.hmrc.dslgen.simple.num.Num.in(" + field + ", \"{CSV}\") ) ), count(1) )"
        : "not " + cls + "( " + field + " not in ({CSV}) )";
    }

    if (a.equals("must is not one of") || a.equals("must not be one of")) {
      return numeric
        ? "Number( intValue == 0 ) from accumulate( " + cls +
          "( " + field + " != null, eval( uk.gov.hmrc.dslgen.simple.num.Num.in(" + field + ", \"{CSV}\") ) ), count(1) )"
        : "not " + cls + "( " + field + " in ({CSV}) )";
    }

    return "eval( /* TODO map: " + before + " " + path + " " + after + " */ true )";
  }

  public static boolean isUnknownComparator(String after) {
    if (after == null) return true;
    String a = after.trim().toLowerCase();
    return !(a.equals("equals") || a.equals("must equal") || a.equals("must equals")
          || a.equals("must not equal")
          || a.equals("must is one of") || a.equals("must be one of") || a.equals("must be either")
          || a.equals("must is not one of") || a.equals("must not be one of"));
  }

  private static String classOf(String path) {
    int i = path.indexOf('.');
    return i < 0 ? path : path.substring(0, i);
  }
  private static String fieldPathOf(String path) {
    int i = path.indexOf('.');
    return i < 0 ? "" : path.substring(i + 1);
  }
}
