package com.miniml;

import java.util.*;

public class TypeInference {
    private int nextVarId = 0;
    private Map<Expr, Type> typeMap = new HashMap<>();
    private Map<String, Type> env = new HashMap<>();
    private Map<String, Set<Type>> instantiations = new HashMap<>();
    private String currentFilename = "<unknown>";
    
    public TypeInference() {
        initializeBuiltins();
    }

    public void setFilename(String filename) {
        this.currentFilename = filename;
    }
    
    public Map<String, Set<Type>> getInstantiations() {
        return instantiations;
    }
    
    private void initializeBuiltins() {
        Type intBinOp = new Type.TFun(new Type.TInt(), 
            new Type.TFun(new Type.TInt(), new Type.TInt()));
        Type doubleBinOp = new Type.TFun(new Type.TDouble(), 
            new Type.TFun(new Type.TDouble(), new Type.TDouble()));
        Type intComparison = new Type.TFun(new Type.TInt(), 
            new Type.TFun(new Type.TInt(), new Type.TBool()));
        
        env.put("print", new Type.TScheme(List.of("a"), 
            new Type.TFun(new Type.TVar("a"), new Type.TUnit())));
        
        env.put("box", new Type.TBoxed());
    }
    
    public Type inferModule(Module module) throws TypeException {
        for (String importName : module.imports()) {
            loadModuleInterface(importName);
        }
        
        for (Module.TopLevel decl : module.declarations()) {
            if (decl instanceof Module.TopLevel.FnDecl(String name, List<Module.Param> params, var returnType, Expr body)) {
                Type fnType = inferTopLevelFn(params, returnType, body);
                Type scheme = generalize(new HashMap<>(), fnType);
                env.put(name, scheme);
                
                instantiations.put(name, new HashSet<>());
            } else if (decl instanceof Module.TopLevel.LetDecl(String name, Expr value)) {
                Type valueType = infer(env, value);
                Type scheme = generalize(new HashMap<>(), valueType);
                env.put(name, scheme);
            } else if (decl instanceof Module.TopLevel.TypeDef(String typeName, List<String> typeParams, List<Module.Constructor> constructors)) {
                for (Module.Constructor ctor : constructors) {
                    Type ctorType;
                    if (ctor.paramType().isPresent()) {
                        Type paramType = resolveTypeAnnotation(ctor.paramType().get());
                        Type resultType = typeParams.isEmpty() ? 
                            new Type.TName(typeName) : 
                            new Type.TApp(typeName, typeParams.stream().map(p -> (Type)new Type.TVar(p)).toList());
                        ctorType = new Type.TFun(paramType, resultType);
                    } else {
                        ctorType = typeParams.isEmpty() ? 
                            new Type.TName(typeName) : 
                            new Type.TApp(typeName, typeParams.stream().map(p -> (Type)new Type.TVar(p)).toList());
                    }
                    Type scheme = typeParams.isEmpty() ? ctorType : new Type.TScheme(typeParams, ctorType);
                    env.put(ctor.name(), scheme);
                }
            }
        }
        
        Type result = new Type.TUnit();
        if (module.mainExpr() != null) {
            result = infer(env, module.mainExpr());
        }
        
        finalizeInstantiations();
        pruneTypeMap();
        
        return result;
    }
    
    public void loadModuleInterface(String moduleName) {
        try {
            java.nio.file.Path mliPath = java.nio.file.Path.of("target/" + moduleName + ".mli");
            ModuleInterface moduleInterface = ModuleInterface.readFromFile(mliPath);
            
            for (Map.Entry<String, Type> entry : moduleInterface.getExports().entrySet()) {
                String qualifiedName = moduleName + "." + entry.getKey();
                Type scheme = generalize(new HashMap<>(), entry.getValue());
                env.put(qualifiedName, scheme);
                env.put(entry.getKey(), scheme);
            }
        } catch (java.io.IOException e) {
        }
    }
    
