#!/bin/bash

COMPILER_CP="target/classes:$HOME/.m2/repository/org/ow2/asm/asm/9.6/asm-9.6.jar:$HOME/.m2/repository/org/ow2/asm/asm-util/9.6/asm-util-9.6.jar"
PASSED=0
FAILED=0
TOTAL=0

echo "Running MiniML tests..."
echo "======================"

for test_file in tests/*.ml; do
    TOTAL=$((TOTAL + 1))
    test_name=$(basename "$test_file" .ml)
    
    expected=$(grep "# Expected:" "$test_file" | sed 's/# Expected: //')
    
    if [ -z "$expected" ]; then
        echo "❌ $test_name: No expected output specified"
        FAILED=$((FAILED + 1))
        continue
    fi
    
    class_name="${test_name}"
    
    java -cp "$COMPILER_CP" com.miniml.Main "$test_file" > /dev/null 2>&1
    
    if [ $? -ne 0 ]; then
        echo "❌ $test_name: Compilation failed"
        FAILED=$((FAILED + 1))
        continue
    fi
    
    actual=$(java "$class_name" 2>&1 | head -n 1)
    
    rm -f "${class_name}.class"
    
    if [ "$actual" = "$expected" ]; then
        echo "✅ $test_name"
        PASSED=$((PASSED + 1))
    else
        echo "❌ $test_name: Expected '$expected', got '$actual'"
        FAILED=$((FAILED + 1))
    fi
done

echo "======================"
echo "Results: $PASSED/$TOTAL passed, $FAILED failed"

if [ $FAILED -eq 0 ]; then
    exit 0
else
    exit 1
fi
