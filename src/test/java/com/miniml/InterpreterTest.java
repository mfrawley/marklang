package com.miniml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class InterpreterTest {
    private Interpreter interpreter;
    
    @BeforeEach
    void setUp() {
        interpreter = new Interpreter();
    }
    
    @Test
    void testIntLit() {
        Expr expr = new Expr.IntLit(42);
        assertEquals(42, interpreter.eval(expr));
    }
    
    @Test
    void testFloatLit() {
        Expr expr = new Expr.FloatLit(3.14);
        assertEquals(3.14, interpreter.eval(expr));
    }
    
    @Test
    void testBoolLit() {
        Expr expr = new Expr.BoolLit(true);
        assertEquals(true, interpreter.eval(expr));
        
        expr = new Expr.BoolLit(false);
        assertEquals(false, interpreter.eval(expr));
    }
    
    @Test
    void testStringLit() {
        Expr expr = new Expr.StringLit("hello");
        assertEquals("hello", interpreter.eval(expr));
    }
    
    @Test
    void testUnit() {
        Expr expr = new Expr.Unit();
        assertEquals(Unit.INSTANCE, interpreter.eval(expr));
    }
    
    @Test
    void testVarUndefined() {
        Expr expr = new Expr.Var("x");
        assertThrows(RuntimeException.class, () -> interpreter.eval(expr));
    }
    
    @Test
    void testVarDefined() {
        Map<String, Object> env = new HashMap<>();
        env.put("x", 42);
        interpreter = new Interpreter(env);
        
        Expr expr = new Expr.Var("x");
        assertEquals(42, interpreter.eval(expr));
    }
    
    @Test
    void testLet() {
        Expr value = new Expr.IntLit(10);
        Expr body = new Expr.Var("x");
        Expr expr = new Expr.Let("x", value, body);
        
        assertEquals(10, interpreter.eval(expr));
    }
    
    @Test
    void testLetNested() {
        Expr innerValue = new Expr.IntLit(5);
        Expr innerBody = new Expr.Var("y");
        Expr outerValue = new Expr.IntLit(10);
        Expr outerBody = new Expr.Let("y", innerValue, innerBody);
        Expr expr = new Expr.Let("x", outerValue, outerBody);
        
        assertEquals(5, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpAddInt() {
        Expr left = new Expr.IntLit(10);
        Expr right = new Expr.IntLit(20);
        Expr expr = new Expr.BinOp(Expr.Op.ADD, left, right);
        
        assertEquals(30, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpAddDouble() {
        Expr left = new Expr.FloatLit(10.5);
        Expr right = new Expr.FloatLit(20.3);
        Expr expr = new Expr.BinOp(Expr.Op.ADD, left, right);
        
        assertEquals(30.8, (Double)interpreter.eval(expr), 0.001);
    }
    
    @Test
    void testBinOpAddMixed() {
        Expr left = new Expr.IntLit(10);
        Expr right = new Expr.FloatLit(20.5);
        Expr expr = new Expr.BinOp(Expr.Op.ADD, left, right);
        
        assertEquals(30.5, (Double)interpreter.eval(expr), 0.001);
    }
    
    @Test
    void testBinOpSubInt() {
        Expr left = new Expr.IntLit(30);
        Expr right = new Expr.IntLit(10);
        Expr expr = new Expr.BinOp(Expr.Op.SUB, left, right);
        
        assertEquals(20, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpMulInt() {
        Expr left = new Expr.IntLit(6);
        Expr right = new Expr.IntLit(7);
        Expr expr = new Expr.BinOp(Expr.Op.MUL, left, right);
        
        assertEquals(42, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpDivInt() {
        Expr left = new Expr.IntLit(20);
        Expr right = new Expr.IntLit(4);
        Expr expr = new Expr.BinOp(Expr.Op.DIV, left, right);
        
        assertEquals(5, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpModInt() {
        Expr left = new Expr.IntLit(17);
        Expr right = new Expr.IntLit(5);
        Expr expr = new Expr.BinOp(Expr.Op.MOD, left, right);
        
        assertEquals(2, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpEq() {
        Expr left = new Expr.IntLit(42);
        Expr right = new Expr.IntLit(42);
        Expr expr = new Expr.BinOp(Expr.Op.EQ, left, right);
        
        assertEquals(true, interpreter.eval(expr));
        
        right = new Expr.IntLit(43);
        expr = new Expr.BinOp(Expr.Op.EQ, left, right);
        assertEquals(false, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpNe() {
        Expr left = new Expr.IntLit(42);
        Expr right = new Expr.IntLit(43);
        Expr expr = new Expr.BinOp(Expr.Op.NE, left, right);
        
        assertEquals(true, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpLt() {
        Expr left = new Expr.IntLit(10);
        Expr right = new Expr.IntLit(20);
        Expr expr = new Expr.BinOp(Expr.Op.LT, left, right);
        
        assertEquals(true, interpreter.eval(expr));
        
        left = new Expr.IntLit(30);
        expr = new Expr.BinOp(Expr.Op.LT, left, right);
        assertEquals(false, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpGt() {
        Expr left = new Expr.IntLit(30);
        Expr right = new Expr.IntLit(20);
        Expr expr = new Expr.BinOp(Expr.Op.GT, left, right);
        
        assertEquals(true, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpLe() {
        Expr left = new Expr.IntLit(20);
        Expr right = new Expr.IntLit(20);
        Expr expr = new Expr.BinOp(Expr.Op.LE, left, right);
        
        assertEquals(true, interpreter.eval(expr));
        
        left = new Expr.IntLit(19);
        expr = new Expr.BinOp(Expr.Op.LE, left, right);
        assertEquals(true, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpGe() {
        Expr left = new Expr.IntLit(20);
        Expr right = new Expr.IntLit(20);
        Expr expr = new Expr.BinOp(Expr.Op.GE, left, right);
        
        assertEquals(true, interpreter.eval(expr));
        
        left = new Expr.IntLit(21);
        expr = new Expr.BinOp(Expr.Op.GE, left, right);
        assertEquals(true, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpAnd() {
        Expr left = new Expr.BoolLit(true);
        Expr right = new Expr.BoolLit(true);
        Expr expr = new Expr.BinOp(Expr.Op.AND, left, right);
        
        assertEquals(true, interpreter.eval(expr));
        
        right = new Expr.BoolLit(false);
        expr = new Expr.BinOp(Expr.Op.AND, left, right);
        assertEquals(false, interpreter.eval(expr));
    }
    
    @Test
    void testBinOpOr() {
        Expr left = new Expr.BoolLit(true);
        Expr right = new Expr.BoolLit(false);
        Expr expr = new Expr.BinOp(Expr.Op.OR, left, right);
        
        assertEquals(true, interpreter.eval(expr));
        
        left = new Expr.BoolLit(false);
        right = new Expr.BoolLit(false);
        expr = new Expr.BinOp(Expr.Op.OR, left, right);
        assertEquals(false, interpreter.eval(expr));
    }
    
    @Test
    void testUnaryOpNeg() {
        Expr operand = new Expr.IntLit(42);
        Expr expr = new Expr.UnaryOp(Expr.UnOp.NEG, operand);
        
        assertEquals(-42, interpreter.eval(expr));
        
        operand = new Expr.FloatLit(3.14);
        expr = new Expr.UnaryOp(Expr.UnOp.NEG, operand);
        assertEquals(-3.14, (Double)interpreter.eval(expr), 0.001);
    }
    
    @Test
    void testUnaryOpNot() {
        Expr operand = new Expr.BoolLit(true);
        Expr expr = new Expr.UnaryOp(Expr.UnOp.NOT, operand);
        
        assertEquals(false, interpreter.eval(expr));
        
        operand = new Expr.BoolLit(false);
        expr = new Expr.UnaryOp(Expr.UnOp.NOT, operand);
        assertEquals(true, interpreter.eval(expr));
    }
    
    @Test
    void testIfTrue() {
        Expr cond = new Expr.BoolLit(true);
        Expr thenBranch = new Expr.IntLit(10);
        Expr elseBranch = new Expr.IntLit(20);
        Expr expr = new Expr.If(cond, thenBranch, elseBranch);
        
        assertEquals(10, interpreter.eval(expr));
    }
    
    @Test
    void testIfFalse() {
        Expr cond = new Expr.BoolLit(false);
        Expr thenBranch = new Expr.IntLit(10);
        Expr elseBranch = new Expr.IntLit(20);
        Expr expr = new Expr.If(cond, thenBranch, elseBranch);
        
        assertEquals(20, interpreter.eval(expr));
    }
    
    @Test
    void testListLitEmpty() {
        Expr expr = new Expr.ListLit(List.of());
        Object result = interpreter.eval(expr);
        
        assertTrue(result instanceof List);
        assertEquals(0, ((List<?>)result).size());
    }
    
    @Test
    void testListLitWithElements() {
        Expr expr = new Expr.ListLit(List.of(
            new Expr.IntLit(1),
            new Expr.IntLit(2),
            new Expr.IntLit(3)
        ));
        Object result = interpreter.eval(expr);
        
        assertTrue(result instanceof List);
        List<?> list = (List<?>)result;
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }
    
    @Test
    void testSequenceEmpty() {
        Expr expr = new Expr.Sequence(List.of());
        assertEquals(Unit.INSTANCE, interpreter.eval(expr));
    }
    
    @Test
    void testSequenceSingle() {
        Expr expr = new Expr.Sequence(List.of(new Expr.IntLit(42)));
        assertEquals(42, interpreter.eval(expr));
    }
    
    @Test
    void testSequenceMultiple() {
        Expr expr = new Expr.Sequence(List.of(
            new Expr.IntLit(1),
            new Expr.IntLit(2),
            new Expr.IntLit(3)
        ));
        assertEquals(3, interpreter.eval(expr));
    }
    
    @Test
    void testFormatValueNull() {
        assertEquals("null", Interpreter.formatValue(null));
    }
    
    @Test
    void testFormatValueUnit() {
        assertEquals("()", Interpreter.formatValue(Unit.INSTANCE));
    }
    
    @Test
    void testFormatValueList() {
        List<Object> list = List.of(1, 2, 3);
        assertEquals("[1, 2, 3]", Interpreter.formatValue(list));
    }
    
    @Test
    void testFormatValueNestedList() {
        List<Object> inner = List.of(1, 2);
        List<Object> outer = List.of(inner, 3);
        assertEquals("[[1, 2], 3]", Interpreter.formatValue(outer));
    }
    
    @Test
    void testComplexExpression() {
        Expr value1 = new Expr.IntLit(10);
        Expr value2 = new Expr.IntLit(20);
        Expr body = new Expr.BinOp(
            Expr.Op.ADD,
            new Expr.Var("x"),
            new Expr.Var("y")
        );
        Expr expr = new Expr.Let("x", value1,
            new Expr.Let("y", value2, body)
        );
        
        assertEquals(30, interpreter.eval(expr));
    }
}
