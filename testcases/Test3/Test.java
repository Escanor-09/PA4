// Test3: NEGATIVE TEST — Polymorphic dispatch, no monomorphization expected.
// The variable `animal` may point to Dog or Cat depending on loop iteration.
// Analysis should correctly identify this as a multi-target call site.
// Expected output: alternating "Woof" and "Meow", 100000 times total.

abstract class Animal {
    abstract String speak();
}

class Dog extends Animal {
    @Override
    String speak() { return "Woof"; }
}

class Cat extends Animal {
    @Override
    String speak() { return "Meow"; }
}

public class Test3 {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int woof = 0, meow = 0;
        for (int i = 0; i < 100000; i++) {
            Animal animal = (i % 2 == 0) ? new Dog() : new Cat(); // two possible types
            String s = animal.speak(); // polymorphic — must NOT be devirtualized
            if (s.equals("Woof")) woof++;
            else meow++;
        }
        long end = System.currentTimeMillis();
        System.out.println("Woof: " + woof); // 50000
        System.out.println("Meow: " + meow); // 50000
        System.out.println("Time(ms): " + (end - start));
    }
}
