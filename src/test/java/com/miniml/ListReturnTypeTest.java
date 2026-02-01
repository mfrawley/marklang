package com.miniml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

public class ListReturnTypeTest {
    
    @Test
    void testParsing_ListTypeAnnotation() {
        String code = "fn identity (xs: list<int>) : list<int> = xs;";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Module module = parser.parseModule();
        
        assertEquals(1, module.declarations().size());
        Module.TopLevel.FnDecl fnDecl = (Module.TopLevel.FnDecl) module.declarations().get(0);
        assertEquals("identity", fnDecl.name());
        assertEquals(1, fnDecl.params().size());
        
        Module.Param param = fnDecl.params().get(0);
        assertTrue(param.typeAnnotation().isPresent());
        assertTrue(param.typeAnnotation().get() instanceof Type.TList);
        
        Type.TList listType = (Type.TList) param.typeAnnotation().get();
        assertTrue(listType.elementType() instanceof Type.TInt);
        
        assertTrue(fnDecl.returnType().isPresent());
        assertTrue(fnDecl.returnType().get() instanceof Type.TList);
    }
    
    @Test
    void testTypeInference_ListReturnType() throws TypeInference.TypeException {
        String code = "fn identity (xs: list<int>) : list<int> = xs; identity [1, 2, 3]";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Module module = parser.parseModule();
        
        TypeInference ti = new TypeInference();
        Type resultType = ti.inferModule(module);
        
        System.out.println("Result type: " + resultType);
        assertTrue(resultType instanceof Type.TList);
        Type.TList listType = (Type.TList) resultType;
        assertTrue(listType.elementType() instanceof Type.TInt);
    }
    
    @Test
    void testCompilation_ListReturnType() throws TypeInference.TypeException {
        String code = "fn identity (xs: list<int>) : list<int> = xs; identity [1, 2, 3]";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Module module = parser.parseModule();
        
        TypeInference ti = new TypeInference();
        ti.inferModule(module);
        
        Compiler compiler = new Compiler("TestListReturn", ti.getTypeMap(), ti.getInstantiations());
        
        // This should not throw an exception during compilation
        assertDoesNotThrow(() -> compiler.compileModule(module));
    }
    
    @Test
    void testRecursive_ListTransformation() throws TypeInference.TypeException {
        String code = "fn twice_all (xs: list<int>) : list<int> = match xs with | [] -> [] | h :: t -> (h * 2) :: twice_all t; twice_all [1, 2, 3]";
        
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Module module = parser.parseModule();
        
        System.out.println("Main expression: " + module.mainExpr());
        System.out.println("Main expression class: " + (module.mainExpr() != null ? module.mainExpr().getClass().getName() : "null"));
        
        TypeInference ti = new TypeInference();
        Type resultType = ti.inferModule(module);
        
        System.out.println("Result type: " + resultType);
        assertTrue(resultType instanceof Type.TList, "Expected TList but got: " + resultType);
        
        Compiler compiler = new Compiler("TestTwiceAll", ti.getTypeMap(), ti.getInstantiations());
        assertDoesNotThrow(() -> compiler.compileModule(module));
    }
}
