package com.miniml;

import java.util.*;

public class TypeInference {
    private int nextVarId = 0;
    private Map<Expr, Type> typeMap = new HashMap<>();
    private Map<String, Type> env = new HashMap<>();
    private Map<String, Set<Type>> instantiations = new HashMap<>();
    
    public TypeInference() {
        initializeBuiltins();
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
    }
    
    public Type inferModule(Module module) throws TypeException {
        for (Module.TopLevel decl : module.declarations()) {
            if (decl instanceof Module.TopLevel.FnDecl(String name, List<Module.Param> params, Expr body)) {
                Type fnType = inferTopLevelFn(params, body);
                Type scheme = generalize(new HashMap<>(), fnType);
                env.put(name, scheme);
                
                typeMap.put(body, fnType);
                instantiations.put(name, new HashSet<>());
            } else if (decl instanceof Module.TopLevel.LetDecl(String name, Expr value)) {
                Type valueType = infer(env, value);
                Type scheme = generalize(new HashMap<>(), valueType);
                env.put(name, scheme);
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
    
    private Type inferTopLevelFn(List<Module.Param> params, Expr body) throws TypeException {
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
            case Expr.IntLit i -> new Type.TInt();
            case Expr.FloatLit f -> new Type.TDouble();
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
                yield freshVar();
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
                Type funcType = infer(localEnv, func);
                Type resultType = funcType;
                
                List<Type> argTypes = new ArrayList<>();
                for (Expr arg : args) {
                    Type argType = infer(localEnv, arg);
                    argTypes.add(argType);
                    Type freshResult = freshVar();
                    unify(resultType, new Type.TFun(argType, freshResult));
                    resultType = freshResult;
                }
                
                String funcName = null;
                if (func instanceof Expr.Var v) {
                    funcName = v.name();
                } else if (func instanceof Expr.QualifiedVar qv) {
                    funcName = qv.name();
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
                        Type reconstructed = finalType;
                        for (int i = argTypes.size() - 1; i >= 0; i--) {
                            reconstructed = new Type.TFun(prune(argTypes.get(i)), reconstructed);
                        }
                        instantiations.get(funcName).add(reconstructed);
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
            
            case Expr.BinOp(Expr.Op op, Expr left, Expr right) -> {
                Type leftType = infer(localEnv, left);
                Type rightType = infer(localEnv, right);
                
                yield switch (op) {
                    case ADD, SUB, MUL, DIV, MOD -> {
                        unify(leftType, rightType);
                        yield leftType;
                    }
                    case EQ, NE, LT, GT, LE, GE -> {
                        unify(leftType, rightType);
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
                infer(localEnv, instance);
                for (Expr arg : args) {
                    infer(localEnv, arg);
                }
                yield inferJavaInstanceCallType(className, methodName);
            }
        };
        
        typeMap.put(expr, type);
        return type;
    }
    
    public void pruneTypeMap() {
        Map<Expr, Type> prunedMap = new HashMap<>();
        for (Map.Entry<Expr, Type> entry : typeMap.entrySet()) {
            prunedMap.put(entry.getKey(), prune(entry.getValue()));
        }
        typeMap = prunedMap;
    }
    
    private Type inferJavaCallType(String className, String methodName) {
        if (className.equals("java.lang.Math")) {
            return switch (methodName) {
                case "sqrt", "sin", "cos", "tan", "log", "exp", "pow" -> new Type.TDouble();
                case "abs", "max", "min" -> new Type.TInt();
                default -> freshVar();
            };
        }
        return freshVar();
    }
    
    private Type inferJavaInstanceCallType(String className, String methodName) {
        if (className.equals("java.lang.String")) {
            return switch (methodName) {
                case "length" -> new Type.TInt();
                case "toUpperCase", "toLowerCase" -> new Type.TString();
                default -> freshVar();
            };
        }
        return freshVar();
    }
    
    private Type freshVar() {
        return new Type.TVar("t" + (nextVarId++));
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
        
        if (type1 instanceof Type.TFun(Type p1, Type r1) && type2 instanceof Type.TFun(Type p2, Type r2)) {
            unify(p1, p2);
            unify(r1, r2);
            return;
        }
        
        if (type1.getClass().equals(type2.getClass())) {
            return;
        }
        
        throw new TypeException("Type mismatch: cannot unify " + type1 + " with " + type2);
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
        return type;
    }
    
    private boolean occursInType(String var, Type type) {
        Type pruned = prune(type);
        if (pruned instanceof Type.TVar(String name)) {
            return name.equals(var);
        }
        if (pruned instanceof Type.TFun(Type param, Type result)) {
            return occursInType(var, param) || occursInType(var, result);
        }
        return false;
    }
    
    private Type applySubst(Map<String, Type> subst, Type type) {
        return switch (type) {
            case Type.TVar(String name) -> subst.getOrDefault(name, type);
            case Type.TFun(Type param, Type result) -> 
                new Type.TFun(applySubst(subst, param), applySubst(subst, result));
            default -> type;
        };
    }
    
    public Type getType(Expr expr) {
        return typeMap.get(expr);
    }
    
    public Map<Expr, Type> getTypeMap() {
        return typeMap;
    }
    
    public static class TypeException extends Exception {
        public TypeException(String message) {
            super(message);
        }
    }
}
