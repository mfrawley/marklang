# Test: Division with Result
let divide x y =
  if y == 0 then Error "division by zero"
  else Ok (x / y);

let r1 = divide 10 2;
let r2 = divide 10 0;

let v1 = match r1 with
| Ok x -> x
| Error e -> -1;

let v2 = match r2 with
| Ok x -> x
| Error e -> -1;

v1 == 5 && v2 == -1
