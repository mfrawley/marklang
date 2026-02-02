# Proposal 001: Java Foreign Types

**Status**: Implemented  
**Author**: MiniML Team  
**Date**: 2026-02-02  
**Implemented**: 2026-02-02

## Problem

Currently, MiniML's Java interop only works for methods that return primitives mapped to MiniML types (int, double, String, bool, unit). The `Class/method` and `obj/method` syntax exists and uses reflection at compile time, but when a Java method returns an arbitrary Java type (like `StringBuilder`, `ArrayList<T>`, `TreeMap<K,V>`), the type inference system falls back to `freshVar()` - a generic type variable.

This means:
- Java objects can't flow through the type system with their actual types
- Method calls on Java objects don't get proper type checking
- Users can't work with the vast Java standard library (collections, I/O, utilities, etc.)
- Type errors only appear at runtime, not compile time

Example that doesn't work today:
```ocaml
let map = TreeMap/new in        (* type inference returns 't1, loses TreeMap info *)
map/put "key" "value";          (* can't look up .put() method on 't1 *)
map/get "key"                   (* can't look up .get() method *)
```

## Solution

Add a `TJava` type variant to the MiniML type system to represent arbitrary Java types. The compiler already uses Java reflection at compile time (see `TypeInference.inferJavaCallType()`), so we extend this to:

1. Return `TJava(className, typeArgs)` for unknown Java types instead of `freshVar()`
2. Track Java class names through the type system
3. Use reflection to look up methods on `TJava` instances
4. Generate correct bytecode using the Java class name

Additionally, unify the import resolution mechanism: when `import` doesn't find a MiniML module, fall back to Java reflection. This allows `import java.util.TreeMap` to work with the same syntax as MiniML modules.

## Detailed Design

### 1. Type System Changes (`Type.java`)

Add new type variant:

```java
record TJava(String className, List<Type> typeArgs) implements Type {
    @Override
    public String toString() {
        if (typeArgs.isEmpty()) {
            return className;
        }
        return className + "<" + 
               String.join(", ", typeArgs.stream().map(Type::toString).toList()) + 
               ">";
    }
}
```

Update `toJvmType()`:

```java
case TJava(String className, List<Type> typeArgs) -> 
    "L" + className.replace('.', '/') + ";";
```

### 2. Type Inference Changes (`TypeInference.java`)

Update `javaTypeToMiniML()` (currently at line 593):

```java
private Type javaTypeToMiniML(Class<?> javaType) {
    if (javaType == int.class || javaType == Integer.class) {
        return new Type.TInt();
    } else if (javaType == double.class || javaType == Double.class) {
        return new Type.TDouble();
    } else if (javaType == boolean.class || javaType == Boolean.class) {
        return new Type.TBool();
    } else if (javaType == String.class) {
        return new Type.TString();
    } else if (javaType == void.class) {
        return new Type.TUnit();
    } else {
        // NEW: Return TJava instead of freshVar()
        return javaTypeToTJava(javaType);
    }
}

private Type javaTypeToTJava(Class<?> javaType) {
    // Handle generic types
    if (javaType.getTypeParameters().length > 0) {
        List<Type> typeArgs = new ArrayList<>();
        for (java.lang.reflect.TypeVariable<?> tv : javaType.getTypeParameters()) {
            typeArgs.add(freshVar());  // Fresh type variable for each generic parameter
        }
        return new Type.TJava(javaType.getName(), typeArgs);
    }
    
    // Non-generic types
    return new Type.TJava(javaType.getName(), List.of());
}
```

Update `inferJavaCallType()` for static calls (currently at line 524):

```java
private Type inferJavaCallType(String className, String methodName, List<Type> argTypes) {
    Class<?> clazz;
    try {
        clazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
        throw new TypeException("Java class not found: " + className + 
                              ". Ensure it's on the classpath or use 'import java' to specify full name.");
    }
    
    for (java.lang.reflect.Method method : clazz.getMethods()) {
        if (method.getName().equals(methodName) && 
            java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
            method.getParameterCount() == argTypes.size()) {
            
            Class<?>[] paramTypes = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Type miniMLParamType = javaTypeToMiniML(paramTypes[i]);
                Type argType = fullyPrune(argTypes.get(i));
                if (!miniMLParamType.equals(argType)) {
                    matches = false;
                    break;
                }
            }
            
            if (matches) {
                return javaTypeToMiniML(method.getReturnType());
            }
        }
    }
    
    // No matching method found
    throw new TypeException("Static method '" + methodName + "' not found on Java class " + 
                          className + " with matching parameter types");
}
```

