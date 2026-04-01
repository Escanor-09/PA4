# PA4: 1-Week Soot Monomorphization Workflow (2type+1H)

## Day 1: Infrastructure & Soot Boilerplate
* Goal: Get Soot to run, parse your test code into Jimple, and output class files without crashing. [cite: 11, 25]
* Setup: Create your project structure (src/, tests/) and set up your build system with the Soot dependency. [cite: 38, 39]
* The Entry Point: Write Main.java to parse command-line arguments and initialize Soot. [cite: 44, 46]
* Soot Initialization: Write the boilerplate to call G.reset(), set the Soot classpath, and configure it to process directories.
* The Transformer Hook: Create a class extending SceneTransformer (for the whole-program analysis) and another extending BodyTransformer (for the method-level transformation). Inject these into Soot's PackManager.

## Day 2: The 2type+1H Data Structures
* Goal: Translate the paper's math into Java classes.
* Context Classes: Implement MethodContext (holding two SootClass types) and HeapContext (holding the allocation Unit and one SootClass type). [cite: 471] Ensure equals() and hashCode() are perfectly implemented.
* The Maps: Instantiate your global pointsTo map, fieldPointsTo map, and callGraph map.
* The Helper Functions: Implement the crucial T(l) function, which takes an allocation site and returns the class containing that instruction (the allocator's type). [cite: 480, 481, 482, 505]

## Day 3: Intraprocedural Pointer Analysis (The Worklist)
* Goal: Track object flow within a single method.
* Worklist Setup: Initialize a queue. Start by adding the entry point method under a dummy MethodContext.
* Processing Allocations: When your worklist pops an assignment like `a = new Obj()`, implement the record function. [cite: 483] Create a HeapContext using the current instruction and the first type from the active MethodContext. [cite: 484]
* Processing Assignments: Handle `a = b` by unioning b's abstract objects into a's set.
* Processing Fields: Handle `a.f = b` and `a = b.f` by reading/writing to your fieldPointsTo map using the base object's HeapContext.

## Day 4: Interprocedural Extension (Crossing Method Boundaries)
* Goal: Connect caller and callee to build the Context-Sensitive Call Graph.
* Detecting Calls: When the worklist pops a virtual call, retrieve the HeapContext set for the receiver.
* The Context Shift: For each abstract object, use its dynamic type to resolve the target method. Apply the merge function: the new context's first type is T(l'), and the second type is the one stored in the receiver's HeapContext. [cite: 483, 484]
* Argument Passing: Map the caller's arguments to the target's parameters under the newly created MethodContext.
* Graph Building: Add the edge to your global callGraph map and push the target method's body onto the worklist.

## Day 5: The Oracle (Identifying Monomorphization Targets)
* Goal: Find exactly where the optimization is mathematically safe.
* Call Graph Traversal: Write a separate phase that runs after your worklist is completely empty. Iterate through all the keys in your callGraph map.
* The Condition: Look for any (MethodContext, CallSite) pair where the Set of target methods has a size of exactly 1. [cite: 104]
* Tagging: Create a registry that marks specific VirtualInvokeExpr units in specific methods as safe to monomorphize.

## Day 6: Jimple Transformation (Method Cloning)
* Goal: Generate the static versions of the target methods. [cite: 105]
* The BodyTransformer: Trigger your method-level transformer.
* Method Duplication: For every target method identified on Day 5, clone the SootMethod.
* Signature Modification: Remove the VIRTUAL modifier and add STATIC. Add the receiver's type as the new first parameter at index 0. [cite: 106]
* Identity Patching: Inside the cloned Jimple body, change the IdentityStmt assigning @this to read from @parameter0 instead. [cite: 106] Shift all other parameters up by 1. Add the new method to its declaring class.

## Day 7: Jimple Transformation (Call Site Patching) & Compilation
* Goal: Swap the instructions and output valid .class files.
* Patching the Call: Iterate through the Jimple instructions. When you hit a tagged VirtualInvokeExpr, extract its arguments.
* Creating the Static Call: Construct a new StaticInvokeExpr targeting your Day 6 cloned method. Pass the receiver as an explicit extra argument. [cite: 106]
* Swapping the Unit: Replace the old InvokeStmt or AssignStmt with the new one using Soot's UnitPatchingChain.swapWith() method.
* Validation: Run the pipeline on a dummy Java file and check the sootOutput/ directory. Run the output with standard java (using the -Xint flag to disable JIT) to ensure the bytecode verifier accepts it. [cite: 32]