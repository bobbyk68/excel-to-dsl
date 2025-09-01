package uk.gov.hmrc.rules.core.match;

import java.util.List;

/** Result of matching a literal line to an atomic regex */
public record AtomicHit(
    String id,
    List<String> groups // captured groups 1..n
) {}
