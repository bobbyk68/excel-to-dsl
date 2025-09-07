package uk.gov.hmrc.dslgen.simple.canon;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PatternRegistry {

  public static final class DslWhenMapping {
    public final Signature sig;
    public final String lhs;
    public final String rhs;
    public DslWhenMapping(Signature sig, String lhs, String rhs) { this.sig = sig; this.lhs = lhs; this.rhs = rhs; }
  }

  private final Map<Signature, DslWhenMapping> map = new LinkedHashMap<>();

  public void register(Signature sig, String lhs, String rhs) {
    map.computeIfAbsent(sig, k -> new DslWhenMapping(sig, lhs, rhs));
  }

  public Map<Signature, DslWhenMapping> mappings() { return map; }
}
