package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;
import java.util.ArrayList;

public record Cons(Expr head, Expr tail) implements Expr {
    @Override
    public Object eval(Environment env) {
        Object headValue = head.eval(env);
        Object tailValue = tail.eval(env);
        
        if (!(tailValue instanceof List<?> tailList)) {
            throw new RuntimeException("Cons tail must be a list");
        }
        
        List<Object> result = new ArrayList<>();
        result.add(headValue);
        result.addAll(tailList);
        return result;
    }
}
