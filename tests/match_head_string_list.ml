# Test: Match and extract head on a string list
# Expected: a
let l = ["a","b"];

match l with
| [] -> ""
| h :: t -> h
