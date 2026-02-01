package com.miniml.expr;

import com.miniml.Environment;

public record Unit() implements Expr {
    @Override
    public Object eval(Environment env) {
        return com.miniml.Unit.INSTANCE;
    }
}
