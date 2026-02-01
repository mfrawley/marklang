package com.miniml.expr;

import com.miniml.Environment;
import com.miniml.Result;

public record Constructor(String name, java.util.Optional<Expr> arg) implements Expr {
    @Override
    public Object eval(Environment env) {
        if (name.equals("Ok")) {
            Object value = arg.isPresent() ? arg.get().eval(env) : null;
            return new Result.Ok<Object, Object>(value);
        } else if (name.equals("Error")) {
            Object error = arg.isPresent() ? arg.get().eval(env) : null;
            return new Result.Error<Object, Object>(error);
        }
        
        throw new RuntimeException("Unknown constructor: " + name);
    }
}
