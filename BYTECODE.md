# JVM Bytecode Guide for the Compiler

## Overview

The JVM is a stack-based virtual machine. Operations push and pop values from an operand stack. Local variables are stored separately in numbered slots.

## Key Concepts

### The Stack

- **Operand Stack**: Where calculations happen. Instructions push values onto or pop values off the stack.
- Example: To add 2 + 3:
  1. Push 2 onto stack → Stack: [2]
  2. Push 3 onto stack → Stack: [2, 3]
  3. Execute IADD → Stack: [5]

### Local Variables

- Stored in numbered slots (0, 1, 2, ...)
- `ILOAD n` - Load int from local variable slot n onto stack
- `ISTORE n` - Store int from stack into local variable slot n

### Method Descriptors

Format: `(parameter types)return type`
- `()V` - No parameters, void return (V = void)
- `(I)I` - One int parameter, returns int (I = int)
- `(II)I` - Two int parameters, returns int
- `([Ljava/lang/String;)V` - Array of String, void return (main method signature)

## Common Instructions Used in Our Compiler

### Loading Constants

- `LDC value` - Load constant (int, float, string) onto stack
- `ICONST_0` - Push int 0 onto stack
- `ICONST_1` - Push int 1 onto stack

### Arithmetic (integers)

- `IADD` - Pop two ints, push their sum
- `ISUB` - Pop two ints, push their difference (second - first)
- `IMUL` - Pop two ints, push their product
- `IDIV` - Pop two ints, push their quotient
- `IREM` - Pop two ints, push their remainder (modulo)

### Type Conversion

- `D2I` - Convert double on stack to int

### Local Variables

- `ILOAD n` - Push int from local variable n onto stack
- `ISTORE n` - Pop int from stack into local variable n
- `ALOAD n` - Push object reference from local n (A = address/reference)

### Comparisons

These pop two ints and jump if condition is true:
- `IF_ICMPEQ label` - Jump if equal
- `IF_ICMPNE label` - Jump if not equal
- `IF_ICMPLT label` - Jump if less than
- `IF_ICMPGT label` - Jump if greater than
- `IF_ICMPLE label` - Jump if less than or equal
- `IF_ICMPGE label` - Jump if greater than or equal

### Control Flow

- `IFEQ label` - Pop int, jump to label if it's 0 (false)
- `GOTO label` - Unconditional jump to label
- `RETURN` - Return from void method
- `IRETURN` - Pop int from stack and return it

### Method Calls

- `INVOKESTATIC class method descriptor` - Call static method
- `INVOKEVIRTUAL class method descriptor` - Call instance method
- `INVOKESPECIAL class method descriptor` - Call constructor or superclass method

### Stack Manipulation

- `POP` - Discard top value from stack
- `SWAP` - Swap top two stack values

### Field Access

- `GETSTATIC class field descriptor` - Get static field value onto stack
  - Example: `GETSTATIC java/lang/System out Ljava/io/PrintStream;` gets System.out

## How Our Compiler Works

### Integer Literal: `42`
```
LDC 42          // Push 42 onto stack
```

### Variable: `let x = 5 in x + 10`
```
LDC 5           // Push 5
ISTORE 0        // Store in local variable 0 (x)
ILOAD 0         // Load x back onto stack
LDC 10          // Push 10
IADD            // Add them
```

### Function Call: `factorial 5`
```
LDC 5           // Push argument
INVOKESTATIC test lambda_factorial (I)I  // Call factorial(int) -> int
```

### If Expression: `if n <= 1 then 1 else n`
```
ILOAD n         // Load n
ICONST_1        // Load 1
IF_ICMPLE then  // Jump to 'then' if n <= 1
ILOAD n         // else branch: load n
GOTO end        // Skip 'then' branch
then:
  ICONST_1      // then branch: load 1
end:
  // Result is on stack
```

### Recursive Function: `let rec factorial n = if n <= 1 then 1 else n * factorial (n-1)`

Compiles to a static method:
```java
private static int lambda_factorial(int n) {  // n is in local slot 0
    ILOAD 0          // Load n
    ICONST_1         // Load 1
    IF_ICMPLE then   // If n <= 1, goto then
    
    ILOAD 0          // else: Load n
    ILOAD 0          // Load n again
    ICONST_1         // Load 1
    ISUB             // n - 1
    INVOKESTATIC lambda_factorial (I)I  // factorial(n-1)
    IMUL             // n * result
    GOTO end
    
  then:
    ICONST_1         // return 1
    
  end:
    IRETURN          // Return int from stack
}
```

### Comparison: `x = 5`

Comparisons return 0 (false) or 1 (true):
```
ILOAD x          // Load x
LDC 5            // Load 5
IF_ICMPEQ true   // If equal, jump to true
ICONST_0         // Push 0 (false)
GOTO end
true:
  ICONST_1       // Push 1 (true)
end:
  // 0 or 1 is on stack
```

## Class Structure

Every `.class` file contains:
1. **Class header**: Version, name, superclass
2. **Constructor**: `<init>()` that calls superclass constructor
3. **Methods**: Our compiled functions + main method
4. **main method**: Compiles the top-level expression and prints result

## Debugging Tips

- Use `javap -c ClassName.class` to disassemble bytecode and see what was generated
- The stack must be balanced: what you push, you must pop
- Local variable slots are reused - this is fine for our simple compiler
- `COMPUTE_FRAMES | COMPUTE_MAXS` tells ASM to calculate stack size and local variable count automatically
