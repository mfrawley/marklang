I # Proposal 002: Polymorphic Type Variable Syntax

**Status**: Proposed  
**Author**: MiniML Team  
**Date**: 2026-02-03

## Problem

MiniML's type inference engine supports polymorphism internally through Hindley-Milner unification, but the language lacks surface syntax for writing polymorphic type variables in function signatures. Users cannot express that a function works for "any type" using explicit type annotations.

This limitation prevents:
- Writing truly generic standard library functions (map, filter, fold, etc.)
- Self-documenting code with explicit polymorphic signatures
- Teaching/learning value because type signatures can't show polymorphic intent
- Building a proper standard library ecosystem

Example that doesn't work today:
```ocaml
fn map (f: 'a -> 'b) (lst: list<'a>): list<'b> =
  match lst with
  | [] -> []
  | hd :: tl -> f hd :: map f tl
  end
```

Current error: `Unexpected character '''` at the apostrophe in `'a`.

The workaround is to write monomorphic versions for each concrete type:
```ocaml
fn map_int (f: int -> int) (lst: list<int>): list<int> = ...
fn map_double (f: double -> double) (lst: list<double>): list<double> = ...
fn map_string (f: string -> string) (lst: list<string>): list<string> = ...
```

This is verbose, error-prone, and violates the DRY principle.

## Solution

Add surface syntax for type variables following the ML family convention: an apostrophe followed by a lowercase identifier (`'a`, `'b`, `'elem`, `'key`, `'value`, etc.).

Users can then write explicit polymorphic type signatures:

```ocaml
fn identity (x: 'a): 'a = x

fn compose (f: 'b -> 'c) (g: 'a -> 'b) (x: 'a): 'c = 
  f (g x)

fn map (f: 'a -> 'b) (lst: list<'a>): list<'b> =
  match lst with
  | [] -> []
  | hd :: tl -> f hd :: map f tl
  end

fn fold_left (f: 'acc -> 'elem -> 'acc) (init: 'acc) (lst: list<'elem>): 'acc =
  match lst with
  | [] -> init
  | hd :: tl -> fold_left f (f init hd) tl
  end
```

The type inference system already handles these internally; this proposal only adds the syntax to expose that capability to users.

## Detailed Design

### Syntax

Type variables follow the ML/OCaml/SML convention:
- Start with an apostrophe `'`
- Followed by a lowercase letter `[a-z]`
- Optionally followed by alphanumeric characters or underscores `[a-zA-Z0-9_]*`
- Examples: `'a`, `'b`, `'elem`, `'key`, `'value`, `'t1`, `'result_type`

Type variables can appear:
1. In function parameter type annotations: `fn foo (x: 'a): ...`
2. In function return type annotations: `fn foo (x: int): 'a = ...`
3. In type constructors: `list<'a>`, `result<'ok, 'err>`
4. In nested positions: `list<'a -> 'b>`, `result<list<'a>, string>`

### Semantics

**Type Variable Scope**: Type variables are scoped to the function signature. Each occurrence of the same type variable name within a signature refers to the same type.

```ocaml
fn pair (x: 'a) (y: 'a): ('a, 'a) = (x, y)
```

Here, both parameters and both tuple elements must have the same type. If the user calls `pair 1 2`, then `'a` unifies with `int`. If they call `pair 1 "hello"`, it's a type error.

**Implicit Quantification**: Type variables are implicitly universally quantified at the function level. The signature `'a -> 'a` is equivalent to the System F notation `∀a. a -> a`.

**Fresh Type Variables**: When the type inference engine encounters an explicit type variable from user syntax, it treats it as a fresh type variable (just as it already does internally). During type checking, it unifies this variable with inferred types from the function body and call sites.

**Polymorphic Instantiation**: When a polymorphically-typed function is called, each call site gets fresh instantiations of the type variables. This is already how MiniML's type inference works; explicit syntax doesn't change the semantics.

```ocaml
fn identity (x: 'a): 'a = x

let n = identity 42 in        (* 'a instantiated to int *)
let s = identity "hello" in   (* 'a instantiated to string *)
n + length s
```

**Type Variable Naming**: Type variable names are meaningful to humans for documentation but semantically irrelevant. The inference algorithm unifies based on structure, not names. However, using consistent names (`'a`, `'b`, `'c` for generic types; `'key`, `'value` for maps; `'elem` for collections) improves readability.

### Grammar Changes

Update the type grammar to include type variables:

```ebnf
type_expr        ::= type_var | type_name | type_app | type_fun | type_tuple
type_var         ::= "'" ident_lower
type_name        ::= "int" | "double" | "string" | "bool" | "unit"
type_app         ::= ident "<" type_expr { "," type_expr } ">"
type_fun         ::= type_expr "->" type_expr
type_tuple       ::= "(" type_expr "," type_expr { "," type_expr } ")"
ident_lower      ::= [a-z] [a-zA-Z0-9_]*
```

Examples:
- `'a` - simple type variable
- `list<'a>` - parameterized list type
- `'a -> 'b` - function from any type to any type
- `result<'ok, 'err>` - result with two type parameters
- `('a, 'b)` - tuple of two potentially different types

