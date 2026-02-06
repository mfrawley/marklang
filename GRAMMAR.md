# MiniML Grammar (EBNF)

This document describes the formal grammar of the MiniML language in Extended Backus-Naur Form (EBNF).

## EBNF Notation

- `::=` means "is defined as"
- `|` separates alternatives
- `[ ... ]` optional (zero or one)
- `{ ... }` repetition (zero or more)
- `( ... )` grouping
- `"..."` terminal string
- `'...'` terminal character
- `(* ... *)` comments

## Lexical Grammar

```ebnf
(* Literals *)
integer    ::= digit { digit }
float      ::= digit { digit } '.' digit { digit }
string     ::= '"' { character } '"'
boolean    ::= "true" | "false"
unit       ::= "()"

(* Identifiers *)
letter     ::= 'a'..'z' | 'A'..'Z'
digit      ::= '0'..'9'
ident      ::= ( letter | '_' ) { letter | digit | '_' }
qual_ident ::= ident '.' ident

(* Keywords *)
keyword    ::= "let" | "fn" | "fun" | "in" | "if" | "then" | "else" 
             | "match" | "with" | "type" | "of"
             | "Ok" | "Error" | "print"
             | "true" | "false" | "import"

(* Operators *)
add_op     ::= '+' | '-'
mul_op     ::= '*' | '/' | '%'
cmp_op     ::= "==" | "!=" | '<' | '>' | "<=" | ">="
logic_op   ::= "&&" | "||"

(* Comments *)
comment    ::= '#' { character } newline
```

## Syntactic Grammar

### Module Structure

```ebnf
module      ::= { import } { declaration } [ main_expr ]

import           ::= "import" qualified_ident

qualified_ident  ::= ident { '.' ident }

declaration ::= fn_decl | let_decl | type_decl

fn_decl     ::= "fn" ident { param } '=' expr

let_decl    ::= "let" ident '=' expr

type_decl   ::= "type" ident [ type_params ] '=' [ '|' ] constructor { '|' constructor }

type_params ::= '<' ident { ',' ident } '>'

constructor ::= ident [ "of" type_expr ]

type_expr   ::= type_var
              | "int" | "double" | "string" | "bool" | "unit"
              | ident '<' type_expr { ',' type_expr } '>'  (* Parameterized type *)
              | type_expr "->" type_expr                    (* Function type *)
              | '(' type_expr ')'                           (* Parenthesized type *)

type_var    ::= "'" ident_lower                             (* Type variable like 'a, 'b *)

ident_lower ::= ('a'..'z') { letter | digit | '_' }

param       ::= ident | '(' ident ':' type_expr ')'

main_expr   ::= expr
```

### Expressions

```ebnf
(* Precedence from lowest to highest *)

expr            ::= let_expr
                  | fn_expr
                  | lambda_expr
                  | if_expr
                  | match_expr
                  | print_expr
                  | or_expr

or_expr         ::= and_expr { "||" and_expr }

and_expr        ::= comparison_expr { "&&" comparison_expr }

comparison_expr ::= cons_expr { cmp_op cons_expr }

cons_expr       ::= add_expr [ "::" cons_expr ]

add_expr        ::= mul_expr { add_op mul_expr }

mul_expr        ::= app_expr { mul_op app_expr }

app_expr        ::= primary_expr { primary_expr | java_instance_call }

primary_expr    ::= integer
                  | float
                  | string
                  | boolean
                  | unit
                  | list_literal
                  | result_ok
                  | result_error
                  | java_static_call
                  | java_static_field
                  | ident
                  | qual_ident
                  | '(' expr ')'

java_static_call  ::= ident '/' ident { primary_expr }

java_static_field ::= ident '/' ident

java_instance_call ::= primary_expr '/' ident { primary_expr }

let_expr        ::= "let" ident '=' expr "in" expr

fn_expr         ::= "fn" ident { param } [ ':' type_expr ] '=' expr "in" expr

lambda_expr     ::= "fun" { param } "->" expr

if_expr         ::= "if" expr "then" expr "else" expr

match_expr      ::= "match" expr "with" { match_case }

print_expr      ::= "print" expr

list_literal    ::= '[' [ expr { ';' expr } ] ']'

result_ok       ::= "Ok" primary_expr

result_error    ::= "Error" primary_expr
```

