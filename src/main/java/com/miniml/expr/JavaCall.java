package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;

public record JavaCall(String className, String methodName, List<Expr> args) implements Expr {
    @Override
    public Object eval(Environment env) {
        try {
            Class<?> clazz = Class.forName(className);
            
            List<Object> argValues = new ArrayList<>();
            for (Expr arg : args) {
                argValues.add(arg.eval(env));
            }
            
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) &&
                    java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
                    method.getParameterCount() == argValues.size()) {
                    
                    Object[] argArray = argValues.toArray();
                    return method.invoke(null, argArray);
                }
            }
            
            throw new RuntimeException("No matching static method found: " + className + "." + methodName);
        } catch (Exception e) {
            throw new RuntimeException("Java call failed: " + e.getMessage(), e);
        }
    }
}
