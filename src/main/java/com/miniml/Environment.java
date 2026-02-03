package com.miniml;

import java.util.*;

public class Environment {
    private final Map<String, Integer> nameToSlot;
    private final Object[] slots;
    private final Environment parent;
    private int nextSlot;
    private Map<String, String> javaImports;
    
    public Environment() {
        this(null, 16);
        this.javaImports = new HashMap<>();
    }
    
    public Environment(Environment parent) {
        this(parent, 16);
        this.javaImports = parent != null ? parent.javaImports : new HashMap<>();
    }
    
    public Environment(Environment parent, int initialCapacity) {
        this.parent = parent;
        this.nameToSlot = new HashMap<>();
        this.slots = new Object[initialCapacity];
        this.nextSlot = 0;
        this.javaImports = parent != null ? parent.javaImports : new HashMap<>();
    }
    
    public static Environment fromMap(Map<String, Object> map) {
        Environment env = new Environment(null, map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            env.define(entry.getKey(), entry.getValue());
        }
        return env;
    }
    
    public Object get(String name) {
        Integer slot = nameToSlot.get(name);
        if (slot != null) {
            return slots[slot];
        }
        if (parent != null) {
            return parent.get(name);
        }
        throw new RuntimeException("Undefined variable: " + name);
    }
    
    public boolean isDefined(String name) {
        if (nameToSlot.containsKey(name)) {
            return true;
        }
        if (parent != null) {
            return parent.isDefined(name);
        }
        return false;
    }
    
    public void define(String name, Object value) {
        if (nextSlot >= slots.length) {
            throw new RuntimeException("Environment capacity exceeded");
        }
        nameToSlot.put(name, nextSlot);
        slots[nextSlot] = value;
        nextSlot++;
    }
    
    public void set(String name, Object value) {
        Integer slot = nameToSlot.get(name);
        if (slot != null) {
            slots[slot] = value;
            return;
        }
        if (parent != null && parent.isDefined(name)) {
            parent.set(name, value);
            return;
        }
        throw new RuntimeException("Cannot set undefined variable: " + name);
    }
    
    public Environment extend() {
        return new Environment(this);
    }
    
    public Environment getParent() {
        return parent;
    }
    
    public Integer getSlot(String name) {
        Integer slot = nameToSlot.get(name);
        if (slot != null) {
            return slot;
        }
        if (parent != null) {
            return parent.getSlot(name);
        }
        return null;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (parent != null) {
            map.putAll(parent.toMap());
        }
        for (Map.Entry<String, Integer> entry : nameToSlot.entrySet()) {
            map.put(entry.getKey(), slots[entry.getValue()]);
        }
        return map;
    }
    
    public void setJavaImports(Map<String, String> imports) {
        this.javaImports = imports;
    }
    
    public Map<String, String> getJavaImports() {
        return javaImports;
    }
    
    public String resolveJavaClass(String shortName) {
        return javaImports.getOrDefault(shortName, shortName);
    }
}
