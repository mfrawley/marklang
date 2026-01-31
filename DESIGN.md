# MiniML-JVM Design Document

A simple ML-style functional programming language targeting the JVM.

## Core Features

- Hindley-Milner type inference
- Algebraic data types (ADTs)
- Pattern matching
- Immutability by default
- First-class functions
- Interoperability with Java

## Syntax Examples

### Basic Values and Functions

```ml
let x = 42

let add a b = a + b

let factorial n =
  if n <= 1 then 1
  else n * factorial (n - 1)
```

### Type Annotations (Optional)

```ml
let add : Int -> Int -> Int = fun a b -> a + b

let id : 'a -> 'a = fun x -> x
```

### Algebraic Data Types

```ml
type Option 'a =
  | None
  | Some of 'a

type List 'a =
  | Nil
  | Cons of 'a * List 'a

type Tree 'a =
  | Leaf
  | Node of Tree 'a * 'a * Tree 'a
```

### Pattern Matching

```ml
let rec length list =
  match list with
  | Nil -> 0
  | Cons (head, tail) -> 1 + length tail

let getOrDefault opt default =
  match opt with
  | None -> default
  | Some value -> value

let rec map f list =
  match list with
  | Nil -> Nil
  | Cons (x, xs) -> Cons (f x, map f xs)
```

### Let Bindings and Expressions

```ml
let x = 10 in
let y = 20 in
x + y

let double x = x * 2 in
double 5
```

### Lambda Expressions

```ml
let apply f x = f x

let result = apply (fun x -> x * 2) 5

let compose f g = fun x -> f (g x)
```

### Records (Alternative to tuples)

```ml
type Person = { name: String; age: Int }

let alice = { name = "Alice"; age = 30 }

let getName person = person.name

let olderPerson person = { person with age = person.age + 1 }
```

### Java Interop

```ml
let println s = Java.System.out.println s

let now = Java.System.currentTimeMillis ()

let array = Java.new "java.util.ArrayList" []
```

## Built-in Types

- `Int` - 32-bit integers (maps to Java `int`)
- `Long` - 64-bit integers (maps to Java `long`)
- `Float` - 64-bit floats (maps to Java `double`)
- `Bool` - booleans (maps to Java `boolean`)
- `String` - strings (maps to Java `String`)
- `Unit` - unit type (maps to Java `void`)
- `'a` - type variables for polymorphism

## Operators

- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Comparison: `=`, `<>`, `<`, `>`, `<=`, `>=`
- Boolean: `&&`, `||`, `not`
- List cons: `::`

## Compilation Strategy

1. Parse source to AST
2. Type inference using Algorithm W (Hindley-Milner)
3. Desugar to simpler core language
4. Generate JVM bytecode using ASM library

## File Structure

- `.mml` extension for source files
- Module name derived from filename
- Each file is a module

## Example Program

```ml
type List 'a =
  | Nil
  | Cons of 'a * List 'a

let rec map f list =
  match list with
  | Nil -> Nil
  | Cons (x, xs) -> Cons (f x, map f xs)

let rec filter pred list =
  match list with
  | Nil -> Nil
  | Cons (x, xs) ->
      if pred x then
        Cons (x, filter pred xs)
      else
        filter pred xs

let numbers = Cons (1, Cons (2, Cons (3, Cons (4, Nil))))

let doubled = map (fun x -> x * 2) numbers

let evens = filter (fun x -> x % 2 = 0) numbers
```
