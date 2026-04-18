// Test8: Virtual call on an object read from a static field.
// Logger.instance is assigned exactly one concrete type (FileLogger).
// Your analysis tracks staticMap; if it correctly resolves Logger.instance -> FileLogger,
// the log() dispatch is monomorphic.
// Expected output: "logged" printed, Time shown.

interface Logger {
    void log(String msg);
}

class FileLogger implements Logger {
    private int count = 0;
    @Override
    public void log(String msg) { count++; }
    public int getCount() { return count; }
}

public class Test8 {
    static Logger instance = new FileLogger(); // static field, single concrete type

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            instance.log("event"); // virtual call via static field — should be devirtualized
        }
        long end = System.currentTimeMillis();
        System.out.println("logged: " + ((FileLogger) instance).getCount()); // 100000
        System.out.println("Time(ms): " + (end - start));
    }
}
