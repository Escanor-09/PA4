import soot.*;
import soot.options.Options;

/**
 * Launcher for running PA4 monomorphization analysis on DaCapo benchmarks.
 *
 * Each DaCapo benchmark has its classes pre-extracted into an out-<name> directory
 * by TamiFlex. We wire our AnalysisTransformer (whole-program PTA) and Transformation
 * (devirtualization) into Soot's pack pipeline, then point it at the right entry class.
 *
 * Usage (run from the PA4/ directory):
 *   java -Xmx8g -cp ".:soot-4.6.0-jar-with-dependencies.jar" BenchmarkMain <out-dir> [dacapo-jar]
 *
 * Examples:
 *   java -Xmx8g -cp ".:soot-4.6.0-jar-with-dependencies.jar" BenchmarkMain \
 *        ../benchmark/tami-outs/tami-outs/out-avrora
 *
 *   java -Xmx8g -cp ".:soot-4.6.0-jar-with-dependencies.jar" BenchmarkMain \
 *        ../benchmark/tami-outs/tami-outs/out-fop
 *
 * Supported benchmarks and their entry points:
 *   avrora  → avrora.Main
 *   batik   → org.apache.batik.apps.rasterizer.Main
 *   fop     → org.apache.fop.cli.Main
 *   luindex → Harness  (no standalone main; analysis limited by reflection)
 *   xalan   → Harness  (no standalone main; analysis limited by reflection)
 */
public class BenchmarkMain {

    // Per-benchmark configuration: [mainClass, appPackagePrefix, extraExclude...]
    static String[] configFor(String benchName) {
        switch (benchName) {
            case "avrora":
                // avrora has its own main() — cleanest analysis entry point
                return new String[]{ "avrora.Main", "avrora", "cck" };

            case "batik":
                // Apache Batik SVG rasterizer
                return new String[]{ "org.apache.batik.apps.rasterizer.Main",
                                     "org.apache.batik" };

            case "fop":
                // Apache FOP PDF formatter
                return new String[]{ "org.apache.fop.cli.Main",
                                     "org.apache.fop" };

            case "luindex":
                // luindex uses Harness + reflection; best we can do is use Harness
                // and include org.dacapo.luindex + org.apache.lucene
                return new String[]{ "Harness",
                                     "org.dacapo.luindex", "org.apache.lucene" };

            case "xalan":
                // xalan is an XSLT processor, entry via Harness
                return new String[]{ "Harness",
                                     "org.apache.xalan", "org.apache.xpath" };

            default:
                System.err.println("[WARN] Unknown benchmark '" + benchName
                        + "'. Using Harness as entry point.");
                return new String[]{ "Harness" };
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: BenchmarkMain <out-dir> [dacapo-jar]");
            System.err.println("  out-dir    : path to TamiFlex output dir, e.g.");
            System.err.println("               ../benchmark/tami-outs/tami-outs/out-avrora");
            System.err.println("  dacapo-jar : path to dacapo jar");
            System.err.println("               (default: ../benchmark/dacapo-9.12-MR1-bach.jar)");
            System.exit(1);
        }

        String outDir    = args[0];
        String dacapoJar = (args.length > 1) ? args[1]
                                             : "../benchmark/dacapo-9.12-MR1-bach.jar";

        // Detect benchmark name from the directory name (out-avrora → avrora)
        String dirName   = outDir.replaceAll(".*[\\/]", ""); // last path component
        String benchName = dirName.startsWith("out-") ? dirName.substring(4) : dirName;
        String[] config  = configFor(benchName);

        String mainClass = config[0];
        String cp        = outDir + ":" + dacapoJar;
        String reflLog   = "reflection-log:" + outDir + "/refl.log";

        System.out.println("=== PA4 Benchmark Analysis ===");
        System.out.println("Benchmark  : " + benchName);
        System.out.println("Out dir    : " + outDir);
        System.out.println("Entry point: " + mainClass);
        System.out.println("DaCapo jar : " + dacapoJar);
        System.out.println("==============================\n");

        if (mainClass.equals("Harness")) {
            System.out.println("[WARN] This benchmark uses the DaCapo Harness as entry point.");
            System.out.println("[WARN] Harness loads benchmark code via reflection, which our");
            System.out.println("[WARN] static PTA cannot follow. Mono-call count will be low.\n");
        }

        G.reset();

        // Wire in our whole-program analysis
        AnalysisTransformer analysisTransformer = new AnalysisTransformer();
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", analysisTransformer));

        // Wire in our per-method transformation (devirtualization)
        Transformation transformation = new Transformation();
        PackManager.v().getPack("jtp").add(new Transform("jtp.transform", transformation));

        // Build the exclude list: always exclude core JDK internals and
        // DaCapo harness infrastructure
        java.util.List<String> sootArgsList = new java.util.ArrayList<>(java.util.Arrays.asList(
            "-whole-program",
            "-app",
            "-allow-phantom-refs",
            "-no-bodies-for-excluded",
            "-keep-line-number",
            "-soot-classpath", cp,
            "-prepend-classpath",
            "-main-class", mainClass,
            "-process-dir", outDir,
            // TamiFlex reflection log for better call-graph coverage
            "-p", "cg", reflLog,
            // Output Jimple so we can inspect devirtualized IR
            "-f", "J",
            // Always exclude true JDK internals
            "-x", "java.",
            "-x", "javax.",
            "-x", "sun.",
            "-x", "jdk.",
            "-x", "com.sun.",
            // Exclude DaCapo harness infrastructure (not benchmark logic)
            "-x", "org.dacapo.harness.",
            "-x", "org.dacapo.parser."
        ));

        // For benchmarks that use Harness, include the known app packages explicitly
        // (index 1+ in config are the app package prefixes)
        for (int i = 1; i < config.length; i++) {
            sootArgsList.add("-i");
            sootArgsList.add(config[i]);
        }

        String[] sootArgs = sootArgsList.toArray(new String[0]);

        soot.Main.main(sootArgs);
    }
}
