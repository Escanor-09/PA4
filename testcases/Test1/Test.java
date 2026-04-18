// Test1: Monomorphization of a simple single-implementation virtual call.
// The analysis should detect that `shape` always points to Circle,
// making shape.area() a monomorphic call site.
// Expected output: 78.53981633974483 (printed 100000 times, or average shown)

abstract class Shape {
    abstract double area();
}

class Circle extends Shape {
    double r;
    Circle(double r) { this.r = r; }
    @Override
    double area() { return Math.PI * r * r; }
}

public class Test {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        double sum = 0;
        for (int i = 0; i < 100000; i++) {
            Shape shape = new Circle(5.0); // only ever Circle
            sum += shape.area();           // virtual call -> should be devirtualized
        }
        long end = System.currentTimeMillis();
        System.out.println("Result: " + (sum / 100000));
        System.out.println("Time(ms): " + (end - start));
    }
}
