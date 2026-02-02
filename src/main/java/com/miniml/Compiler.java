package com.miniml;

import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;
import com.miniml.expr.*;
import com.miniml.expr.Expr.Op;
import com.miniml.expr.Expr.UnOp;

public class Compiler {
    private final String className;
    private final ClassWriter cw;
    private MethodVisitor mv;
    private final Map<String, Integer> locals = new HashMap<>();
    private final Map<String, String> localTypes = new HashMap<>();
    private int nextLocal = 0;
    private int labelCounter = 0;
    private List<String> imports = new ArrayList<>();
    private final Map<Expr, Type> typeMap;
    private final Map<String, Set<Type>> instantiations;
    private final Map<String, String> javaImports = new HashMap<>();

    public Compiler(String className) {
        this(className, new HashMap<>(), new HashMap<>());
    }
    
    public Compiler(String className, Map<Expr, Type> typeMap, Map<String, Set<Type>> instantiations) {
        this.className = className;
        this.cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        this.typeMap = typeMap;
        this.instantiations = instantiations;
    }
    
    private String resolveJavaClassName(String shortName) {
        if (shortName.contains(".")) {
            return shortName;
        }
        
        String imported = javaImports.get(shortName);
        if (imported != null) {
            return imported;
        }
        
        try {
            Class.forName("java.lang." + shortName);
            return "java.lang." + shortName;
        } catch (ClassNotFoundException e) {
            return shortName;
        }
    }

    public byte[] compileModule(Module module) {
        cw.visit(V17, ACC_PUBLIC, className, null, "java/lang/Object", null);
        
        this.imports = module.imports();
        
        List<Module.TopLevel.LetDecl> letDecls = new ArrayList<>();
        
        for (Module.TopLevel decl : module.declarations()) {
            if (decl instanceof Module.TopLevel.FnDecl(String name, List<Module.Param> params, var returnType, Expr body)) {
                Set<Type> types = instantiations.getOrDefault(name, Set.of());
                if (types.isEmpty()) {
                    compileTopLevelFunction(name, params, body, null);
                } else {
                    for (Type type : types) {
                        compileTopLevelFunction(name, params, body, type);
                    }
                }
            } else if (decl instanceof Module.TopLevel.LetDecl letDecl) {
                letDecls.add(letDecl);
            } else if (decl instanceof Module.TopLevel.TypeDef(String typeName, List<String> typeParams, List<Module.Constructor> constructors)) {
                generateSumTypeInterface(typeName);
                for (Module.Constructor ctor : constructors) {
                    generateConstructorClass(ctor.name(), ctor.paramType().isPresent(), typeName);
                }
            }
        }
        
        if (!letDecls.isEmpty()) {
            compileStaticInitializer(letDecls);
        }
        
        if (module.mainExpr() != null) {
            compileMainMethod(module.mainExpr());
        }
        
        compileConstructor();
        
        cw.visitEnd();
        return cw.toByteArray();
    }

