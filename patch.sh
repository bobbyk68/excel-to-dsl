#!/usr/bin/env bash
set -euo pipefail

# --- CONFIG: update these three paths if your package paths differ ---
COMPOSER_FILE="src/HyphenTwoPhaseComposer.java"
MATCHER_FILE="src/WhenPatternMatcher.java"
DSLR_FILE="src/DslrWriter.java"

PATCH_FILE="flip_then_dash.patch"

cat > "$PATCH_FILE" <<'PATCH'
*** 1,99999 ****
--- 1,99999 ----
*** $COMPOSER_FILE.orig	2025-09-25
--- $COMPOSER_FILE	2025-09-25
***************
*** 1,200 ****
--- 1,999 ----
+ // PATCH: Add Role enum, flip THEN/ALL_OF dash English in composeEnglish
  import java.util.*;

  public class HyphenTwoPhaseComposer {

      public static final class Result {
          public final java.util.List<String> dslrLines;    // English lines for DSLR (1 or 2)
-         public final java.util.List<String> dslKeys;      // English keys for DSL (one or two: anchor & "- dash")
-         public Result(List<String> dslrLines, List<String> dslKeys) {
-             this.dslrLines = dslrLines; this.dslKeys = dslKeys;
-         }
+         public final java.util.List<String> dslKeys;      // English keys for DSL (one or two: anchor & "- dash")
+         public Result(List<String> dslrLines, List<String> dslKeys) {
+             this.dslrLines = dslrLines; this.dslKeys = dslKeys;
+         }
      }
+
+     // NEW: role of the clause being composed
+     public enum Role { IF, THEN }

-     // Split once; English stays as requirement. No flipping here (flip is encoded in DSL drools mapping).
-     public Result composeEnglish(String englishTemplateWithPlaceholders, java.util.List<String> groups) {
+     // Split once; for THEN+ALL_OF flip the DASH English to violator wording so DSLR & DSL keys align
+     public Result composeEnglish(Role role,
+                                  ConstraintCase kase,
+                                  String englishTemplateWithPlaceholders,
+                                  java.util.List<String> groups) {
          String concrete = bind(englishTemplateWithPlaceholders, groups);
          String[] parts = concrete.split("\\s+-\\s+", 2);
          if (parts.length == 1) {
-             return new Result(List.of(concrete), List.of(parts[0].trim()));
+             return new Result(List.of(concrete), List.of(parts[0].trim()));
          }
          String anchor = parts[0].trim();
          String dash   = parts[1].trim();

-         return new Result(List.of(anchor, "- " + dash), List.of(anchor, "- " + dash));
+         // Flip ONLY for THEN + ALL_OF → English dash must read as the violator
+         if (role == Role.THEN && ConstraintCase.canonical(kase) == ConstraintCase.ALL_OF) {
+             dash = flipDashEnglish(dash);
+         }
+         return new Result(List.of(anchor, "- " + dash), List.of(anchor, "- " + dash));
      }

      private String bind(String template, java.util.List<String> groups) {
          String out = template;
          for (int i = 0; i < groups.size(); i++) {
              String g = groups.get(i);
              out = out.replace("{"+(i+1)+"}", g);
          }
          return out;
      }
+
+     // NEW: textual flip of operator phrases for THEN/ALL_OF dash English
+     private String flipDashEnglish(String dash) {
+         String s = " " + dash.trim() + " ";
+         // normalize casing for matching, but preserve original words spacing
+         String lower = s.toLowerCase(Locale.ROOT);
+
+         // Replace multi-word operators first using placeholders to avoid cascades
+         lower = lower.replace(" less than or equal to ", " __FLIP_GT__ ");
+         lower = lower.replace(" greater than or equal to ", " __FLIP_LT__ ");
+         lower = lower.replace(" not equal to ", " __FLIP_EQ__ ");
+         lower = lower.replace(" equal to ", " __FLIP_NEQ__ ");
+         lower = lower.replace(" equals ", " __FLIP_NEQ__ ");
+         lower = lower.replace(" less than ", " __FLIP_GTE__ ");
+         lower = lower.replace(" greater than ", " __FLIP_LTE__ ");
+
+         // Map placeholders back to flipped English
+         lower = lower.replace(" __FLIP_GT__ ", " greater than ");
+         lower = lower.replace(" __FLIP_LT__ ", " less than ");
+         lower = lower.replace(" __FLIP_GTE__ ", " greater than or equal to ");
+         lower = lower.replace(" __FLIP_LTE__ ", " less than or equal to ");
+         lower = lower.replace(" __FLIP_EQ__ ", " equal to ");
+         lower = lower.replace(" __FLIP_NEQ__ ", " not equal to ");
+
+         // Simple subject tidy-ups (optional)
+         lower = lower.replace(" with ", " ");
+         lower = lower.replace("  ", " ");
+
+         return lower.trim();
+     }
  }
*** $MATCHER_FILE.orig	2025-09-25
--- $MATCHER_FILE	2025-09-25
***************
*** 1,200 ****
--- 1,999 ----
  import java.io.*;
  import java.util.*;

  public class WhenPatternMatcher implements Closeable {
      private final DslWriter dsl;
      private final DslrWriter dslr;
      private final HyphenTwoPhaseComposer composer = new HyphenTwoPhaseComposer();

      public WhenPatternMatcher(String dslPath, String dslrPath) throws IOException {
          this.dsl = new DslWriter(dslPath);
          this.dslr = new DslrWriter(dslrPath);
      }

      public void collectAll(java.util.List<RuleRow> rows) throws IOException {
          for (RuleRow row : rows) {
              AtomicHit ifHit   = PatternLibrary.findMatch(row.ifCondition(), true);
              AtomicHit thenHit = PatternLibrary.findMatch(row.thenCondition(), false);

              ConstraintCase kase = ConstraintCase.fromEnglish(thenHit.template);

-             dslr.beginRule(row.id());
-
-             HyphenTwoPhaseComposer.Result ifRes = composer.composeEnglish(ifHit.template, ifHit.groups);
-             writeDslrWhen(ifRes.dslrLines);
-             emitDslEntries(ifRes.dslKeys, true, ConstraintCase.EXISTS);
-
-             HyphenTwoPhaseComposer.Result thenRes = composer.composeEnglish(thenHit.template, thenHit.groups);
-             writeDslrWhen(thenRes.dslrLines);
-             emitDslEntries(thenRes.dslKeys, false, kase);
+             dslr.beginRule(row.id());
+
+             // IF: no flip
+             HyphenTwoPhaseComposer.Result ifRes = composer.composeEnglish(
+                 HyphenTwoPhaseComposer.Role.IF,
+                 ConstraintCase.EXISTS,
+                 ifHit.template,
+                 ifHit.groups
+             );
+             writeDslrWhen(ifRes.dslrLines);
+             emitDslEntries(ifRes.dslKeys, true, ConstraintCase.EXISTS);
+
+             // THEN: flip dash English only for ALL_OF
+             HyphenTwoPhaseComposer.Result thenRes = composer.composeEnglish(
+                 HyphenTwoPhaseComposer.Role.THEN,
+                 kase,
+                 thenHit.template,
+                 thenHit.groups
+             );
+             writeDslrWhen(thenRes.dslrLines);
+             emitDslEntries(thenRes.dslKeys, false, kase);

              dslr.endRule();
          }
      }

      private void writeDslrWhen(java.util.List<String> englishLines) throws IOException {
-         if (englishLines.size() == 1) {
-             dslr.whenFromEnglish(englishLines.get(0)); // method splits if it sees dash
-             return;
-         }
-         // englishLines already carry "- " on second if dash; write both
-         String anchor = englishLines.get(0);
-         String dash = englishLines.get(1).replaceFirst("^\\s*-\\s*", "");
-         dslr.whenFromEnglish(anchor);
-         dslr.whenFromEnglish(" - " + dash);
+         if (englishLines.size() == 1) {
+             dslr.whenAnchor(englishLines.get(0));
+             return;
+         }
+         // englishLines[0] is anchor; englishLines[1] already includes "- ..." text
+         String anchor = englishLines.get(0);
+         String dash = englishLines.get(1).replaceFirst("^\\s*-\\s*", "");
+         dslr.whenAnchor(anchor);
+         dslr.whenDash(dash);
      }

      private void emitDslEntries(java.util.List<String> englishKeys, boolean isIf, ConstraintCase kase) throws IOException {
          if (englishKeys.size() == 1) {
              dsl.emitEntries(englishKeys.get(0), isIf, kase);
              return;
          }
          dsl.emitEntries(englishKeys.get(0), isIf, kase);
          dsl.emitEntries(englishKeys.get(1), isIf, kase);
      }

      @Override public void close() throws IOException { dsl.close(); dslr.close(); }
  }
*** $DSLR_FILE.orig	2025-09-25
--- $DSLR_FILE	2025-09-25
***************
*** 1,200 ****
--- 1,999 ----
  import java.io.*;

  public class DslrWriter implements Closeable {
      private final BufferedWriter bw;
      private final String path;
      public DslrWriter(String path) throws IOException {
          this.path = path;
          this.bw = new BufferedWriter(new FileWriter(path, false));
      }

      public void beginRule(String id) throws IOException {
          bw.write("rule \"" + id + "\"\nwhen\n");
      }
-
-     // OLD: split on " - " and sometimes wrote an extra blank line
-     public void whenFromEnglish(String englishConcrete) throws IOException {
-         String[] parts = englishConcrete.split("\\s+-\\s+", 2);
-         if (parts.length == 1) {
-             bw.write("  " + englishConcrete + "\n");
-         } else {
-             bw.write("  " + parts[0].trim() + "\n");
-             bw.write("    - " + parts[1].trim() + "\n");
-         }
-     }
+
+     // NEW: explicit anchor/dash writers to avoid blank spacer lines
+     public void whenAnchor(String anchor) throws IOException {
+         bw.write("  " + anchor + "\n");
+     }
+     public void whenDash(String dash) throws IOException {
+         bw.write("    - " + dash + "\n");
+     }

      public void endRule() throws IOException {
          bw.write("then\n  // TODO RHS\nend\n\n");
      }

      @Override public void close() throws IOException { bw.flush(); bw.close(); }
      public String path() { return path; }
  }
PATCH

echo "Creating backups..."
for f in "$COMPOSER_FILE" "$MATCHER_FILE" "$DSLR_FILE"; do
  cp "$f" "$f.orig"
done

echo "Applying patch..."
# Try git apply if in a git repo; otherwise, do inline replacement using ed
if command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
  # Expand variables in the patch (placeholders are literal in our here-doc),
  # so we need to substitute file paths before applying.
  sed -i.bak "s|\$COMPOSER_FILE|$COMPOSER_FILE|g; s|\$MATCHER_FILE|$MATCHER_FILE|g; s|\$DSLR_FILE|$DSLR_FILE|g" "$PATCH_FILE"
  git apply "$PATCH_FILE"
  echo "Patch applied with git."
else
  # Fallback: just overwrite the three files with patched .orig-aware content
  # (User has originals saved as *.orig already)
  echo "git not available; please manually merge using $PATCH_FILE or use 'patch -p0 < $PATCH_FILE'."
fi

echo "Done."
