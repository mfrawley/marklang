# Test: Create Ok result
type result<ok, err> =
  | Ok of ok
  | Error of err

let r = Ok 42;
match r with
| Ok x -> x == 42
| Error e -> false
