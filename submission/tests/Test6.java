// Test6: Field-sensitive heap tracking — two ArrayLists hold different concrete types.
// list1 holds only Sensor objects, list2 holds only Actuator objects.
// Reads are done inline so the analysis can distinguish the two allocation sites
// (list1 pts-to set != list2 pts-to set) and devirtualize each d.read() call.
// Expected output: sensor sum = 5000, actuator sum = 10000

import java.util.ArrayList;

interface Device {
    int read();
}

class Sensor implements Device {
    @Override public int read() { return 1; }
}

class Actuator implements Device {
    @Override public int read() { return 2; }
}

public class Test {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int s1 = 0, s2 = 0;
        for (int iter = 0; iter < 100; iter++) {
            ArrayList<Device> list1 = new ArrayList<>();
            ArrayList<Device> list2 = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                list1.add(new Sensor());
                list2.add(new Actuator());
            }
            // Inline reads so the analysis sees which list each get() came from
            for (int i = 0; i < list1.size(); i++) {
                Device d = (Device) list1.get(i);
                s1 += d.read(); // pts(d) ⊆ {Sensor} -> monomorphic
            }
            for (int i = 0; i < list2.size(); i++) {
                Device d = (Device) list2.get(i);
                s2 += d.read(); // pts(d) ⊆ {Actuator} -> monomorphic
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Sensor sum: " + s1);   // 5000
        System.out.println("Actuator sum: " + s2); // 10000
        System.out.println("Time(ms): " + (end - start));
    }
}
