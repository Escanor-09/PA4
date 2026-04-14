class Factory {
    A create() { return new A(); }
}

class Wrapper {
    Factory f;
    Wrapper(Factory f) { this.f = f; }

    A make() { return f.create(); }
}

class A {}

public class Test {
    public static void main(String[] args) {
        Wrapper w1 = new Wrapper(new Factory());
        Wrapper w2 = new Wrapper(new Factory());

        A a1 = w1.make();
        A a2 = w2.make();
    }
}