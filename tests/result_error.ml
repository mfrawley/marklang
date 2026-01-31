# Test: Create Error result
type result<ok, err> =
  | Ok of ok
  | Error of err

let r = Error "failed";
match r with
| Ok x -> false
| Error e -> true
