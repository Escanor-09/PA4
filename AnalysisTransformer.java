import soot.*;
import soot.JastAddJ.PrimitiveType;
import soot.jimple.AnyNewExpr;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;

import heros.solver.Pair;

interface Field{

}

class RealField implements Field{
    private final SootField field;

    RealField(SootField field){
        this.field = field;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if(!(obj instanceof RealField)) return false;

        RealField other = (RealField)obj;
        return Objects.equals(this.field, other.field);
    }

    @Override
    public int hashCode(){
        return Objects.hash(field);
    }

    @Override
    public String toString(){
        return field.getName();
    }
}

class ArrayField implements Field{
    private final String name;

    private ArrayField(String name){
        this.name = name;
    }

    public static final ArrayField ARRAY = new ArrayField("*");

    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if(!(obj instanceof ArrayField)) return false;

        ArrayField other = (ArrayField)obj;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode(){
        return Objects.hash(name);
    }

    @Override
    public String toString(){
        return this.name;
    }
}

class KeyField implements Field{
    private final AllocationSite keyObj; //abstract key object

    KeyField(AllocationSite keyObj){
        this.keyObj = keyObj;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if(!(obj instanceof KeyField)) return false;

        KeyField other = (KeyField)obj;
        return Objects.equals(this.keyObj, other.keyObj);
    }

    @Override
    public int hashCode(){
        return Objects.hash(keyObj);
    }

    @Override
    public String toString(){
        return "key(" + keyObj + ")";
    }
}

class AllocationSiteContext{
    final List<Unit> chain;

    AllocationSiteContext(List<Unit> chain){
        this.chain = chain;
    }

    static AllocationSiteContext empty(){
        return new AllocationSiteContext(new ArrayList<>());
    }

    AllocationSiteContext push(Unit u, int k){
        List<Unit> newList = new ArrayList<>();
        newList.add(u);

        for(int i = 0; i < Math.min(k-1, chain.size()); i++){
            newList.add(chain.get(i));
        }
        return new AllocationSiteContext(newList);
    }

    AllocationSiteContext truncate(int k){
        return new AllocationSiteContext(new ArrayList<>(chain.subList(0, Math.min(k,chain.size()))));
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if(!(obj instanceof AllocationSiteContext)) return false;

        AllocationSiteContext other = (AllocationSiteContext) obj;
        return Objects.equals(this.chain, other.chain);
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.chain);
    }

    @Override
    public String toString(){
        List<Integer> lines = new ArrayList<>();
        for(Unit u : this.chain){
            lines.add(u.hashCode());
        }
        return lines.toString();
    }
}

class AllocationSite{
    private final Unit allocationSite;
    private final SootClass type;
    private final AllocationSiteContext heapContext;
    private final boolean isConstant;
    private final String tag;

    AllocationSite(Unit allocationSite, SootClass type, AllocationSiteContext heapContext){
        this.allocationSite = allocationSite;
        this.type = type;
        this.heapContext = heapContext;
        this.isConstant = false;
        this.tag = "OBJ";
    }

    AllocationSite(Unit allocationSite, SootClass type, AllocationSiteContext heapContext, String tag){
        this.allocationSite = allocationSite;
        this.type = type;
        this.heapContext = heapContext;
        this.isConstant = false;
        this.tag = tag;
    }

    private AllocationSite(boolean isConstant,String tag){
        this.isConstant = isConstant;
        this.allocationSite = null;
        this.type = null;
        this.heapContext = null;
        this.tag = tag;
    }

    public static final AllocationSite CONST = new AllocationSite(true,"CONST");
    public static final AllocationSite PRIMITIVE = new AllocationSite(true,"PRIMITIVE");

    public SootClass getType(){
        return this.type;
    }

    public boolean isConstant(){
        return this.isConstant;
    }

    public AllocationSiteContext getHeapContext(){
        return this.heapContext;
    }

    public Unit getAllocationSite(){
        return this.allocationSite;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj){
            return true;
        }
        if(!(obj instanceof AllocationSite)) return false;
        AllocationSite other = (AllocationSite)obj;

        if(this.isConstant || other.isConstant){
            return Objects.equals(this.tag, other.tag) && this.isConstant && other.isConstant;
        }
        return Objects.equals(this.allocationSite, other.allocationSite) && Objects.equals(this.type, other.type) && Objects.equals(this.heapContext, other.heapContext) && Objects.equals(this.tag, other.tag);
    }

    @Override
    public int hashCode(){
        if(this.isConstant) return Objects.hash(tag);
        return Objects.hash(this.allocationSite,this.type, this.heapContext, this.tag);
    }

    @Override
    public String toString(){
        if(this.isConstant) return this.tag;
        int line = this.allocationSite != null ? this.allocationSite.getJavaSourceStartLineNumber() : -1;

        return "Obj(" + (this.type != null ? this.type.getName() : "?") + ", L" + line + ", hc = " + this.heapContext + ")";
    }
}

class FieldRef{
    final AllocationSite base;
    final Field field;

    FieldRef(AllocationSite base, Field field){
        this.base = base;
        this.field = field;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if(!(obj instanceof FieldRef)) return false;

        FieldRef other = (FieldRef)obj;
        return Objects.equals(this.base,other.base) && Objects.equals(this.field, other.field);
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.base, this.field);
    }
    @Override
    public String toString(){
        return base + "." + field.toString();
    }
}

class State{

