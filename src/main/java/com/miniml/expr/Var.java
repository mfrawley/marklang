package com.miniml.expr;

import com.miniml.Environment;

public record Var(String name) implements Expr {
    @Override
    public Object eval(Environment env) {
        return env.get(name);
    }
}
