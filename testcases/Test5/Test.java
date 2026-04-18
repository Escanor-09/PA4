// Test5: Objects stored in ArrayList and retrieved via virtual call.
// All objects added are of type FastEngine; the get() result is used in a virtual dispatch.
// Your analysis models ArrayList with ArrayField summary.
// With good heap context, the retrieved object's type is known -> monomorphic dispatch.
// Expected output: 1000000 (sum of speeds)

import java.util.ArrayList;

interface Engine {
    int getSpeed();
}

class FastEngine implements Engine {
    private final int speed;
    FastEngine(int s) { this.speed = s; }
    @Override
    public int getSpeed() { return speed; }
}

public class Test5 {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        long total = 0;
        for (int iter = 0; iter < 1000; iter++) {
            ArrayList<Engine> list = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                list.add(new FastEngine(1)); // only FastEngine objects
            }
            for (int i = 0; i < list.size(); i++) {
                Engine e = (Engine) list.get(i);
                total += e.getSpeed(); // virtual call — should be devirtualized if analysis tracks list content
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Total speed: " + total); // 1000000
        System.out.println("Time(ms): " + (end - start));
    }
}