### Patterns

```ebnf
pattern       ::= wildcard_pattern
                | var_pattern
                | int_pattern
                | bool_pattern
                | nil_pattern
                | cons_pattern
                | ok_pattern
                | error_pattern

wildcard_pattern ::= '_'

var_pattern      ::= ident

int_pattern      ::= integer

bool_pattern     ::= boolean

nil_pattern      ::= "[]"

cons_pattern     ::= pattern "::" pattern

ok_pattern       ::= "Ok" pattern

error_pattern    ::= "Error" pattern

match_case       ::= '|' pattern "->" expr
```

## Type System (Informational)

```ebnf
(* Type syntax - used in type inference, not in source code *)
type ::= type_int
       | type_double
       | type_string
       | type_bool
       | type_unit
       | type_list
       | type_result
       | type_fun
       | type_var

type_int     ::= "int"
type_double  ::= "double"
type_string  ::= "string"
type_bool    ::= "bool"
type_unit    ::= "unit"
type_list    ::= "list" '<' type '>'
type_result  ::= "result" '<' type ',' type '>'
type_fun     ::= type "->" type
type_var     ::= '\'' ident  (* e.g., 'a, 'b *)
```

## Operator Precedence

From lowest to highest precedence:

1. `||` (logical OR)
2. `&&` (logical AND)
3. `==`, `!=`, `<`, `>`, `<=`, `>=` (comparison)
4. `::` (list cons, right-associative)
5. `+`, `-` (addition, subtraction)
6. `*`, `/`, `%` (multiplication, division, modulo)
7. Function application (left-associative)
8. Primary expressions

### Java Interop

```ebnf
(* Java static method calls *)
java_static_call  ::= ident '/' ident { primary_expr }

(* Java static field access *)
java_static_field ::= ident '/' ident

(* Java instance method calls *)
java_instance_call ::= primary_expr '/' ident { primary_expr }
```

**Notes:**
- Static calls require the class name to start with an uppercase letter
- Class names are automatically prefixed with `java.lang.` (e.g., `Integer` → `java.lang.Integer`)
- Instance method calls can be chained
- The `/` operator is context-dependent: it's division in arithmetic expressions, but method call syntax when followed by an identifier after a primary expression

## Examples

### Simple Expression
```ocaml
let x = 42 in x + 1
```

Parses as:
```
let_expr(
  ident("x"),
  integer(42),
  add_expr(ident("x"), integer(1))
)
```

### Function Definition
```ocaml
fn factorial n =
  if n <= 1 then 1
  else n * factorial (n - 1)
in factorial 5
```

### Pattern Matching
```ocaml
match list with
| [] -> 0
| head :: tail -> head
```

### Sum Types (User-Defined Variants)
```ocaml
type option<a> =
  | Some of a
  | None

type result<ok, err> =
  | Ok of ok
  | Error of err

type shape =
  | Circle of double
  | Rectangle of double
  | Square of double
```

### Using Sum Types
```ocaml
type option<a> = Some of a | None

let x = Some 42;
let y = None;

match x with
| Some value -> value
| None -> 0
```

### Result Type (Built-in, will be moved to userspace)
```ocaml
let result = Ok 42;
match result with
| Ok value -> value
| Error msg -> -1
```

### Java Interop Examples
```ocaml
(* Static method call *)
let x = Integer/parseInt "42" in
x + 1

(* Instance method call *)
let s = "hello" in
s/toUpperCase

(* Method with argument *)
let sb = StringBuilder/new in
sb/append "hello"

(* Chained instance calls *)
"hello"/toUpperCase/length

(* Static field access *)
Integer/MAX_VALUE
```

### Operator Precedence Example
```ocaml
true || false && x == 5
```

Parses as: `true || (false && (x == 5))`

## Notes

- **Type inference**: MiniML uses Hindley-Milner type inference. Types are inferred automatically and type annotations are not currently supported in the surface syntax.
- **Semicolons**: Used as separators in list literals `[1; 2; 3]` and between top-level declarations.
- **Whitespace**: Whitespace is generally insignificant except for separating tokens.
- **Comments**: Line comments start with `#` and continue to end of line.
- **Unit value**: `()` is both a type and a value.
- **Short-circuit evaluation**: `&&` and `||` use short-circuit evaluation.
