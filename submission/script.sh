#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"
TESTS_DIR="$SCRIPT_DIR/tests"
SOOT_JAR="$SRC_DIR/soot-4.6.0-jar-with-dependencies.jar"
SRC_CP="$SRC_DIR:$SOOT_JAR"

# ─── 1. Clean ────────────────────────────────────────────────────────────────
echo "=== Cleaning .class files ==="
find "$SRC_DIR" -name "*.class" -delete 2>/dev/null || true

# ─── 2. Compile src/ ─────────────────────────────────────────────────────────
echo "=== Compiling src/ ==="
javac -cp "$SOOT_JAR" \
  "$SRC_DIR/AnalysisTransformer.java" \
  "$SRC_DIR/Transformation.java" \
  "$SRC_DIR/Main.java" \
  -d "$SRC_DIR"
echo "Compilation successful."
echo ""

# ─── helper: run Test N times under -Xint, return avg ms ─────────────────────
avg_time() {
    local cp="$1"
    local runs="$2"
    local total=0
    local output=""
    for ((r=1; r<=runs; r++)); do
        output=$(java -Xint -cp "$cp" Test 2>/dev/null)
        ms=$(echo "$output" | grep -oP '(?<=Time\(ms\): )\d+' || echo 0)
        total=$((total + ms))
    done
    echo $((total / runs))
    # Print output from last run for correctness check
    echo "$output" >&3
}

# ─── 3. Testcases ────────────────────────────────────────────────────────────
RUNS=3

for i in $(seq 1 10); do
    TEST="Test$i"
    echo "============================================================"
    echo "  $TEST"
    echo "============================================================"

    BUILD_DIR="$TESTS_DIR/${TEST}_build"
    OUT_DIR="$TESTS_DIR/${TEST}_out"
    mkdir -p "$BUILD_DIR" "$OUT_DIR"

    # Copy testcase as Test.java (public class Test must match filename)
    cp "$TESTS_DIR/${TEST}.java" "$BUILD_DIR/Test.java"
    javac -d "$BUILD_DIR" "$BUILD_DIR/Test.java" 2>/dev/null

    # --- BEFORE ---
    echo "--- BEFORE (original) ---"
    BEFORE_TOTAL=0
    LAST_OUT=""
    for ((r=1; r<=RUNS; r++)); do
        LAST_OUT=$(java -Xint -cp "$BUILD_DIR" Test 2>/dev/null)
        MS=$(echo "$LAST_OUT" | grep -oP '(?<=Time\(ms\): )\d+' || echo 0)
        BEFORE_TOTAL=$((BEFORE_TOTAL + MS))
    done
    BEFORE_AVG=$((BEFORE_TOTAL / RUNS))
    echo "$LAST_OUT"
    echo "Avg wall-clock (${RUNS} runs): ${BEFORE_AVG} ms"
    echo ""

    # --- TRANSFORM ---
    echo "--- Applying transformation ---"
    TRANSFORM_LOG=$(java -cp "$SRC_CP" Main "$TEST" "$BUILD_DIR" "$OUT_DIR" 2>&1 | grep -E "\[TRANSFORMATION\]|\[WRAPPER\]" || true)
    DEVIRT_COUNT=$(echo "$TRANSFORM_LOG" | grep -c "\[TRANSFORMATION\].*devirtualized" || true)
    echo "$TRANSFORM_LOG"
    echo ""

    # --- AFTER ---
    echo "--- AFTER (transformed) ---"
    AFTER_TOTAL=0
    LAST_OUT_A=""
    for ((r=1; r<=RUNS; r++)); do
        LAST_OUT_A=$(java -Xint -cp "$OUT_DIR" Test 2>/dev/null)
        MS=$(echo "$LAST_OUT_A" | grep -oP '(?<=Time\(ms\): )\d+' || echo 0)
        AFTER_TOTAL=$((AFTER_TOTAL + MS))
    done
    AFTER_AVG=$((AFTER_TOTAL / RUNS))
    echo "$LAST_OUT_A"
    echo "Avg wall-clock (${RUNS} runs): ${AFTER_AVG} ms"
    echo ""

    # --- STATS ---
    if [ "$BEFORE_AVG" -gt 0 ] && [ "$AFTER_AVG" -gt 0 ]; then
        DIFF=$((BEFORE_AVG - AFTER_AVG))
        PCT=$(echo "scale=1; ($DIFF * 100) / $BEFORE_AVG" | bc 2>/dev/null || echo "N/A")
        echo ">>> Before: ${BEFORE_AVG} ms | After: ${AFTER_AVG} ms | Improvement: ${DIFF} ms (${PCT}%)"
    else
        echo ">>> Before: ${BEFORE_AVG} ms | After: ${AFTER_AVG} ms"
    fi
    echo ""

done

echo "=== All testcases complete ==="
