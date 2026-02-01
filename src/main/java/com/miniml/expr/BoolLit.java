package com.miniml.expr;

import com.miniml.Environment;

public record BoolLit(boolean value) implements Expr {
    @Override
    public Object eval(Environment env) {
        return value;
    }
}
