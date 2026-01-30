# Test: Match and extract head
# Expected: 1
match [1, 2, 3] with
| [] -> 0
| h :: t -> h
