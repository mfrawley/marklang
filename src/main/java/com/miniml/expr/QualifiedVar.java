package com.miniml.expr;

import com.miniml.Environment;

public record QualifiedVar(String moduleName, String name) implements Expr {
    @Override
    public Object eval(Environment env) {
        String qualifiedName = moduleName + "." + name;
        if (env.isDefined(qualifiedName)) {
            return env.get(qualifiedName);
        }
        if (env.isDefined(name)) {
            return env.get(name);
        }
        throw new RuntimeException("Undefined qualified variable: " + qualifiedName);
    }
}
