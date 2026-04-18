import soot.*;
import soot.jimple.*;
import java.util.*;

public class Transformation extends BodyTransformer {

    private static int castCounter = 0;

    @Override
    public void internalTransform(Body body, String phaseName, Map<String, String> options) {

        // WORKFLOW:
        // AnalysisTransformer (wjtp) already identified monomorphic call sites AND
        // pre-built static wrapper methods for each concrete target while all bodies
        // were still in JimpleBody form.
        //
        // Here (jtp) we simply:
        //  1. Find each virtual/interface invoke that has a pre-built wrapper.
        //  2. Cast the base to the concrete type (JVM verifier requires the concrete
        //     type on the stack for the static call's first parameter).
        //  3. Replace the dispatch with staticinvoke(castBase, originalArgs...).
        //
        // staticinvoke = invokestatic in JVM bytecode: zero dispatch overhead,
        // no vtable lookup, no itable search. The cast is the only extra cost,
        // same as the checkcast+virtualinvoke approach, but invokestatic replaces
        // invokevirtual so we save the vtable index lookup too.

        Map<String, SootMethod> monoTargets = AnalysisTransformer.monoTargets;
        Map<SootMethod, SootMethod> wrapperCache = AnalysisTransformer.wrapperCache;

        if (monoTargets.isEmpty()) return;

        PatchingChain<Unit> units = body.getUnits();
        List<Unit> toProcess = new ArrayList<>(units);
        int devirtualizedCount = 0;

        for (Unit unit : toProcess) {
            Stmt stmt = (Stmt) unit;
            if (!stmt.containsInvokeExpr()) continue;

            InvokeExpr ie = stmt.getInvokeExpr();
            if (!(ie instanceof VirtualInvokeExpr) && !(ie instanceof InterfaceInvokeExpr)) continue;

            SootMethod target = monoTargets.get(stmt.toString());
            if (target == null) continue;

            SootMethod wrapper = wrapperCache.get(target);
            if (wrapper == null) continue; // wrapper not built (e.g. abstract body)

            InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
            Local base = (Local) iie.getBase();

            // Cast base to the concrete type so the verifier accepts it as the
            // first argument of the static wrapper.
            RefType concreteType = target.getDeclaringClass().getType();
            Local castLocal = Jimple.v().newLocal("$mono_recv_" + (castCounter++), concreteType);
            body.getLocals().add(castLocal);
            AssignStmt castStmt = Jimple.v().newAssignStmt(
                castLocal, Jimple.v().newCastExpr(base, concreteType));
            units.insertBefore(castStmt, stmt);

            // Replace virtual/interface dispatch with staticinvoke
            List<Value> newArgs = new ArrayList<>();
            newArgs.add(castLocal);
            newArgs.addAll(iie.getArgs());

            String before = stmt.toString();
            stmt.getInvokeExprBox().setValue(
                Jimple.v().newStaticInvokeExpr(wrapper.makeRef(), newArgs));

            devirtualizedCount++;
            System.out.println("[TRANSFORMATION] " + body.getMethod().getSignature());
            System.out.println("  Before : " + before);
            System.out.println("  After  : checkcast(" + concreteType + ") + " + stmt);
        }

        if (devirtualizedCount > 0) {
            System.out.println("[TRANSFORMATION] " + body.getMethod().getSignature()
                    + " — devirtualized " + devirtualizedCount + " call(s) total");
            body.validate();
        }
    }
}
