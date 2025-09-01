package uk.gov.hmrc.rules.core.match;

import java.util.regex.Pattern;

public record CompiledAtomic(
    String id,
    Pattern regex,     // compiled from "^" + pattern + "$"
    String pattern,    // original regex text (from JSON)
    String dsl         // original dsl text (from JSON)
) {}
