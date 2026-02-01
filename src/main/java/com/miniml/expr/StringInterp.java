package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;

public record StringInterp(List<Object> parts) implements Expr {
    @Override
    public Object eval(Environment env) {
        StringBuilder result = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof String s) {
                result.append(s);
            } else if (part instanceof Expr expr) {
                Object value = expr.eval(env);
                result.append(value);
            }
        }
        return result.toString();
    }
}
