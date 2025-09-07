package uk.gov.hmrc.dslgen.simple.num;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.*;

public final class Num {
  private Num(){}
  public static boolean in(BigDecimal val, String csv) {
    if (val == null || csv == null) return false;
    List<BigDecimal> list = Arrays.stream(csv.split("\\s*,\\s*"))
        .filter(s -> !s.isBlank())
        .map(s -> new BigDecimal(s.replace(",", "")))
        .collect(Collectors.toList());
    for (BigDecimal bd : list) if (val.compareTo(bd) == 0) return true;
    return false;
  }
}
