import soot.*;
import soot.options.Options;


public class Main {

    public static void main(String[] args) {

        G.reset();

        String testName = (args.length > 0) ? args[0] : "Test1";
        String inputDir  = (args.length > 1) ? args[1] : ("tests/" + testName + "_build");
        String outputDir = (args.length > 2) ? args[2] : ("out/" + testName);

        Options.v().set_keep_line_number(true);

        // Phase 1
        AnalysisTransformer analysisTransformer = new AnalysisTransformer();
        PackManager.v().getPack("wjtp").add(
            new Transform("wjtp.analysis", analysisTransformer));

        // Phase 2
        Transformation transformation = new Transformation();
        PackManager.v().getPack("jtp").add(
            new Transform("jtp.transform", transformation));

        String[] sootArgs = {
            "-cp",  inputDir,
            "-pp",
            "-w",
            "-app",
            "-allow-phantom-refs",
            "-no-bodies-for-excluded",
            "-exclude", "java.",
            "-exclude", "javax.",
            "-exclude", "sun.",
            "-exclude", "com.sun.",
            "-exclude", "jdk.",
            "-f",   "class",
            "-d",   outputDir,
            "-main-class", "Test",
            "-process-dir", inputDir
        };

        soot.Main.main(sootArgs);
    }
}
