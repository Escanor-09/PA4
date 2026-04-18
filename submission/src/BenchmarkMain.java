import soot.*;
import soot.options.Options;

public class BenchmarkMain {

    static String[] configFor(String benchName) {
        switch (benchName) {
            case "avrora":
                return new String[]{ "avrora.Main", "avrora", "cck" };
            case "batik":
                return new String[]{ "org.apache.batik.apps.rasterizer.Main", "org.apache.batik" };
            case "fop":
                return new String[]{ "org.apache.fop.cli.Main", "org.apache.fop" };
            case "luindex":
                return new String[]{ "Harness", "org.dacapo.luindex", "org.apache.lucene" };
            case "xalan":
                return new String[]{ "Harness", "org.apache.xalan", "org.apache.xpath" };
            default:
                return new String[]{ "Harness" };
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: BenchmarkMain <out-dir> [dacapo-jar]");
            System.exit(1);
        }

        String outDir    = args[0];
        String dacapoJar = (args.length > 1) ? args[1] : "../benchmark/dacapo-9.12-MR1-bach.jar";

        String dirName   = outDir.replaceAll(".*[\\/]", "");
        String benchName = dirName.startsWith("out-") ? dirName.substring(4) : dirName;
        String[] config  = configFor(benchName);

        String mainClass = config[0];
        String cp        = outDir + ":" + dacapoJar;
        String reflLog   = "reflection-log:" + outDir + "/refl.log";

        G.reset();

        AnalysisTransformer analysisTransformer = new AnalysisTransformer();
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", analysisTransformer));

        Transformation transformation = new Transformation();
        PackManager.v().getPack("jtp").add(new Transform("jtp.transform", transformation));

        // Set reflection log path for the PTA worklist seeding
        AnalysisTransformer.refLogPath = outDir + "/refl.log";

        java.util.List<String> sootArgsList = new java.util.ArrayList<>(java.util.Arrays.asList(
            "-whole-program",
            "-app",
            "-allow-phantom-refs",
            "-no-bodies-for-excluded",
            "-soot-classpath", cp,
            "-prepend-classpath",
            "-keep-line-number",
            "-main-class", mainClass,
            "-process-dir", outDir,
            "-p", "cg.spark", "on",
            "-p", "cg", reflLog,
            "-f", "n",
            "-ire",
            "-i", "org.apache.*",
            "-i", "org.dacapo.*",
            "-i", "jdt.*",
            "-i", "jdk.*",
            "-i", "java.*",
            "-i", "org.*",
            "-i", "com.*"
        ));

        for (int i = 1; i < config.length; i++) {
            sootArgsList.add("-i");
            sootArgsList.add(config[i]);
        }

        String[] sootArgs = sootArgsList.toArray(new String[0]);

        soot.Main.main(sootArgs);

        System.out.println("\n=== " + benchName + " | Monomorphic Sites: " + AnalysisTransformer.monoCalls.size() + " ===");
    }
}