    Map<Local, Set<AllocationSite>> stackMap;
    Map<FieldRef, Set<AllocationSite>> heapMap;
    Map<SootField, Set<AllocationSite>> staticMap;
    Set<AllocationSite> returnValues;
    AllocationSiteContext methodContext;
    
    State(AllocationSiteContext methodContext){
        this.stackMap = new HashMap<>();
        this.heapMap = new HashMap<>();
        this.staticMap = new HashMap<>();
        this.returnValues = new HashSet<>();
        this.methodContext = methodContext;
    }

    //copy helper
    State(State other){

        //copying method Context
        this.methodContext = other.methodContext;

        //stackMap copy
        this.stackMap = new HashMap<>();
        for(var e: other.stackMap.entrySet()){
            this.stackMap.put(e.getKey(),new HashSet<>(e.getValue()));
        }

        //heapMap
        this.heapMap = new HashMap<>();
        for(var e: other.heapMap.entrySet()){
            this.heapMap.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        //staticMap
        this.staticMap = new HashMap<>();
        for(var e: other.staticMap.entrySet()){
            this.staticMap.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        //return values
        this.returnValues = new HashSet<>();
        this.returnValues.addAll(other.returnValues);
    }

    State copy(){
        return new State(this);
    }

    public boolean merge(State other){

        if(!Objects.equals(this.methodContext, other.methodContext)){
            return false;
        }
        boolean changed = false;

        //merge stackMap
        for(var e: other.stackMap.entrySet()){
            Set<AllocationSite> pSet = this.stackMap.computeIfAbsent(e.getKey(), k-> new HashSet<>());
            if(pSet.addAll(e.getValue())) changed = true;
        }

        //merge heapMap
        for(var e: other.heapMap.entrySet()){
            Set<AllocationSite> pSet = this.heapMap.computeIfAbsent(e.getKey(), k-> new HashSet<>());
            if(pSet.addAll(e.getValue())) changed = true;
        }
        //merge staticMap
        for(var e: other.staticMap.entrySet()){
            Set<AllocationSite> pSet = this.staticMap.computeIfAbsent(e.getKey(), k-> new HashSet<>());
            if(pSet.addAll(e.getValue())) changed = true;
        }

        //return values
        if(this.returnValues.addAll(other.returnValues)) changed = true;

        return changed;
    }

    //helper function to get Points-to info
    Set<AllocationSite> getPts(Local l){
        return this.stackMap.getOrDefault(l, Collections.emptySet());
    }

    //getRetrun values
    Set<AllocationSite> getReturnValues(){
        return this.returnValues;
    }

    //helper function to do strong update
    void strongUpdate(Local l, Set<AllocationSite> pSet){
        this.stackMap.put(l, new HashSet<>(pSet));
    }

    //helper function to do weak update
    void weakUpdate(Local l, Set<AllocationSite> pSet){
        this.stackMap.computeIfAbsent(l, k-> new HashSet<>()).addAll(pSet);
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if(!(obj instanceof State)) return false;

        State other = (State)obj;
        return Objects.equals(this.stackMap, other.stackMap) && Objects.equals(this.heapMap, other.heapMap) && Objects.equals(this.staticMap, other.staticMap) && Objects.equals(this.methodContext, other.methodContext) && Objects.equals(this.returnValues,other.returnValues);
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.stackMap, this.heapMap, this.staticMap, this.methodContext, this.returnValues);
    }
}

public class AnalysisTransformer extends SceneTransformer{

    static class CallerRecord{
        final SootMethod caller;
        final AllocationSiteContext callercontext;
        final Stmt callSite;

        CallerRecord(SootMethod caller, AllocationSiteContext callerContext, Stmt callSite){
            this.caller = caller;
            this.callercontext = callerContext;
            this.callSite = callSite;
        }

        @Override
        public boolean equals(Object obj){
            if(this == obj) return true;
            if(!(obj instanceof CallerRecord)) return false;

            CallerRecord other = (CallerRecord)obj;
            return Objects.equals(this.caller, other.caller) && Objects.equals(this.callSite, other.callSite) && Objects.equals(this.callercontext, other.callercontext);
        }

        @Override
        public int hashCode(){
            return Objects.hash(this.callSite, this.caller, this.callercontext);
        }
    }

    static Map<Pair<SootMethod,AllocationSiteContext>, State> methodState;
    static Map<Pair<SootMethod, AllocationSiteContext>, Set<CallerRecord>> callerMap;
    static Queue<Pair<SootMethod, AllocationSiteContext>> globalWorkList;
    static CallGraph cg;
    static final int K = 2;
    static final boolean DEBUG = false;
    // static int totalCalls = 0;
    // static int monocalls = 0;
    static Map<String, String> monoCalls = new HashMap<>();

