package com.miniml;

import java.util.HashMap;
import java.util.Map;

public class ReplClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes = new HashMap<>();
    
    public ReplClassLoader() {
        super(ReplClassLoader.class.getClassLoader());
    }
    
    public void defineClass(String name, byte[] bytes) {
        classBytes.put(name, bytes);
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classBytes.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }
        return super.findClass(name);
    }
}
