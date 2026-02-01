package com.miniml.expr;

import com.miniml.Environment;
import com.miniml.Unit;

public record Print(Expr value) implements Expr {
    @Override
    public Object eval(Environment env) {
        Object val = value.eval(env);
        System.out.println(val);
        return Unit.INSTANCE;
    }
}
