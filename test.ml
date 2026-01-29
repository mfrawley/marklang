# Test various Java static methods
let abs_val = java_call "java.lang.Math" "abs" (0 - 10) in
let max_val = java_call "java.lang.Math" "max" 5 10 in
let min_val = java_call "java.lang.Math" "min" 3 7 in
print "abs(-10)={abs_val}, max(5,10)={max_val}, min(3,7)={min_val}"
