package com.miniml;

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
        assertTrue(expr instanceof Expr.IntLit);
        assertEquals(42, ((Expr.IntLit) expr).value());
    }
    
    @Test
    void testFloatLiteral() {
        Expr expr = parse("3.14");
        assertTrue(expr instanceof Expr.FloatLit);
        assertEquals(3.14, ((Expr.FloatLit) expr).value(), 0.001);
    }
    
    @Test
    void testStringLiteral() {
        Expr expr = parse("\"hello\"");
        assertTrue(expr instanceof Expr.StringLit);
        assertEquals("hello", ((Expr.StringLit) expr).value());
    }
    
    @Test
    void testVariable() {
        Expr expr = parse("x");
        assertTrue(expr instanceof Expr.Var);
        assertEquals("x", ((Expr.Var) expr).name());
    }
    
    @Test
    void testBinaryOp() {
        Expr expr = parse("1 + 2");
        assertTrue(expr instanceof Expr.BinOp);
        Expr.BinOp binOp = (Expr.BinOp) expr;
        assertEquals(Expr.Op.ADD, binOp.op());
        assertTrue(binOp.left() instanceof Expr.IntLit);
        assertTrue(binOp.right() instanceof Expr.IntLit);
    }
    
    @Test
    void testMultiplication() {
        Expr expr = parse("3 * 4");
        assertTrue(expr instanceof Expr.BinOp);
        Expr.BinOp binOp = (Expr.BinOp) expr;
        assertEquals(Expr.Op.MUL, binOp.op());
    }
    
    @Test
    void testOperatorPrecedence() {
        Expr expr = parse("1 + 2 * 3");
        assertTrue(expr instanceof Expr.BinOp);
        Expr.BinOp add = (Expr.BinOp) expr;
        assertEquals(Expr.Op.ADD, add.op());
        assertTrue(add.left() instanceof Expr.IntLit);
        assertTrue(add.right() instanceof Expr.BinOp);
        Expr.BinOp mul = (Expr.BinOp) add.right();
        assertEquals(Expr.Op.MUL, mul.op());
    }
    
    @Test
    void testParentheses() {
        Expr expr = parse("(1 + 2) * 3");
        assertTrue(expr instanceof Expr.BinOp);
        Expr.BinOp mul = (Expr.BinOp) expr;
        assertEquals(Expr.Op.MUL, mul.op());
        assertTrue(mul.left() instanceof Expr.BinOp);
        Expr.BinOp add = (Expr.BinOp) mul.left();
        assertEquals(Expr.Op.ADD, add.op());
    }
    
    @Test
    void testLetExpression() {
        Expr expr = parse("let x = 10 in x + 5");
        assertTrue(expr instanceof Expr.Let);
        Expr.Let let = (Expr.Let) expr;
        assertEquals("x", let.name());
        assertTrue(let.value() instanceof Expr.IntLit);
        assertTrue(let.body() instanceof Expr.BinOp);
    }
    
    @Test
    void testIfExpression() {
        Expr expr = parse("if true then 1 else 0");
        assertTrue(expr instanceof Expr.If);
        Expr.If ifExpr = (Expr.If) expr;
        assertTrue(ifExpr.cond() instanceof Expr.Var);
        assertTrue(ifExpr.thenBranch() instanceof Expr.IntLit);
        assertTrue(ifExpr.elseBranch() instanceof Expr.IntLit);
    }
    
    @Test
    void testFunctionApplication() {
        Expr expr = parse("f 10");
        assertTrue(expr instanceof Expr.App);
        Expr.App app = (Expr.App) expr;
        assertTrue(app.func() instanceof Expr.Var);
        assertEquals(1, app.args().size());
        assertTrue(app.args().get(0) instanceof Expr.IntLit);
    }
    
    @Test
    void testMultipleArguments() {
        Expr expr = parse("f 1 2 3");
        assertTrue(expr instanceof Expr.App);
        Expr.App app = (Expr.App) expr;
        assertEquals(3, app.args().size());
    }
    
    @Test
    void testPrintExpression() {
        Expr expr = parse("print \"hello\"");
        assertTrue(expr instanceof Expr.Print);
        Expr.Print print = (Expr.Print) expr;
        assertTrue(print.value() instanceof Expr.StringLit);
    }
    
    @Test
    void testListLiteral() {
        Expr expr = parse("[1, 2, 3]");
        assertTrue(expr instanceof Expr.ListLit);
        Expr.ListLit list = (Expr.ListLit) expr;
        assertEquals(3, list.elements().size());
    }
    
    @Test
    void testEmptyList() {
        Expr expr = parse("[]");
        assertTrue(expr instanceof Expr.ListLit);
        Expr.ListLit list = (Expr.ListLit) expr;
        assertEquals(0, list.elements().size());
    }
    
    @Test
    void testComparison() {
        Expr expr = parse("x == 5");
        assertTrue(expr instanceof Expr.BinOp);
        Expr.BinOp binOp = (Expr.BinOp) expr;
        assertEquals(Expr.Op.EQ, binOp.op());
    }
    
    @Test
    void testQualifiedVariable() {
        Expr expr = parse("Utils.twice");
        assertTrue(expr instanceof Expr.QualifiedVar);
        Expr.QualifiedVar qv = (Expr.QualifiedVar) expr;
        assertEquals("Utils", qv.moduleName());
        assertEquals("twice", qv.name());
    }
    
    @Test
    void testQualifiedFunctionCall() {
        Expr expr = parse("Utils.twice 42");
        assertTrue(expr instanceof Expr.App);
        Expr.App app = (Expr.App) expr;
        assertTrue(app.func() instanceof Expr.QualifiedVar);
        assertEquals(1, app.args().size());
    }
}
