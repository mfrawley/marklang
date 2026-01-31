package com.miniml;

import java.util.List;

public sealed interface Expr {
    record IntLit(int value) implements Expr {}
    record FloatLit(double value) implements Expr {}
    record BoolLit(boolean value) implements Expr {}
    record StringLit(String value) implements Expr {}
    record StringInterp(List<Object> parts) implements Expr {}
    record Var(String name) implements Expr {}
    record QualifiedVar(String moduleName, String name) implements Expr {}
    record UnaryOp(UnOp op, Expr operand) implements Expr {}
    record BinOp(Op op, Expr left, Expr right) implements Expr {}
    record If(Expr cond, Expr thenBranch, Expr elseBranch) implements Expr {}
    record Let(String name, Expr value, Expr body) implements Expr {}
    record LetRec(String name, List<String> params, Expr value, Expr body) implements Expr {}
    record Lambda(List<String> params, Expr body) implements Expr {}
    record App(Expr func, List<Expr> args) implements Expr {}
    record Sequence(List<Expr> exprs) implements Expr {}
    record Print(Expr value) implements Expr {}
    record JavaCall(String className, String methodName, List<Expr> args) implements Expr {}
    record JavaInstanceCall(String className, String methodName, Expr instance, List<Expr> args) implements Expr {}
    record ListLit(List<Expr> elements) implements Expr {}
    record Cons(Expr head, Expr tail) implements Expr {}
    record Match(Expr scrutinee, List<MatchCase> cases) implements Expr {}
    record Unit() implements Expr {}
    record Constructor(String name, java.util.Optional<Expr> arg) implements Expr {}

    record MatchCase(Pattern pattern, Expr body) {}

    enum Op {
        ADD, SUB, MUL, DIV, MOD,
        EQ, NE, LT, GT, LE, GE,
        AND, OR
    }

    enum UnOp {
        NEG, NOT
    }
}