Update `inferJavaInstanceCallType()` (currently at line 554):

```java
private Type inferJavaInstanceCallType(Type instanceType, String methodName) {
    Class<?> clazz = null;
    
    if (instanceType instanceof Type.TString) {
        clazz = String.class;
    } else if (instanceType instanceof Type.TInt) {
        clazz = Integer.class;
    } else if (instanceType instanceof Type.TDouble) {
        clazz = Double.class;
    } else if (instanceType instanceof Type.TBool) {
        clazz = Boolean.class;
    } else if (instanceType instanceof Type.TJava(String className, List<Type> typeArgs)) {
        // NEW: Handle TJava instances
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new TypeException("Java class not found: " + className + 
                                  ". Ensure it's on the classpath.");
        }
    } else if (instanceType instanceof Type.TName(String name)) {
        try {
            clazz = Class.forName("java.lang." + name);
        } catch (ClassNotFoundException e) {
            throw new TypeException("Java class not found: java.lang." + name);
        }
    }
    
    if (clazz != null) {
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName) && 
                java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                return javaTypeToMiniML(method.getReturnType());
            }
        }
        // Method not found on a known Java class
        throw new TypeException("Method '" + methodName + "' not found on Java class " + 
                              clazz.getName());
    }
    
    // Only reach here for non-Java types
    return freshVar();
}
```

### 3. Module Resolution Changes (`TypeInference.java`)

Update `loadModuleInterface()` to fall back to Java reflection (currently at line 102):

```java
private Map<String, String> javaImports = new HashMap<>();  // short name -> full class name

public void loadModuleInterface(String moduleName) {
    // 1. Try MiniML module interface file
    try {
        java.nio.file.Path mliPath = java.nio.file.Path.of("target/" + moduleName + ".mli");
        ModuleInterface moduleInterface = ModuleInterface.readFromFile(mliPath);
        
        for (Map.Entry<String, Type> entry : moduleInterface.getExports().entrySet()) {
            String qualifiedName = moduleName + "." + entry.getKey();
            Type scheme = generalize(new HashMap<>(), entry.getValue());
            env.put(qualifiedName, scheme);
            env.put(entry.getKey(), scheme);
        }
        return; // Success!
    } catch (java.io.IOException e) {
        // Fall through to try Java
    }
    
    // 2. Try Java class via reflection
    try {
        Class<?> javaClass = Class.forName(moduleName);
        // Register the short name for later Class/method resolution
        String shortName = moduleName.substring(moduleName.lastIndexOf('.') + 1);
        javaImports.put(shortName, moduleName);
    } catch (ClassNotFoundException e) {
        // Neither MiniML nor Java found - error
        throw new TypeException("Module not found: " + moduleName + 
                              ". Not a MiniML module or Java class.");
    }
}

private String getShortName(String fullName) {
    int lastDot = fullName.lastIndexOf('.');
    return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
}
```

Update `inferJavaCallType()` to check `javaImports` (currently at line 524):

```java
private Type inferJavaCallType(String className, String methodName, List<Type> argTypes) {
    // Resolve short name to full name if imported
    String fullClassName = javaImports.getOrDefault(className, "java.lang." + className);
    
    Class<?> clazz;
    try {
        clazz = Class.forName(fullClassName);
    } catch (ClassNotFoundException e) {
        throw new TypeException("Java class not found: " + fullClassName + 
                              ". Use 'import' to specify the full class name.");
    }
    
    // Handle constructor calls (method name is "new")
    if (methodName.equals("new")) {
        return inferJavaConstructor(clazz, argTypes);
    }
    
    // Handle static method calls
    for (java.lang.reflect.Method method : clazz.getMethods()) {
        if (method.getName().equals(methodName) && 
            java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
            method.getParameterCount() == argTypes.size()) {
            
            Class<?>[] paramTypes = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Type miniMLParamType = javaTypeToMiniML(paramTypes[i]);
                Type argType = fullyPrune(argTypes.get(i));
                if (!miniMLParamType.equals(argType)) {
                    matches = false;
                    break;
                }
            }
            
            if (matches) {
                return javaTypeToMiniML(method.getReturnType());
            }
        }
    }
    
    // No matching method found
    throw new TypeException("Static method '" + methodName + "' not found on Java class " + 
                          fullClassName + " with matching parameter types");
}

private Type inferJavaConstructor(Class<?> clazz, List<Type> argTypes) {
    // No-arg constructor
    if (argTypes.isEmpty()) {
        try {
            clazz.getConstructor();
            return javaTypeToTJava(clazz);
        } catch (NoSuchMethodException e) {
            throw new TypeException("No no-arg constructor found for Java class " + clazz.getName());
        }
    }
    
    // Constructor with arguments - find matching constructor
    for (java.lang.reflect.Constructor<?> constructor : clazz.getConstructors()) {
        if (constructor.getParameterCount() == argTypes.size()) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Type miniMLParamType = javaTypeToMiniML(paramTypes[i]);
                Type argType = fullyPrune(argTypes.get(i));
                if (!miniMLParamType.equals(argType)) {
                    matches = false;
                    break;
                }
            }
            
            if (matches) {
                return javaTypeToTJava(clazz);
            }
        }
    }
    
    throw new TypeException("No matching constructor found for Java class " + clazz.getName() + 
                          " with given parameter types");
}
```

