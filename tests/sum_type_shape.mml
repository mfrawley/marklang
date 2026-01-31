# Test: Sum type with different payload types
type shape =
  | Circle of double
  | Square of double

let s = Circle 5.0;
match s with
| Circle radius -> radius == 5.0
| Square side -> false
