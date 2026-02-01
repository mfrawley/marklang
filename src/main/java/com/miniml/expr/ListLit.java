package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;
import java.util.ArrayList;

public record ListLit(List<Expr> elements) implements Expr {
    @Override
    public Object eval(Environment env) {
        List<Object> result = new ArrayList<>();
        for (Expr elem : elements) {
            result.add(elem.eval(env));
        }
        return result;
    }
}
