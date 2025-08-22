package uk.gov.hmrc.dslgen.when;

import java.util.Set;

public final class WhenSnippet {
    public final String id;
    public final String phrase;          // DSL phrase that goes into .dslr
    public final Set<Var> provides;      // variables introduced
    public final Set<Var> requires;      // variables needed

    public WhenSnippet(String id, String phrase, Set<Var> provides, Set<Var> requires) {
        this.id = id;
        this.phrase = phrase;
        this.provides = provides;
        this.requires = requires;
    }
}