package com.miniml;

public sealed interface Pattern {
    record Wildcard() implements Pattern {
        @Override
        public String toString() { return "_"; }
    }
    
    record Var(String name) implements Pattern {
        @Override
        public String toString() { return name; }
    }
    
    record IntLit(int value) implements Pattern {
        @Override
        public String toString() { return String.valueOf(value); }
    }
    
    record BoolLit(boolean value) implements Pattern {
        @Override
        public String toString() { return String.valueOf(value); }
    }
    
    record StringLit(String value) implements Pattern {
        @Override
        public String toString() { return "\"" + value + "\""; }
    }
    
    record Nil() implements Pattern {
        @Override
        public String toString() { return "[]"; }
    }
    
    record Cons(Pattern head, Pattern tail) implements Pattern {
        @Override
        public String toString() { return head + " :: " + tail; }
    }
    
    record Constructor(String name, java.util.Optional<Pattern> arg) implements Pattern {
        @Override
        public String toString() { 
            return arg.map(a -> name + " " + a).orElse(name);
        }
    }
}
