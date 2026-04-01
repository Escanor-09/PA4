import soot.*;
import soot.options.Options;

public class PA4{
    public static void main(String args[]){

        G.reset();

        String classPath = "./testcases";

        Options.v().set_keep_line_number(true);

        //analysis(whole program)
        AnalysisTransformer analysisTransformer = new AnalysisTransformer();
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", analysisTransformer));
        
        //transformation(per method)
        Transformation transformation = new Transformation();
        PackManager.v().getPack("jtp").add(new Transform("jtp.transform",transformation));

        String sootArgs[] = {
            "-cp",classPath,
            "-pp",
            "-w",
            "-app",
            "-allow-phantom-refs",
            "-no-bodies-for-excluded",
            "-exclude","java.",
            "-exclude","javax.",
            "-exclude","sun.*",
            "-exclude","com.sun.*",
            "-exclude","jdk.*",
            "-f","J",
            "-main-class","Test",
            "-process-dir",classPath
        };

        soot.Main.main(sootArgs);
    }
}