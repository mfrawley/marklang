# Test: Math.sqrt
let result = java_call "java.lang.Math" "sqrt" 25.0 in
let diff = result - 5.0 in
let abs_diff = if diff < 0.0 then 0.0 - diff else diff in
abs_diff < 0.00001
