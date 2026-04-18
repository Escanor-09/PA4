// Test9: Recursive handler chain — tests that the analysis reaches a fixed point
// over a manually built singly-linked list of Handler objects.
// All handlers are of type PrintHandler; dispatch on handle() is monomorphic
// despite the recursive structure. Tests fixed-point convergence over heap cycles.
// Expected output: chain length = 10, result = 10

interface Handler {
    int handle(int x);
}

class PrintHandler implements Handler {
    Handler next;
    PrintHandler(Handler next) { this.next = next; }

    @Override
    public int handle(int x) {
        if (next != null) return 1 + next.handle(x); // virtual call — always PrintHandler
        return 1;
    }
}

public class Test9 {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int total = 0;
        for (int iter = 0; iter < 10000; iter++) {
            // Build a chain of 10 PrintHandlers
            Handler h = null;
            for (int i = 0; i < 10; i++) {
                h = new PrintHandler(h);
            }
            total += h.handle(0); // triggers recursive virtual dispatch chain
        }
        long end = System.currentTimeMillis();
        System.out.println("Chain depth result: " + (total / 10000)); // 10
        System.out.println("Time(ms): " + (end - start));
    }
}
