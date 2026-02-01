package com.miniml;
import com.miniml.expr.*;
import static com.miniml.expr.Expr.Op;
import static com.miniml.expr.Expr.UnOp;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParserTest {
    
    private Expr parse(String input) {
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        return parser.parseExpr();
    }
    
    @Test
    void testIntLiteral() {
        Expr expr = parse("42");
        assertTrue(expr instanceof IntLit);
        assertEquals(42, ((IntLit) expr).value());
    }
    
    @Test
    void testFloatLiteral() {
        Expr expr = parse("3.14");
        assertTrue(expr instanceof FloatLit);
        assertEquals(3.14, ((FloatLit) expr).value(), 0.001);
    }
    
    @Test
    void testStringLiteral() {
        Expr expr = parse("\"hello\"");
        assertTrue(expr instanceof StringLit);
        assertEquals("hello", ((StringLit) expr).value());
    }
    
    @Test
    void testVariable() {
        Expr expr = parse("x");
        assertTrue(expr instanceof Var);
        assertEquals("x", ((Var) expr).name());
    }
    
    @Test
    void testBinaryOp() {
        Expr expr = parse("1 + 2");
        assertTrue(expr instanceof BinOp);
        BinOp binOp = (BinOp) expr;
        assertEquals(Op.ADD, binOp.op());
        assertTrue(binOp.left() instanceof IntLit);
        assertTrue(binOp.right() instanceof IntLit);
    }
    
    @Test
    void testMultiplication() {
        Expr expr = parse("3 * 4");
        assertTrue(expr instanceof BinOp);
        BinOp binOp = (BinOp) expr;
        assertEquals(Op.MUL, binOp.op());
    }
    
    @Test
    void testOperatorPrecedence() {
        Expr expr = parse("1 + 2 * 3");
        assertTrue(expr instanceof BinOp);
        BinOp add = (BinOp) expr;
        assertEquals(Op.ADD, add.op());
        assertTrue(add.left() instanceof IntLit);
        assertTrue(add.right() instanceof BinOp);
        BinOp mul = (BinOp) add.right();
        assertEquals(Op.MUL, mul.op());
    }
    
    @Test
    void testParentheses() {
        Expr expr = parse("(1 + 2) * 3");
        assertTrue(expr instanceof BinOp);
        BinOp mul = (BinOp) expr;
        assertEquals(Op.MUL, mul.op());
        assertTrue(mul.left() instanceof BinOp);
        BinOp add = (BinOp) mul.left();
        assertEquals(Op.ADD, add.op());
    }
    
    @Test
    void testLetExpression() {
        Expr expr = parse("let x = 10 in x + 5");
        assertTrue(expr instanceof Let);
        Let let = (Let) expr;
        assertEquals("x", let.name());
        assertTrue(let.value() instanceof IntLit);
        assertTrue(let.body() instanceof BinOp);
    }
    
    @Test
    void testIfExpression() {
        Expr expr = parse("if true then 1 else 0");
        assertTrue(expr instanceof If);
        If ifExpr = (If) expr;
        assertTrue(ifExpr.cond() instanceof BoolLit);
        assertTrue(ifExpr.thenBranch() instanceof IntLit);
        assertTrue(ifExpr.elseBranch() instanceof IntLit);
    }
    
    @Test
    void testFunctionApplication() {
        Expr expr = parse("f 10");
        assertTrue(expr instanceof App);
        App app = (App) expr;
        assertTrue(app.func() instanceof Var);
        assertEquals(1, app.args().size());
        assertTrue(app.args().get(0) instanceof IntLit);
    }
    
    @Test
    void testMultipleArguments() {
        Expr expr = parse("f 1 2 3");
        assertTrue(expr instanceof App);
        App app = (App) expr;
        assertEquals(3, app.args().size());
    }
    
    @Test
    void testPrintExpression() {
        Expr expr = parse("print \"hello\"");
        assertTrue(expr instanceof Print);
        Print print = (Print) expr;
        assertTrue(print.value() instanceof StringLit);
    }
    
    @Test
    void testMathMaxApplication() {
        Expr expr = parse("Math.max 42 17");
        assertTrue(expr instanceof App);
        App app = (App) expr;
        assertTrue(app.func() instanceof QualifiedVar);
        QualifiedVar qvar = (QualifiedVar) app.func();
        assertEquals("Math", qvar.moduleName());
        assertEquals("max", qvar.name());
        assertEquals(2, app.args().size());
        assertTrue(app.args().get(0) instanceof IntLit);
        assertTrue(app.args().get(1) instanceof IntLit);
        assertEquals(42, ((IntLit) app.args().get(0)).value());
        assertEquals(17, ((IntLit) app.args().get(1)).value());
    }
    
    @Test
    void testArithmeticNotFunctionApplication() {
        Expr expr = parse("10 / 2");
        assertTrue(expr instanceof BinOp);
        BinOp binOp = (BinOp) expr;
        assertEquals(Op.DIV, binOp.op());
        assertTrue(binOp.left() instanceof IntLit);
        assertTrue(binOp.right() instanceof IntLit);
        assertEquals(10, ((IntLit) binOp.left()).value());
        assertEquals(2, ((IntLit) binOp.right()).value());
    }
    
    @Test
    void testListLiteral() {
        Expr expr = parse("[1, 2, 3]");
        assertTrue(expr instanceof ListLit);
        ListLit list = (ListLit) expr;
        assertEquals(3, list.elements().size());
    }
    
    @Test
    void testEmptyList() {
        Expr expr = parse("[]");
        assertTrue(expr instanceof ListLit);
        ListLit list = (ListLit) expr;
        assertEquals(0, list.elements().size());
    }
    
    @Test
    void testComparison() {
        Expr expr = parse("x == 5");
        assertTrue(expr instanceof BinOp);
        BinOp binOp = (BinOp) expr;
        assertEquals(Op.EQ, binOp.op());
    }
    
    @Test
    void testQualifiedVariable() {
        Expr expr = parse("Utils.twice");
        assertTrue(expr instanceof QualifiedVar);
        QualifiedVar qv = (QualifiedVar) expr;
        assertEquals("Utils", qv.moduleName());
        assertEquals("twice", qv.name());
    }
    
    @Test
    void testQualifiedFunctionCall() {
        Expr expr = parse("Utils.twice 42");
        assertTrue(expr instanceof App);
        App app = (App) expr;
        assertTrue(app.func() instanceof QualifiedVar);
        assertEquals(1, app.args().size());
    }
}
