# Test: Math.tan
let result = java_call "java.lang.Math" "tan" 0.7853981633974483 in
let diff = result - 1.0 in
let abs_diff = if diff < 0.0 then 0.0 - diff else diff in
abs_diff < 0.001
