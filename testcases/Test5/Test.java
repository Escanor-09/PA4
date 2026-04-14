class A { B get() { return new B(); } }
class C extends A { B get() { return new B(); } }
class B {}

public class Test {
    public static void main(String[] args) {
        A x;
        if(args.length > 0) x = new A();
        else x = new C();

        B b = x.get();
    }
}