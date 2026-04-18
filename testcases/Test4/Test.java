// Test4: Call chain — virtual dispatch through an intermediary helper.
// formatter.format(msg) where formatter is always a UpperCaseFormatter.
// Both the call to process() and format() within are monomorphic.
// Expected output: "HELLO WORLD" printed 100000 times.

interface Formatter {
    String format(String s);
}

class UpperCaseFormatter implements Formatter {
    @Override
    public String format(String s) { return s.toUpperCase(); }
}

public class Test4 {
    static String process(Formatter f, String input) {
        return f.format(input); // virtual call — always UpperCaseFormatter
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        String last = "";
        for (int i = 0; i < 100000; i++) {
            Formatter fmt = new UpperCaseFormatter();
            last = process(fmt, "hello world");
        }
        long end = System.currentTimeMillis();
        System.out.println(last);             // HELLO WORLD
        System.out.println("Time(ms): " + (end - start));
    }
}
