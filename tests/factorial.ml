# Test: Recursive factorial function
# Expected: 120
fn factorial n =
  if n <= 1 then 1
  else n * factorial (n - 1)
in
factorial 5
