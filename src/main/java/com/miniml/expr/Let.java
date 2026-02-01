package com.miniml.expr;

import com.miniml.Environment;

public record Let(String name, Expr value, Expr body) implements Expr {
    @Override
    public Object eval(Environment env) {
        Object val = value.eval(env);
        Environment newEnv = env.extend();
        newEnv.define(name, val);
        return body.eval(newEnv);
    }
}
