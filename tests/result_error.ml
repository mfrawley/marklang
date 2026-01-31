# Test: Create Error result
let r = Error "failed";
match r with
| Ok x -> false
| Error e -> true
