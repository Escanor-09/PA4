// Test10: Virtual call on an object retrieved from a HashMap via a known key.
// The map stores Compressor objects keyed by string constants.
// Your analysis models HashMap with KeyField; if key points-to is tracked precisely,
// the retrieved value's type is known -> monomorphic dispatch on compress().
// Expected output: compressed lengths printed, Time shown.

import java.util.HashMap;

interface Compressor {
    int compress(int data);
}

class RLECompressor implements Compressor {
    @Override
    public int compress(int data) { return data / 2; } // dummy RLE
}

class HuffCompressor implements Compressor {
    @Override
    public int compress(int data) { return data / 3; } // dummy Huffman
}

public class Test10 {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        HashMap<String, Compressor> registry = new HashMap<>();

        String rleKey = "rle";
        String huffKey = "huff";

        registry.put(rleKey, new RLECompressor());   // key -> RLECompressor
        registry.put(huffKey, new HuffCompressor()); // key -> HuffCompressor

        long rleTotal = 0, huffTotal = 0;
        for (int i = 0; i < 100000; i++) {
            Compressor c1 = (Compressor) registry.get(rleKey);  // known key -> RLECompressor
            Compressor c2 = (Compressor) registry.get(huffKey); // known key -> HuffCompressor
            rleTotal  += c1.compress(900); // virtual call — should be monomorphic (RLE)
            huffTotal += c2.compress(900); // virtual call — should be monomorphic (Huff)
        }
        long end = System.currentTimeMillis();
        System.out.println("RLE total: "  + rleTotal);  // 45000000
        System.out.println("Huff total: " + huffTotal); // 30000000
        System.out.println("Time(ms): " + (end - start));
    }
}
