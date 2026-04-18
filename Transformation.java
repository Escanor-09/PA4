import soot.*;
import soot.jimple.*;
import java.util.*;

public class Transformation extends BodyTransformer {

    @Override
    public void internalTransform(Body body, String phaseName, Map<String, String> options) {

        // WORKFLOW:
        // 1. Get the monomorphic call sites identified by AnalysisTransformer
        // 2. Iterate over all statements in this method body
        // 3. For each statement that is a virtual/interface invoke:
        //    a. Look up stmt.toString() in monoTargets
        //    b. If found → replace VirtualInvokeExpr / InterfaceInvokeExpr
        //                   with SpecialInvokeExpr to the single concrete target
        // 4. Validate the patched body

        Map<String, SootMethod> monoTargets = AnalysisTransformer.monoTargets;

        // Nothing to optimize if analysis found no monomorphic calls
        if (monoTargets.isEmpty()) return;

        PatchingChain<Unit> units = body.getUnits();

        // Collect units to patch (avoid ConcurrentModificationException)
        List<Unit> toProcess = new ArrayList<>(units);

        int devirtualizedCount = 0;

        for (Unit unit : toProcess) {
            Stmt stmt = (Stmt) unit;

            if (!stmt.containsInvokeExpr()) continue;

            InvokeExpr ie = stmt.getInvokeExpr();

            // Only devirtualize virtual and interface dispatches
            if (!(ie instanceof VirtualInvokeExpr) && !(ie instanceof InterfaceInvokeExpr)) continue;

            // Look up this call site in the monomorphic targets map
            // Key is stmt.toString() — same string the analysis used when it recorded the site
            SootMethod target = monoTargets.get(stmt.toString());
            if (target == null) continue;

            // The original invoke expr is always an InstanceInvokeExpr (base + method + args)
            // Both VirtualInvokeExpr and InterfaceInvokeExpr extend InstanceInvokeExpr
            InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
            Local base = (Local) iie.getBase();

            // Build a SpecialInvokeExpr that calls the concrete target directly:
            //   virtualinvoke base.<Interface: void foo()>()
            //     becomes
            //   specialinvoke base.<ConcreteClass: void foo()>()
            //
            // specialinvoke is the Jimple opcode for direct (non-dispatched) instance calls.
            // It is normally used for constructors and private methods, and here for
            // devirtualized calls where we have proven there is exactly one possible target.
            SpecialInvokeExpr newExpr = Jimple.v().newSpecialInvokeExpr(
                base,           // same receiver local
                target.makeRef(), // concrete method reference (includes declaring class)
                iie.getArgs()   // same arguments
            );

            // Patch the statement in-place.
            // getInvokeExprBox() returns the ValueBox that holds the InvokeExpr inside
            // this statement (whether it's an InvokeStmt or the RHS of an AssignStmt).
            // setValue() replaces the old virtual/interface expr with the special one.
            String before = stmt.toString();
            stmt.getInvokeExprBox().setValue(newExpr);
            String after = stmt.toString();

            devirtualizedCount++;
            System.out.println("[TRANSFORMATION] " + body.getMethod().getSignature());
            System.out.println("  Before : " + before);
            System.out.println("  After  : " + after);
        }

        if (devirtualizedCount > 0) {
            System.out.println("[TRANSFORMATION] " + body.getMethod().getSignature()
                    + " — devirtualized " + devirtualizedCount + " call(s) total");
            body.validate();
        }
    }
}