## Test Plan

### Test 1: Identity Function (`tests/poly_identity.mml`)

```ocaml
fn identity (x: 'a): 'a = x

let n = identity 42 in
let s = identity "test" in
n == 42 && s == "test"

# Expected: true
```

### Test 2: Composition (`tests/poly_compose.mml`)

```ocaml
fn compose (f: 'b -> 'c) (g: 'a -> 'b) (x: 'a): 'c = 
  f (g x)

fn double (x: int): int = x * 2
fn show (x: int): string = java_call "java.lang.String" "valueOf" x

let doubleShow = compose show double in
doubleShow 21

# Expected: "42"
```

### Test 3: List Map (`tests/poly_map.mml`)

```ocaml
fn map (f: 'a -> 'b) (lst: list<'a>): list<'b> =
  match lst with
  | [] -> []
  | hd :: tl -> f hd :: map f tl
  end

fn double (x: int): int = x * 2

map double [1, 2, 3]

# Expected: [2, 4, 6]
```

### Test 4: List Filter (`tests/poly_filter.mml`)

```ocaml
fn filter (pred: 'a -> bool) (lst: list<'a>): list<'a> =
  match lst with
  | [] -> []
  | hd :: tl -> if pred hd then hd :: filter pred tl else filter pred tl
  end

fn is_positive (x: int): bool = x > 0

filter is_positive [-2, -1, 0, 1, 2]

# Expected: [1, 2]
```

### Test 5: Fold Left (`tests/poly_fold_left.mml`)

```ocaml
fn fold_left (f: 'acc -> 'elem -> 'acc) (init: 'acc) (lst: list<'elem>): 'acc =
  match lst with
  | [] -> init
  | hd :: tl -> fold_left f (f init hd) tl
  end

fn add (x: int) (y: int): int = x + y

fold_left add 0 [1, 2, 3, 4, 5]

# Expected: 15
```

### Test 6: Option Type (`tests/poly_option.mml`)

```ocaml
fn map_result (f: 'a -> 'b) (r: result<'a, 'e>): result<'b, 'e> =
  match r with
  | Ok x -> Ok (f x)
  | Error e -> Error e
  end

fn double (x: int): int = x * 2

let r1 = map_result double (Ok 21) in
let r2 = map_result double (Error "fail") in
match r1 with
| Ok n -> n == 42
| Error _ -> false
end

# Expected: true
```

### Test 7: Type Error - Unification Failure (`tests/poly_error_unify.mml`)

```ocaml
fn pair (x: 'a) (y: 'a): ('a, 'a) = (x, y)

pair 1 "hello"

# Expected: Type error - cannot unify int with string
```

### Test 8: Pair Type (`tests/poly_pair.mml`)

```ocaml
fn fst (p: ('a, 'b)): 'a = 
  match p with
  | (x, y) -> x
  end

fn snd (p: ('a, 'b)): 'b = 
  match p with
  | (x, y) -> y
  end

let p = (42, "hello") in
fst p == 42 && snd p == "hello"

# Expected: true
```

## Benefits

### 1. Standard Library Development

The primary motivation: enables writing a proper polymorphic standard library with generic functions for lists, options, results, maps, sets, etc.

```ocaml
# stdlib/List.mml
fn length (lst: list<'a>): int = ...
fn map (f: 'a -> 'b) (lst: list<'a>): list<'b> = ...
fn filter (pred: 'a -> bool) (lst: list<'a>): list<'a> = ...
fn fold_left (f: 'b -> 'a -> 'b) (acc: 'b) (lst: list<'a>): 'b = ...
```

Without this feature, every function needs `_int`, `_double`, `_string` variants, making the stdlib unmaintainable.

### 2. Self-Documenting Code

Type signatures become precise documentation:

```ocaml
fn map (f: 'a -> 'b) (lst: list<'a>): list<'b> = ...
```

This signature immediately tells you:
- Takes a function from any type `'a` to any type `'b`
- Takes a list of `'a` elements
- Returns a list of `'b` elements
- The function `f` is applied to transform each element

Compare to the current situation where users must read the implementation to understand the type behavior.

### 3. Code Reuse

Write once, use everywhere. Instead of writing `map_int`, `map_double`, `map_string`, etc., write one generic `map` function.

### 4. Type Safety

Explicit type variables make the type checker more precise. The user's intent is clear, and the compiler can verify it matches the implementation.

### 5. Educational Value

MiniML becomes a better teaching language for type systems. Students can see how polymorphism works explicitly rather than only through inference.

### 6. ML Family Consistency

Matches the syntax of OCaml, SML, and Haskell (which uses `a` without the apostrophe). This makes MiniML more familiar to functional programmers and easier to learn from existing ML resources.

### 7. Enables Future Features

Polymorphic type syntax is a prerequisite for:
- Type classes/traits
- Higher-kinded types
- More sophisticated module systems
- Generic algebraic data types

## Drawbacks

### 1. Lexer Complexity

