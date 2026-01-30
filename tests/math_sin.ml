# Test: Math.sin
let result = java_call "java.lang.Math" "sin" 1.5707963267948966 in
let diff = result - 1.0 in
let abs_diff = if diff < 0.0 then 0.0 - diff else diff in
abs_diff < 0.00001
