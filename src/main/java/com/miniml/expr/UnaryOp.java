package com.miniml.expr;

import com.miniml.Environment;

public record UnaryOp(UnOp op, Expr operand) implements Expr {
    @Override
    public Object eval(Environment env) {
        Object val = operand.eval(env);
        
        return switch (op) {
            case NEG -> {
                if (val instanceof Integer) yield -(int)val;
                if (val instanceof Double) yield -(double)val;
                throw new RuntimeException("Invalid operand for negation");
            }
            case NOT -> !(boolean)val;
        };
    }
}
