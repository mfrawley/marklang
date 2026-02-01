package com.miniml;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {
    
    private Object compileAndRun(String input) throws Exception {
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
    void testArithmetic() throws Exception {
        compileAndRun("2 + 3 * 4 + 10 / 2 + 25");
    }
    
    @Test
    void testMathMaxShort() throws Exception {
        compileAndRun("""
                import Math
                Math.max 42 17 == 42""");
    }
    
    @Test
    void testMathPowShort() throws Exception {
        compileAndRun("""
            import Math
            let result = Math.pow 5.0 2.0 in
            let diff = result - 25.0 in
            let abs_diff = if diff < 0.0 then 0.0 - diff else diff in
            abs_diff < 0.00001
            """);
    }
    
    @Test
    void testMathSqrtShort() throws Exception {
        compileAndRun("""
            import Math
            let result = Math.sqrt 25.0 in
            let diff = result - 5.0 in
            let abs_diff = if diff < 0.0 then 0.0 - diff else diff in
            abs_diff < 0.00001
            """);
    }
    
    static class TestClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }
}
