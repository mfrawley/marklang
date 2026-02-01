package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;
import java.util.ArrayList;

public record App(Expr func, List<Expr> args) implements Expr {
    @Override
    public Object eval(Environment env) {
        Object f = func.eval(env);
        
        if (!(f instanceof Lambda.Closure closure)) {
            throw new RuntimeException("Cannot apply non-function value: " + f);
        }
        
        List<Object> argValues = new ArrayList<>();
        for (Expr arg : args) {
            argValues.add(arg.eval(env));
        }
        
        return closure.apply(argValues);
    }
}
