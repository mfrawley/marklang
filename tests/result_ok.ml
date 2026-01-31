# Test: Create Ok result
let r = Ok 42;
match r with
| Ok x -> x == 42
| Error e -> false