    private void finalizeInstantiations() {
        Map<String, Set<Type>> finalized = new HashMap<>();
        for (Map.Entry<String, Set<Type>> entry : instantiations.entrySet()) {
            Set<Type> prunedTypes = new HashSet<>();
            for (Type t : entry.getValue()) {
                Type pruned = prune(t);
                if (!(pruned instanceof Type.TVar)) {
                    prunedTypes.add(pruned);
                }
            }
            if (!prunedTypes.isEmpty()) {
                finalized.put(entry.getKey(), prunedTypes);
            }
        }
        instantiations = finalized;
    }
    
    private Type inferTopLevelFn(List<Module.Param> params, Optional<Type> returnTypeAnnotation, Expr body) throws TypeException {
        Map<String, Type> localEnv = new HashMap<>(env);
        List<Type> paramTypes = new ArrayList<>();
        
        for (Module.Param param : params) {
            Type paramType;
            if (param.typeAnnotation().isPresent()) {
                paramType = param.typeAnnotation().get();
            } else {
                paramType = freshVar();
            }
            localEnv.put(param.name(), paramType);
            paramTypes.add(paramType);
        }
        
        Type resultType = infer(localEnv, body);
        
        if (returnTypeAnnotation.isPresent()) {
            unify(resultType, returnTypeAnnotation.get());
            resultType = returnTypeAnnotation.get();
        }
        
        Type fnType = resultType;
        for (int i = paramTypes.size() - 1; i >= 0; i--) {
            fnType = new Type.TFun(paramTypes.get(i), fnType);
        }
        
        return fnType;
    }
    
    private Type inferFn(List<String> params, Expr body, Map<String, Type> parentEnv) throws TypeException {
        Map<String, Type> localEnv = new HashMap<>(parentEnv);
        List<Type> paramTypes = new ArrayList<>();
        
        for (String param : params) {
            Type paramType = freshVar();
            localEnv.put(param, paramType);
            paramTypes.add(paramType);
        }
        
        Type resultType = infer(localEnv, body);
        
        Type fnType = resultType;
        for (int i = paramTypes.size() - 1; i >= 0; i--) {
            fnType = new Type.TFun(paramTypes.get(i), fnType);
        }
        
        return fnType;
    }
    
