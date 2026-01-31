package com.miniml;

import java.util.List;
import java.util.Objects;

public sealed interface Type {
    record TInt() implements Type {
        @Override
        public String toString() { return "Int"; }
    }
    
    record TDouble() implements Type {
        @Override
        public String toString() { return "Double"; }
    }
    
    record TString() implements Type {
        @Override
        public String toString() { return "String"; }
    }
    
    record TBool() implements Type {
        @Override
        public String toString() { return "Bool"; }
    }
    
    record TUnit() implements Type {
        @Override
        public String toString() { return "Unit"; }
    }
    
    record TList(Type elementType) implements Type {
        @Override
        public String toString() { 
            return elementType + " list"; 
        }
    }
    
    record TVar(String name) implements Type {
        @Override
        public String toString() { return "'" + name; }
    }
    
    record TNumeric(String name) implements Type {
        @Override
        public String toString() { return "'" + name; }
    }
    
    record TFun(Type param, Type result) implements Type {
        @Override
        public String toString() { 
            return "(" + param + " -> " + result + ")"; 
        }
    }
    
    record TScheme(List<String> vars, Type type) implements Type {
        @Override
        public String toString() {
            if (vars.isEmpty()) return type.toString();
            return "forall " + String.join(" ", vars) + ". " + type;
        }
    }
    
    default String toJvmType() {
        return switch (this) {
            case TInt t -> "I";
            case TDouble t -> "D";
            case TString t -> "Ljava/lang/String;";
            case TBool t -> "I";
            case TUnit t -> "V";
            case TList t -> "Ljava/util/List;";
            case TVar t -> throw new IllegalStateException("Unresolved type variable '" + t.name() + "' during code generation");
            case TNumeric t -> throw new IllegalStateException("Unresolved numeric type variable '" + t.name() + "' during code generation");
            case TFun t -> "Ljava/lang/Object;";
            case TScheme(List<String> vars, Type innerType) -> innerType.toJvmType();
        };
    }
}
