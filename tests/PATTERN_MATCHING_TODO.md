# Pattern Matching Tests - TODO

The following test cases need to be added once pattern matching bytecode generation is fixed:

## Basic Pattern Matching

### Test: Match empty list
```ml
match [] with
| [] -> 0
| h :: t -> 1
```
Expected: 0

### Test: Match non-empty list
```ml
match [1, 2, 3] with
| [] -> 0
| h :: t -> h
```
Expected: 1

### Test: Match and extract tail
```ml
match [1, 2, 3] with
| [] -> []
| h :: t -> t
```
Expected: [2, 3]

## Recursive Functions with Pattern Matching

### Test: List length
```ml
fn length lst = match lst with
  | [] -> 0
  | h :: t -> 1 + length t
in length [1, 2, 3, 4, 5]
```
Expected: 5

### Test: List sum
```ml
fn sum lst = match lst with
  | [] -> 0
  | h :: t -> h + sum t
in sum [1, 2, 3, 4]
```
Expected: 10

### Test: List map
```ml
fn map f lst = match lst with
  | [] -> []
  | h :: t -> (f h) :: (map f t)
in
fn double x = x * 2 in
map double [1, 2, 3]
```
Expected: [2, 4, 6]

### Test: List filter
```ml
fn filter pred lst = match lst with
  | [] -> []
  | h :: t -> if pred h then h :: filter pred t else filter pred t
in
fn is_even x = x % 2 == 0 in
filter is_even [1, 2, 3, 4, 5, 6]
```
Expected: [2, 4, 6]

## Current Issue

Pattern matching compilation has JVM stackmap frame verification errors. The issue is that different match arms leave different stack depths when jumping to the end label. This needs to be fixed by ensuring all pattern compilation paths maintain consistent stack state.
