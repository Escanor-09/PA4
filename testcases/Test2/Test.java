// Test2: Context-sensitivity stress — two call sites each with a different concrete type.
// Shape s1 always -> Square, Shape s2 always -> Triangle.
// Both call sites are independently monomorphic and should be devirtualized.
// Expected output: 25.0 and 6.0

abstract class Shape2 {
    abstract double area();
}

class Square extends Shape2 {
    double side;
    Square(double s) { this.side = s; }
    @Override
    double area() { return side * side; }
}

class Triangle extends Shape2 {
    double base, height;
    Triangle(double b, double h) { this.base = b; this.height = h; }
    @Override
    double area() { return 0.5 * base * height; }
}

public class Test2 {
    static double compute(Shape2 s) {
        return s.area(); // virtual call — target depends on call site
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        double sum1 = 0, sum2 = 0;
        for (int i = 0; i < 100000; i++) {
            Shape2 s1 = new Square(5.0);
            Shape2 s2 = new Triangle(4.0, 3.0);
            sum1 += compute(s1); // always Square.area()
            sum2 += compute(s2); // always Triangle.area()
        }
        long end = System.currentTimeMillis();
        System.out.println("Square avg: " + (sum1 / 100000));   // 25.0
        System.out.println("Triangle avg: " + (sum2 / 100000)); // 6.0
        System.out.println("Time(ms): " + (end - start));
    }
}
