// Test7: Virtual dispatch on an object stored in a field of another object.
// Node.payload is always a Counter; node.payload.tick() should be monomorphic.
// Tests that your heapMap correctly tracks field -> allocation site mappings.
// Expected output: 100000

interface Tickable {
    int tick();
}

class Counter implements Tickable {
    private int val = 0;
    @Override
    public int tick() { return ++val; }
}

class Node {
    Tickable payload;
    Node(Tickable t) { this.payload = t; }
}

public class Test {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int total = 0;
        for (int i = 0; i < 100000; i++) {
            Counter c = new Counter();
            Node node = new Node(c);       // node.payload = Counter
            total += node.payload.tick();  // virtual call via field — target is always Counter
        }
        long end = System.currentTimeMillis();
        System.out.println("Total ticks: " + total); // 100000
        System.out.println("Time(ms): " + (end - start));
    }
}