    @Override
    public void internalTransform(String phaseName, Map<String,String> options){
        methodState = new HashMap<>();
        callerMap = new HashMap<>();
        cg = Scene.v().getCallGraph();

        globalWorkList = new LinkedList<>();

        //entry method
        SootMethod main = Scene.v().getMainMethod();
        
        //intial Context
        AllocationSiteContext emptyContext = AllocationSiteContext.empty();

        State init = new State(emptyContext);

        Pair<SootMethod, AllocationSiteContext> entry = new Pair<>(main, emptyContext);

        methodState.put(entry, init);
        globalWorkList.add(entry);

        while(!globalWorkList.isEmpty()){
            Pair<SootMethod, AllocationSiteContext> curr = globalWorkList.poll();

            SootMethod method = curr.getO1();
            AllocationSiteContext context = curr.getO2();

            State inState = methodState.get(curr);

            Map<Unit, State> outStates = runWorkList(method, inState);

            State exitState = new State(context);
            for(Unit u: method.getActiveBody().getUnits()){
                if(u instanceof ReturnStmt || u instanceof soot.jimple.ReturnVoidStmt || u instanceof soot.jimple.ThrowStmt){
                    State s = outStates.get(u);
                    if(s != null) exitState.merge(s);
                }
            }
            if(DEBUG){
                printAnalysisState("EXIT STATE", method, context, exitState);
            }
            propagateToCallers(method, context, exitState);
        }
        // Print final points-to state for main after full analysis
        Pair<SootMethod, AllocationSiteContext> mainKey = new Pair<>(main, emptyContext);
        State mainFinalInState = methodState.get(mainKey);
        if(mainFinalInState != null){
            Map<Unit, State> finalOutStates = runWorkList(main, mainFinalInState.copy());
            State finalState = new State(emptyContext);
            for(Unit u : main.getActiveBody().getUnits()){
                if(u instanceof ReturnStmt || u instanceof soot.jimple.ReturnVoidStmt || u instanceof soot.jimple.ThrowStmt){
                    State s = finalOutStates.get(u);
                    if(s != null) finalState.merge(s);
                }
            }
            printFinalMainState(finalState, main, emptyContext);
        }

        System.out.println("\n=================Monomorphic Calls=================");

        for(String info : monoCalls.values()){
            System.out.println("\n[Mono Call]");
            System.out.println(info);
        }
        System.out.println("Total Mono Calls: " + monoCalls.size());
        System.out.println("================================");
    }

