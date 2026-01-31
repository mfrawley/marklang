package com.miniml;

import java.util.Map;
import java.util.Set;

public class TypeDumper {
    
    public static void dumpModule(Module module, Map<Expr, Type> typeMap, Map<String, Set<Type>> instantiations) {
        System.out.println("=== MODULE DUMP ===");
        System.out.println("\nImports: " + module.imports());
        
        System.out.println("\nDeclarations:");
        for (Module.TopLevel decl : module.declarations()) {
            if (decl instanceof Module.TopLevel.FnDecl(String name, var params, var returnType, Expr body)) {
                System.out.println("  fn " + name + " " + params + " =");
                dumpExpr(body, typeMap, 4);
                Type bodyType = typeMap.get(body);
                System.out.println("    : " + bodyType);
                
                if (instantiations.containsKey(name)) {
                    System.out.println("    instantiations: " + instantiations.get(name));
                }
            }
        }
        
        if (module.mainExpr() != null) {
            System.out.println("\nMain expression:");
            dumpExpr(module.mainExpr(), typeMap, 2);
            Type mainType = typeMap.get(module.mainExpr());
            System.out.println("  : " + mainType);
        }
        
        System.out.println("\n=== END MODULE DUMP ===\n");
    }
    
    private static void dumpExpr(Expr expr, Map<Expr, Type> typeMap, int indent) {
        String prefix = " ".repeat(indent);
        Type type = typeMap.get(expr);
        
        switch (expr) {
            case Expr.IntLit(int value) -> 
                System.out.println(prefix + value + " : " + type);
            case Expr.FloatLit(double value) ->
                System.out.println(prefix + value + " : " + type);
            case Expr.Var(String name) ->
                System.out.println(prefix + name + " : " + type);
            case Expr.BinOp(Expr.Op op, Expr left, Expr right) -> {
                System.out.println(prefix + "(" + op + ")");
                dumpExpr(left, typeMap, indent + 2);
                dumpExpr(right, typeMap, indent + 2);
                System.out.println(prefix + "  => " + type);
            }
            case Expr.App(Expr func, var args) -> {
                System.out.println(prefix + "App:");
                dumpExpr(func, typeMap, indent + 2);
                for (Expr arg : args) {
                    dumpExpr(arg, typeMap, indent + 2);
                }
                System.out.println(prefix + "  => " + type);
            }
            case Expr.Print(Expr value) -> {
                System.out.println(prefix + "print:");
                dumpExpr(value, typeMap, indent + 2);
                System.out.println(prefix + "  => " + type);
            }
            default -> System.out.println(prefix + expr.getClass().getSimpleName() + " : " + type);
        }
    }
}
