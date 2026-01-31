package com.miniml;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TypeInferenceTest {
    
    private Type infer(String input) throws TypeInference.TypeException {
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Expr expr = parser.parseExpr();
        TypeInference inference = new TypeInference();
        return inference.infer(new HashMap<>(), expr);
    }
    
    @Test
    void testIntLiteral() throws TypeInference.TypeException {
        Type type = infer("42");
        assertTrue(type instanceof Type.TInt);
    }
    
    @Test
    void testFloatLiteral() throws TypeInference.TypeException {
        Type type = infer("3.14");
        assertTrue(type instanceof Type.TDouble);
    }
    
    @Test
    void testStringLiteral() throws TypeInference.TypeException {
        Type type = infer("\"hello\"");
        assertTrue(type instanceof Type.TString);
    }
    
    @Test
    void testAddition() throws TypeInference.TypeException {
        Type type = infer("1 + 2");
        assertTrue(type instanceof Type.TInt || type instanceof Type.TNumeric);
    }
    
    @Test
    void testMultiplication() throws TypeInference.TypeException {
        Type type = infer("3 * 4");
        assertTrue(type instanceof Type.TInt || type instanceof Type.TNumeric);
    }
    
    @Test
    void testComparison() throws TypeInference.TypeException {
        Type type = infer("5 == 5");
        assertTrue(type instanceof Type.TBool);
    }
    
    @Test
    void testLessThan() throws TypeInference.TypeException {
        Type type = infer("3 < 5");
        assertTrue(type instanceof Type.TBool);
    }
    
    @Test
    void testLetExpression() throws TypeInference.TypeException {
        Type type = infer("let x = 10 in x + 5");
        assertTrue(type instanceof Type.TInt || type instanceof Type.TNumeric);
    }
    
    @Test
    void testIfExpression() throws TypeInference.TypeException {
        Type type = infer("if 1 == 1 then 10 else 20");
        assertTrue(type instanceof Type.TInt);
    }
    
    @Test
    void testIfWithMismatchedBranches() {
        assertThrows(TypeInference.TypeException.class, () -> {
            infer("if true then 1 else \"hello\"");
        });
    }
    
    @Test
    void testListLiteral() throws TypeInference.TypeException {
        Type type = infer("[1, 2, 3]");
        assertTrue(type instanceof Type.TList);
        Type.TList listType = (Type.TList) type;
        assertTrue(listType.elementType() instanceof Type.TInt);
    }
    
    @Test
    void testEmptyList() throws TypeInference.TypeException {
        Type type = infer("[]");
        assertTrue(type instanceof Type.TList);
    }
    
    @Test
    void testStringInterpolation() throws TypeInference.TypeException {
        Type type = infer("\"value: {x}\"");
        assertTrue(type instanceof Type.TString);
    }
    
    @Test
    void testPrintExpression() throws TypeInference.TypeException {
        Type type = infer("print \"hello\"");
        assertTrue(type instanceof Type.TUnit);
    }
    
    @Test
    void testUndefinedVariable() {
        assertThrows(TypeInference.TypeException.class, () -> {
            infer("x");
        });
    }
    
    @Test
    void testTypeUnificationFailure() {
        assertThrows(TypeInference.TypeException.class, () -> {
            infer("1 + \"hello\"");
        });
    }
    
    @Test
    void testNumericTypeInference() throws TypeInference.TypeException {
        Type type = infer("2 * 3");
        assertTrue(type instanceof Type.TInt || type instanceof Type.TNumeric);
    }
    
    @Test
    void testFloatArithmetic() throws TypeInference.TypeException {
        Type type = infer("2.5 * 3.5");
        assertTrue(type instanceof Type.TDouble || type instanceof Type.TNumeric);
    }
    
    @Test
    void testNestedLet() throws TypeInference.TypeException {
        Type type = infer("let x = 10 in let y = 20 in x + y");
        assertTrue(type instanceof Type.TInt || type instanceof Type.TNumeric);
    }
    
    @Test
    void testLetShadowing() throws TypeInference.TypeException {
        Type type = infer("let x = 10 in let x = 20 in x");
        assertTrue(type instanceof Type.TInt);
    }
    
    @Test
    void testConsList() throws TypeInference.TypeException {
        Type type = infer("1 :: [2, 3]");
        assertTrue(type instanceof Type.TList);
        Type.TList listType = (Type.TList) type;
        assertTrue(listType.elementType() instanceof Type.TInt);
    }
    
    @Test
    void testMatchExpression() throws TypeInference.TypeException {
        Type type = infer("match [1, 2] with | [] -> 0 | h :: t -> h");
        assertTrue(type instanceof Type.TInt);
    }
}
