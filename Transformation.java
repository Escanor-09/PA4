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
        if(monoTargets.isEmpty()) return;

        PatchingChain<Unit> units = body.getUnits();

        // Collect units to patch (avoid ConcurrentModificationException)
        List<Unit> toProcess = new ArrayList<>(units);

        for(Unit unit : toProcess){
            Stmt stmt = (Stmt) unit;

            if(!stmt.containsInvokeExpr()) continue;

            InvokeExpr ie = stmt.getInvokeExpr();

            // Only devirtualize virtual and interface dispatches
            if(!(ie instanceof VirtualInvokeExpr) && !(ie instanceof InterfaceInvokeExpr)) continue;

            // Look up this call site in the monomorphic targets map
            SootMethod target = monoTargets.get(stmt.toString());
            if(target == null) continue;

            // TODO: Build SpecialInvokeExpr with the concrete target
            // SpecialInvokeExpr newExpr = Jimple.v().newSpecialInvokeExpr(...);

            // TODO: Replace the invoke expr in the stmt
            // stmt.getInvokeExprBox().setValue(newExpr);

            // TODO: Print what was devirtualized (for debugging/output)
            // System.out.println("[Devirtualized] " + stmt + " → " + target.getSignature());
        }

        // TODO: Validate the body after patching
        // body.validate();
    }
}
