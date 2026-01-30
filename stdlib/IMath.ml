fn abs x = if x < 0 then 0 - x else x;

fn max a b = if a > b then a else b;

fn min a b = if a < b then a else b;

fn square x = x * x;

fn pow x n = if n < 1 then 1 else x * pow x (n - 1);

let _test1 = abs 5 in
let _test2 = max 10 20 in
let _test3 = min 10 20 in
let _test4 = square 5 in
0
