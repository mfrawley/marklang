package com.miniml.expr;

import com.miniml.Environment;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;

public record JavaCall(String className, String methodName, List<Expr> args) implements Expr {
    @Override
    public Object eval(Environment env) {
        try {
            String fullClassName = env.resolveJavaClass(className);
            Class<?> clazz = Class.forName(fullClassName);
            
            List<Object> argValues = new ArrayList<>();
            for (Expr arg : args) {
                argValues.add(arg.eval(env));
            }
            
            if (methodName.equals("new")) {
                Object[] argArray = argValues.toArray();
                if (argArray.length == 0) {
                    return clazz.getDeclaredConstructor().newInstance();
                }
                
                for (java.lang.reflect.Constructor<?> constructor : clazz.getConstructors()) {
                    if (constructor.getParameterCount() == argArray.length) {
                        try {
                            return constructor.newInstance(argArray);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                    }
                }
                throw new RuntimeException("No matching constructor found for: " + className);
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
