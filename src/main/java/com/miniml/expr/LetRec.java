package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;

public record LetRec(String name, List<String> params, Expr value, Expr body) implements Expr {
    @Override
    public Object eval(Environment env) {
        Environment newEnv = env.extend();
        
        newEnv.define(name, null);
        
        Lambda lambda = new Lambda(params, value);
        Lambda.Closure closure = new Lambda.Closure(params, value, newEnv);
        newEnv.set(name, closure);
        
        return body.eval(newEnv);
    }
}
