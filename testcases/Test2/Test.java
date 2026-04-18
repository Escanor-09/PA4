// Test2: Two independently monomorphic virtual calls in the same method body.
// s1 is always Square, s2 is always Triangle — both call sites devirtualizable.
// Tests that the analysis correctly resolves two distinct allocation sites for the
// same abstract declared type in the same scope.
// Expected output: Square avg = 25.0, Triangle avg = 6.0

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

public class Test {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        double sum1 = 0, sum2 = 0;
        for (int i = 0; i < 100000; i++) {
            Shape2 s1 = new Square(5.0);
            Shape2 s2 = new Triangle(4.0, 3.0);
            sum1 += s1.area(); // pts(s1) = {Square}   -> monomorphic
            sum2 += s2.area(); // pts(s2) = {Triangle}  -> monomorphic
        }
        long end = System.currentTimeMillis();
        System.out.println("Square avg: "   + (sum1 / 100000)); // 25.0
        System.out.println("Triangle avg: " + (sum2 / 100000)); // 6.0
        System.out.println("Time(ms): " + (end - start));
    }
}
