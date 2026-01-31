# Test: Result type as userspace definition
type result<ok, err> =
  | Ok of ok
  | Error of err

let r = Ok 42;
match r with
| Ok value -> value == 42
| Error msg -> false
