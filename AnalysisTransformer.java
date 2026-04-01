import soot.*;
import soot.jimple.AnyNewExpr;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;

import java.util.*;

class AllocationSite{
    private final Unit allocationSite;
    private final int lineNumber;
    private final SootClass type;
    private final boolean isConstant;

    AllocationSite(Unit allocationSite, int lineNumber, SootClass type){
        this.allocationSite = allocationSite;
        this.lineNumber = lineNumber;
        this.type = type;
        this.isConstant = false;
    }

    private AllocationSite(boolean isConstant){
        this.isConstant = isConstant;
        this.allocationSite = null;
        this.type = null;
        this.lineNumber = -1;
    }

    public static final AllocationSite CONST = new AllocationSite(true);

    public SootClass getType(){
        return this.type;
    }

    public boolean isConstant(){
        return this.isConstant;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj){
            return true;
        }
        if(!(obj instanceof AllocationSite)) return false;
        AllocationSite other = (AllocationSite)obj;

        if(this.isConstant || other.isConstant){
            return this.isConstant && other.isConstant;
        }
        return Objects.equals(this.allocationSite, other.allocationSite) && Objects.equals(this.type, other.type);
    }

    @Override
    public int hashCode(){
        if(this.isConstant) return 1;
        return Objects.hash(this.allocationSite,this.type);
    }

    @Override
    public String toString(){
        if(isConstant) return "CONST";
        return "Obj(" + (type != null ? type.getName() : "?") + ", L" + lineNumber + ")";
    }
}

class FieldRef{
    final AllocationSite base;
    final SootField field;

    FieldRef(AllocationSite base, SootField field){
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
        return base + "." + field.getName();
    }
}

class State{

    Map<Local, Set<AllocationSite>> stackMap;
    Map<FieldRef, Set<AllocationSite>> heapMap;
    Map<SootField, Set<AllocationSite>> staticMap;
    
    State(){
        this.stackMap = new HashMap<>();
        this.heapMap = new HashMap<>();
        this.staticMap = new HashMap<>();
    }

    //copy helper
    State(State other){

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
    }

    State copy(){
        return new State(this);
    }

    public boolean merge(State other){
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

        return changed;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if(!(obj instanceof State)) return false;

        State other = (State)obj;
        return Objects.equals(this.stackMap, other.stackMap) && Objects.equals(this.heapMap, other.heapMap) && Objects.equals(this.staticMap, other.staticMap);
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.stackMap, this.heapMap, this.staticMap);
    }
}

public class AnalysisTransformer extends BodyTransformer{
    @Override
    public void internalTransform(Body body,String phaseName, Map<String,String> options){

    }

    public void transferFunction(State state, Unit unit){

        Stmt stmt = (Stmt)unit;

        if(stmt.containsInvokeExpr()){

            //a = foo() or a = x.foo()
            if(stmt instanceof AssignStmt){

            }
            //foo() or foo(parameters)
            else{

            }
        }

        //assign stmt ==
        else if(stmt instanceof AssignStmt aStmt){
            Value lhs = aStmt.getLeftOp();
            Value rhs = aStmt.getRightOp();

            //alloc statement
            if(rhs instanceof AnyNewExpr r && lhs instanceof Local l){
                Type t = r.getType();

                if(t instanceof RefType refType){
                    SootClass sc = refType.getSootClass();
                    int lineNumber = stmt.getJavaSourceStartLineNumber();
                    Unit u = (Unit)stmt;
                    AllocationSite aSite = new AllocationSite(u, lineNumber, sc);
                    state.stackMap.put(l,new HashSet<>(Set.of(aSite)));
                }

                else if(t instanceof ArrayType){

                }
            }

            //copy stmt
            else if(rhs instanceof Local r && lhs instanceof Local l){
                Set<AllocationSite> pSet = state.stackMap.computeIfAbsent(r, k-> new HashSet<>());
                state.stackMap.put(r, pSet);
            }

            //load a = x.f
            else if(rhs instanceof InstanceFieldRef iRef && lhs instanceof Local l){
                Local base = (Local)iRef.getBase();
                SootField sf = iRef.getField();
                Set<AllocationSite> basePSet = state.stackMap.getOrDefault(base, Collections.emptySet());
                Set<AllocationSite> lSet = state.stackMap.computeIfAbsent(l, k-> new HashSet<>());
                lSet.clear();
                for(AllocationSite a: basePSet){
                    FieldRef fRef = new FieldRef(a, sf);
                    Set<AllocationSite> sfMap = state.heapMap.getOrDefault(fRef,Collections.emptySet());
                    lSet.addAll(sfMap);
                }
            }

            //store stmt x.f = a or x.f = const
            else if(lhs instanceof InstanceFieldRef iRef){
                Local base = (Local)iRef.getBase();
                SootField sf = iRef.getField();
                Set<AllocationSite> basePts = state.stackMap.getOrDefault(base, Collections.emptySet());
                
                if(rhs instanceof Local r){
                    Set<AllocationSite> rSet = state.stackMap.getOrDefault(r, Collections.emptySet());
                    if(basePts.size() == 1){
                        AllocationSite only = basePts.iterator().next();
                        FieldRef fRef = new FieldRef(only, sf);
                        state.heapMap.put(fRef, new HashSet<>(rSet));
                    }else{
                        for(AllocationSite obj: basePts){
                            FieldRef fRef = new FieldRef(obj, sf);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).addAll(rSet);
                        }
                    }
                }

                else if(rhs instanceof Constant){
                    AllocationSite aSite = AllocationSite.CONST;
                    if(basePts.size() == 1){
                        AllocationSite only = basePts.iterator().next();
                        FieldRef fRef = new FieldRef(only,sf);
                        state.heapMap.put(fRef, new HashSet<>(Set.of(aSite)));
                    }else{
                        for(AllocationSite obj: basePts){
                            FieldRef fRef = new FieldRef(obj, sf);
                            state.heapMap.computeIfAbsent(fRef, k-> new HashSet<>()).add(aSite);
                        }
                    }
                }
            }

            //static load a = C.x
            else if(rhs instanceof StaticFieldRef sRef && lhs instanceof Local l){
                SootField sf = sRef.getField();
                Set<AllocationSite> fieldPts = state.staticMap.getOrDefault(sf, Collections.emptySet());
                state.stackMap.put(l, new HashSet<>(fieldPts));
            }

            //static store C.x = a or C.x = const
            else if(lhs instanceof StaticFieldRef sRef){
                SootField sf = sRef.getField();

                if(rhs instanceof Local r){
                    Set<AllocationSite> rSet = state.stackMap.getOrDefault(r, Collections.emptySet());
                    state.staticMap.put(sf,new HashSet<>(rSet));
                }
                else if(rhs instanceof Constant){
                    AllocationSite aSite = AllocationSite.CONST;
                    state.staticMap.put(sf, new HashSet<>(Set.of(aSite)));
                }
            }
        }

        //return stmt
        else if(stmt instanceof ReturnStmt){

        }
    }
    
}