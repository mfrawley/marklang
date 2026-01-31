# Test: Division with Result
let r1 = Ok 5 in
let v1 = match r1 with
| Ok x -> x
| Error e -> 0 in
v1 == 5
