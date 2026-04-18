// Test10: Virtual call through a two-level field chain (field of field).
// Pipeline.stage.process() — Pipeline.stage always holds a ConcreteStage.
// Tests that the analysis correctly tracks heap depth: it must follow
//   Pipeline.stage  (InstanceFieldRef)  -> ConcreteStage allocation site
// and then resolve the virtual call process() on that site as monomorphic.
// Expected output: 100000, Time shown.

interface Stage {
    int process(int x);
}

class ConcreteStage implements Stage {
    @Override
    public int process(int x) { return x + 1; }
}

class Pipeline {
    Stage stage;
    Pipeline(Stage s) { this.stage = s; }

    int run(int x) {
        return stage.process(x); // virtual call through instance field
    }
}

public class Test {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int total = 0;
        for (int i = 0; i < 100000; i++) {
            Pipeline p = new Pipeline(new ConcreteStage());
            total += p.run(0); // stage.process() inside run() -> always ConcreteStage
        }
        long end = System.currentTimeMillis();
        System.out.println("Total: " + total); // 100000
        System.out.println("Time(ms): " + (end - start));
    }
}
