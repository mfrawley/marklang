package com.miniml.expr;

import com.miniml.Environment;

public sealed interface Expr permits
    IntLit, FloatLit, BoolLit, StringLit, Unit,
    Var, QualifiedVar,
    BinOp, UnaryOp,
    If, Sequence,
    Let, LetRec,
    Lambda, App,
    Match, Print, StringInterp,
    ListLit, Cons, Constructor,
    JavaCall, JavaInstanceCall, JavaStaticField {
    
    Object eval(Environment env);
    
    record MatchCase(com.miniml.Pattern pattern, Expr body) {}
    
    enum Op {
        ADD, SUB, MUL, DIV, MOD,
        EQ, NE, LT, GT, LE, GE,
        AND, OR
    }
    
    enum UnOp {
        NEG, NOT
    }
}
