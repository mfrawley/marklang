package com.miniml;

public final class Unit {
    public static final Unit INSTANCE = new Unit();
    
    private Unit() {}
    
    @Override
    public String toString() {
        return "()";
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Unit;
    }
    
    @Override
    public int hashCode() {
        return 0;
    }
}
