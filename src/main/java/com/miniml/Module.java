package com.miniml;

import java.util.List;
import java.util.Optional;

public record Module(List<String> imports, List<TopLevel> declarations, Expr mainExpr) {
    public record Param(String name, Optional<Type> typeAnnotation) {}
    
    public sealed interface TopLevel {
        record FnDecl(String name, List<Param> params, Optional<Type> returnType, Expr body) implements TopLevel {}
        record LetDecl(String name, Expr value) implements TopLevel {}
    }
}
