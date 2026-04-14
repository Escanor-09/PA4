class Factory {
    A create() { return new A(); }
}

class A {
    B f;
    void set(B x) { this.f = x; }
    B get() { return this.f; }
}

class B {}

public class Test {
    public static void main(String[] args) {
        Factory f1 = new Factory();

        A a1 = f1.create();
        A a2 = f1.create();  // SAME receiver

        B b1 = new B();
        B b2 = new B();

        a1.set(b1);
        a2.set(b2);

        B r1 = a1.get();
        B r2 = a2.get();
    }
}