    public byte[] compile(Expr expr) {
        cw.visit(V17, ACC_PUBLIC, className, null, "java/lang/Object", null);

        compileMainMethod(expr);
        compileConstructor();

        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private void compileTopLevelFunction(String name, List<Module.Param> params, Expr body, Type instantiationType) {
        MethodVisitor prevMv = mv;
        Map<String, Integer> prevLocals = new HashMap<>(locals);
        Map<String, String> prevLocalTypes = new HashMap<>(localTypes);
        int prevNextLocal = nextLocal;

        locals.clear();
        localTypes.clear();
        nextLocal = 0;
        
        List<String> paramTypes = new ArrayList<>();
        Type currentType = instantiationType;
        
        for (Module.Param param : params) {
            String paramType = "I";
            if (currentType instanceof Type.TFun(Type paramT, Type resultT)) {
                paramType = paramT.toJvmType();
                currentType = resultT;
            } else if (param.typeAnnotation().isPresent()) {
                paramType = param.typeAnnotation().get().toJvmType();
            }
            locals.put(param.name(), nextLocal);
            nextLocal += paramType.equals("D") ? 2 : 1;
            localTypes.put(param.name(), paramType);
            paramTypes.add(paramType);
        }
        
        String returnType;
        if (currentType != null) {
            returnType = currentType.toJvmType();
        } else {
            Type bodyType = typeMap.getOrDefault(body, new Type.TInt());
            returnType = bodyType.toJvmType();
        }
        
        String methodName = name;
        if (instantiationType != null) {
            methodName = name + "$" + getTypeSuffix(instantiationType);
        }
        
        String descriptor = "(" + String.join("", paramTypes) + ")" + returnType;
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, methodName, descriptor, null, null);
        mv.visitCode();

        compileExpr(body);
        
        if (returnType.equals("D")) {
            mv.visitInsn(DRETURN);
        } else if (returnType.equals("V")) {
            mv.visitInsn(RETURN);
        } else if (returnType.startsWith("L")) {
            mv.visitInsn(ARETURN);
        } else {
            mv.visitInsn(IRETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = prevMv;
        locals.clear();
        locals.putAll(prevLocals);
        localTypes.clear();
        localTypes.putAll(prevLocalTypes);
        nextLocal = prevNextLocal;
    }
    
    private void compileStaticInitializer(List<Module.TopLevel.LetDecl> letDecls) {
        for (Module.TopLevel.LetDecl letDecl : letDecls) {
            Type valueType = typeMap.getOrDefault(letDecl.value(), new Type.TInt());
            String jvmType = valueType.toJvmType();
            cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, letDecl.name(), jvmType, null, null).visitEnd();
        }
        
        MethodVisitor prevMv = mv;
        Map<String, Integer> prevLocals = new HashMap<>(locals);
        Map<String, String> prevLocalTypes = new HashMap<>(localTypes);
        int prevNextLocal = nextLocal;
        
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        locals.clear();
        localTypes.clear();
        nextLocal = 0;
        
        mv.visitCode();
        for (Module.TopLevel.LetDecl letDecl : letDecls) {
            Type valueType = typeMap.getOrDefault(letDecl.value(), new Type.TInt());
            String jvmType = valueType.toJvmType();
            compileExpr(letDecl.value());
            mv.visitFieldInsn(PUTSTATIC, className, letDecl.name(), jvmType);
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        mv = prevMv;
        locals.clear();
        locals.putAll(prevLocals);
        localTypes.clear();
        localTypes.putAll(prevLocalTypes);
        nextLocal = prevNextLocal;
    }
    
    private String getTypeSuffix(Type type) {
        if (type instanceof Type.TFun(Type param, Type result)) {
            String paramSuffix = switch (param) {
                case Type.TInt() -> "Int";
                case Type.TDouble() -> "Double";
                case Type.TString() -> "String";
                case Type.TBool() -> "Bool";
                default -> "Generic";
            };
            return paramSuffix;
        }
        return switch (type) {
            case Type.TInt() -> "Int";
            case Type.TDouble() -> "Double";
            case Type.TString() -> "String";
            case Type.TBool() -> "Bool";
            default -> "Generic";
        };
    }

    private void compileConstructor() {
        MethodVisitor constructor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private void compileMainMethod(Expr expr) {
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();

        locals.clear();
        localTypes.clear();
        nextLocal = 0;
        
        compileExpr(expr);

        Type exprType = typeMap.getOrDefault(expr, new Type.TUnit());
        if (exprType instanceof Type.TUnit) {
            mv.visitInsn(POP);
        } else {
            String jvmType = exprType.toJvmType();
            if (jvmType.equals("D")) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitInsn(DUP_X2);
                mv.visitInsn(POP);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(D)V", false);
            } else if (exprType instanceof Type.TBool) {
                Label trueLabel = new Label();
                Label endLabel = new Label();
                mv.visitJumpInsn(IFNE, trueLabel);
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("false");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                mv.visitJumpInsn(GOTO, endLabel);
                mv.visitLabel(trueLabel);
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("true");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                mv.visitLabel(endLabel);
            } else if (exprType instanceof Type.TList) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
            } else if (exprType instanceof Type.TJava) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
            } else if (exprType instanceof Type.TString) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            } else {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
            }
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void compileExpr(Expr expr) {
        switch (expr) {
            case com.miniml.expr.Unit u -> mv.visitFieldInsn(GETSTATIC, "com/miniml/Unit", "INSTANCE", "Lcom/miniml/Unit;");
            
            case IntLit(int value) -> mv.visitLdcInsn(value);
            
            case FloatLit(double value) -> mv.visitLdcInsn(value);
            
            case BoolLit(boolean value) -> mv.visitInsn(value ? ICONST_1 : ICONST_0);
            
            case StringLit(String value) -> mv.visitLdcInsn(value);
            
            case StringInterp(List<Object> parts) -> {
                mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                
                for (Object part : parts) {
                    if (part instanceof String str) {
                        mv.visitLdcInsn(str);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    } else if (part instanceof Var(String varName)) {
                        String type = localTypes.getOrDefault(varName, "I");
                        Integer local = locals.get(varName);
                        if (local == null) {
                            throw new RuntimeException("Undefined variable: " + varName);
                        }
                        if (type.equals("Ljava/lang/String;")) {
                            mv.visitVarInsn(ALOAD, local);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                        } else {
                            mv.visitVarInsn(ILOAD, local);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
                        }
                    } else if (part instanceof Expr e) {
                        compileExpr(e);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
                    }
                }
                
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            }
            
            case Print(Expr value) -> {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                compileExpr(value);
                Type valueType = typeMap.getOrDefault(value, new Type.TInt());
                if (value instanceof Var(String varName)) {
                    String jvmType = localTypes.getOrDefault(varName, "I");
                    valueType = jvmType.equals("D") ? new Type.TDouble() : new Type.TInt();
                }
                if (value instanceof StringLit || value instanceof StringInterp) {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                } else if (valueType instanceof Type.TDouble) {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(D)V", false);
                } else {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
                }
                mv.visitInsn(ICONST_0);
            }
            
            case Var(String name) -> {
                Integer local = locals.get(name);
                if (local != null) {
                    String type = localTypes.getOrDefault(name, "I");
                    if (type.equals("D")) {
                        mv.visitVarInsn(DLOAD, local);
                    } else if (type.startsWith("L") || type.startsWith("[")) {
                        mv.visitVarInsn(ALOAD, local);
                    } else {
                        mv.visitVarInsn(ILOAD, local);
                    }
                } else {
                    Type varType = typeMap.getOrDefault(expr, new Type.TInt());
                    String jvmType = varType.toJvmType();
                    mv.visitFieldInsn(GETSTATIC, className, name, jvmType);
                }
            }
            
            case QualifiedVar(String moduleName, String memberName) -> {
                String descriptor = "()I";
                mv.visitMethodInsn(INVOKESTATIC, moduleName, memberName, descriptor, false);
            }
            
            case UnaryOp(UnOp op, Expr operand) -> {
                compileExpr(operand);
                Type operandType = typeMap.getOrDefault(operand, new Type.TInt());
                switch (op) {
                    case NEG -> {
                        if (operandType instanceof Type.TDouble) {
                            mv.visitInsn(DNEG);
                        } else {
                            mv.visitInsn(INEG);
                        }
                    }
                    case NOT -> {
                        Label trueLabel = new Label();
                        Label endLabel = new Label();
                        mv.visitJumpInsn(IFEQ, trueLabel);
                        mv.visitInsn(ICONST_0);
                        mv.visitJumpInsn(GOTO, endLabel);
                        mv.visitLabel(trueLabel);
                        mv.visitInsn(ICONST_1);
                        mv.visitLabel(endLabel);
                    }
                }
            }
            
            case BinOp(Op op, Expr left, Expr right) -> {
                if (op == Op.AND) {
                    Label falseLabel = new Label();
                    Label endLabel = new Label();
                    compileExpr(left);
                    mv.visitJumpInsn(IFEQ, falseLabel);
                    compileExpr(right);
                    mv.visitJumpInsn(IFEQ, falseLabel);
                    mv.visitInsn(ICONST_1);
                    mv.visitJumpInsn(GOTO, endLabel);
                    mv.visitLabel(falseLabel);
                    mv.visitInsn(ICONST_0);
                    mv.visitLabel(endLabel);
                } else if (op == Op.OR) {
                    Label trueLabel = new Label();
                    Label endLabel = new Label();
                    compileExpr(left);
                    mv.visitJumpInsn(IFNE, trueLabel);
                    compileExpr(right);
                    mv.visitJumpInsn(IFNE, trueLabel);
                    mv.visitInsn(ICONST_0);
                    mv.visitJumpInsn(GOTO, endLabel);
                    mv.visitLabel(trueLabel);
                    mv.visitInsn(ICONST_1);
                    mv.visitLabel(endLabel);
                } else {
                    compileExpr(left);
                    Type leftType = typeMap.getOrDefault(left, new Type.TInt());
                    
                    if (left instanceof Var(String varName) && localTypes.get(varName) != null && localTypes.get(varName).equals("Ljava/lang/Object;")) {
                        if (leftType instanceof Type.TInt) {
                            mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        } else if (leftType instanceof Type.TDouble) {
                            mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                        } else if (leftType instanceof Type.TString) {
                            mv.visitTypeInsn(CHECKCAST, "java/lang/String");
                        }
                    }
                    
                    compileExpr(right);
                    Type rightType = typeMap.getOrDefault(right, new Type.TInt());
                    
                    if (right instanceof Var(String varName) && localTypes.get(varName) != null && localTypes.get(varName).equals("Ljava/lang/Object;")) {
                        if (rightType instanceof Type.TInt) {
                            mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        } else if (rightType instanceof Type.TDouble) {
                            mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                        } else if (rightType instanceof Type.TString) {
                            mv.visitTypeInsn(CHECKCAST, "java/lang/String");
                        }
                    }
                    
                    boolean leftIsDouble = leftType instanceof Type.TDouble;
                    boolean rightIsDouble = rightType instanceof Type.TDouble;
                    boolean isDouble = leftIsDouble || rightIsDouble;
                    boolean isString = leftType instanceof Type.TString || rightType instanceof Type.TString;
                    
                    if (isDouble) {
                        if (!rightIsDouble) {
                            mv.visitInsn(I2D);
                        } else if (!leftIsDouble) {
                            mv.visitInsn(DUP2_X1);
                            mv.visitInsn(POP2);
                            mv.visitInsn(I2D);
                            mv.visitInsn(DUP2_X2);
                            mv.visitInsn(POP2);
                        }
                    }
                    
                    switch (op) {
                        case ADD -> mv.visitInsn(isDouble ? DADD : IADD);
                        case SUB -> mv.visitInsn(isDouble ? DSUB : ISUB);
                        case MUL -> mv.visitInsn(isDouble ? DMUL : IMUL);
                        case DIV -> mv.visitInsn(isDouble ? DDIV : IDIV);
                        case MOD -> mv.visitInsn(isDouble ? DREM : IREM);
                        case EQ, NE, LT, GT, LE, GE -> compileComparison(op, leftType, rightType);
                        default -> throw new RuntimeException("Unexpected operator: " + op);
                    }
                }
            }
            
            case If(Expr cond, Expr thenBranch, Expr elseBranch) -> {
                Label elseLabel = new Label();
                Label endLabel = new Label();
                
                compileExpr(cond);
                mv.visitJumpInsn(IFEQ, elseLabel);
                
                compileExpr(thenBranch);
                mv.visitJumpInsn(GOTO, endLabel);
                
                mv.visitLabel(elseLabel);
                compileExpr(elseBranch);
                
                mv.visitLabel(endLabel);
            }
            
            case Let(String name, Expr value, Expr body) -> {
                compileExpr(value);
                
                Type valueType = typeMap.get(value);
                if (valueType == null) {
                    valueType = new Type.TInt();
                }
                String jvmType = valueType.toJvmType();
                
                int local = nextLocal;
                locals.put(name, local);
                localTypes.put(name, jvmType);
                
                if (jvmType.equals("D")) {
                    mv.visitVarInsn(DSTORE, local);
                    nextLocal += 2;
                } else if (jvmType.startsWith("L")) {
                    mv.visitVarInsn(ASTORE, local);
                    nextLocal += 1;
                } else {
                    mv.visitVarInsn(ISTORE, local);
                    nextLocal += 1;
                }
                
                compileExpr(body);
                freeLocal(name);
            }
            
            case LetRec(String name, List<String> params, Expr value, Expr body) -> {
                String methodName = "lambda_" + name;
                compileLambdaMethod(methodName, params, value);
                compileExpr(body);
            }
            
            case Lambda(List<String> params, Expr lambdaBody) -> {
                String methodName = "lambda_" + (labelCounter++);
                compileLambdaMethod(methodName, params, lambdaBody);
            }
            
            case App(Expr func, List<Expr> args) -> {
                if (func instanceof Var(String funcName)) {
                    if ("box".equals(funcName) && args.size() == 1) {
                        compileExpr(args.get(0));
                        Type argType = typeMap.getOrDefault(args.get(0), new Type.TInt());
                        switch (argType) {
                            case Type.TInt i -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                            case Type.TDouble d -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                            case Type.TBool b -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                            default -> throw new RuntimeException("Cannot box type: " + argType);
                        }
                        return;
                    }
                    
                    List<String> argTypeDescs = new ArrayList<>();
                    List<Type> argTypes = new ArrayList<>();
                    for (Expr arg : args) {
                        compileExpr(arg);
                        Type argType = typeMap.getOrDefault(arg, new Type.TInt());
                        argTypes.add(argType);
                        argTypeDescs.add(argType.toJvmType());
                    }
                    
                    String methodName = funcName;
                    String returnType;
                    
                    if (instantiations.containsKey(funcName) && !instantiations.get(funcName).isEmpty()) {
                        Type firstArgType = argTypes.isEmpty() ? new Type.TInt() : argTypes.get(0);
                        returnType = firstArgType.toJvmType();
                        
                        Type reconstructedType = firstArgType;
                        for (int i = argTypes.size() - 1; i >= 0; i--) {
                            reconstructedType = new Type.TFun(argTypes.get(i), reconstructedType);
                        }
                        
                        String suffix = getTypeSuffix(reconstructedType);
                        methodName = funcName + "$" + suffix;
                    } else {
                        methodName = "lambda_" + funcName;
                        Type appType = typeMap.getOrDefault(expr, new Type.TInt());
                        returnType = appType.toJvmType();
                    }
                    
                    String descriptor = "(" + String.join("", argTypeDescs) + ")" + returnType;
                    mv.visitMethodInsn(INVOKESTATIC, className, methodName, descriptor, false);
                } else if (func instanceof QualifiedVar(String moduleName, String memberName)) {
                    List<String> argTypeDescs = new ArrayList<>();
                    for (Expr arg : args) {
                        compileExpr(arg);
                        Type argType = typeMap.getOrDefault(arg, new Type.TInt());
                        argTypeDescs.add(argType.toJvmType());
                    }
                    
                    Type appType = typeMap.getOrDefault(expr, new Type.TInt());
                    String returnTypeDesc = appType.toJvmType();
                    String descriptor = "(" + String.join("", argTypeDescs) + ")" + returnTypeDesc;
                    mv.visitMethodInsn(INVOKESTATIC, moduleName, memberName, descriptor, false);
                } else {
                    throw new RuntimeException("Only named functions can be called for now");
                }
            }
            
            case Sequence(List<Expr> exprs) -> {
                for (int i = 0; i < exprs.size(); i++) {
                    compileExpr(exprs.get(i));
                    if (i < exprs.size() - 1) {
                        mv.visitInsn(POP);
                    }
                }
            }
            
            case JavaCall javaCall -> {
                String className = javaCall.className();
                String methodName = javaCall.methodName();
                List<Expr> args = javaCall.args();
                
                String actualClassName = resolveJavaClassName(className);
                Type javaCallType = typeMap.get(javaCall);
                if (javaCallType instanceof Type.TJava(String fullName, List<Type> typeArgs)) {
                    actualClassName = fullName;
                }
                String jvmClassName = actualClassName.replace('.', '/');
                
                if (methodName.equals("new")) {
                    mv.visitTypeInsn(NEW, jvmClassName);
                    mv.visitInsn(DUP);
                    
                    for (Expr arg : args) {
                        compileExpr(arg);
                    }
                    
                    String descriptor = inferJavaConstructorDescriptor(args);
                    mv.visitMethodInsn(INVOKESPECIAL, jvmClassName, "<init>", descriptor, false);
                } else {
                    for (Expr arg : args) {
                        compileExpr(arg);
                    }
                    
                    String descriptor = inferJavaMethodDescriptor(javaCall, actualClassName, methodName, args);
                    mv.visitMethodInsn(INVOKESTATIC, jvmClassName, methodName, descriptor, false);
                }
            }
            
            case JavaInstanceCall javaInstanceCall -> {
                compileExpr(javaInstanceCall.instance());
                
                Type instanceType = typeMap.getOrDefault(javaInstanceCall.instance(), new Type.TInt());
                
                for (Expr arg : javaInstanceCall.args()) {
                    compileExpr(arg);
                }
                
                String actualClassName = getJavaClassName(instanceType);
                String jvmClassName = actualClassName.replace('.', '/');
                String descriptor = inferInstanceMethodDescriptor(javaInstanceCall, actualClassName, javaInstanceCall.methodName(), javaInstanceCall.args());
                mv.visitMethodInsn(INVOKEVIRTUAL, jvmClassName, javaInstanceCall.methodName(), descriptor, false);
            }
            
            case JavaStaticField(String className, String fieldName) -> {
                String jvmClassName = className.replace('.', '/');
                Type fieldType = typeMap.getOrDefault(expr, new Type.TInt());
                String descriptor = fieldType.toJvmType();
                mv.visitFieldInsn(GETSTATIC, jvmClassName, fieldName, descriptor);
            }
            
            case ListLit(List<Expr> elements) -> {
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                
                for (Expr elem : elements) {
                    mv.visitInsn(DUP);
                    compileExpr(elem);
                    Type elemType = typeMap.getOrDefault(elem, new Type.TInt());
                    if (elemType instanceof Type.TInt) {
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    } else if (elemType instanceof Type.TDouble) {
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    } else if (elemType instanceof Type.TBool) {
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    }
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
                    mv.visitInsn(POP);
                }
            }
            
            case Constructor(String name, java.util.Optional<Expr> arg) -> {
                String className = "com/miniml/" + name;
                mv.visitTypeInsn(NEW, className);
                mv.visitInsn(DUP);
                if (arg.isPresent()) {
                    compileExpr(arg.get());
                    Type argType = typeMap.getOrDefault(arg.get(), new Type.TInt());
                    boxIfNeeded(argType);
                    mv.visitMethodInsn(INVOKESPECIAL, className, "<init>", "(Ljava/lang/Object;)V", false);
                } else {
                    mv.visitMethodInsn(INVOKESPECIAL, className, "<init>", "()V", false);
                }
            }
            
            case Cons(Expr head, Expr tail) -> {
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                
                mv.visitInsn(DUP);
                compileExpr(head);
                Type headType = typeMap.getOrDefault(head, new Type.TInt());
                if (headType instanceof Type.TInt) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                } else if (headType instanceof Type.TDouble) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                } else if (headType instanceof Type.TBool) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                }
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
                mv.visitInsn(POP);
                
                mv.visitInsn(DUP);
                compileExpr(tail);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true);
                mv.visitInsn(POP);
            }
            
            case Match(Expr scrutinee, List<Match.MatchCase> cases) -> {
                compileExpr(scrutinee);
                int scrutineeLocal = allocLocal("$scrutinee");
                mv.visitVarInsn(ASTORE, scrutineeLocal);
                String scrutineeJvmType = inferType(scrutinee);
                localTypes.put("$scrutinee", scrutineeJvmType);
                
                Label endLabel = new Label();
                int savedNextLocal = nextLocal;
                
                for (int i = 0; i < cases.size(); i++) {
                    Match.MatchCase matchCase = cases.get(i);
                    Label nextCaseLabel = (i < cases.size() - 1) ? new Label() : null;
                    
                    Map<String, Integer> savedLocals = new HashMap<>(locals);
                    Map<String, String> savedLocalTypes = new HashMap<>(localTypes);
                    nextLocal = savedNextLocal;
                    
                    compilePattern(matchCase.pattern(), scrutinee, scrutineeLocal, nextCaseLabel, endLabel);
                    compileExpr(matchCase.body());
                    mv.visitJumpInsn(GOTO, endLabel);
                    
                    locals.clear();
                    locals.putAll(savedLocals);
                    localTypes.clear();
                    localTypes.putAll(savedLocalTypes);
                    
                    if (nextCaseLabel != null) {
                        mv.visitLabel(nextCaseLabel);
                    }
                }
                
                nextLocal = savedNextLocal;
                mv.visitLabel(endLabel);
                freeLocal("$scrutinee");
            }
        }
    }
    
    private void compilePattern(Pattern pattern, Expr scrutinee, int scrutineeLocal, Label failLabel, Label endLabel) {
        switch (pattern) {
            case Pattern.Wildcard() -> {
            }
            
            case Pattern.Var(String name) -> {
                mv.visitVarInsn(ALOAD, scrutineeLocal);
                int varLocal = allocLocal(name);
                mv.visitVarInsn(ASTORE, varLocal);
                localTypes.put(name, "Ljava/util/List;");
            }
            
            case Pattern.IntLit(int value) -> {
                mv.visitVarInsn(ALOAD, scrutineeLocal);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
                if (failLabel != null) {
                    mv.visitJumpInsn(IFNE, failLabel);
                }
                
                mv.visitVarInsn(ALOAD, scrutineeLocal);
                mv.visitInsn(ICONST_0);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                mv.visitLdcInsn(value);
                if (failLabel != null) {
                    mv.visitJumpInsn(IF_ICMPNE, failLabel);
                }
            }
            
            case Pattern.Nil() -> {
                mv.visitVarInsn(ALOAD, scrutineeLocal);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
                if (failLabel != null) {
                    mv.visitJumpInsn(IFEQ, failLabel);
                }
            }
            
            case Pattern.Cons(Pattern head, Pattern tail) -> {
                mv.visitVarInsn(ALOAD, scrutineeLocal);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
                if (failLabel != null) {
                    mv.visitJumpInsn(IFNE, failLabel);
                } else {
                    Label matchOk = new Label();
                    mv.visitJumpInsn(IFEQ, matchOk);
                    mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
                    mv.visitInsn(DUP);
                    mv.visitLdcInsn("Match failure: expected non-empty list");
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
                    mv.visitInsn(ATHROW);
                    mv.visitLabel(matchOk);
                }
                
                if (head instanceof Pattern.Var(String headName)) {
                    Type scrutineeType = typeMap.getOrDefault(scrutinee, new Type.TList(new Type.TInt()));
                    Type elemType = (scrutineeType instanceof Type.TList(Type inner)) ? inner : new Type.TInt();
                    
                    mv.visitVarInsn(ALOAD, scrutineeLocal);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
                    
                    String jvmType = typeToJVMType(elemType);
                    if (jvmType.equals("I")) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        int headVarLocal = allocLocal(headName);
                        mv.visitVarInsn(ISTORE, headVarLocal);
                        localTypes.put(headName, "I");
                    } else if (jvmType.equals("D")) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                        int headVarLocal = allocLocal(headName);
                        mv.visitVarInsn(DSTORE, headVarLocal);
                        localTypes.put(headName, "D");
                    } else {
                        mv.visitTypeInsn(CHECKCAST, typeToClassName(jvmType));
                        int headVarLocal = allocLocal(headName);
                        mv.visitVarInsn(ASTORE, headVarLocal);
                        localTypes.put(headName, jvmType);
                    }
                }
                
                if (tail instanceof Pattern.Var(String tailName)) {
                    mv.visitVarInsn(ALOAD, scrutineeLocal);
                    mv.visitInsn(ICONST_1);
                    mv.visitVarInsn(ALOAD, scrutineeLocal);
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "subList", "(II)Ljava/util/List;", true);
                    int tailVarLocal = allocLocal(tailName);
                    mv.visitVarInsn(ASTORE, tailVarLocal);
                    localTypes.put(tailName, "Ljava/util/List;");
                }
            }
            
            case Pattern.Constructor(String name, java.util.Optional<Pattern> arg) -> {
                String className = "com/miniml/" + name;
                mv.visitVarInsn(ALOAD, scrutineeLocal);
                mv.visitTypeInsn(INSTANCEOF, className);
                if (failLabel != null) {
                    mv.visitJumpInsn(IFEQ, failLabel);
                } else {
                    mv.visitInsn(POP);
                }
                
                if (arg.isPresent() && arg.get() instanceof Pattern.Var(String varName)) {
                    mv.visitVarInsn(ALOAD, scrutineeLocal);
                    mv.visitTypeInsn(CHECKCAST, className);
                    mv.visitMethodInsn(INVOKEVIRTUAL, className, "value", "()Ljava/lang/Object;", false);
                    int varLocal = allocLocal(varName);
                    mv.visitVarInsn(ASTORE, varLocal);
                    localTypes.put(varName, "Ljava/lang/Object;");
                }
            }
            
            default -> throw new RuntimeException("Pattern not yet implemented: " + pattern);
        }
    }
    
    private String inferJavaMethodDescriptor(Expr javaCallExpr, String className, String methodName, List<Expr> args) {
        StringBuilder desc = new StringBuilder("(");
        for (Expr arg : args) {
            Type argType = typeMap.getOrDefault(arg, new Type.TInt());
            desc.append(argType.toJvmType());
        }
        desc.append(")");
        
        Type returnType = typeMap.getOrDefault(javaCallExpr, new Type.TInt());
        desc.append(returnType.toJvmType());
        
        return desc.toString();
    }
    
    private String inferJavaConstructorDescriptor(List<Expr> args) {
        StringBuilder desc = new StringBuilder("(");
        for (Expr arg : args) {
            Type argType = typeMap.getOrDefault(arg, new Type.TInt());
            desc.append(argType.toJvmType());
        }
        desc.append(")V");
        
        return desc.toString();
    }
    
    private String getJavaClassName(Type type) {
        return switch (type) {
            case Type.TString s -> "java.lang.String";
            case Type.TInt i -> "java.lang.Integer";
            case Type.TDouble d -> "java.lang.Double";
            case Type.TBool b -> "java.lang.Boolean";
            case Type.TName(String name) -> "java.lang." + name;
            case Type.TJava(String className, List<Type> typeArgs) -> className;
            default -> "java.lang.Object";
        };
    }
    
    private String inferInstanceMethodDescriptor(Expr instanceCallExpr, String className, String methodName, List<Expr> args) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Method bestMethod = null;
            
            for (java.lang.reflect.Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && 
                    method.getParameterCount() == args.size() &&
                    java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    
                    Class<?>[] paramTypes = method.getParameterTypes();
                    boolean matches = true;
                    for (int i = 0; i < args.size(); i++) {
                        Type argType = typeMap.getOrDefault(args.get(i), new Type.TInt());
                        String argJvmType = argType.toJvmType();
                        String paramJvmType = org.objectweb.asm.Type.getDescriptor(paramTypes[i]);
                        
                        if (!isCompatible(argJvmType, paramJvmType)) {
                            matches = false;
                            break;
                        }
                    }
                    
                    if (matches) {
                        if (bestMethod == null || method.getReturnType().equals(clazz)) {
                            bestMethod = method;
                            if (method.getReturnType().equals(clazz)) {
                                break;
                            }
                        }
                    }
                }
            }
            
            if (bestMethod != null) {
                StringBuilder desc = new StringBuilder("(");
                for (Class<?> paramType : bestMethod.getParameterTypes()) {
                    desc.append(org.objectweb.asm.Type.getDescriptor(paramType));
                }
                desc.append(")");
                desc.append(org.objectweb.asm.Type.getDescriptor(bestMethod.getReturnType()));
                return desc.toString();
            }
        } catch (ClassNotFoundException e) {
        }
        
        StringBuilder desc = new StringBuilder("(");
        for (Expr arg : args) {
            Type argType = typeMap.getOrDefault(arg, new Type.TInt());
            desc.append(argType.toJvmType());
        }
        desc.append(")");
        Type returnType = typeMap.getOrDefault(instanceCallExpr, new Type.TInt());
        desc.append(returnType.toJvmType());
        return desc.toString();
    }
    
    private boolean isCompatible(String argType, String paramType) {
        if (argType.equals(paramType)) {
            return true;
        }
        
        if (argType.equals("Ljava/lang/String;") && paramType.equals("Ljava/lang/CharSequence;")) {
            return true;
        }
        if (argType.equals("Ljava/lang/String;") && paramType.equals("Ljava/lang/Object;")) {
            return true;
        }
        
        if (argType.equals("I") && (paramType.equals("I") || paramType.equals("Ljava/lang/Integer;"))) {
            return true;
        }
        if (argType.equals("D") && (paramType.equals("D") || paramType.equals("Ljava/lang/Double;"))) {
            return true;
        }
        
        return false;
    }

    private void compileComparison(Op op, Type leftType, Type rightType) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        
        boolean isString = leftType instanceof Type.TString || rightType instanceof Type.TString;
        boolean isDouble = leftType instanceof Type.TDouble || rightType instanceof Type.TDouble;
        boolean isUnit = leftType instanceof Type.TUnit || rightType instanceof Type.TUnit;
        
        if (isUnit) {
            if (op == Op.EQ || op == Op.NE) {
                mv.visitMethodInsn(INVOKEVIRTUAL, "com/miniml/Unit", "equals", "(Ljava/lang/Object;)Z", false);
                if (op == Op.EQ) {
                    mv.visitJumpInsn(IFNE, trueLabel);
                } else {
                    mv.visitJumpInsn(IFEQ, trueLabel);
                }
            } else {
                throw new RuntimeException("Cannot use comparison operators other than == and != on unit type");
            }
        } else if (isString) {
            if (op == Op.EQ || op == Op.NE) {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                if (op == Op.EQ) {
                    mv.visitJumpInsn(IFNE, trueLabel);
                } else {
                    mv.visitJumpInsn(IFEQ, trueLabel);
                }
            } else {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "compareTo", "(Ljava/lang/String;)I", false);
                int opcode = switch (op) {
                    case LT -> IFLT;
                    case GT -> IFGT;
                    case LE -> IFLE;
                    case GE -> IFGE;
                    default -> throw new RuntimeException("Not a comparison op: " + op);
                };
                mv.visitJumpInsn(opcode, trueLabel);
            }
        } else if (isDouble) {
            mv.visitInsn(DCMPG);
            int opcode = switch (op) {
                case EQ -> IFEQ;
                case NE -> IFNE;
                case LT -> IFLT;
                case GT -> IFGT;
                case LE -> IFLE;
                case GE -> IFGE;
                default -> throw new RuntimeException("Not a comparison op: " + op);
            };
            mv.visitJumpInsn(opcode, trueLabel);
        } else {
            int opcode = switch (op) {
                case EQ -> IF_ICMPEQ;
                case NE -> IF_ICMPNE;
                case LT -> IF_ICMPLT;
                case GT -> IF_ICMPGT;
                case LE -> IF_ICMPLE;
                case GE -> IF_ICMPGE;
                default -> throw new RuntimeException("Not a comparison op: " + op);
            };
            mv.visitJumpInsn(opcode, trueLabel);
        }
        
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(endLabel);
    }

    private void compileLambdaMethod(String methodName, List<String> params, Expr body) {
        MethodVisitor prevMv = mv;
        Map<String, Integer> prevLocals = new HashMap<>(locals);
        Map<String, String> prevLocalTypes = new HashMap<>(localTypes);
        int prevNextLocal = nextLocal;

        String descriptor = "(" + "I".repeat(params.size()) + ")I";
        mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, methodName, descriptor, null, null);
        mv.visitCode();

        locals.clear();
        localTypes.clear();
        nextLocal = 0;
        for (String param : params) {
            locals.put(param, nextLocal++);
            localTypes.put(param, "I");
        }

        compileExpr(body);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = prevMv;
        locals.clear();
        locals.putAll(prevLocals);
        localTypes.clear();
        localTypes.putAll(prevLocalTypes);
        nextLocal = prevNextLocal;
    }

    private int allocLocal(String name) {
        int local = nextLocal++;
        locals.put(name, local);
        return local;
    }

    private void freeLocal(String name) {
        locals.remove(name);
    }
    
    private String inferType(Expr expr) {
        return switch (expr) {
            case com.miniml.expr.Unit u -> "Lcom/miniml/Unit;";
            case IntLit i -> "I";
            case FloatLit f -> "D";
            case BoolLit b -> "I";
            case StringLit s -> "Ljava/lang/String;";
            case StringInterp si -> "Ljava/lang/String;";
            case Var(String name) -> localTypes.getOrDefault(name, "I");
            case UnaryOp(UnOp op, Expr operand) -> inferType(operand);
            case BinOp(Op op, Expr left, Expr right) -> inferType(left);
            case If(Expr cond, Expr thenBranch, Expr elseBranch) -> inferType(thenBranch);
            case Let(String n, Expr v, Expr body) -> inferType(body);
            case LetRec(String n, List<String> p, Expr v, Expr body) -> inferType(body);
            case Print p -> "I";
            case Sequence(List<Expr> exprs) -> 
                exprs.isEmpty() ? "I" : inferType(exprs.get(exprs.size() - 1));
            case JavaCall(String className, String methodName, List<Expr> args) -> 
                inferJavaCallReturnType(className, methodName);
            case JavaInstanceCall(String className, String methodName, Expr inst, List<Expr> args) -> 
                inferJavaInstanceCallReturnType(className, methodName);
            case JavaStaticField(String className, String fieldName) -> {
                Type fieldType = typeMap.getOrDefault(expr, new Type.TInt());
                yield fieldType.toJvmType();
            }
            case App(Expr func, List<Expr> args) -> {
                if (func instanceof Var(String name)) {
                    yield "I";
                } else if (func instanceof QualifiedVar) {
                    yield "I";
                }
                yield "I";
            }
            case ListLit l -> "Ljava/util/List;";
            case Cons c -> "Ljava/util/List;";
            case Constructor(String name, java.util.Optional<Expr> arg) -> "Lcom/miniml/" + name + ";";
            case Match(Expr scrutinee, List<Match.MatchCase> cases) -> 
                cases.isEmpty() ? "I" : inferType(cases.get(0).body());
            case QualifiedVar qv -> "I";
            case Lambda l -> "I";
        };
    }
    
    private String inferJavaCallReturnType(String className, String methodName) {
        if (className.equals("java.lang.Math")) {
            return switch (methodName) {
                case "sqrt", "sin", "cos", "tan", "log", "exp", "pow" -> "D";
                case "abs", "max", "min" -> "I";
                default -> "I";
            };
        }
        return "I";
    }
    
    private String inferJavaInstanceCallReturnType(String className, String methodName) {
        if (className.equals("java.lang.String")) {
            return switch (methodName) {
                case "length" -> "I";
                case "toUpperCase", "toLowerCase" -> "Ljava/lang/String;";
                default -> "Ljava/lang/String;";
            };
        }
        return "I";
    }
    
    private String typeToJVMType(Type type) {
        return switch (type) {
            case Type.TInt() -> "I";
            case Type.TDouble() -> "D";
            case Type.TString() -> "Ljava/lang/String;";
            case Type.TUnit() -> "Lcom/miniml/Unit;";
            case Type.TList(Type inner) -> "Ljava/util/List;";
            default -> "Ljava/lang/Object;";
        };
    }
    
    private String typeToClassName(String jvmType) {
        if (jvmType.startsWith("L") && jvmType.endsWith(";")) {
            return jvmType.substring(1, jvmType.length() - 1);
        }
        return "java/lang/Object";
    }
    
    private void generateSumTypeInterface(String typeName) {
        try {
            ClassWriter ifaceCw = new ClassWriter(0);
            ifaceCw.visit(V17, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE, 
                "com/miniml/" + typeName, null, "java/lang/Object", null);
            ifaceCw.visitEnd();
            
            byte[] classBytes = ifaceCw.toByteArray();
            String ifaceFileName = "target/com/miniml/" + typeName + ".class";
            java.nio.file.Path path = java.nio.file.Paths.get(ifaceFileName);
            java.nio.file.Files.createDirectories(path.getParent());
            try (FileOutputStream fos = new FileOutputStream(ifaceFileName)) {
                fos.write(classBytes);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write sum type interface: " + typeName, e);
        }
    }
    
    private void generateConstructorClass(String ctorName, boolean hasParam, String typeName) {
        try {
            ClassWriter ctorCw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ctorCw.visit(V17, ACC_PUBLIC, "com/miniml/" + ctorName, null, "java/lang/Object", 
                new String[]{"com/miniml/" + typeName});
            
            if (hasParam) {
                FieldVisitor fv = ctorCw.visitField(ACC_PRIVATE + ACC_FINAL, "value", "Ljava/lang/Object;", null, null);
                fv.visitEnd();
                
                MethodVisitor ctorMv = ctorCw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
                ctorMv.visitCode();
                ctorMv.visitVarInsn(ALOAD, 0);
                ctorMv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                ctorMv.visitVarInsn(ALOAD, 0);
                ctorMv.visitVarInsn(ALOAD, 1);
                ctorMv.visitFieldInsn(PUTFIELD, "com/miniml/" + ctorName, "value", "Ljava/lang/Object;");
                ctorMv.visitInsn(RETURN);
                ctorMv.visitMaxs(0, 0);
                ctorMv.visitEnd();
                
                MethodVisitor getMv = ctorCw.visitMethod(ACC_PUBLIC, "value", "()Ljava/lang/Object;", null, null);
                getMv.visitCode();
                getMv.visitVarInsn(ALOAD, 0);
                getMv.visitFieldInsn(GETFIELD, "com/miniml/" + ctorName, "value", "Ljava/lang/Object;");
                getMv.visitInsn(ARETURN);
                getMv.visitMaxs(0, 0);
                getMv.visitEnd();
            } else {
                MethodVisitor ctorMv = ctorCw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                ctorMv.visitCode();
                ctorMv.visitVarInsn(ALOAD, 0);
                ctorMv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                ctorMv.visitInsn(RETURN);
                ctorMv.visitMaxs(0, 0);
                ctorMv.visitEnd();
            }
            
            ctorCw.visitEnd();
            byte[] classBytes = ctorCw.toByteArray();
            
            String ctorFileName = "target/com/miniml/" + ctorName + ".class";
            java.nio.file.Path path = java.nio.file.Paths.get(ctorFileName);
            java.nio.file.Files.createDirectories(path.getParent());
            try (FileOutputStream fos = new FileOutputStream(ctorFileName)) {
                fos.write(classBytes);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write constructor class: " + ctorName, e);
        }
    }
    
    private void boxIfNeeded(Type type) {
        if (type instanceof Type.TInt) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (type instanceof Type.TDouble) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        } else if (type instanceof Type.TBool) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        }
    }

    public void writeClassFile(String outputPath) throws IOException {
        byte[] bytecode = cw.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(bytecode);
        }
    }
    
    private boolean containsNumericVar(Type type) {
        return switch (type) {
            case Type.TNumeric n -> true;
            case Type.TFun(Type param, Type result) -> containsNumericVar(param) || containsNumericVar(result);
            case Type.TList(Type elem) -> containsNumericVar(elem);
            default -> false;
        };
    }
    
    private Type substituteNumericWith(Type type, Type replacement) {
        return switch (type) {
            case Type.TNumeric n -> replacement;
            case Type.TFun(Type param, Type result) ->
                new Type.TFun(substituteNumericWith(param, replacement), substituteNumericWith(result, replacement));
            case Type.TList(Type elem) -> new Type.TList(substituteNumericWith(elem, replacement));
            default -> type;
        };
    }
    
    private Type buildFunctionType(List<Module.Param> params, Type returnType) {
        Type fnType = returnType;
        for (int i = params.size() - 1; i >= 0; i--) {
            Type paramType = params.get(i).typeAnnotation().orElse(returnType);
            fnType = new Type.TFun(paramType, fnType);
        }
        return fnType;
    }
}
