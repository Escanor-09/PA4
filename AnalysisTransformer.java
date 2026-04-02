import soot.*;
import soot.jimple.AnyNewExpr;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeStmt;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;

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
            lines.add(u.getJavaSourceStartLineNumber());
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
        return Objects.equals(this.allocationSite, other.allocationSite) && Objects.equals(this.type, other.type) && Objects.equals(this.heapContext, other.heapContext);
    }

    @Override
    public int hashCode(){
        if(this.isConstant) return Objects.hash(tag);
        return Objects.hash(this.allocationSite,this.type, this.heapContext);
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
    AllocationSiteContext methodContext;
    
    State(AllocationSiteContext methodContext){
        this.stackMap = new HashMap<>();
        this.heapMap = new HashMap<>();
        this.staticMap = new HashMap<>();
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

        return changed;
    }

    //helper function to get Points-to info
    Set<AllocationSite> getPts(Local l){
        return this.stackMap.getOrDefault(l, Collections.emptySet());
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
        return Objects.equals(this.stackMap, other.stackMap) && Objects.equals(this.heapMap, other.heapMap) && Objects.equals(this.staticMap, other.staticMap) && Objects.equals(this.methodContext, other.methodContext);
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.stackMap, this.heapMap, this.staticMap, this.methodContext);
    }
}

public class AnalysisTransformer extends SceneTransformer{

    static Map<Pair<SootMethod,AllocationSiteContext>, State> methodState;

    @Override
    public void internalTransform(String phaseName, Map<String,String> options){

    }

    private State transferFunction(Unit unit, State state, SootMethod sm){
        Stmt stmt = (Stmt)unit;

        if(stmt.containsInvokeExpr()){

        }


        else if(stmt instanceof AssignStmt aStmt){

            Value lhs = aStmt.getLeftOp();
            Value rhs = aStmt.getRightOp();

            //alloc
            if(rhs instanceof NewExpr r && lhs instanceof Local l){
                RefType objType = r.getBaseType();
                SootClass sc = objType.getSootClass();
                AllocationSiteContext context = state.methodContext;
                AllocationSite aSite = new AllocationSite(unit, sc, context);
                state.strongUpdate(l, new HashSet<>(Set.of(aSite)));
            }

            else if(rhs instanceof NewArrayExpr r && lhs instanceof Local l){
                
            }
        }

        else if(stmt instanceof ReturnStmt){

        }
        return state;
    }
}