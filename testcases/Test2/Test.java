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
        Factory f2 = new Factory();
        A a1 = f1.create();
        A a2 = f2.create();
        B b1 = new B();
        B b2 = new B();
        a1.set(b1);
        a2.set(b2);
        B r1 = a1.get();  // expect {b1} only
        B r2 = a2.get();  // expect {b2} only
    }
}