    public Type infer(Map<String, Type> localEnv, Expr expr) throws TypeException {
        Type type = switch (expr) {
            case Expr.Unit u -> new Type.TUnit();
            case Expr.IntLit i -> new Type.TInt();
            case Expr.FloatLit f -> new Type.TDouble();
            case Expr.BoolLit b -> new Type.TBool();
            case Expr.StringLit s -> new Type.TString();
            case Expr.StringInterp si -> new Type.TString();
            
            case Expr.Var(String name) -> {
                if (!localEnv.containsKey(name)) {
                    throw new TypeException("Undefined variable: " + name);
                }
                yield instantiate(localEnv.get(name));
            }
            
            case Expr.QualifiedVar(String moduleName, String memberName) -> {
                String qualifiedName = moduleName + "." + memberName;
                if (env.containsKey(qualifiedName)) {
                    yield instantiate(env.get(qualifiedName));
                }
                if (env.containsKey(memberName)) {
                    yield instantiate(env.get(memberName));
                }
                throw new TypeException("Undefined qualified variable: " + qualifiedName);
            }
            
            case Expr.Let(String name, Expr value, Expr body) -> {
                Type valueType = infer(localEnv, value);
                Type scheme = generalize(localEnv, valueType);
                Map<String, Type> newEnv = new HashMap<>(localEnv);
                newEnv.put(name, scheme);
                yield infer(newEnv, body);
            }
            
            case Expr.LetRec(String name, List<String> params, Expr value, Expr body) -> {
                Type fnType = freshVar();
                Map<String, Type> newEnv = new HashMap<>(localEnv);
                newEnv.put(name, fnType);
                
                Type inferredFnType = inferFn(params, value, localEnv);
                unify(fnType, inferredFnType);
                
                Type scheme = generalize(localEnv, fnType);
                newEnv.put(name, scheme);
                yield infer(newEnv, body);
            }
            
            case Expr.Lambda(List<String> params, Expr lambdaBody) -> {
                Map<String, Type> newEnv = new HashMap<>(localEnv);
                List<Type> paramTypes = new ArrayList<>();
                for (String param : params) {
                    Type paramType = freshVar();
                    paramTypes.add(paramType);
                    newEnv.put(param, paramType);
                }
                
                Type bodyType = infer(newEnv, lambdaBody);
                Type fnType = bodyType;
                for (int i = params.size() - 1; i >= 0; i--) {
                    fnType = new Type.TFun(paramTypes.get(i), fnType);
                }
                yield fnType;
            }
            
            case Expr.App(Expr func, List<Expr> args) -> {
                String funcName = null;
                if (func instanceof Expr.Var v) {
                    funcName = v.name();
                } else if (func instanceof Expr.QualifiedVar qv) {
                    funcName = qv.name();
                }
                
                if ("box".equals(funcName) && args.size() == 1) {
                    Type argType = infer(localEnv, args.get(0));
                    Type boxedType = switch (argType) {
                        case Type.TInt i -> new Type.TName("Integer");
                        case Type.TDouble d -> new Type.TName("Double");
                        case Type.TBool b -> new Type.TName("Boolean");
                        default -> new Type.TVar("boxed_" + nextVarId++);
                    };
                    yield boxedType;
                }
                
                Type funcType = infer(localEnv, func);
                Type resultType = funcType;
                
                List<Type> argTypes = new ArrayList<>();
                for (Expr arg : args) {
                    Type argType = infer(localEnv, arg);
                    argTypes.add(argType);
                    Type freshResult = freshVar();
                    unify(resultType, new Type.TFun(argType, freshResult));
                    resultType = fullyPrune(freshResult);
                }
                
                if (funcName != null) {
                    
                    Type fullType = prune(funcType);
                    for (Type argType : argTypes) {
                        Type prunedArg = prune(argType);
                        if (fullType instanceof Type.TFun(Type p, Type r)) {
                            fullType = r;
                        }
                    }
                    
                    Type finalType = prune(resultType);
                    if (instantiations.containsKey(funcName) && !(finalType instanceof Type.TVar)) {
                        Type reconstructed = fullyPrune(finalType);
                        for (int i = argTypes.size() - 1; i >= 0; i--) {
                            reconstructed = new Type.TFun(fullyPrune(argTypes.get(i)), reconstructed);
                        }
                        if (!containsUnresolvedVars(reconstructed)) {
                            instantiations.get(funcName).add(reconstructed);
                        }
                    }
                }
                
                yield resultType;
            }
            
            case Expr.If(Expr cond, Expr thenBranch, Expr elseBranch) -> {
                Type condType = infer(localEnv, cond);
                unify(condType, new Type.TBool());
                
                Type thenType = infer(localEnv, thenBranch);
                Type elseType = infer(localEnv, elseBranch);
                unify(thenType, elseType);
                
                yield thenType;
            }
            
            case Expr.UnaryOp(Expr.UnOp op, Expr operand) -> {
                Type operandType = infer(localEnv, operand);
                yield switch (op) {
                    case NEG -> {
                        Type numericType = freshNumeric();
                        unify(operandType, numericType);
                        yield numericType;
                    }
                    case NOT -> {
                        unify(operandType, new Type.TBool());
                        yield new Type.TBool();
                    }
                };
            }

            case Expr.BinOp(Expr.Op op, Expr left, Expr right) -> {
                Type leftType = infer(localEnv, left);
                Type rightType = infer(localEnv, right);
                
                yield switch (op) {
                    case ADD, SUB, MUL, DIV, MOD -> {
                        Type numericType = freshNumeric();
                        unify(leftType, numericType);
                        unify(rightType, numericType);
                        yield numericType;
                    }
                    case EQ, NE, LT, GT, LE, GE -> {
                        unify(leftType, rightType);
                        yield new Type.TBool();
                    }
                    case AND, OR -> {
                        unify(leftType, new Type.TBool());
                        unify(rightType, new Type.TBool());
                        yield new Type.TBool();
                    }
                };
            }
            
            case Expr.Print(Expr value) -> {
                infer(localEnv, value);
                yield new Type.TUnit();
            }
            
            case Expr.Sequence(List<Expr> exprs) -> {
                Type lastType = new Type.TUnit();
                for (Expr e : exprs) {
                    lastType = infer(localEnv, e);
                }
                yield lastType;
            }
            
            case Expr.JavaCall(String className, String methodName, List<Expr> args) -> {
                for (Expr arg : args) {
                    infer(localEnv, arg);
                }
                yield inferJavaCallType(className, methodName);
            }
            
            case Expr.JavaInstanceCall(String className, String methodName, Expr instance, List<Expr> args) -> {
                Type instanceType = infer(localEnv, instance);
                for (Expr arg : args) {
                    infer(localEnv, arg);
                }
                yield inferJavaInstanceCallType(instanceType, methodName);
            }
            
            case Expr.ListLit(List<Expr> elements) -> {
                if (elements.isEmpty()) {
                    yield new Type.TList(freshVar());
                }
                Type elementType = infer(localEnv, elements.get(0));
                for (int i = 1; i < elements.size(); i++) {
                    Type elemType = infer(localEnv, elements.get(i));
                    unify(elementType, elemType);
                }
                yield new Type.TList(elementType);
            }
            
            case Expr.Cons(Expr head, Expr tail) -> {
                Type headType = infer(localEnv, head);
                Type tailType = infer(localEnv, tail);
                Type listType = new Type.TList(headType);
                unify(tailType, listType);
                yield listType;
            }
            
            case Expr.Constructor(String name, java.util.Optional<Expr> arg) -> {
                if (!localEnv.containsKey(name)) {
                    throw new TypeException("Undefined constructor: " + name);
                }
                Type ctorType = instantiate(localEnv.get(name));
                if (arg.isPresent()) {
                    Type argType = infer(localEnv, arg.get());
                    Type resultType = freshVar();
                    unify(ctorType, new Type.TFun(argType, resultType));
                    yield resultType;
                } else {
                    yield ctorType;
                }
            }
            
            case Expr.Match(Expr scrutinee, List<Expr.MatchCase> cases) -> {
                Type scrutineeType = infer(localEnv, scrutinee);
                
                if (cases.isEmpty()) {
                    throw new TypeException("Match expression must have at least one case");
                }
                
                Type resultType = null;
                for (Expr.MatchCase matchCase : cases) {
                    Map<String, Type> patternEnv = new HashMap<>(localEnv);
                    inferPattern(matchCase.pattern(), scrutineeType, patternEnv);
                    Type caseType = infer(patternEnv, matchCase.body());
                    
                    if (resultType == null) {
                        resultType = caseType;
                    } else {
                        unify(resultType, caseType);
                    }
                }
                
                yield resultType;
            }
        };
        
        typeMap.put(expr, type);
        return type;
    }
    