### 4. Parser Changes (`Parser.java`)

Update import parsing to support dotted identifiers (currently at line 20):

```java
while (match(Token.Type.IMPORT)) {
    // Parse dotted identifier: java.util.TreeMap
    StringBuilder moduleName = new StringBuilder();
    moduleName.append(expect(Token.Type.IDENT).value);
    
    while (peek().type == Token.Type.DOT) {
        advance();  // consume dot
        moduleName.append(".");
        moduleName.append(expect(Token.Type.IDENT).value);
    }
    
    imports.add(moduleName.toString());
}
```

### 5. Compiler Changes (`Compiler.java`)

Update constructor call compilation to handle arguments. Currently `Class/new` generates:
```
NEW class
DUP
INVOKESPECIAL <init>()V
```

With arguments `Class/new arg1 arg2`, generate:
```
NEW class
DUP
<compile arg1>
<compile arg2>
INVOKESPECIAL <init>(arg1Type arg2Type)V
```

Need to:
- Look up constructor signature via reflection
- Generate correct descriptor string
- Compile and push arguments onto stack before INVOKESPECIAL

Ensure bytecode generation handles `TJava` correctly:
- Method calls on `TJava` instances use correct class names
- Constructor calls with arguments use correct signatures
- Casting/unboxing works correctly

### 6. Grammar Changes (`GRAMMAR.md`)

Update import syntax to support dotted identifiers:

```ebnf
import           ::= "import" qualified_ident
qualified_ident  ::= ident { '.' ident }
```

This allows both simple names (`import Utils`) and dotted names (`import java.util.TreeMap`).

## Test Plan

### Test 1: Basic Java Type (`tests/java_stringbuilder.mml`)

```ocaml
import java.lang.StringBuilder

let sb = StringBuilder/new in
sb/append "Hello";
sb/append " ";
sb/append "World";
sb/toString

# Expected: "Hello World"
```

### Test 2: Generic Collections (`tests/java_treemap.mml`)

```ocaml
import java.util.TreeMap

let map = TreeMap/new in
map/put "foo" "bar";
map/put "baz" "qux";
let result = map/get "foo" in
result/equals "bar"

# Expected: true
```

### Test 3: Chained Calls (`tests/java_arraylist.mml`)

```ocaml
import java.util.ArrayList

let list = ArrayList/new in
list/add 1;
list/add 2;
list/add 3;
list/size

# Expected: 3
```

### Test 4: Constructor with Arguments (`tests/java_constructor_args.mml`)

```ocaml
import java.lang.StringBuilder

# StringBuilder constructor with capacity argument
let sb = StringBuilder/new 32 in
sb/append "Hello";
sb/capacity

# Expected: 32 (or greater)
```

### Test 5: java.lang Auto-Import (`tests/java_lang_auto.mml`)

```ocaml
# StringBuilder is in java.lang, no import needed
let sb = StringBuilder/new in
sb/append "test";
sb/length

# Expected: 4
```

### Test 6: Error - Class Not Found (`tests/java_error_class.mml`)

```ocaml
let obj = NonExistentClass/new in
obj

# Expected: Compile error: "Java class not found: java.lang.NonExistentClass"
```

### Test 6: Error - Method Not Found (`tests/java_error_method.mml`)

```ocaml
let s = "test" in
s/nonExistentMethod

# Expected: Compile error: "Method 'nonExistentMethod' not found on Java class java.lang.String"
```

### Test 7: Error - Missing Import (`tests/java_error_import.mml`)

```ocaml
# TreeMap is NOT in java.lang, needs import
let map = TreeMap/new in
map

# Expected: Compile error: "Java class not found: java.lang.TreeMap. Use 'import' to specify the full class name."
```

### Test 8: Unified Import (`tests/java_mixed_imports.mml`)

