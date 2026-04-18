import soot.*;
import soot.jimple.*;
import java.util.*;

public class Transformation extends BodyTransformer {

    private static int castCounter = 0;

    @Override
    public void internalTransform(Body body, String phaseName, Map<String, String> options) {

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
            if (wrapper == null) continue;

            InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
            Local base = (Local) iie.getBase();

            RefType concreteType = target.getDeclaringClass().getType();
            Local castLocal = Jimple.v().newLocal("$mono_recv_" + (castCounter++), concreteType);
            body.getLocals().add(castLocal);
            AssignStmt castStmt = Jimple.v().newAssignStmt(
                castLocal, Jimple.v().newCastExpr(base, concreteType));
            units.insertBefore(castStmt, stmt);

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