    public void pruneTypeMap() {
        Map<Expr, Type> prunedMap = new HashMap<>();
        for (Map.Entry<Expr, Type> entry : typeMap.entrySet()) {
            prunedMap.put(entry.getKey(), fullyPrune(entry.getValue()));
        }
        typeMap = prunedMap;
    }
    
    private void inferPattern(Pattern pattern, Type expectedType, Map<String, Type> env) throws TypeException {
        switch (pattern) {
            case Pattern.Wildcard() -> {
            }
            
            case Pattern.Var(String name) -> {
                env.put(name, expectedType);
            }
            
            case Pattern.IntLit(int value) -> {
                unify(expectedType, new Type.TInt());
            }
            
            case Pattern.BoolLit(boolean value) -> {
                unify(expectedType, new Type.TBool());
            }
            
            case Pattern.StringLit(String value) -> {
                unify(expectedType, new Type.TString());
            }
            
            case Pattern.Nil() -> {
                Type elementType = freshVar();
                unify(expectedType, new Type.TList(elementType));
            }
            
            case Pattern.Cons(Pattern head, Pattern tail) -> {
                Type elementType = freshVar();
                Type listType = new Type.TList(elementType);
                unify(expectedType, listType);
                inferPattern(head, elementType, env);
                inferPattern(tail, listType, env);
            }
            
            case Pattern.Constructor(String name, java.util.Optional<Pattern> arg) -> {
                if (!env.containsKey(name)) {
                    throw new TypeException("Undefined constructor: " + name);
                }
                Type ctorType = instantiate(env.get(name));
                if (arg.isPresent()) {
                    Type argType = freshVar();
                    Type resultType = freshVar();
                    unify(ctorType, new Type.TFun(argType, resultType));
                    unify(expectedType, resultType);
                    inferPattern(arg.get(), argType, env);
                } else {
                    unify(expectedType, ctorType);
                }
            }
        }
    }
    
