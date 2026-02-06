package com.miniml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class ReplSessionTest {
    private ReplSession repl;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    public void setup() {
        repl = new ReplSession();
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @Test
    public void testBasicArithmetic() throws Exception {
        ReplSession.EvalResult result = repl.eval("5 + 3");
        System.setOut(originalOut);
        
        assertFalse(result.isDeclaration);
        assertEquals(new Type.TInt(), result.type);
        assertTrue(outputStream.toString().contains("8"));
    }

    @Test
    public void testLetDeclaration() throws Exception {
        ReplSession.EvalResult result = repl.eval("let x = 42");
        System.setOut(originalOut);
        
        assertTrue(result.isDeclaration);
        assertEquals(new Type.TInt(), result.type);
    }


    @Test
    public void testImportList() throws Exception {
        ReplSession.EvalResult result = repl.eval("import List");
        System.setOut(originalOut);
        
        assertTrue(result.isDeclaration);
        assertEquals(new Type.TUnit(), result.type);
    }

    @Test
    public void testImportedFunctionType() throws Exception {
        repl.eval("import List");
        outputStream.reset();
        
        ReplSession.EvalResult result = repl.eval("List.map");
        System.setOut(originalOut);
        
        assertFalse(result.isDeclaration);
        assertInstanceOf(Type.TFun.class, result.type);
        assertTrue(outputStream.toString().contains("<function"));
    }

    @Test
    public void testImportedFunctionCall() throws Exception {
        repl.eval("import List");
        outputStream.reset();
        
        ReplSession.EvalResult result = repl.eval("List.length [1, 2, 3]");
        System.setOut(originalOut);
        
        assertFalse(result.isDeclaration);
        assertEquals(new Type.TInt(), result.type);
        assertTrue(outputStream.toString().contains("3"));
    }

    @Test
    public void testQualifiedVariableCall() throws Exception {
        repl.eval("import List");
        outputStream.reset();
        
        ReplSession.EvalResult result = repl.eval("List.length [1, 2, 3, 4, 5]");
        System.setOut(originalOut);
        
        assertTrue(outputStream.toString().contains("5"));
    }


    @Test
    public void testListLiteral() throws Exception {
        ReplSession.EvalResult result = repl.eval("[1, 2, 3]");
        System.setOut(originalOut);
        
        assertInstanceOf(Type.TList.class, result.type);
        assertTrue(outputStream.toString().contains("[1, 2, 3]"));
    }

    @Test
    public void testBooleanExpression() throws Exception {
        ReplSession.EvalResult result = repl.eval("5 > 3");
        System.setOut(originalOut);
        
        assertEquals(new Type.TBool(), result.type);
        assertTrue(outputStream.toString().contains("true"));
    }

    @Test
    public void testStringLiteral() throws Exception {
        ReplSession.EvalResult result = repl.eval("\"hello\"");
        System.setOut(originalOut);
        
        assertEquals(new Type.TString(), result.type);
        assertTrue(outputStream.toString().contains("hello"));
    }



    @Test
    public void testTypeInference() throws Exception {
        Type type = repl.inferType("5 + 3");
        assertEquals(new Type.TInt(), type);
    }

    @Test
    public void testTypeInferenceAfterImport() throws Exception {
        repl.eval("import List");
        
        Type type = repl.inferType("List.length [1, 2, 3]");
        assertEquals(new Type.TInt(), type);
    }

    @Test
    public void testUndefinedVariableError() {
        assertThrows(Exception.class, () -> repl.eval("undefined_var"));
    }

}
