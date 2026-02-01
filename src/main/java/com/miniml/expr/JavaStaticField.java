package com.miniml.expr;

import com.miniml.Environment;
import java.lang.reflect.Field;

public record JavaStaticField(String className, String fieldName) implements Expr {
    @Override
    public Object eval(Environment env) {
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Java static field access failed: " + e.getMessage(), e);
        }
    }
}
