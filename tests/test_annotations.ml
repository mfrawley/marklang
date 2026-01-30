fn abs (x: double) = if x < 0.0 then 0.0 - x else x;

let a = abs 3.14 in
let b = abs (0.0 - 5.5) in
print a
