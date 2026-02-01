package com.miniml;

import java.util.*;
import java.lang.reflect.*;

public class Interpreter {
    private final Map<String, Object> env;
    
    public Interpreter() {
        this.env = new HashMap<>();
    }
    
    public Interpreter(Map<String, Object> env) {
        this.env = new HashMap<>(env);
    }
    
    public Object eval(Expr expr) {
        return switch (expr) {
            case Expr.IntLit(int value) -> value;
            case Expr.FloatLit(double value) -> value;
            case Expr.BoolLit(boolean value) -> value;
            case Expr.StringLit(String value) -> value;
            case Expr.Unit u -> Unit.INSTANCE;
            
            case Expr.Var(String name) -> {
                if (!env.containsKey(name)) {
                    throw new RuntimeException("Undefined variable: " + name);
                }
                yield env.get(name);
            }
            
            case Expr.Let(String name, Expr value, Expr body) -> {
                Object val = eval(value);
                Map<String, Object> newEnv = new HashMap<>(env);
                newEnv.put(name, val);
                yield new Interpreter(newEnv).eval(body);
            }
            
            case Expr.BinOp(Expr.Op op, Expr left, Expr right) -> {
                Object l = eval(left);
                Object r = eval(right);
                yield evalBinOp(op, l, r);
            }
            
            case Expr.UnaryOp(Expr.UnOp op, Expr operand) -> {
                Object val = eval(operand);
                yield evalUnaryOp(op, val);
            }
            
            case Expr.If(Expr cond, Expr thenBranch, Expr elseBranch) -> {
                boolean c = (boolean) eval(cond);
                yield c ? eval(thenBranch) : eval(elseBranch);
            }
            
            case Expr.ListLit(List<Expr> elements) -> {
                List<Object> list = new ArrayList<>();
                for (Expr elem : elements) {
                    list.add(eval(elem));
                }
                yield list;
            }
            
            case Expr.Print(Expr value) -> {
                Object val = eval(value);
                System.out.println(formatValue(val));
                yield val;
            }
            
            case Expr.Sequence(List<Expr> exprs) -> {
                Object result = Unit.INSTANCE;
                for (Expr e : exprs) {
                    result = eval(e);
                }
                yield result;
            }
            
            case Expr.App(Expr func, List<Expr> args) -> {
                if (func instanceof Expr.Var(String name) && "box".equals(name)) {
                    Object arg = eval(args.get(0));
                    yield boxValue(arg);
                }
                throw new RuntimeException("Function application not yet supported in interpreter");
            }
            
            case Expr.JavaCall(String className, String methodName, List<Expr> args) -> {
                yield evalJavaStaticMethod(className, methodName, args);
            }
            
            case Expr.JavaInstanceCall(String className, String methodName, Expr instance, List<Expr> args) -> {
                Object inst = eval(instance);
                yield evalJavaInstanceMethod(inst, methodName, args);
            }
            
            case Expr.JavaStaticField(String className, String fieldName) -> {
                yield evalJavaStaticField(className, fieldName);
            }
            
            default -> throw new RuntimeException("Expression not yet supported in interpreter: " + expr.getClass().getSimpleName());
        };
    }
    
    private Object evalBinOp(Expr.Op op, Object left, Object right) {
        return switch (op) {
            case ADD -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left + (int)right;
                } else if (left instanceof Double || right instanceof Double) {
                    yield toDouble(left) + toDouble(right);
                }
                throw new RuntimeException("Invalid operands for +");
            }
            case SUB -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left - (int)right;
                } else if (left instanceof Double || right instanceof Double) {
                    yield toDouble(left) - toDouble(right);
                }
                throw new RuntimeException("Invalid operands for -");
            }
            case MUL -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left * (int)right;
                } else if (left instanceof Double || right instanceof Double) {
                    yield toDouble(left) * toDouble(right);
                }
                throw new RuntimeException("Invalid operands for *");
            }
            case DIV -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left / (int)right;
                } else if (left instanceof Double || right instanceof Double) {
                    yield toDouble(left) / toDouble(right);
                }
                throw new RuntimeException("Invalid operands for /");
            }
            case MOD -> (int)left % (int)right;
            case EQ -> Objects.equals(left, right);
            case NE -> !Objects.equals(left, right);
            case LT -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left < (int)right;
                } else {
                    yield toDouble(left) < toDouble(right);
                }
            }
            case GT -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left > (int)right;
                } else {
                    yield toDouble(left) > toDouble(right);
                }
            }
            case LE -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left <= (int)right;
                } else {
                    yield toDouble(left) <= toDouble(right);
                }
            }
            case GE -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (int)left >= (int)right;
                } else {
                    yield toDouble(left) >= toDouble(right);
                }
            }
            case AND -> (boolean)left && (boolean)right;
            case OR -> (boolean)left || (boolean)right;
        };
    }
    
    private Object evalUnaryOp(Expr.UnOp op, Object operand) {
        return switch (op) {
            case NEG -> {
                if (operand instanceof Integer) yield -(int)operand;
                if (operand instanceof Double) yield -(double)operand;
                throw new RuntimeException("Invalid operand for negation");
            }
            case NOT -> !(boolean)operand;
        };
    }
    
    private double toDouble(Object val) {
        if (val instanceof Integer) return ((Integer)val).doubleValue();
        if (val instanceof Double) return (Double)val;
        throw new RuntimeException("Cannot convert to double: " + val);
    }
    
    private Object boxValue(Object val) {
        if (val instanceof Integer) return val;
        if (val instanceof Double) return val;
        if (val instanceof Boolean) return val;
        return val;
    }
    
    private Object evalJavaStaticMethod(String className, String methodName, List<Expr> args) {
        try {
            Class<?> clazz = Class.forName(className);
            Object[] argValues = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                argValues[i] = eval(args.get(i));
            }
            
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && 
                    Modifier.isStatic(method.getModifiers()) &&
                    method.getParameterCount() == args.size()) {
                    return method.invoke(null, argValues);
                }
            }
            throw new RuntimeException("No matching static method: " + className + "." + methodName);
        } catch (Exception e) {
            throw new RuntimeException("Error calling static method: " + e.getMessage(), e);
        }
    }
    
    private Object evalJavaInstanceMethod(Object instance, String methodName, List<Expr> args) {
        try {
            Object[] argValues = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                argValues[i] = eval(args.get(i));
            }
            
            Class<?> clazz = instance.getClass();
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && 
                    method.getParameterCount() == args.size()) {
                    return method.invoke(instance, argValues);
                }
            }
            throw new RuntimeException("No matching instance method: " + methodName);
        } catch (Exception e) {
            throw new RuntimeException("Error calling instance method: " + e.getMessage(), e);
        }
    }
    
    private Object evalJavaStaticField(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Error accessing static field: " + e.getMessage(), e);
        }
    }
    
    public static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Unit) return "()";
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }
}