    private State transferFunction(Unit unit, State state, SootMethod sm){
        Stmt stmt = (Stmt)unit;

        // System.out.println("    transferFunction: "+ stmt + " [" + stmt.getClass().getSimpleName() + "]");
        //function calls
        if(stmt.containsInvokeExpr()){

            //types of InvokeExpr
            InvokeExpr ie = stmt.getInvokeExpr();

            //handle modeled library methods (ArrayList, HashMap summaries)
            if(ie.getMethod().getDeclaringClass().isJavaLibraryClass()){
                return applyLibrarySummary(stmt, ie, state);
            }

            List<SootMethod> targets = resolveTargets(ie, stmt,state);
            if(targets.isEmpty()) return state;

            //static invoke function
            if(ie instanceof StaticInvokeExpr){
                for(SootMethod tgt: targets){
                    if(!tgt.hasActiveBody()) continue;

                    AllocationSiteContext newContext = state.methodContext;

                    Pair<SootMethod, AllocationSiteContext> key = new Pair<>(tgt, newContext);
                    callerMap.computeIfAbsent(key, k-> new HashSet<>()).add(new CallerRecord(sm, state.methodContext, stmt));

                    State calleeEntry = buildCalleeEntryState(tgt, ie, state, null);
                    calleeEntry.methodContext = newContext;

                    State old = methodState.get(key);

                    if(old == null){
                        methodState.put(key, calleeEntry);
                        globalWorkList.add(key);
                    }else if(old.merge(calleeEntry)){
                        globalWorkList.add(key);
                    }
                }
                return state;
            }

            //special invoke funciton
            if(ie instanceof SpecialInvokeExpr sie){
                
                Local baseLocal = (Local)sie.getBase();
                Set<AllocationSite> baseSet = state.getPts(baseLocal);

                for(AllocationSite recv : baseSet){

                    AllocationSiteContext recvHeapContext = recv.getHeapContext() != null ? recv.getHeapContext() : AllocationSiteContext.empty();
                    AllocationSiteContext newContext = recvHeapContext.push(recv.getAllocationSite(), K);
                    //AllocationSiteContext newContext = recv.getHeapContext();

                    for(SootMethod tgt: targets){
                        if(!tgt.hasActiveBody()) continue;

                        Pair<SootMethod, AllocationSiteContext> key = new Pair<>(tgt, newContext);
                        callerMap.computeIfAbsent(key, k-> new HashSet<>()).add(new CallerRecord(sm, state.methodContext, stmt));

                        State calleeEntry = buildCalleeEntryState(tgt, ie, state, recv);

                        calleeEntry.methodContext = newContext;

                        State old = methodState.get(key);
                        
                        if(old == null){
                            methodState.put(key,calleeEntry);
                            globalWorkList.add(key);
                        }else if(old.merge(calleeEntry)){
                            globalWorkList.add(key);
                        }
                    }
                }
                return state;
            }

            //instance invoke function
            if(ie instanceof InstanceInvokeExpr iie){

                Local base = (Local)iie.getBase();
                Set<AllocationSite> baseSet = state.getPts(base);

                for(AllocationSite recv: baseSet){

                    AllocationSiteContext recvHeapContext = recv.getHeapContext() != null ? recv.getHeapContext() : AllocationSiteContext.empty();
                    AllocationSiteContext newContext = recvHeapContext.push(recv.getAllocationSite(), K);

                    //AllocationSiteContext newContext = recv.getHeapContext();

                    for(SootMethod tgt: targets){

                        if(!tgt.hasActiveBody()) continue;

                        Pair<SootMethod, AllocationSiteContext> key = new Pair<>(tgt, newContext);
                        callerMap.computeIfAbsent(key, k-> new HashSet<>()).add(new CallerRecord(sm,state.methodContext, stmt));

                        State calleeEntry = buildCalleeEntryState(tgt, ie, state, recv);

                        calleeEntry.methodContext = newContext;

                        State old = methodState.get(key);

                        if(old == null){
                            methodState.put(key,calleeEntry);
                            globalWorkList.add(key);
                        }

                        else if(old.merge(calleeEntry)){
                            globalWorkList.add(key);
                        }
                    }
                }
                return state;
            }

            return state;
        }

        //assign stmt
        else if(stmt instanceof AssignStmt aStmt){

            Value lhs = aStmt.getLeftOp();
            Value rhs = aStmt.getRightOp();

            //alloc
            if(rhs instanceof NewExpr r && lhs instanceof Local l){
                RefType objType = r.getBaseType();
                SootClass sc = objType.getSootClass();
                // AllocationSiteContext heapContext;

                // if(state.methodContext.chain.isEmpty()){
                //     heapContext = AllocationSiteContext.empty();
                // }else{
                //     heapContext = new AllocationSiteContext(List.of(state.methodContext.chain.get(0)));
                // }
                //AllocationSiteContext heapContext = new AllocationSiteContext(List.of(unit));
                AllocationSiteContext heapContext = state.methodContext.chain.isEmpty() ? AllocationSiteContext.empty() : new AllocationSiteContext(List.of(state.methodContext.chain.get(0)));
                AllocationSite aSite = new AllocationSite(unit, sc, heapContext);
                state.strongUpdate(l, Set.of(aSite));
            }

            //1D-array allocation
            else if(rhs instanceof NewArrayExpr r && lhs instanceof Local l){
                Type baseType = r.getBaseType();
                SootClass sc = null;

                if(baseType instanceof RefType rt){
                    sc = rt.getSootClass();
                }

                //AllocationSiteContext heapContext = state.methodContext.chain.isEmpty() ? AllocationSiteContext.empty() : new AllocationSiteContext(List.of(state.methodContext.chain.get(0)));

                AllocationSiteContext heapContext = state.methodContext.chain.isEmpty() ? AllocationSiteContext.empty() : new AllocationSiteContext(List.of(state.methodContext.chain.get(0)));

                AllocationSite aSite = new AllocationSite(unit, sc, heapContext);

                state.strongUpdate(l, Set.of(aSite));

                FieldRef fRef = new FieldRef(aSite, ArrayField.ARRAY);

                if(baseType instanceof PrimType){
                    state.heapMap.put(fRef, new HashSet<>(Set.of(AllocationSite.PRIMITIVE)));
                }else{
                    state.heapMap.put(fRef, new HashSet<>());
                }
            }

            //Multi Dimensional Array Allocation
            else if(rhs instanceof NewMultiArrayExpr r && lhs instanceof Local l){

                Type baseType = r.getBaseType();
                while(baseType instanceof ArrayType at){
                    baseType = at.baseType;
                }

                SootClass sc = (baseType instanceof RefType rt) ? rt.getSootClass() : null;
                
                //AllocationSiteContext heapContext = state.methodContext.chain.isEmpty() ? AllocationSiteContext.empty() : new AllocationSiteContext(List.of(state.methodContext.chain.get(0)));
                AllocationSiteContext heapContext = state.methodContext.chain.isEmpty() ? AllocationSiteContext.empty() : new AllocationSiteContext(List.of(state.methodContext.chain.get(0)));
                //outer array
                AllocationSite outer = new AllocationSite(unit, sc, heapContext, "OUTER");

                //inner array
                AllocationSite inner = new AllocationSite(unit, sc, heapContext, "INNER");

                state.strongUpdate(l, Set.of(outer));

                FieldRef outerField = new FieldRef(outer, ArrayField.ARRAY);
                state.heapMap.put(outerField, new HashSet<>(Set.of(inner)));

                FieldRef innerField = new FieldRef(inner, ArrayField.ARRAY);

                if(baseType instanceof PrimType){
                    state.heapMap.put(innerField, new HashSet<>(Set.of(AllocationSite.PRIMITIVE)));
                }else{
                    state.heapMap.put(innerField, new HashSet<>());
                }
            }
        
            //copy
            else if(lhs instanceof Local l && rhs instanceof Local r){
                Set<AllocationSite> rSet = state.getPts(r);
                state.strongUpdate(l, rSet);
            }

            //field load a = x.f;
            else if(rhs instanceof InstanceFieldRef iRef && lhs instanceof Local l){
                Local baseLocal = (Local)iRef.getBase();
                SootField sf = iRef.getField();
                Set<AllocationSite> baseSet = state.getPts(baseLocal);
                Set<AllocationSite> result = new HashSet<>();

                for(AllocationSite a: baseSet){
                    RealField rf = new RealField(sf);
                    FieldRef fRef = new FieldRef(a, rf);
                    result.addAll(state.heapMap.getOrDefault(fRef, Collections.emptySet()));    
                }
                state.strongUpdate(l, result);
            }
            
            //field store x.f = a or x.f = const
            else if(lhs instanceof InstanceFieldRef iRef){

                Local baseLocal = (Local)iRef.getBase();
                SootField sf = iRef.getField();
                RealField rf = new RealField(sf);
                Set<AllocationSite> baseSet = state.getPts(baseLocal);

                if(rhs instanceof Local r){
                    
                    Set<AllocationSite> rSet = state.getPts(r);
                    //strong update
                    if(baseSet.size() == 1){
                        AllocationSite a = baseSet.iterator().next();
                        FieldRef fRef = new FieldRef(a, rf);
                        state.heapMap.put(fRef, new HashSet<>(rSet));
                    }
                    //weak update
                    else{
                        for(AllocationSite a: baseSet){
                            FieldRef fRef = new FieldRef(a, rf);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).addAll(rSet);
                        }
                    }
                }
                //constant
                else if(rhs instanceof Constant r){
                    
                    //strong update
                    if(baseSet.size() == 1){
                        AllocationSite a = baseSet.iterator().next();
                        FieldRef fRef = new FieldRef(a, rf);
                        state.heapMap.put(fRef, new HashSet<>(Set.of(AllocationSite.CONST)));
                    }
                    //weak update
                    else{
                        for(AllocationSite a: baseSet){
                            FieldRef fRef = new FieldRef(a, rf);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).add(AllocationSite.CONST);
                        }
                    }
                }
            }
            
