package uk.gov.hmrc.dslgen;

public final class Router {

  public static final class RouteResult {
    private final boolean ok;
    private final String reason;

    private RouteResult(boolean ok, String reason) { this.ok = ok; this.reason = reason; }

    public static RouteResult ok()                { return new RouteResult(true,  null); }
    public static RouteResult fail(String reason) { return new RouteResult(false, reason); }

    public boolean ok()     { return ok; }
    public String  reason() { return reason; }
  }

  public RouteResult route(Signature sig) {
    if (sig.flags.contains("UNKNOWN_ANCHOR"))   return RouteResult.fail("UNKNOWN_ANCHOR");
    if (sig.flags.contains("SENTINEL_BLOCKED")) return RouteResult.fail("SENTINEL_BLOCKED");
    if (sig.flags.contains("NO_KEY"))           return RouteResult.fail("NO_KEY");
    return RouteResult.ok();
  }
}
