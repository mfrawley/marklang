package com.miniml;
import com.miniml.expr.*;
import static com.miniml.expr.Expr.Op;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TypeInferenceOverloadTest {
    
    @Test
    void testDoubleParameterInfersDoubleAdditionIsolated() throws TypeInference.TypeException {
        TypeInference ti = new TypeInference();
        
        Expr x = new Var("x");
        Expr body = new BinOp(Op.ADD, x, x);
        
        Module.Param param = new Module.Param("x", Optional.of(new Type.TDouble()));
        List<Module.TopLevel> decls = List.of(
            new Module.TopLevel.FnDecl("add", List.of(param), Optional.of(new Type.TDouble()), body)
        );
        
        Module module = new Module(List.of(), decls, null);
        ti.inferModule(module);
        ti.pruneTypeMap();
        
        Type bodyType = ti.getTypeMap().get(body);
        System.out.println("Single double function body type: " + bodyType);
        Type prunedBodyType = ti.fullyResolve(bodyType);
        System.out.println("Single double function body type (resolved): " + prunedBodyType);
        
        assertTrue(prunedBodyType instanceof Type.TDouble, 
            "Expected body type to be Double, but got: " + prunedBodyType);
    }
    
    @Test
    void testDoubleParameterInfersDoubleAddition() throws TypeInference.TypeException {
        TypeInference ti = new TypeInference();
        
        Expr x = new Var("x");
        Expr body = new BinOp(Op.ADD, x, x);
        
        Module.Param param = new Module.Param("x", Optional.of(new Type.TDouble()));
        
        Type fnType = ti.inferTopLevelFn(List.of(param), Optional.empty(), body);
        
        Type bodyType = ti.getTypeMap().get(body);
        Type prunedBodyType = ti.fullyResolve(bodyType);
        
        assertTrue(prunedBodyType instanceof Type.TDouble, 
            "Expected body type to be Double, but got: " + prunedBodyType);
    }
    
    @Test
    void testIntParameterInfersIntAdditionIsolated() throws TypeInference.TypeException {
        TypeInference ti = new TypeInference();
        
        Expr x = new Var("x");
        Expr body = new BinOp(Op.ADD, x, x);
        
        Module.Param param = new Module.Param("x", Optional.of(new Type.TInt()));
        List<Module.TopLevel> decls = List.of(
            new Module.TopLevel.FnDecl("add", List.of(param), Optional.of(new Type.TInt()), body)
        );
        
        Module module = new Module(List.of(), decls, null);
        ti.inferModule(module);
        ti.pruneTypeMap();
        
        Type bodyType = ti.getTypeMap().get(body);
        System.out.println("Single int function body type: " + bodyType);
        Type prunedBodyType = ti.fullyResolve(bodyType);
        System.out.println("Single int function body type (resolved): " + prunedBodyType);
        
        assertTrue(prunedBodyType instanceof Type.TInt, 
            "Expected body type to be Int, but got: " + prunedBodyType);
    }
    
    @Test
    void testIntParameterInfersIntAddition() throws TypeInference.TypeException {
        TypeInference ti = new TypeInference();
        
        Expr x = new Var("x");
        Expr body = new BinOp(Op.ADD, x, x);
        
        Module.Param param = new Module.Param("x", Optional.of(new Type.TInt()));
        
        Type fnType = ti.inferTopLevelFn(List.of(param), Optional.empty(), body);
        
        Type bodyType = ti.getTypeMap().get(body);
        Type prunedBodyType = ti.fullyResolve(bodyType);
        
        assertTrue(prunedBodyType instanceof Type.TInt, 
            "Expected body type to be Int, but got: " + prunedBodyType);
    }
    
    @Test
    void testTwoSeparateFunctionsPreserveTypes() throws TypeInference.TypeException {
        TypeInference ti = new TypeInference();
        
        Expr xDouble = new Var("x");
        Expr bodyDouble = new BinOp(Op.ADD, xDouble, xDouble);
        Module.Param paramDouble = new Module.Param("x", Optional.of(new Type.TDouble()));
        
        Expr xInt = new Var("x");
        Expr bodyInt = new BinOp(Op.ADD, xInt, xInt);
        Module.Param paramInt = new Module.Param("x", Optional.of(new Type.TInt()));
        
        System.out.println("bodyDouble identity: " + System.identityHashCode(bodyDouble));
        System.out.println("bodyInt identity: " + System.identityHashCode(bodyInt));
        System.out.println("Are they the same object? " + (bodyDouble == bodyInt));
        System.out.println("Are they equal? " + bodyDouble.equals(bodyInt));
        
        List<Module.TopLevel> decls = List.of(
            new Module.TopLevel.FnDecl("addDouble", List.of(paramDouble), Optional.of(new Type.TDouble()), bodyDouble),
            new Module.TopLevel.FnDecl("addInt", List.of(paramInt), Optional.of(new Type.TInt()), bodyInt)
        );
        
        Module module = new Module(List.of(), decls, null);
        ti.inferModule(module);
        
        System.out.println("=== BEFORE pruneTypeMap ===");
        System.out.println("Double body type: " + ti.getTypeMap().get(bodyDouble));
        System.out.println("Int body type: " + ti.getTypeMap().get(bodyInt));
        
        ti.pruneTypeMap();
        
        System.out.println("=== AFTER pruneTypeMap ===");
        Type bodyDoubleType = ti.getTypeMap().get(bodyDouble);
        System.out.println("Double body type (from map): " + bodyDoubleType);
        Type prunedDoubleType = ti.fullyResolve(bodyDoubleType);
        System.out.println("Double body type (after fullyResolve): " + prunedDoubleType);
        
        Type bodyIntType = ti.getTypeMap().get(bodyInt);
        System.out.println("Int body type (before fullyResolve): " + bodyIntType);
        Type prunedIntType = ti.fullyResolve(bodyIntType);
        System.out.println("Int body type (after fullyResolve): " + prunedIntType);
        
        assertTrue(prunedDoubleType instanceof Type.TDouble, 
            "Expected first function body type to be Double, but got: " + prunedDoubleType);
        assertTrue(prunedIntType instanceof Type.TInt, 
            "Expected second function body type to be Int, but got: " + prunedIntType);
    }
    
    @Test
    void testOverloadResolution() throws TypeInference.TypeException {
        TypeInference ti = new TypeInference();
        
        Expr xDouble = new Var("x");
        Expr bodyDouble = new BinOp(Op.ADD, xDouble, xDouble);
        Module.Param paramDouble = new Module.Param("x", Optional.of(new Type.TDouble()));
        
        List<Module.TopLevel> decls = List.of(
            new Module.TopLevel.FnDecl("add", List.of(paramDouble), Optional.of(new Type.TDouble()), bodyDouble)
        );
        
        Expr intArg = new IntLit(5);
        Expr call = new App(new Var("add"), List.of(intArg));
        
        Module module = new Module(List.of(), decls, call);
        
        try {
            ti.inferModule(module);
            fail("Should fail when calling double function with int argument");
        } catch (TypeInference.TypeException e) {
            assertTrue(e.getMessage().contains("cannot unify") || 
                      e.getMessage().contains("Type mismatch"),
                "Expected type mismatch error, got: " + e.getMessage());
        }
    }
    
    @Test
    void testOverloadWithMatchingType() throws TypeInference.TypeException {
        TypeInference ti = new TypeInference();
        
        Expr xDouble = new Var("x");
        Expr bodyDouble = new BinOp(Op.ADD, xDouble, xDouble);
        Module.Param paramDouble = new Module.Param("x", Optional.of(new Type.TDouble()));
        
        List<Module.TopLevel> decls = List.of(
            new Module.TopLevel.FnDecl("add", List.of(paramDouble), Optional.of(new Type.TDouble()), bodyDouble)
        );
        
        Expr doubleArg = new FloatLit(5.0);
        Expr call = new App(new Var("add"), List.of(doubleArg));
        
        Module module = new Module(List.of(), decls, call);
        
        Type resultType = ti.inferModule(module);
        
        assertNotNull(resultType, "Inference should succeed");
    }
}
