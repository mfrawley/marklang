#!/bin/bash

COMPILER_CP="target/classes:$HOME/.m2/repository/org/ow2/asm/asm/9.6/asm-9.6.jar:$HOME/.m2/repository/org/ow2/asm/asm-util/9.6/asm-util-9.6.jar"
PASSED=0
FAILED=0
TOTAL=0

echo "Running MiniML tests..."
echo "======================"

# First compile any modules in tests/modules/
if [ -d "tests/modules" ]; then
    java -cp "$COMPILER_CP" com.miniml.Main tests/modules/utils.mml > /dev/null 2>&1
fi

for test_file in tests/*.mml; do
    # Skip utils.mml as it's a dependency module
    if [[ "$test_file" == "tests/modules/utils.mml" ]]; then
        continue
    fi
    TOTAL=$((TOTAL + 1))
    test_name=$(basename "$test_file" .mml)
    
    # Check if this is a new-style boolean test (no # Expected: line)
    expected=$(grep "# Expected:" "$test_file" | sed 's/# Expected: //')
    is_bool_test=false
    
    if [ -z "$expected" ]; then
        # Assume it's a boolean test that should output "true"
        expected="true"
        is_bool_test=true
    fi
    
    # Convert to PascalCase (same logic as Main.java)
    class_name=""
    capitalize_next=true
    for (( i=0; i<${#test_name}; i++ )); do
        char="${test_name:$i:1}"
        if [ "$char" = "_" ]; then
            capitalize_next=true
        else
            if [ "$capitalize_next" = true ]; then
                upper_char=$(echo "$char" | tr '[:lower:]' '[:upper:]')
                class_name="${class_name}${upper_char}"
                capitalize_next=false
            else
                class_name="${class_name}${char}"
            fi
        fi
    done
    
    compile_output=$(java -cp "$COMPILER_CP" com.miniml.Main "$test_file" 2>&1)
    compile_exit_code=$?
    
    # Check if this test expects a compilation error
    if grep -q "# Expected: Type error" "$test_file" || grep -q "# Expected: Compilation error" "$test_file"; then
        if [ $compile_exit_code -ne 0 ]; then
            echo "✅ $test_name (expected compilation failure)"
            PASSED=$((PASSED + 1))
        else
            echo "❌ $test_name: Expected compilation to fail, but it succeeded"
            FAILED=$((FAILED + 1))
        fi
        continue
    fi
    
    if [ $compile_exit_code -ne 0 ]; then
        echo "❌ $test_name: Compilation failed"
        echo "   Error: $compile_output"
        FAILED=$((FAILED + 1))
        continue
    fi
    
    actual=$(java -cp target:target/classes:tests/modules "$class_name" 2>&1 | head -n 1)
    
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
