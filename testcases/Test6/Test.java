// Test6: Context-sensitivity of heap — two ArrayLists hold different concrete types.
// list1 holds only Sensor, list2 holds only Actuator.
// The analysis must distinguish these two allocation sites (2-obj sensitivity).
// A less precise analysis would merge the two and fail to devirtualize either.
// Expected output: sensor readings sum = 5000, actuator readings sum = 10000

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

public class Test6 {
    static int sumReads(ArrayList<Device> devices) {
        int sum = 0;
        for (int i = 0; i < devices.size(); i++) {
            Device d = (Device) devices.get(i);
            sum += d.read(); // virtual call — context-sensitively monomorphic
        }
        return sum;
    }

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
            s1 += sumReads(list1); // always Sensor.read()
            s2 += sumReads(list2); // always Actuator.read()
        }
        long end = System.currentTimeMillis();
        System.out.println("Sensor sum: " + s1);   // 5000
        System.out.println("Actuator sum: " + s2); // 10000
        System.out.println("Time(ms): " + (end - start));
    }
}
