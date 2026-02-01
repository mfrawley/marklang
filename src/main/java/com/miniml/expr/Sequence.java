package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;

public record Sequence(List<Expr> exprs) implements Expr {
    @Override
    public Object eval(Environment env) {
        Object result = com.miniml.Unit.INSTANCE;
        for (Expr e : exprs) {
            result = e.eval(env);
        }
        return result;
    }
}