            //array fieldload x = a[i]
            else if(rhs instanceof ArrayRef aRef && lhs instanceof Local l){
                Local base = (Local)aRef.getBase();
                Set<AllocationSite> result = new HashSet<>();
                Set<AllocationSite> baseSet = state.getPts(base);

                for(AllocationSite a: baseSet){
                    FieldRef fRef = new FieldRef(a, ArrayField.ARRAY);
                    result.addAll(state.heapMap.getOrDefault(fRef, Collections.emptySet()));
                }
                state.strongUpdate(l, result);
            }
            
            //array field store a[i] = x or a[i] = const
            else if(lhs instanceof ArrayRef aRef){

                Local baseLocal = (Local)aRef.getBase();
                Set<AllocationSite> baseSet = state.getPts(baseLocal);

                if(rhs instanceof Local r){
                    Set<AllocationSite> rSet = state.getPts(r);

                    //strong update
                    if(baseSet.size() == 1){
                        AllocationSite aSite = baseSet.iterator().next();
                        FieldRef fRef = new FieldRef(aSite, ArrayField.ARRAY);
                        state.heapMap.put(fRef,new HashSet<>(rSet));
                    }

                    else{
                        for(AllocationSite a: baseSet){
                            FieldRef fRef = new FieldRef(a, ArrayField.ARRAY);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).addAll(rSet);
                        }
                    }
                }

