package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;

public record Lambda(List<String> params, Expr body) implements Expr {
    @Override
    public Object eval(Environment env) {
        return new Closure(params, body, env);
    }
    
    public record Closure(List<String> params, Expr body, Environment capturedEnv) {
        public Object apply(List<Object> args) {
            if (args.size() != params.size()) {
                throw new RuntimeException(
                    "Function expects " + params.size() + " arguments but got " + args.size()
                );
            }
            
            Environment callEnv = capturedEnv.extend();
            for (int i = 0; i < params.size(); i++) {
                callEnv.define(params.get(i), args.get(i));
            }
            
            return body.eval(callEnv);
        }
    }
}
