package com.miniml;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    
    private Object compile(String input) throws Exception {
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Module module = parser.parseModule();
        
        TypeInference typeInf = new TypeInference();
        typeInf.inferModule(module);
        
        Compiler compiler = new Compiler("TestClass", typeInf.getTypeMap(), typeInf.getInstantiations());
        byte[] bytecode = compiler.compileModule(module);
        
        TestClassLoader classLoader = new TestClassLoader();
        Class<?> clazz = classLoader.defineClass("TestClass", bytecode);
        Method main = clazz.getMethod("main", String[].class);
        main.invoke(null, (Object) new String[0]);
        
        return null;
    }
    
    @Test
    void testSimpleArithmetic() throws Exception {
        compile("2 + 3");
    }
    
    @Test
    void testMultiplication() throws Exception {
        compile("3 * 4");
    }
    
    @Test
    void testLetExpression() throws Exception {
        compile("let x = 10 in x + 5");
    }
    
    @Test
    void testIfExpression() throws Exception {
        compile("if 1 == 1 then 10 else 20");
    }
    
    @Test
    void testPrintExpression() throws Exception {
        compile("print \"hello\"");
    }
    
    @Test
    void testListLiteral() throws Exception {
        compile("[1, 2, 3]");
    }
    
    @Test
    void testComparison() throws Exception {
        compile("5 > 3");
    }
    
    @Test
    void testStringInterpolation() throws Exception {
        compile("let x = 42 in print \"value: {x}\"");
    }
    
    @Test
    void testNestedLet() throws Exception {
        compile("let x = 10 in let y = 20 in x + y");
    }
    
    @Test
    void testJavaCall() throws Exception {
        compile("java_call \"java.lang.Math\" \"abs\" (0 - 5)");
    }
    
    @Test
    void testMatchExpression() throws Exception {
        compile("match [1, 2, 3] with | [] -> 0 | h :: t -> h");
    }
    
    static class TestClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }
}
