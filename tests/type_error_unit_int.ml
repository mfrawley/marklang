# Test: Type error - unit vs int in match
# Expected: Type error
match [1, 2, 3] with
| [] -> ()
| h :: t -> h
