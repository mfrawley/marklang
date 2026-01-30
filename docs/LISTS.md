# Lists in MiniML

MiniML supports ML-style polymorphic lists with type inference and pattern matching.

## Syntax

### List Literals

```ml
[]              # Empty list
[1, 2, 3]       # List of integers
[1.5, 2.5]      # List of doubles
["a", "b"]      # List of strings
```

### Cons Operator

The cons operator `::` prepends an element to a list (right-associative):

```ml
1 :: [2, 3]         # [1, 2, 3]
1 :: 2 :: 3 :: []   # [1, 2, 3]
```

### Pattern Matching

Pattern matching destructures lists:

```ml
match list with
| [] -> 0                    # Match empty list
| h :: t -> h               # Match head and tail
| x :: [] -> x              # Match single element
| x :: y :: rest -> x + y   # Match multiple elements
```

## Type System

Lists are polymorphic and type-inferred:

```ml
[]                  # 'a list (polymorphic empty list)
[1, 2, 3]          # Int list
[1.0, 2.0]         # Double list
1 :: [2, 3]        # Int list (unified)
```

All elements in a list must have the same type:

```ml
[1, 2.5]           # Type error: cannot unify Int with Double
```

## Implementation Details

### Runtime Representation

Lists are compiled to `java.util.ArrayList`:
- Empty list: `new ArrayList()`
- List literal: `ArrayList` with elements added
- Cons: New `ArrayList` with head, then `addAll` from tail

### Boxing

Primitive types are boxed when stored in lists:
- `Int` → `java.lang.Integer`
- `Double` → `java.lang.Double`
- `Bool` → `java.lang.Boolean`

### Pattern Matching Compilation

**Status: Not Yet Implemented**

Pattern matching will compile to:
- `isEmpty()` checks for empty list patterns
- `get(0)` to extract head
- `subList(1, size())` to extract tail
- Conditional jumps between match arms

Currently has JVM bytecode verification issues that need to be resolved.

## Examples

### Working Examples

```ml
# Empty list
[]

# Integer list
[1, 2, 3, 4, 5]

# Build list with cons
1 :: 2 :: 3 :: []

# Prepend to existing list
0 :: [1, 2, 3]

# Nested expressions in list
[1 + 1, 2 * 2, 3 - 1]
```

### Planned Examples (Pattern Matching)

```ml
# List length
fn length lst = match lst with
  | [] -> 0
  | h :: t -> 1 + length t

# List sum
fn sum lst = match lst with
  | [] -> 0
  | h :: t -> h + sum t

# List map
fn map f lst = match lst with
  | [] -> []
  | h :: t -> (f h) :: (map f t)

# List filter
fn filter pred lst = match lst with
  | [] -> []
  | h :: t -> if pred h then h :: filter pred t else filter pred t
```

## Test Coverage

### Passing Tests
- ✅ `list_literal.ml` - Empty list
- ✅ `list_literal_int.ml` - Integer list literal
- ✅ `list_cons.ml` - Single element cons
- ✅ `list_cons_empty.ml` - Cons to empty list
- ✅ `list_cons_chain.ml` - Chained cons operations

### Pending Tests
See `tests/PATTERN_MATCHING_TODO.md` for pattern matching tests awaiting implementation.

## Known Limitations

1. **Pattern matching not working**: JVM bytecode stackmap frame verification errors
2. **No standard library functions**: `List.map`, `List.filter`, etc. need to be implemented
3. **No list comprehensions**: Not planned for initial implementation
4. **No pattern guards**: `| h :: t when h > 0 -> ...` not yet supported

## Future Work

1. Fix pattern matching bytecode generation
2. Add standard library functions:
   - `List.length`
   - `List.map`
   - `List.filter`
   - `List.fold`
   - `List.append`
   - `List.reverse`
3. Add pattern exhaustiveness checking
4. Optimize list operations (avoid creating intermediate lists)
