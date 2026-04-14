class Data {}
class DataA extends Data {}
class DataB extends Data {}

class Box {
    Data item;
    
    void put(Data i) { 
        this.item = i; 
    }
    
    Data get() { 
        return this.item; 
    }
}

class Builder {
    Box b;

    Builder() {
        // L1: The Allocation Site of the Box
        this.b = new Box(); 
    }

    void build(Data val) {
        b.put(val);
    }

    Data retrieve() {
        return b.get();
    }
}

public class Test {
    public static void main(String[] args) {
        // L2: First Builder allocation
        Builder builder1 = new Builder(); 
        // L3: DataA allocation
        builder1.build(new DataA());      
        Data res1 = builder1.retrieve();

        // L4: Second Builder allocation
        Builder builder2 = new Builder(); 
        // L5: DataB allocation
        builder2.build(new DataB());      
        Data res2 = builder2.retrieve();
    }
}