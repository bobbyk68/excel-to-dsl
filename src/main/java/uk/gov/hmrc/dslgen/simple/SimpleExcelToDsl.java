package uk.gov.hmrc.dslgen.simple;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import uk.gov.hmrc.dslgen.simple.PhraseSplit.Side;
import uk.gov.hmrc.dslgen.simple.canon.PatternRegistry;
import uk.gov.hmrc.dslgen.simple.canon.Signature;
import uk.gov.hmrc.dslgen.simple.human.WordingBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class SimpleExcelToDsl {

  static final class Cfg {
    Path file;
    int sheet = 0;
    int skipRows = 0;
    int ifCol = 1;      // B
    int thenCol = 2;    // C
    int ruleIdCol = -1; // -1 => last non-empty
    Path types = Path.of("types.properties");
    boolean strictTypes = false;
    boolean reportOnly = false;
    int limit = Integer.MAX_VALUE;
  }

  static Cfg parse(String[] args) {
    if (args.length < 1) {
      System.out.println("""
          Usage: java -jar dslgen-excel-simple.jar <file.xlsx>
            [--sheet 0] [--skip-rows 0] [--if-col B] [--then-col C] [--ruleid-col -1]
            [--types types.properties] [--strict-types]
            [--report-only] [--limit N]
          """);
      System.exit(1);
    }
    Cfg c = new Cfg();
    c.file = Path.of(args[0]);
    for (int i=1;i<args.length;i++) {
      String a = args[i];
      switch (a) {
        case "--sheet" -> c.sheet = Integer.parseInt(args[++i]);
        case "--skip-rows" -> c.skipRows = Integer.parseInt(args[++i]);
        case "--if-col" -> c.ifCol = toIndex(args[++i]);
        case "--then-col" -> c.thenCol = toIndex(args[++i]);
        case "--ruleid-col" -> c.ruleIdCol = toIndexAllowMinus(args[++i]);
        case "--types" -> c.types = Path.of(args[++i]);
        case "--strict-types" -> c.strictTypes = true;
        case "--report-only" -> c.reportOnly = true;
        default -> {
          if (a.startsWith("--limit=")) c.limit = Integer.parseInt(a.substring(8));
          else System.out.println("Ignoring unknown arg: " + a);
        }
      }
    }
    return c;
  }

  static int toIndexAllowMinus(String s) { return "-1".equals(s) ? -1 : toIndex(s); }

  static int toIndex(String s) {
    if (s.matches("\\d+")) return Integer.parseInt(s);
    int idx = 0;
    for (char ch : s.toUpperCase(Locale.ROOT).toCharArray()) {
      if (ch < 'A' || ch > 'Z') throw new IllegalArgumentException("Bad column: " + s);
      idx = idx * 26 + (ch - 'A' + 1);
    }
    return idx - 1;
  }

  record Row(String ruleId, String ifRaw, String thenRaw) {}

  static List<Row> read(Cfg c) throws IOException {
    try (Workbook wb = new XSSFWorkbook(Files.newInputStream(c.file))) {
      Sheet sh = wb.getSheetAt(c.sheet);
      List<Row> out = new ArrayList<>();
      int rIndex = 0;
      for (org.apache.poi.ss.usermodel.Row r : sh) {
        if (rIndex++ < c.skipRows) continue;
        String ifRaw = get(r, c.ifCol).trim();
        String thenRaw = get(r, c.thenCol).trim();
        if (ifRaw.isEmpty() && thenRaw.isEmpty()) continue;
        int ridx = (c.ruleIdCol >= 0) ? c.ruleIdCol : (r.getLastCellNum() - 1);
        String ruleId = get(r, ridx).trim();
        out.add(new Row(ruleId, ifRaw, thenRaw));
      }
      return out;
    }
  }

  static String get(org.apache.poi.ss.usermodel.Row r, int idx) {
    if (r == null) return "";
    Cell c = r.getCell(idx);
    if (c == null) return "";
    return switch (c.getCellType()) {
      case STRING -> c.getStringCellValue();
      case NUMERIC -> (DateUtil.isCellDateFormatted(c)
                        ? c.getDateCellValue().toString()
                        : String.valueOf(c.getNumericCellValue()).replaceAll("\\.0+$",""));
      case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
      case FORMULA -> {
        try { yield c.getStringCellValue(); }
        catch (IllegalStateException e) { yield String.valueOf(c.getNumericCellValue()); }
      }
      default -> "";
    };
  }

  static void writeDsl(Collection<String> whenLines, Path out) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("# Auto-generated DSL (deduped by canonical signature)");
    lines.addAll(whenLines);
    lines.add("[then]raise \"{code}\" with message \"{msg}\" = results.addError(\"{code}\", \"{msg}\")");
    Files.write(out, lines);
  }

  static void writeDslr(List<String> rules, Path out) throws IOException { Files.write(out, rules); }

  static String renderDslrRule(String name, List<String> guards, List<String> dashed) {
    StringBuilder sb = new StringBuilder();
    sb.append("rule \"").append(name).append("\"\nwhen\n");
    for (String g : guards) sb.append("    ").append(g).append("\n");
    for (String d : dashed) sb.append("    ").append(d).append("\n");
    sb.append("then\n    // IF-only (Excel THEN treated as extra WHEN)\nend\n\n");
    return sb.toString();
  }

  private static String rootOf(String dottedPath) {
    if (dottedPath == null || dottedPath.isBlank()) return "";
    int i = dottedPath.indexOf('.');
    return i < 0 ? dottedPath : dottedPath.substring(0, i);
  }

  public static void main(String[] args) throws Exception {
    Cfg cfg = parse(args);
    TypeHints types = TypeHints.load(cfg.types, cfg.strictTypes);

    // Load external pattern config (if ./config exists)
    uk.gov.hmrc.dslgen.simple.config.PatternConfig patternCfg = null;
    try {
      Path defaultCfgDir = Paths.get("config");
      if (Files.exists(defaultCfgDir)) {
        patternCfg = uk.gov.hmrc.dslgen.simple.config.PatternConfig.load(defaultCfgDir);
        System.out.println("Loaded external pattern config from ./config");
      }
    } catch (Exception ex) {
      System.err.println("WARN: failed to load external pattern config: " + ex.getMessage());
    }

    List<Row> rows = read(cfg);
    System.out.println("Read rows: " + rows.size());

    Set<String> distinctBefore = new TreeSet<>();
    Set<String> distinctAfter  = new TreeSet<>();
    Set<String> unknownAfter   = new TreeSet<>();

    PatternRegistry registry = new PatternRegistry();
    List<String> dslr = new ArrayList<>();
    int n = 1;

    int processed = 0;
    for (Row r : rows) {
      if (processed++ >= cfg.limit) break;

      Side ifS = PhraseSplit.split(r.ifRaw);
      Side thS = PhraseSplit.split(r.thenRaw);

      distinctBefore.add(ifS.before()); distinctAfter.add(ifS.after());
      distinctBefore.add(thS.before()); distinctAfter.add(thS.after());

      // IF mapping (RHS)
      boolean ifNum = types.isBigDecimal(ifS.type());
      String rhsIf;
      {
        var comp = (patternCfg == null) ? null : patternCfg.comparatorFor(ifS.after());
        if (comp != null) {
          String cls = rootOf(ifS.type());
          String fieldPath = ifS.type().contains(".") ? ifS.type().substring(ifS.type().indexOf('.')+1) : "";
          Map<String,String> vars = Map.of("Class", cls, "fieldPath", fieldPath);
          rhsIf = uk.gov.hmrc.dslgen.simple.config.TemplateEngine.apply(ifNum ? comp.rhsNumeric : comp.rhsString, vars);
        } else {
          rhsIf = OperatorLibrary.rhsFor(ifS.before(), ifS.type(), ifS.after(), ifNum);
          if (OperatorLibrary.isUnknownComparator(ifS.after())) unknownAfter.add(ifS.after());
        }
      }

      // THEN-as-WHEN mapping (RHS)
      boolean thNum = types.isBigDecimal(thS.type());
      String rhsTh;
      {
        var comp = (patternCfg == null) ? null : patternCfg.comparatorFor(thS.after());
        if (comp != null) {
          String cls = rootOf(thS.type());
          String fieldPath = thS.type().contains(".") ? thS.type().substring(thS.type().indexOf('.')+1) : "";
          Map<String,String> vars = Map.of("Class", cls, "fieldPath", fieldPath);
          rhsTh = uk.gov.hmrc.dslgen.simple.config.TemplateEngine.apply(thNum ? comp.rhsNumeric : comp.rhsString, vars);
        } else {
          rhsTh = OperatorLibrary.rhsFor(thS.before(), thS.type(), thS.after(), thNum);
          if (OperatorLibrary.isUnknownComparator(thS.after())) unknownAfter.add(thS.after());
        }
      }

      // Canonical signatures (for dedupe)
      Signature sigIf = Signature.of(ifS.before(), ifS.type(), ifS.after(), ifNum);
      Signature sigTh = Signature.of(thS.before(), thS.type(), thS.after(), thNum);

      // Pretty LHS sentences (using config if present)
      String prettyIf;
      {
        var comp = (patternCfg == null) ? null : patternCfg.comparatorFor(ifS.after());
        if (comp != null) {
          String quantKey = (patternCfg.quantifierOf(ifS.before()));
          String head = "AT_LEAST_ONE".equals(quantKey)
              ? "At least one " + uk.gov.hmrc.dslgen.simple.human.Humanize.possessiveSingular(rootOf(ifS.type()))
              : "All " + uk.gov.hmrc.dslgen.simple.human.Humanize.possessivePlural(rootOf(ifS.type()));
          String fieldPhrase = uk.gov.hmrc.dslgen.simple.human.Humanize.dotPathToWords(ifS.type().contains(".")?ifS.type().substring(ifS.type().indexOf('.')+1):"");
          prettyIf = head + " " + fieldPhrase + " " + comp.lhsPretty;
        } else {
          prettyIf = WordingBuilder.prettyWhenLhs(ifS.before(), ifS.type(), ifS.after());
        }
      }

      String prettyTh;
      {
        var comp = (patternCfg == null) ? null : patternCfg.comparatorFor(thS.after());
        if (comp != null) {
          String quantKey = (patternCfg.quantifierOf(thS.before()));
          String head = "AT_LEAST_ONE".equals(quantKey)
              ? "At least one " + uk.gov.hmrc.dslgen.simple.human.Humanize.possessiveSingular(rootOf(thS.type()))
              : "All " + uk.gov.hmrc.dslgen.simple.human.Humanize.possessivePlural(rootOf(thS.type()));
          String fieldPhrase = uk.gov.hmrc.dslgen.simple.human.Humanize.dotPathToWords(thS.type().contains(".")?thS.type().substring(thS.type().indexOf('.')+1):"");
          prettyTh = head + " " + fieldPhrase + " " + comp.lhsPretty;
        } else {
          prettyTh = WordingBuilder.prettyWhenLhs(thS.before(), thS.type(), thS.after());
        }
      }

      registry.register(sigIf, "[when]" + prettyIf, rhsIf);
      registry.register(sigTh, "[when]" + prettyTh, rhsTh);

      // DSLR rule (guards + dashed pretty)
      String root1 = rootOf(ifS.type()); String root2 = rootOf(thS.type());
      List<String> guards = new ArrayList<>();
      if (!root1.isBlank()) guards.add(root1 + "()");
      if (!root2.isBlank() && !root2.equals(root1)) guards.add(root2 + "()");

      String d1 = "- " + prettyIf;
      String d2 = "- " + prettyTh;
      String name = "R" + (n++) + (r.ruleId().isBlank()? "" : "_" + r.ruleId());
      dslr.add(renderDslrRule(name, guards, List.of(d1, d2)));
    }

    if (cfg.reportOnly) {
      System.out.println("\n=== Report (no files written) ===");
      System.out.println("Rows processed: " + Math.min(processed, cfg.limit));
      System.out.println("Distinct BEFORE: " + distinctBefore);
      System.out.println("Distinct AFTER:  " + distinctAfter);
      if (!unknownAfter.isEmpty()) {
        System.out.println("UNKNOWN comparator phrases:");
        unknownAfter.forEach(s -> System.out.println("  - " + s));
      }
      return;
    }

    LinkedHashSet<String> whenLines = new LinkedHashSet<>();
    registry.mappings().values().forEach(m -> whenLines.add(m.lhs + " = " + m.rhs));
    writeDsl(whenLines, Path.of("rules.dsl"));
    writeDslr(dslr, Path.of("rules.dslr"));
    System.out.println("Wrote rules.dsl and rules.dslr");
  }
}