                //constant
                else if(rhs instanceof Constant r){
                    
                    //strong update
                    if(baseSet.size() == 1){
                        AllocationSite a = baseSet.iterator().next();
                        FieldRef fRef = new FieldRef(a, ArrayField.ARRAY);
                        state.heapMap.put(fRef, new HashSet<>(Set.of(AllocationSite.CONST)));
                    }

                    //weak update
                    else{
                        for(AllocationSite a: baseSet){
                            FieldRef fRef = new FieldRef(a, ArrayField.ARRAY);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).add(AllocationSite.CONST);
                        }
                    }
                }
            }
            
            //static load a = C.x
            else if(lhs instanceof Local l && rhs instanceof StaticFieldRef sRef){
                SootField sf = sRef.getField();
                Set<AllocationSite> rSet = state.staticMap.getOrDefault(sf, Collections.emptySet());
                state.strongUpdate(l, rSet);
            }
            
            //static store C.x = a or C.x = 10
            else if(lhs instanceof StaticFieldRef sRef){

                SootField sf = sRef.getField();

                if(rhs instanceof Local r){
                    Set<AllocationSite> rSet = state.getPts(r);
                    state.staticMap.put(sf, new HashSet<>(rSet));
                }
                //constant
                else if(rhs instanceof Constant r){
                    state.staticMap.put(sf, new HashSet<>(Set.of(AllocationSite.CONST)));
                }
            }
        
            //castExpr
            else if(lhs instanceof Local l && rhs instanceof soot.jimple.CastExpr ce){
                if(ce.getOp() instanceof Local r){
                    state.strongUpdate(l, state.getPts(r));
                }
            }
        }

        //return statement
        else if(stmt instanceof ReturnStmt r){
            Value op = r.getOp();

            if(op instanceof Local l){
                state.returnValues.addAll(state.getPts(l));
            }
            else if(op instanceof Constant){
                state.returnValues.add(AllocationSite.CONST);
            }
        }

        //catch stmt
        else if(stmt instanceof IdentityStmt iStmt){
            Value lhs = iStmt.getLeftOp();
            Value rhs = iStmt.getRightOp();

            if(rhs instanceof soot.jimple.CaughtExceptionRef && lhs instanceof Local l){
                SootClass catchType = null;
                for(Trap trap : sm.getActiveBody().getTraps()){
                    if(trap.getHandlerUnit().equals(stmt)){
                        catchType = trap.getException();
                        break;
                    }
                }

                Set<AllocationSite> caughtPts = new HashSet<>();
                for(Set<AllocationSite> pts : state.stackMap.values()){
                    for(AllocationSite a: pts){
                        if(a.isConstant()) continue;
                        if(catchType == null || isSubType(a.getType(), catchType)){
                            caughtPts.add(a);
                        }
                    }
                }
                state.strongUpdate(l, caughtPts);
            }
        }
        return state;
    }

    private boolean isSubType(SootClass child, SootClass parent) {
        if(child == null || parent == null) return true;
        if(child.equals(parent)) return true;

        SootClass current = child;
        while(current.hasSuperclass()){
            current = current.getSuperclass();
            if(current.equals(parent)) return true;
        }

        for(SootClass iFace : child.getInterfaces()){
            if(iFace.equals(parent)) return true;
        }
        return false;
    }

    Map<Unit, State> runWorkList(SootMethod method, State seed){

        seed.returnValues.clear();
        Body body = method.getActiveBody();
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        PatchingChain<Unit> units = body.getUnits();

        // System.out.println("\n>>> runWorkList: " + method.getSignature());
        // System.out.println(">>> Units in method");
        // for(Unit u: units){
        //     System.out.println(" " + u);
        // }

        Map<Unit, State> outState = new HashMap<>();

        for(Unit u: units){
            outState.put(u, new State(seed.methodContext));
        }

        Unit entry = units.getFirst();
        outState.put(entry, seed.copy());

        Queue<Unit> workList = new LinkedList<>();
        workList.add(entry);

        Set<Unit> visited = new HashSet<>();

        while(!workList.isEmpty()){
            Unit curr = workList.poll();

            // System.out.println("\n >> Processing Unit: " + curr);

            State inState;
            List<Unit> preds = cfg.getPredsOf(curr);

            if(preds.isEmpty()){
                inState = seed.copy();
            }else{
                inState = new State(seed.methodContext);
                for(Unit p: preds){
                    inState.merge(outState.get(p));
                }
            }

            // System.out.println("    inState stack: " + inState.stackMap);

             State newOut = transferFunction(curr, inState.copy(), method);

            // System.out.println("    newOut stack : " + newOut.stackMap);
            // System.out.println("    changed     : " + !newOut.equals(outState.get(curr)));

            State oldOut = outState.get(curr);
            if(!visited.contains(curr)|| !newOut.equals(oldOut)){
                outState.put(curr, newOut);
                visited.add(curr);

                for(Unit succ : cfg.getSuccsOf(curr)){
                    workList.add(succ);
                }
            }
        }
        return outState;
    }

    void propagateToCallers(SootMethod callee, AllocationSiteContext context, State exitState){

        // System.out.println("\n>> propagate to Callers: " + callee.getSignature());
        // System.out.println("    exitState heap: " + exitState.heapMap);
        // System.out.println("    exitState returnValues: " + exitState.returnValues);
        Pair<SootMethod, AllocationSiteContext> calleeKey = new Pair<>(callee, context);
        Set<CallerRecord> callerEntries = callerMap.get(calleeKey);
        if(callerEntries == null) return;

        for(CallerRecord rec : callerEntries){
            Pair<SootMethod, AllocationSiteContext> callerKey = new Pair<>(rec.caller, rec.callercontext);
            State callerState = methodState.get(callerKey);
            if(callerState == null) continue;

            boolean changed = false;

            if(rec.callSite instanceof AssignStmt as && as.getLeftOp() instanceof Local l){
                Set<AllocationSite> rSet = exitState.getReturnValues();
                Set<AllocationSite> old = callerState.getPts(l);
                if(!(old.equals(rSet))){
                    callerState.strongUpdate(l, rSet);
                    changed = true;
                }
            }

            for(var e : exitState.heapMap.entrySet()){
                Set<AllocationSite> pSet = callerState.heapMap.computeIfAbsent(e.getKey(), k-> new HashSet<>());
                if(pSet.addAll(e.getValue())) changed = true;
            }

            for(var e: exitState.staticMap.entrySet()){
                Set<AllocationSite> pSet = callerState.staticMap.computeIfAbsent(e.getKey(), k-> new HashSet<>());
                if(pSet.addAll(e.getValue())) changed = true;
            }

            if(changed) globalWorkList.add(callerKey);
        }

    }

    State buildCalleeEntryState(SootMethod sm, InvokeExpr expr, State callerState, AllocationSite recv){
        State calleeState = new State(callerState.methodContext);
        Body body = sm.getActiveBody();

        //copy heap
        for(var e: callerState.heapMap.entrySet()){
            calleeState.heapMap.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        //copy static
        for(var e: callerState.staticMap.entrySet()){
            calleeState.staticMap.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        if(expr instanceof InstanceInvokeExpr iie){
            Local thisLocal = body.getThisLocal();

            if(recv != null){
                calleeState.strongUpdate(thisLocal, Set.of(recv));
            }
        }

        List<Local> params = body.getParameterLocals();
        for(int i = 0; i < expr.getArgCount(); i++){
            Value arg = expr.getArg(i);
            if(arg instanceof Local l){
                Set<AllocationSite> lSet = callerState.getPts(l);
                //calleeState.stackMap.computeIfAbsent(params.get(i), k-> new HashSet<>()).addAll(lSet);
                calleeState.weakUpdate(params.get(i), lSet);
            }

            //constant
            else if(arg instanceof Constant c){
                // calleeState.stackMap.put(params.get(i), new HashSet<>(Set.of(AllocationSite.CONST)));
                calleeState.strongUpdate(params.get(i), Set.of(AllocationSite.CONST));
            }
        }
        return calleeState;
    }

    List<SootMethod> resolveTargets(InvokeExpr ie, Stmt stmt, State state){
        List<SootMethod> result = new ArrayList<>();

        if(ie instanceof StaticInvokeExpr || ie instanceof SpecialInvokeExpr){
            result.add(ie.getMethod());
            return result;
        }

        if(ie instanceof InstanceInvokeExpr iie){
            Local base = (Local) iie.getBase();
            Set<AllocationSite> baseSet = state.getPts(base);
            //totalCalls++;

            Set<SootMethod> targets = new HashSet<>();

            for(AllocationSite recv : baseSet){

                if(recv.isConstant()) continue;

                SootClass recvClass = recv.getType();
                if(recvClass == null) continue;

                SootMethod concreteMethod = dispatchMethod(recvClass, ie.getMethod().getSubSignature());
                if(concreteMethod != null){
                    targets.add(concreteMethod);
                }
            }

            // System.out.println("\n-----resolveTragets------");
            // System.out.println(" Call    : " + ie.getMethod().getSubSignature());
            // System.out.println(" Base pts: " + baseSet);
            // System.out.println(" Dispatched targets: " + targets);

            if(!targets.isEmpty()){
                // System.out.println(" MONOMORPHIC -> " + targets.iterator().next().getSubSignature());
                if(targets.size() == 1){
                    String key = stmt.toString() + "@" + state.methodContext;

                    if(!monoCalls.containsKey(key)){
                        int lineNum = stmt.getJavaSourceStartLineNumber();
                        String info = "Call Site : " + stmt + "\n" +
                        "Line      : " + (lineNum > 0 ? lineNum : "N/A") + "\n" +
                        "Context   : " + state.methodContext + "\n" +
                        "Receiver  : " + baseSet + "\n" +
                        "Target    : " + targets.iterator().next().getSignature();
                        monoCalls.put(key, info);
                    }
                }
                result.addAll(targets);
                return result;
            }
            // }else if(targets.isEmpty()){
            //     // System.out.println(" NO TARGETS from pts-to -> falling back to CG");
            // }else{
            //     // // System.out.println(" POLYMORPHIC (" + targets.size() + " targets) -> falling back to CG");
            //     // for(SootMethod t: targets){
            //     //     System.out.println("    - " + t.getSubSignature());
            //     // }
            // }

            Iterator<Edge> edges = cg.edgesOutOf(stmt);
            while(edges.hasNext()){
                result.add(edges.next().getTgt().method());
            }
        }
        return result;
    }

    SootMethod dispatchMethod(SootClass sc, String subSignature){
        SootClass current = sc;
        while(current != null){
            if(current.declaresMethod(subSignature)){
                SootMethod m = current.getMethod(subSignature);
                if(!m.isAbstract()){
                    return m;
                }
            }

            for(SootClass iFace : current.getInterfaces()){
                if(iFace.declaresMethod(subSignature)){
                    SootMethod m = iFace.getMethod(subSignature);
                    if(!m.isAbstract()) return m;
                }
            }
            if(!current.hasSuperclass()) break;
            current = current.getSuperclass();
        }
        return null;
    }

    void printAnalysisState(String label, SootMethod method, AllocationSiteContext context, State state){
        System.out.println("\n========================================");
        System.out.println("LABEL   : " + label);
        System.out.println("METHOD  : " + method.getSignature());
        System.out.println("CONTEXT : " + context);
        System.out.println("========================================");

        System.out.println("\n--- Stack (Points-To) ---");
        if(state.stackMap.isEmpty()){
            System.out.println("  (empty)");
        }else{
            for(var e : state.stackMap.entrySet()){
                System.out.println("  " + e.getKey() + " -> " + e.getValue());
            }
        }

        System.out.println("\n--- Heap ---");
        if(state.heapMap.isEmpty()){
            System.out.println("  (empty)");
        }else{
            for(var e : state.heapMap.entrySet()){
                System.out.println("  " + e.getKey() + " -> " + e.getValue());
            }
        }

        System.out.println("\n--- Static Fields ---");
        if(state.staticMap.isEmpty()){
            System.out.println("  (empty)");
        }else{
            for(var e : state.staticMap.entrySet()){
                System.out.println("  " + e.getKey().getName() + " -> " + e.getValue());
            }
        }

        System.out.println("\n--- Return Values ---");
        if(state.returnValues.isEmpty()){
            System.out.println("  (empty)");
        }else{
            System.out.println("  " + state.returnValues);
        }

        System.out.println("========================================\n");
    }

    State applyLibrarySummary(Stmt stmt, InvokeExpr ie, State state){

        SootMethod sm = ie.getMethod();
        String className = sm.getDeclaringClass().getName();
        String methodName = sm.getName();

        if(className.equals("java.util.ArrayList")){
            if(ie instanceof InstanceInvokeExpr iie){
                Local base = (Local)iie.getBase();
                Set<AllocationSite> baseSet = state.getPts(base);

                if((methodName.equals("add") || methodName.equals("set")) && ie.getArgCount() >= 1){
                    Value lastArg = ie.getArg(ie.getArgCount()-1);
                    if(lastArg instanceof Local arg){
                        Set<AllocationSite> argSet = state.getPts(arg);
                        for(AllocationSite a: baseSet){
                            FieldRef fRef = new FieldRef(a, ArrayField.ARRAY);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).addAll(argSet);
                        }
                    }
                }

                else if(methodName.equals("get") || methodName.equals("remove")){
                    if(stmt instanceof AssignStmt aStmt && aStmt.getLeftOp() instanceof Local l){
                        Set<AllocationSite> result = new HashSet<>();
                        for(AllocationSite a: baseSet){
                            FieldRef fRef = new FieldRef(a, ArrayField.ARRAY);
                            result.addAll(state.heapMap.getOrDefault(fRef, Collections.emptySet()));
                        }
                        state.strongUpdate(l, result);
                    }
                }

                else if(methodName.equals("clear")){
                    if(baseSet.size() == 1){
                        AllocationSite recv = baseSet.iterator().next();
                        FieldRef fRef = new FieldRef(recv, ArrayField.ARRAY);
                        state.heapMap.put(fRef, new HashSet<>());
                    }
                }
            }
            return state;
        }

        else if(className.equals("java.util.HashMap")){
            if(ie instanceof InstanceInvokeExpr iie){
                Local base = (Local)iie.getBase();
                Set<AllocationSite> baseSet = state.getPts(base);

                if(methodName.equals("put") && ie.getArgCount() == 2){
                    Value keyArg = ie.getArg(0);
                    Value valArg = ie.getArg(1);

                    if(valArg instanceof Local valLocal){
                        Set<AllocationSite> valSet = state.getPts(valLocal);
                        Set<AllocationSite> keySet = keyArg instanceof Local kl ? state.getPts(kl) : Set.of(AllocationSite.CONST);

                        for(AllocationSite recv: baseSet){
                            for(AllocationSite key : keySet){
                                FieldRef fRef = new FieldRef(recv, new KeyField(key));
                                state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).addAll(valSet);
                            }
                        }
                    }
                }

                else if(methodName.equals("get")){
                    if(stmt instanceof AssignStmt aStmt && aStmt.getLeftOp() instanceof Local l){
                        Value keyArg = ie.getArg(0);
                        Set<AllocationSite> keySet = keyArg instanceof Local kl ? state.getPts(kl) : Set.of(AllocationSite.CONST);
                        Set<AllocationSite> result = new HashSet<>();

                        for(AllocationSite recv: baseSet){
                            for(AllocationSite key : keySet){
                                FieldRef fRef = new FieldRef(recv, new KeyField(key));
                                result.addAll(state.heapMap.getOrDefault(fRef, Collections.emptySet()));
                            }
                        }
                        state.strongUpdate(l, result);
                    }
                }

                else if(methodName.equals("remove") && ie.getArgCount() == 1){
                    Value keyArg = ie.getArg(0);
                    Set<AllocationSite> keySet = keyArg instanceof Local kl ? state.getPts(kl) : Set.of(AllocationSite.CONST);

                    for(AllocationSite recv : baseSet){
                        if(baseSet.size() == 1){
                            for(AllocationSite key : keySet){
                                FieldRef fRef = new FieldRef(recv,new KeyField(key));
                                state.heapMap.remove(fRef);
                            }
                        }
                    }
                }

                else if(methodName.equals("clear")){
                    if(baseSet.size() == 1){
                        AllocationSite recv = baseSet.iterator().next();
                        state.heapMap.keySet().removeIf(fRef -> fRef.base.equals(recv) && fRef.field instanceof KeyField);
                    }
                }
            }
            return state;
        }

        else if(className.equals("java.util.LinkedList")){
            if(ie instanceof InstanceInvokeExpr iie){
                Local base = (Local)iie.getBase();
                Set<AllocationSite> baseSet = state.getPts(base);

                //write methods
                Set<String> writeMethods = Set.of("add", "addFirst", "addLast","offer", "offerFirst", "offerLast","push", "set");

                //read methods
                Set<String> readMethods = Set.of("get","getFirst", "getLast", "peek", "peekFirst", "peekLast", "poll", "pollFirst", "pollLast", "pop", "remove", "removeFirst", "removeLast");

                if(writeMethods.contains(methodName) && ie.getArgCount() >= 1){
                    Value lastArg = ie.getArg(ie.getArgCount()-1);
                    if(lastArg instanceof Local argLocal){
                        Set<AllocationSite> argSet = state.getPts(argLocal);
                        for(AllocationSite recv : baseSet){
                            FieldRef fRef = new FieldRef(recv, ArrayField.ARRAY);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).addAll(argSet);
                        }
                    }
                }

                else if(readMethods.contains(methodName)){
                    if(stmt instanceof AssignStmt aStmt && aStmt.getLeftOp() instanceof Local l){
                        Set<AllocationSite> result = new HashSet<>();
                        for(AllocationSite recv : baseSet){
                            FieldRef fRef = new FieldRef(recv,ArrayField.ARRAY);
                            result.addAll(state.heapMap.getOrDefault(fRef,Collections.emptySet()));
                        }
                        state.strongUpdate(l, result);
                    }
                }

                else if(methodName.equals("clear") && baseSet.size() == 1){
                    AllocationSite recv = baseSet.iterator().next();
                    FieldRef fRef = new FieldRef(recv, ArrayField.ARRAY);
                    state.heapMap.put(fRef, new HashSet<>());
                }
            }
        }
        return state;
    }

    void printFinalMainState(State state, SootMethod method, AllocationSiteContext context){
        System.out.println("\n================ FINAL MAIN STATE ================");
        System.out.println("METHOD  : " + method.getSignature());
        System.out.println("CONTEXT : " + context);
        System.out.println("=================================================\n");

        System.out.println("--- Final Stack (Points-To) ---");
        for(var e : state.stackMap.entrySet()){
            if(!e.getKey().getName().startsWith("$")){
                System.out.println("  " + e.getKey() + " -> " + e.getValue());
            }
        }

        System.out.println("\n--- Final Heap ---");
        if(state.heapMap.isEmpty()){
            System.out.println("  (empty)");
        }else{
            for(var e : state.heapMap.entrySet()){
                System.out.println("  " + e.getKey() + " -> " + e.getValue());
            }
        }

        System.out.println("\n--- Final Static Fields ---");
        if(state.staticMap.isEmpty()){
            System.out.println("  (empty)");
        }else{
            for(var e : state.staticMap.entrySet()){
                System.out.println("  " + e.getKey().getName() + " -> " + e.getValue());
            }
        }

        System.out.println("=================================================\n");
    }
}