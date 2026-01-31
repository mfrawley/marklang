# Test: Math.exp
let result = java_call "java.lang.Math" "exp" 1.0 in
let diff = result - 2.718281828459045 in
let abs_diff = if diff < 0.0 then 0.0 - diff else diff in
abs_diff < 0.00001
