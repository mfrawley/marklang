package com.miniml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import com.miniml.expr.*;
import static com.miniml.expr.Expr.Op;
import static com.miniml.expr.Expr.UnOp;

import java.util.*;

public class InterpreterTest {
    private Environment environment;
    
    @BeforeEach
    void setUp() {
        environment = new Environment();
    }
    
    @Test
    void testIntLit() {
        Expr expr = new IntLit(42);
        assertEquals(42, expr.eval(environment));
    }
    
    @Test
    void testFloatLit() {
        Expr expr = new FloatLit(3.14);
        assertEquals(3.14, expr.eval(environment));
    }
    
    @Test
    void testBoolLit() {
        Expr expr = new BoolLit(true);
        assertEquals(true, expr.eval(environment));
        
        expr = new BoolLit(false);
        assertEquals(false, expr.eval(environment));
    }
    
    @Test
    void testStringLit() {
        Expr expr = new StringLit("hello");
        assertEquals("hello", expr.eval(environment));
    }
    
    @Test
    void testUnit() {
        Expr expr = new com.miniml.expr.Unit();
        assertEquals(com.miniml.Unit.INSTANCE, expr.eval(environment));
    }
    
    @Test
    void testVarUndefined() {
        Expr expr = new Var("x");
        assertThrows(RuntimeException.class, () -> expr.eval(environment));
    }
    
    @Test
    void testVarDefined() {
        environment.define("x", 42);
        
        Expr expr = new Var("x");
        assertEquals(42, expr.eval(environment));
    }
    
    @Test
    void testLet() {
        Expr value = new IntLit(10);
        Expr body = new Var("x");
        Expr expr = new Let("x", value, body);
        
        assertEquals(10, expr.eval(environment));
    }
    
    @Test
    void testLetNested() {
        Expr innerValue = new IntLit(5);
        Expr innerBody = new Var("y");
        Expr outerValue = new IntLit(10);
        Expr outerBody = new Let("y", innerValue, innerBody);
        Expr expr = new Let("x", outerValue, outerBody);
        
        assertEquals(5, expr.eval(environment));
    }
    
    @Test
    void testBinOpAddInt() {
        Expr left = new IntLit(10);
        Expr right = new IntLit(20);
        Expr expr = new BinOp(Op.ADD, left, right);
        
        assertEquals(30, expr.eval(environment));
    }
    
    @Test
    void testBinOpAddDouble() {
        Expr left = new FloatLit(10.5);
        Expr right = new FloatLit(20.3);
        Expr expr = new BinOp(Op.ADD, left, right);
        
        assertEquals(30.8, (Double)expr.eval(environment), 0.001);
    }
    
    @Test
    void testBinOpAddMixed() {
        Expr left = new IntLit(10);
        Expr right = new FloatLit(20.5);
        Expr expr = new BinOp(Op.ADD, left, right);
        
        assertEquals(30.5, (Double)expr.eval(environment), 0.001);
    }
    
    @Test
    void testBinOpSubInt() {
        Expr left = new IntLit(30);
        Expr right = new IntLit(10);
        Expr expr = new BinOp(Op.SUB, left, right);
        
        assertEquals(20, expr.eval(environment));
    }
    
    @Test
    void testBinOpMulInt() {
        Expr left = new IntLit(6);
        Expr right = new IntLit(7);
        Expr expr = new BinOp(Op.MUL, left, right);
        
        assertEquals(42, expr.eval(environment));
    }
    
    @Test
    void testBinOpDivInt() {
        Expr left = new IntLit(20);
        Expr right = new IntLit(4);
        Expr expr = new BinOp(Op.DIV, left, right);
        
        assertEquals(5, expr.eval(environment));
    }
    
    @Test
    void testBinOpModInt() {
        Expr left = new IntLit(17);
        Expr right = new IntLit(5);
        Expr expr = new BinOp(Op.MOD, left, right);
        
        assertEquals(2, expr.eval(environment));
    }
    
    @Test
    void testBinOpEq() {
        Expr left = new IntLit(42);
        Expr right = new IntLit(42);
        Expr expr = new BinOp(Op.EQ, left, right);
        
        assertEquals(true, expr.eval(environment));
        
        right = new IntLit(43);
        expr = new BinOp(Op.EQ, left, right);
        assertEquals(false, expr.eval(environment));
    }
    
    @Test
    void testBinOpNe() {
        Expr left = new IntLit(42);
        Expr right = new IntLit(43);
        Expr expr = new BinOp(Op.NE, left, right);
        
        assertEquals(true, expr.eval(environment));
    }
    
    @Test
    void testBinOpLt() {
        Expr left = new IntLit(10);
        Expr right = new IntLit(20);
        Expr expr = new BinOp(Op.LT, left, right);
        
        assertEquals(true, expr.eval(environment));
        
        left = new IntLit(30);
        expr = new BinOp(Op.LT, left, right);
        assertEquals(false, expr.eval(environment));
    }
    
    @Test
    void testBinOpGt() {
        Expr left = new IntLit(30);
        Expr right = new IntLit(20);
        Expr expr = new BinOp(Op.GT, left, right);
        
        assertEquals(true, expr.eval(environment));
    }
    