The apostrophe character `'` is already used in some languages for character literals. While MiniML doesn't currently have char types, this could limit future design space.

**Mitigation**: MiniML already uses double quotes for strings. Single quotes can be reserved exclusively for type variables. If char literals are needed in the future, use a different syntax (e.g., `#a` or backquoted strings).

### 2. Potential Quote Confusion

Users might confuse type variables `'a` with string literals (which use double quotes `"..."`). This is mainly a concern for newcomers.

**Mitigation**: Clear error messages, documentation, and examples. The ML family has used this syntax for decades with minimal confusion.

### 3. Optional Type Annotations

MiniML's type inference works without annotations. Adding explicit type variables might make users think annotations are required, reducing the language's approachability.

**Mitigation**: Document that type annotations are optional but recommended for:
- Top-level functions in libraries
- Complex functions where intent isn't obvious
- Teaching and learning

### 4. Type Variable Naming Conventions

Users might pick inconsistent or confusing type variable names (`'x`, `'foo`, `'thing123`), reducing code clarity.

**Mitigation**: Provide style guide recommendations:
- Use `'a`, `'b`, `'c` for generic/arbitrary types
- Use descriptive names for domain-specific types: `'key`, `'value`, `'elem`, `'error`
- Keep names short (1-2 words max)

### 5. Implementation Complexity

Requires changes to lexer, parser, and type system. While not huge, it adds complexity to the compiler.

**Mitigation**: The type inference engine already handles type variables internally. This proposal mainly adds syntax, not new semantics. The implementation is straightforward.

## Alternatives Considered

### Alternative 1: No Explicit Type Variables

Keep the current system where type annotations only support concrete types, relying entirely on inference.

**Rejected**: Prevents writing a usable standard library. Forces duplication of generic functions. Makes the language unsuitable for serious use.

### Alternative 2: Different Syntax - Angle Brackets

Use `<a>`, `<b>` instead of `'a`, `'b`:

```ocaml
fn map (f: <a> -> <b>) (lst: list<<a>>): list<<b>> = ...
```

**Rejected**: 
- Conflicts with existing parameterized type syntax `list<int>`
- Ugly and verbose with nested types: `list<<a>>`
- No precedent in established languages

### Alternative 3: Different Syntax - Uppercase

Use bare uppercase identifiers like Haskell: `A`, `B`, `Elem`, `Key`

```ocaml
fn map (f: A -> B) (lst: list<A>): list<B> = ...
```

**Rejected**:
- Conflicts with MiniML's convention of uppercase for constructors (Ok, Error, True, False)
- Less visually distinct from concrete types
- Doesn't match OCaml/SML conventions

### Alternative 4: Explicit Quantification

Require explicit forall notation:

```ocaml
fn map forall 'a 'b (f: 'a -> 'b) (lst: list<'a>): list<'b> = ...
```

**Rejected**: Verbose, adds syntax noise. Implicit quantification is standard in ML family languages and works well.

### Alternative 5: Automatic Generalization Only

Don't allow explicit type variables at all. If a user wants polymorphism, they must rely entirely on inference.

**Rejected**: Doesn't solve the documentation problem. Makes type errors harder to understand because there's no user-specified intent to compare against.

## Future Work

### Phase 2: Type Aliases with Type Parameters

Allow defining generic type aliases:

```ocaml
type ('k, 'v) map = java.util.TreeMap<'k, 'v>
```

This would make Java interop more ergonomic and enable custom generic type names.

### Phase 3: Higher-Kinded Types

Allow type constructors as parameters:

```ocaml
fn map_functor (f: 'a -> 'b) (fa: 'f<'a>): 'f<'b> = ...
```

This enables abstracting over type constructors (list, option, result, etc.) and implementing functor/monad patterns generically.

### Phase 4: Type Classes/Traits

Allow constraining type variables:

```ocaml
fn sum (lst: list<'a>) where 'a : Numeric : 'a = ...
```

This would enable generic numeric code, equality constraints, and other advanced type system features.

### Phase 5: Rank-N Types

Allow polymorphic types in non-prenex positions:

```ocaml
fn apply_to_both (f: forall 'a. 'a -> 'a) (x: int) (y: string): (int, string) =
  (f x, f y)
```

This is more advanced and requires significant type system changes, but polymorphic type syntax is a prerequisite.

## Implementation Notes

The implementation touches these components:
- **Lexer**: Recognize `'` followed by identifier as TYPE_VAR token
- **Parser**: Parse TYPE_VAR in type expressions
- **Type System**: Map parsed type variables to internal Type.Var
- **Type Inference**: Treat explicit type vars as fresh variables during unification

The type inference engine already has all the machinery for polymorphism (generalization, instantiation, unification). This proposal primarily adds surface syntax to expose existing capabilities.

## References

- OCaml Manual - Type Expressions: https://v2.ocaml.org/manual/types.html
- SML Type System: http://sml-family.org/
- Hindley-Milner Type Inference: https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system
- Types and Programming Languages (Pierce): Chapter 22 - Type Reconstruction
