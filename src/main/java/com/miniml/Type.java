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
    
    record TResult(Type okType, Type errorType) implements Type {
        @Override
        public String toString() {
            return "result<" + okType + ", " + errorType + ">";
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
    
    record TApp(String name, List<Type> args) implements Type {
        @Override
        public String toString() {
            if (args.isEmpty()) return name;
            return name + "<" + String.join(", ", args.stream().map(Type::toString).toList()) + ">";
        }
    }
    
    record TName(String name) implements Type {
        @Override
        public String toString() { return name; }
    }
    
    record TBoxed() implements Type {
        @Override
        public String toString() { return "<boxed>"; }
    }
    
    record TJava(String className, List<Type> typeArgs) implements Type {
        @Override
        public String toString() {
            if (typeArgs.isEmpty()) {
                return className;
            }
            return className + "<" + 
                   String.join(", ", typeArgs.stream().map(Type::toString).toList()) + 
                   ">";
        }
    }
    
    default String toJvmType() {
        return switch (this) {
            case TInt t -> "I";
            case TDouble t -> "D";
            case TString t -> "Ljava/lang/String;";
            case TBool t -> "I";
            case TUnit t -> "Lcom/miniml/Unit;";
            case TList t -> "Ljava/util/List;";
            case TResult t -> "Lcom/miniml/Result;";
            case TVar t -> throw new IllegalStateException("Unresolved type variable '" + t.name() + "' during code generation");
            case TNumeric t -> throw new IllegalStateException("Unresolved numeric type variable '" + t.name() + "' during code generation");
            case TFun t -> "Ljava/lang/Object;";
            case TScheme(List<String> vars, Type innerType) -> innerType.toJvmType();
            case TApp(String name, List<Type> args) -> "Lcom/miniml/" + name + ";";
            case TName(String name) -> "Lcom/miniml/" + name + ";";
            case TBoxed t -> "Ljava/lang/Object;";
            case TJava(String className, List<Type> typeArgs) -> "L" + className.replace('.', '/') + ";";
        };
    }
}
