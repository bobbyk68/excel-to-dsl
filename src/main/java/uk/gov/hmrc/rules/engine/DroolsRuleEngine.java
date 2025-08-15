package uk.gov.hmrc.rules.engine;

import org.drools.io.ResourceType;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSessionPool;
import uk.gov.hmrc.rules.toggle.RuleToggles;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DroolsRuleEngine {
    private final KieServices ks = KieServices.Factory.get();
    private final AtomicReference<KieContainer> containerRef = new AtomicReference<>();
    private final AtomicReference<KieSessionPool> poolRef = new AtomicReference<>();

    // Configurable roots (classpath-relative) for classpath builds
    private final String dslRoot;   // e.g. "rules/dsl"
    private final String dslrRoot;  // e.g. "rules"

    public DroolsRuleEngine() {
        this("rules/dsl", "rules");
    }
    public DroolsRuleEngine(String dslRoot, String dslrRoot) {
        this.dslRoot = stripSlashes(dslRoot);
        this.dslrRoot = stripSlashes(dslrRoot);
    }

    public synchronized void buildFromToggles(Path togglesJson, boolean filesOnFilesystem) {
        var toggles = RuleToggles.load(togglesJson);
        var kfs = ks.newKieFileSystem();

        // Collect unique DSLs (one per enabled rule) and ordered DSLR list
        var dslSet  = new LinkedHashSet<String>();
        var dslrs   = new ArrayList<String>();

        for (var r : toggles.rules) {
            if (!r.enabled) continue;

            // Derive filenames from first 5 chars
            if (r.id == null || r.id.length() < 5)
                throw new IllegalArgumentException("Rule id must have at least 5 chars: " + r.id);
            var prefix = r.id.substring(0, 5).toUpperCase();
            var base   = prefix + "_rules";

            var dslPath  = dslRoot  + "/" + base + ".dsl";
            var dslrPath = dslrRoot + "/" + base + ".dslr";

            dslSet.add(dslPath);
            dslrs.add(dslrPath);
        }

        // 1) Write all DSLs first
        for (String dsl : dslSet) {
            write(kfs, dsl, filesOnFilesystem, ResourceType.DSL);
        }
        // 2) Then all DSLRs
        for (String dslr : dslrs) {
            write(kfs, dslr, filesOnFilesystem, ResourceType.DESCR); // DSLR
        }

        var kb = ks.newKieBuilder(kfs).buildAll();
        var res = kb.getResults();
        if (res.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("Build errors: " + res.getMessages());
        }

        var rid = kb.getKieModule().getReleaseId();
        var newC = ks.newKieContainer(rid);
        var newP = newC.newKieSessionPool(16);

        var oldC = containerRef.getAndSet(newC);
        var oldP = poolRef.getAndSet(newP);
        if (oldC != null) oldC.dispose(); // pool has no dispose; don't reuse old sessions
    }

    private void write(KieFileSystem kfs, String relPath, boolean fs, ResourceType type) {
        Resource res = fs
            ? ks.getResources().newFileSystemResource(Path.of(relPath).toFile())
            : ks.getResources().newClassPathResource(relPath);

        if (res == null) throw new IllegalArgumentException("Resource not found: " + relPath);
        res.setResourceType(type);

        // KFS needs resources under src/main/resources
        kfs.write("src/main/resources/" + relPath, res);
    }

    private static String stripSlashes(String s) {
        if (s == null) return "";
        return s.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    public KieContainer container()     { return containerRef.get(); }
    public KieSessionPool sessionPool() { return poolRef.get(); }
}