    private Type inferJavaCallType(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && 
                    java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
                    java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    return javaTypeToMiniML(method.getReturnType());
                }
            }
        } catch (ClassNotFoundException e) {
        }
        return freshVar();
    }
    
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
        } else if (instanceType instanceof Type.TName(String name)) {
            try {
                clazz = Class.forName("java.lang." + name);
            } catch (ClassNotFoundException e) {
            }
        }
        
        if (clazz != null) {
            for (java.lang.reflect.Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && 
                    java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    return javaTypeToMiniML(method.getReturnType());
                }
            }
        }
        
        return freshVar();
    }
    
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
            return freshVar();
        }
    }
    
    private Type freshVar() {
        return new Type.TVar("t" + (nextVarId++));
    }
    
    private Type freshNumeric() {
        return new Type.TNumeric("n" + (nextVarId++));
    }
    
    private Type instantiate(Type type) {
        if (type instanceof Type.TScheme(List<String> vars, Type innerType)) {
            Map<String, Type> subst = new HashMap<>();
            for (String var : vars) {
                subst.put(var, freshVar());
            }
            return applySubst(subst, innerType);
        }
        return type;
    }
    
    private Type generalize(Map<String, Type> env, Type type) {
        Set<String> envVars = new HashSet<>();
        for (Type t : env.values()) {
            envVars.addAll(freeTypeVars(t));
        }
        
        Set<String> typeVars = new HashSet<>(freeTypeVars(type));
        typeVars.removeAll(envVars);
        
        if (typeVars.isEmpty()) {
            return type;
        }
        
        return new Type.TScheme(new ArrayList<>(typeVars), type);
    }
    
    private Set<String> freeTypeVars(Type type) {
        return switch (type) {
            case Type.TVar(String name) -> Set.of(name);
            case Type.TNumeric(String name) -> Set.of(name);
            case Type.TFun(Type param, Type result) -> {
                Set<String> vars = new HashSet<>(freeTypeVars(param));
                vars.addAll(freeTypeVars(result));
                yield vars;
            }
            case Type.TScheme(List<String> boundVars, Type innerType) -> {
                Set<String> vars = new HashSet<>(freeTypeVars(innerType));
                vars.removeAll(boundVars);
                yield vars;
            }
            default -> Set.of();
        };
    }
    
    private void unify(Type t1, Type t2) throws TypeException {
        Type type1 = prune(t1);
        Type type2 = prune(t2);
        
        if (type1 instanceof Type.TVar(String name)) {
            if (!type1.equals(type2)) {
                if (occursInType(name, type2)) {
                    throw new TypeException("Recursive type: " + name + " occurs in " + type2);
                }
                bind(name, type2);
            }
            return;
        }
        
        if (type2 instanceof Type.TVar(String name)) {
            if (occursInType(name, type1)) {
                throw new TypeException("Recursive type: " + name + " occurs in " + type1);
            }
            bind(name, type1);
            return;
        }
        
        if (type1 instanceof Type.TNumeric(String name)) {
            if (!type1.equals(type2)) {
                if (type2 instanceof Type.TInt || type2 instanceof Type.TDouble || type2 instanceof Type.TNumeric) {
                    if (occursInType(name, type2)) {
                        throw new TypeException("Recursive type: " + name + " occurs in " + type2);
                    }
                    bind(name, type2);
                } else {
                    throw new TypeException("Type mismatch: numeric type " + type1 + " cannot unify with " + type2);
                }
            }
            return;
        }
        
        if (type2 instanceof Type.TNumeric(String name)) {
            if (type1 instanceof Type.TInt || type1 instanceof Type.TDouble) {
                if (occursInType(name, type1)) {
                    throw new TypeException("Recursive type: " + name + " occurs in " + type1);
                }
                bind(name, type1);
            } else {
                throw new TypeException("Type mismatch: numeric type " + type2 + " cannot unify with " + type1);
            }
            return;
        }
        
        if (type1 instanceof Type.TFun(Type p1, Type r1) && type2 instanceof Type.TFun(Type p2, Type r2)) {
            unify(p1, p2);
            unify(r1, r2);
            return;
        }
        
        if (type1 instanceof Type.TList(Type e1) && type2 instanceof Type.TList(Type e2)) {
            unify(e1, e2);
            return;
        }
        
        if (type1 instanceof Type.TResult(Type ok1, Type err1) && type2 instanceof Type.TResult(Type ok2, Type err2)) {
            unify(ok1, ok2);
            unify(err1, err2);
            return;
        }
        
        if (type1.getClass().equals(type2.getClass())) {
            return;
        }
        
        throw new TypeException("Type mismatch: cannot unify " + type1 + " with " + type2, currentFilename, null);
    }
    
    private Map<String, Type> substitutions = new HashMap<>();
    
    private void bind(String var, Type type) {
        substitutions.put(var, type);
    }
    
    private Type prune(Type type) {
        if (type instanceof Type.TVar(String name) && substitutions.containsKey(name)) {
            Type pruned = prune(substitutions.get(name));
            substitutions.put(name, pruned);
            return pruned;
        }
        if (type instanceof Type.TNumeric(String name) && substitutions.containsKey(name)) {
            Type pruned = prune(substitutions.get(name));
            substitutions.put(name, pruned);
            return pruned;
        }
        return type;
    }
    
    private Type fullyPrune(Type type) {
        Type pruned = prune(type);
        return switch (pruned) {
            case Type.TFun(Type param, Type result) ->
                new Type.TFun(fullyPrune(param), fullyPrune(result));
            case Type.TList(Type elem) ->
                new Type.TList(fullyPrune(elem));
            default -> pruned;
        };
    }
    
    private boolean containsUnresolvedVars(Type type) {
        return switch (type) {
            case Type.TVar v -> true;
            case Type.TNumeric n -> true;
            case Type.TFun(Type param, Type result) ->
                containsUnresolvedVars(param) || containsUnresolvedVars(result);
            case Type.TList(Type elem) -> containsUnresolvedVars(elem);
            default -> false;
        };
    }
    
    private boolean occursInType(String var, Type type) {
        Type pruned = prune(type);
        if (pruned instanceof Type.TVar(String name)) {
            return name.equals(var);
        }
        if (pruned instanceof Type.TNumeric(String name)) {
            return name.equals(var);
        }
        if (pruned instanceof Type.TFun(Type param, Type result)) {
            return occursInType(var, param) || occursInType(var, result);
        }
        if (pruned instanceof Type.TList(Type elementType)) {
            return occursInType(var, elementType);
        }
        return false;
    }
    
    private Type applySubst(Map<String, Type> subst, Type type) {
        return switch (type) {
            case Type.TVar(String name) -> subst.getOrDefault(name, type);
            case Type.TFun(Type param, Type result) -> 
                new Type.TFun(applySubst(subst, param), applySubst(subst, result));
            case Type.TList(Type elementType) ->
                new Type.TList(applySubst(subst, elementType));
            default -> type;
        };
    }
    
    private Type resolveTypeAnnotation(Type annotation) {
        return annotation;
    }
    
    public Type getType(Expr expr) {
        return typeMap.get(expr);
    }
    
    public Map<Expr, Type> getTypeMap() {
        return typeMap;
    }
    
    public Map<String, Type> getEnvironment() {
        return new HashMap<>(env);
    }
    
    public static class TypeException extends Exception {
        private final String filename;
        private final Expr expr;

        public TypeException(String message, String filename, Expr expr) {
            super(message);
            this.filename = filename;
            this.expr = expr;
        }

        public TypeException(String message) {
            this(message, "<unknown>", null);
        }

        public String getFilename() {
            return filename;
        }

        public Expr getExpr() {
            return expr;
        }
    }
}
