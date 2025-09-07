package uk.gov.hmrc.dslgen.simple.config;

import java.util.Map;

public final class TemplateEngine {
  public static String apply(String tpl, Map<String,String> vars) {
    String out = tpl;
    for (var e : vars.entrySet()) out = out.replace("{"+e.getKey()+"}", e.getValue());
    return out;
  }
}
