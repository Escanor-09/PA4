// Test8: Virtual call via a static field assigned in main().
// Assigning instance = new FileLogger() inside main() ensures our analysis
// (which starts from main) records staticMap[Test.instance] = {FileLogger}.
// The subsequent interface call instance.log() is then monomorphic.
// Expected output: logged 100000, Time shown.

interface Logger {
    void log(String msg);
}

class FileLogger implements Logger {
    private int count = 0;
    @Override
    public void log(String msg) { count++; }
    public int getCount() { return count; }
}

public class Test {
    static Logger instance; // assigned in main so analysis sees the concrete write

    public static void main(String[] args) {
        instance = new FileLogger(); // staticMap[Test.instance] = {FileLogger}
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            instance.log("event"); // pts(instance) = {FileLogger} -> monomorphic
        }
        long end = System.currentTimeMillis();
        System.out.println("logged: " + ((FileLogger) instance).getCount()); // 100000
        System.out.println("Time(ms): " + (end - start));
    }
}
