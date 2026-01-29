package com.miniml;

import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class Compiler {
    private final String className;
    private final ClassWriter cw;
    private MethodVisitor mv;
    private final Map<String, Integer> locals = new HashMap<>();
    private final Map<String, String> localTypes = new HashMap<>();
    private int nextLocal = 0;
    private int labelCounter = 0;

    public Compiler(String className) {
        this.className = className;
        this.cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    }

    public byte[] compile(Expr expr) {
        cw.visit(V17, ACC_PUBLIC, className, null, "java/lang/Object", null);

        compileMainMethod(expr);
        compileConstructor();

        cw.visitEnd();
        return cw.toByteArray();
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

        compileExpr(expr);

        if (!(expr instanceof Expr.Print)) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitInsn(SWAP);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        } else {
            mv.visitInsn(POP);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void compileExpr(Expr expr) {
        switch (expr) {
            case Expr.IntLit(int value) -> mv.visitLdcInsn(value);
            
            case Expr.FloatLit(double value) -> {
                mv.visitLdcInsn(value);
                mv.visitInsn(D2I);
            }
            
            case Expr.StringLit(String value) -> mv.visitLdcInsn(value);
            
            case Expr.StringInterp(List<Object> parts) -> {
                mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                
                for (Object part : parts) {
                    if (part instanceof String str) {
                        mv.visitLdcInsn(str);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    } else if (part instanceof Expr.Var(String varName)) {
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
            
            case Expr.Print(Expr value) -> {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                compileExpr(value);
                if (value instanceof Expr.StringLit || value instanceof Expr.StringInterp) {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
                }
                mv.visitInsn(ICONST_0);
            }
            
            case Expr.Var(String name) -> {
                Integer local = locals.get(name);
                if (local == null) {
                    throw new RuntimeException("Undefined variable: " + name);
                }
                String type = localTypes.getOrDefault(name, "I");
                if (type.equals("Ljava/lang/String;")) {
                    mv.visitVarInsn(ALOAD, local);
                } else {
                    mv.visitVarInsn(ILOAD, local);
                }
            }
            
            case Expr.BinOp(Expr.Op op, Expr left, Expr right) -> {
                compileExpr(left);
                compileExpr(right);
                switch (op) {
                    case ADD -> mv.visitInsn(IADD);
                    case SUB -> mv.visitInsn(ISUB);
                    case MUL -> mv.visitInsn(IMUL);
                    case DIV -> mv.visitInsn(IDIV);
                    case MOD -> mv.visitInsn(IREM);
                    case EQ, NE, LT, GT, LE, GE -> compileComparison(op);
                }
            }
            
            case Expr.If(Expr cond, Expr thenBranch, Expr elseBranch) -> {
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
            
            case Expr.Let(String name, Expr value, Expr body) -> {
                compileExpr(value);
                int local = allocLocal(name);
                if (value instanceof Expr.StringLit || value instanceof Expr.StringInterp) {
                    mv.visitVarInsn(ASTORE, local);
                    localTypes.put(name, "Ljava/lang/String;");
                } else {
                    mv.visitVarInsn(ISTORE, local);
                    localTypes.put(name, "I");
                }
                compileExpr(body);
                freeLocal(name);
            }
            
            case Expr.LetRec(String name, List<String> params, Expr value, Expr body) -> {
                String methodName = "lambda_" + name;
                compileLambdaMethod(methodName, params, value);
                compileExpr(body);
            }
            
            case Expr.Lambda(List<String> params, Expr lambdaBody) -> {
                String methodName = "lambda_" + (labelCounter++);
                compileLambdaMethod(methodName, params, lambdaBody);
            }
            
            case Expr.App(Expr func, List<Expr> args) -> {
                if (func instanceof Expr.Var(String funcName)) {
                    for (Expr arg : args) {
                        compileExpr(arg);
                    }
                    String descriptor = "(" + "I".repeat(args.size()) + ")I";
                    mv.visitMethodInsn(INVOKESTATIC, className, "lambda_" + funcName, descriptor, false);
                } else {
                    throw new RuntimeException("Only named functions can be called for now");
                }
            }
            
            case Expr.Sequence(List<Expr> exprs) -> {
                for (int i = 0; i < exprs.size(); i++) {
                    compileExpr(exprs.get(i));
                    if (i < exprs.size() - 1) {
                        mv.visitInsn(POP);
                    }
                }
            }
            
            case Expr.JavaCall(String className, String methodName, List<Expr> args) -> {
                String jvmClassName = className.replace('.', '/');
                
                for (Expr arg : args) {
                    compileExpr(arg);
                }
                
                String descriptor = inferJavaMethodDescriptor(className, methodName, args);
                mv.visitMethodInsn(INVOKESTATIC, jvmClassName, methodName, descriptor, false);
            }
        }
    }
    
    private String inferJavaMethodDescriptor(String className, String methodName, List<Expr> args) {
        StringBuilder desc = new StringBuilder("(");
        for (Expr arg : args) {
            if (arg instanceof Expr.StringLit || arg instanceof Expr.StringInterp) {
                desc.append("Ljava/lang/String;");
            } else if (arg instanceof Expr.FloatLit) {
                desc.append("D");
            } else if (arg instanceof Expr.Var(String varName)) {
                String type = localTypes.getOrDefault(varName, "I");
                if (type.equals("Ljava/lang/String;")) {
                    desc.append("Ljava/lang/String;");
                } else {
                    desc.append("I");
                }
            } else {
                desc.append("I");
            }
        }
        desc.append(")");
        
        if (className.equals("java.lang.Math") && 
            (methodName.equals("sqrt") || methodName.equals("sin") || 
             methodName.equals("cos") || methodName.equals("tan") ||
             methodName.equals("log") || methodName.equals("exp"))) {
            desc.append("D");
        } else if (className.equals("java.lang.System") && methodName.equals("currentTimeMillis")) {
            desc.append("J");
        } else {
            desc.append("I");
        }
        
        return desc.toString();
    }

    private void compileComparison(Expr.Op op) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        
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

    public void writeClassFile(String outputPath) throws IOException {
        byte[] bytecode = cw.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(bytecode);
        }
    }
}
