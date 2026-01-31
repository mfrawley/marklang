# Test: Simple sum type without type parameters
type color =
  | Red
  | Green
  | Blue

let c = Red;
match c with
| Red -> true
| Green -> false
| Blue -> false
