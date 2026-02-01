package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;

public record JavaInstanceCall(String className, String methodName, Expr instance, List<Expr> args) implements Expr {
    @Override
    public Object eval(Environment env) {
        try {
            Object instanceValue = instance.eval(env);
            Class<?> clazz = instanceValue.getClass();
            
            List<Object> argValues = new ArrayList<>();
            for (Expr arg : args) {
                argValues.add(arg.eval(env));
            }
            
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) &&
                    method.getParameterCount() == argValues.size()) {
                    
                    Object[] argArray = argValues.toArray();
                    return method.invoke(instanceValue, argArray);
                }
            }
            
            throw new RuntimeException("No matching instance method found: " + methodName);
        } catch (Exception e) {
            throw new RuntimeException("Java instance call failed: " + e.getMessage(), e);
        }
    }
}