```ocaml
# Mix MiniML and Java imports - same syntax!
import Utils
import java.util.HashMap

let x = Utils.twice 21 in
let map = HashMap/new in
map/put "result" x;
map/get "result"

# Expected: 42
```

## Benefits

1. **Seamless Java interop**: Access the entire Java standard library without manual type definitions
2. **Type safety**: The compiler knows the actual Java type, not just a generic type variable
3. **No runtime overhead**: Reflection happens at compile time, not runtime
4. **Extensible**: Works with any Java class on the classpath (stdlib, third-party JARs, user code)
5. **Familiar syntax**: Uses existing `Class/method` and `obj/method` syntax
6. **Unified imports**: Same `import` syntax for MiniML modules and Java classes
7. **Future-proof**: Prepares for nested MiniML module organization

## Drawbacks

1. **Compile-time classpath dependency**: Java classes must be on classpath when compiling
2. **Limited generic type inference**: Java's generics are more complex than MiniML's type system
3. **Reflection overhead**: Compile time may increase for code with many Java calls
4. **Error messages**: Class not found errors may be confusing

## Alternatives Considered

### Alternative 1: External Type Definitions

Require users to manually define types:

```ocaml
external type StringBuilder
external StringBuilder_new : unit -> StringBuilder
external StringBuilder_append : StringBuilder -> string -> StringBuilder
```

**Rejected**: Too verbose, defeats the purpose of interop.

### Alternative 2: Dynamic Typing for Java

Add a `dynamic` type that opts out of type checking:

```ocaml
let sb : dynamic = StringBuilder/new in
sb/append "test"  (* no type checking *)
```

**Rejected**: Loses type safety, doesn't help with method lookup.

### Alternative 3: Wrapper Code Generation

Generate MiniML wrapper modules for Java classes at compile time.

**Rejected**: Complex, requires caching, doesn't work well with incremental compilation.

## Future Work

### Phase 2: Smart Package Resolution

**Status: Not planned**

Search common packages automatically:

```ocaml
let map = TreeMap/new in  (* searches java.lang, java.util, java.io *)
```

**Reason for rejection**: Ambiguous, slow, and confusing. Explicit imports (`import java.util.TreeMap`) are clearer and not burdensome.

### Phase 3: Better Generic Type Inference

Map Java generics to MiniML type variables more precisely:

```ocaml
let list = ArrayList/new in
list/add 42;              (* infer list : ArrayList<int> *)
let first = list/get 0 in (* infer first : int, not Object *)
```

Implementation: Track generic type arguments through method calls, unify with MiniML types.

### Phase 4: Java Type Annotations

Allow explicit type annotations:

```ocaml
let map : TreeMap<string, int> = TreeMap/new in
```

Implementation: Parse type annotations, verify against reflected types.

## Implementation Checklist

- [ ] Add `TJava` to `Type.java`
- [ ] Update `toJvmType()` for `TJava`
- [ ] Update `javaTypeToMiniML()` to return `TJava`
- [ ] Add `javaTypeToTJava()` helper for generic types
- [ ] Update `inferJavaCallType()` with proper error handling, import resolution, and constructor support
- [ ] Add `inferJavaConstructor()` helper method for constructor type inference
- [ ] Update `inferJavaInstanceCallType()` to handle `TJava` with proper error handling
- [ ] Add `javaImports` map to `TypeInference.java`
- [ ] Update `loadModuleInterface()` to fall back to Java reflection
- [ ] Add `getShortName()` helper method
- [ ] Update `Parser.parseModule()` to parse dotted identifiers in imports
- [ ] Update `Compiler` to handle constructor calls with arguments (bytecode generation)
- [ ] Update `GRAMMAR.md` with qualified_ident syntax
- [ ] Write test: `tests/java_stringbuilder.mml`
- [ ] Write test: `tests/java_treemap.mml`
- [ ] Write test: `tests/java_arraylist.mml`
- [ ] Write test: `tests/java_constructor_args.mml` (constructor with arguments)
- [ ] Write test: `tests/java_lang_auto.mml`
- [ ] Write test: `tests/java_error_class.mml` (class not found)
- [ ] Write test: `tests/java_error_method.mml` (method not found)
- [ ] Write test: `tests/java_error_import.mml` (missing import)
- [ ] Write test: `tests/java_mixed_imports.mml` (MiniML + Java)
- [ ] Update `CLAUDE.md` with Java interop documentation
- [ ] Update this proposal status to `Implemented`

## References

- F# Type Providers: https://learn.microsoft.com/en-us/dotnet/fsharp/tutorials/type-providers/
- OCaml FFI: https://v2.ocaml.org/manual/intfc.html
- Java Reflection API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/package-summary.html
