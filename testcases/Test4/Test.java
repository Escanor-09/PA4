class A {
    B f;
}

class B {}

public class Test {
    public static void main(String[] args) {
        A a = new A();
        B b1 = new B();
        B b2 = new B();

        a.f = b1;
        a.f = b2;

        B r = a.f;  // should be {b2} only
    }
}