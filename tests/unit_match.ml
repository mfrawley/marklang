# Test: Unit in match expression
let result = match [1, 2, 3] with
| [] -> ()
| h :: t -> ();
result == ()
