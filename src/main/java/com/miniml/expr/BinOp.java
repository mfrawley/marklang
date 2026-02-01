package com.miniml.expr;

import com.miniml.Environment;
import java.util.Objects;

public record BinOp(Op op, Expr left, Expr right) implements Expr {
    @Override
    public Object eval(Environment env) {
        Object l = left.eval(env);
        Object r = right.eval(env);
        
        return switch (op) {
            case ADD -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l + (int)r;
                } else if (l instanceof Double || r instanceof Double) {
                    yield toDouble(l) + toDouble(r);
                }
                throw new RuntimeException("Invalid operands for +");
            }
            case SUB -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l - (int)r;
                } else if (l instanceof Double || r instanceof Double) {
                    yield toDouble(l) - toDouble(r);
                }
                throw new RuntimeException("Invalid operands for -");
            }
            case MUL -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l * (int)r;
                } else if (l instanceof Double || r instanceof Double) {
                    yield toDouble(l) * toDouble(r);
                }
                throw new RuntimeException("Invalid operands for *");
            }
            case DIV -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l / (int)r;
                } else if (l instanceof Double || r instanceof Double) {
                    yield toDouble(l) / toDouble(r);
                }
                throw new RuntimeException("Invalid operands for /");
            }
            case MOD -> (int)l % (int)r;
            case EQ -> Objects.equals(l, r);
            case NE -> !Objects.equals(l, r);
            case LT -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l < (int)r;
                } else {
                    yield toDouble(l) < toDouble(r);
                }
            }
            case GT -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l > (int)r;
                } else {
                    yield toDouble(l) > toDouble(r);
                }
            }
            case LE -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l <= (int)r;
                } else {
                    yield toDouble(l) <= toDouble(r);
                }
            }
            case GE -> {
                if (l instanceof Integer && r instanceof Integer) {
                    yield (int)l >= (int)r;
                } else {
                    yield toDouble(l) >= toDouble(r);
                }
            }
            case AND -> (boolean)l && (boolean)r;
            case OR -> (boolean)l || (boolean)r;
        };
    }
    
    private static double toDouble(Object val) {
        if (val instanceof Integer) return ((Integer)val).doubleValue();
        if (val instanceof Double) return (Double)val;
        throw new RuntimeException("Cannot convert to double: " + val);
    }
}
