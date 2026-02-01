package com.miniml.expr;

import com.miniml.Environment;

public record If(Expr cond, Expr thenBranch, Expr elseBranch) implements Expr {
    @Override
    public Object eval(Environment env) {
        boolean c = (boolean) cond.eval(env);
        return c ? thenBranch.eval(env) : elseBranch.eval(env);
    }
}
