package com.miniml;

import java.util.List;

public sealed interface Expr {
    record IntLit(int value) implements Expr {}
    record FloatLit(double value) implements Expr {}
    record StringLit(String value) implements Expr {}
    record StringInterp(List<Object> parts) implements Expr {}
    record Var(String name) implements Expr {}
    record BinOp(Op op, Expr left, Expr right) implements Expr {}
    record If(Expr cond, Expr thenBranch, Expr elseBranch) implements Expr {}
    record Let(String name, Expr value, Expr body) implements Expr {}
    record LetRec(String name, List<String> params, Expr value, Expr body) implements Expr {}
    record Lambda(List<String> params, Expr body) implements Expr {}
    record App(Expr func, List<Expr> args) implements Expr {}
    record Sequence(List<Expr> exprs) implements Expr {}
    record Print(Expr value) implements Expr {}
    record JavaCall(String className, String methodName, List<Expr> args) implements Expr {}

    enum Op {
        ADD, SUB, MUL, DIV, MOD,
        EQ, NE, LT, GT, LE, GE
    }
}