    @Test
    void testBinOpLe() {
        Expr left = new IntLit(20);
        Expr right = new IntLit(20);
        Expr expr = new BinOp(Op.LE, left, right);
        
        assertEquals(true, expr.eval(environment));
        
        left = new IntLit(19);
        expr = new BinOp(Op.LE, left, right);
        assertEquals(true, expr.eval(environment));
    }
    
    @Test
    void testBinOpGe() {
        Expr left = new IntLit(20);
        Expr right = new IntLit(20);
        Expr expr = new BinOp(Op.GE, left, right);
        
        assertEquals(true, expr.eval(environment));
        
        left = new IntLit(21);
        expr = new BinOp(Op.GE, left, right);
        assertEquals(true, expr.eval(environment));
    }
    
    @Test
    void testBinOpAnd() {
        Expr left = new BoolLit(true);
        Expr right = new BoolLit(true);
        Expr expr = new BinOp(Op.AND, left, right);
        
        assertEquals(true, expr.eval(environment));
        
        right = new BoolLit(false);
        expr = new BinOp(Op.AND, left, right);
        assertEquals(false, expr.eval(environment));
    }
    
    @Test
    void testBinOpOr() {
        Expr left = new BoolLit(true);
        Expr right = new BoolLit(false);
        Expr expr = new BinOp(Op.OR, left, right);
        
        assertEquals(true, expr.eval(environment));
        
        left = new BoolLit(false);
        right = new BoolLit(false);
        expr = new BinOp(Op.OR, left, right);
        assertEquals(false, expr.eval(environment));
    }
    
    @Test
    void testUnaryOpNeg() {
        Expr operand = new IntLit(42);
        Expr expr = new UnaryOp(UnOp.NEG, operand);
        
        assertEquals(-42, expr.eval(environment));
        
        operand = new FloatLit(3.14);
        expr = new UnaryOp(UnOp.NEG, operand);
        assertEquals(-3.14, (Double)expr.eval(environment), 0.001);
    }
    
    @Test
    void testUnaryOpNot() {
        Expr operand = new BoolLit(true);
        Expr expr = new UnaryOp(UnOp.NOT, operand);
        
        assertEquals(false, expr.eval(environment));
        
        operand = new BoolLit(false);
        expr = new UnaryOp(UnOp.NOT, operand);
        assertEquals(true, expr.eval(environment));
    }
    
    @Test
    void testIfTrue() {
        Expr cond = new BoolLit(true);
        Expr thenBranch = new IntLit(10);
        Expr elseBranch = new IntLit(20);
        Expr expr = new If(cond, thenBranch, elseBranch);
        
        assertEquals(10, expr.eval(environment));
    }
    
    @Test
    void testIfFalse() {
        Expr cond = new BoolLit(false);
        Expr thenBranch = new IntLit(10);
        Expr elseBranch = new IntLit(20);
        Expr expr = new If(cond, thenBranch, elseBranch);
        
        assertEquals(20, expr.eval(environment));
    }
    
    @Test
    void testListLitEmpty() {
        Expr expr = new ListLit(List.of());
        Object result = expr.eval(environment);
        
        assertTrue(result instanceof List);
        assertEquals(0, ((List<?>)result).size());
    }
    
    @Test
    void testListLitWithElements() {
        Expr expr = new ListLit(List.of(
            new IntLit(1),
            new IntLit(2),
            new IntLit(3)
        ));
        Object result = expr.eval(environment);
        
        assertTrue(result instanceof List);
        List<?> list = (List<?>)result;
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }
    
    @Test
    void testSequenceEmpty() {
        Expr expr = new Sequence(List.of());
        assertEquals(Unit.INSTANCE, expr.eval(environment));
    }
    
    @Test
    void testSequenceSingle() {
        Expr expr = new Sequence(List.of(new IntLit(42)));
        assertEquals(42, expr.eval(environment));
    }
    
    @Test
    void testSequenceMultiple() {
        Expr expr = new Sequence(List.of(
            new IntLit(1),
            new IntLit(2),
            new IntLit(3)
        ));
        assertEquals(3, expr.eval(environment));
    }
    
    @Test
    void testFormatValueNull() {
        assertEquals("null", formatValue(null));
    }
    
    @Test
    void testFormatValueUnit() {
        assertEquals("()", formatValue(Unit.INSTANCE));
    }
    
    @Test
    void testFormatValueList() {
        List<Object> list = List.of(1, 2, 3);
        assertEquals("[1, 2, 3]", formatValue(list));
    }
    
    @Test
    void testFormatValueNestedList() {
        List<Object> inner = List.of(1, 2);
        List<Object> outer = List.of(inner, 3);
        assertEquals("[[1, 2], 3]", formatValue(outer));
    }
    
    @Test
    void testComplexExpression() {
        Expr value1 = new IntLit(10);
        Expr value2 = new IntLit(20);
        Expr body = new BinOp(
            Op.ADD,
            new Var("x"),
            new Var("y")
        );
        Expr expr = new Let("x", value1,
            new Let("y", value2, body)
        );
        
        assertEquals(30, expr.eval(environment));
    }
    
    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof com.miniml.Unit) return "()";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }
}
