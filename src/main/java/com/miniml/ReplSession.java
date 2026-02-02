package com.miniml;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import com.miniml.expr.Expr;

public class ReplSession {
    private int evalCount = 0;
    private final ReplClassLoader classLoader = new ReplClassLoader();
    private final Map<String, Type> typeEnvironment = new HashMap<>();
    private final Environment environment = new Environment();
    private final java.util.List<Module.TopLevel.LetDecl> letDeclarations = new java.util.ArrayList<>();
    private final java.util.List<String> imports = new java.util.ArrayList<>();
    private final Map<String, String> javaImports = new HashMap<>();
    
    public static class EvalResult {
        public final Object value;
        public final Type type;
        public final byte[] bytecode;
        public final boolean isDeclaration;
        
        public EvalResult(Object value, Type type, byte[] bytecode, boolean isDeclaration) {
            this.value = value;
            this.type = type;
            this.bytecode = bytecode;
            this.isDeclaration = isDeclaration;
        }
    }
    
    public EvalResult eval(String input) throws Exception {
        Lexer lexer = new Lexer(input);
        java.util.List<Token> tokens = lexer.tokenize();
        
        if (isImportStatement(tokens)) {
            return evalImport(tokens);
        } else if (isLetDeclaration(tokens)) {
            return evalLetDeclaration(input, tokens);
        } else {
            return evalExpression(input, tokens);
        }
    }
    
    private boolean isImportStatement(java.util.List<Token> tokens) {
        return !tokens.isEmpty() && tokens.get(0).type == Token.Type.IMPORT;
    }
    
    private EvalResult evalImport(java.util.List<Token> tokens) throws Exception {
        StringBuilder moduleName = new StringBuilder();
        int i = 1;
        
        while (i < tokens.size() && tokens.get(i).type != Token.Type.EOF) {
            if (tokens.get(i).type == Token.Type.IDENT) {
                moduleName.append(tokens.get(i).value);
            } else if (tokens.get(i).type == Token.Type.DOT) {
                moduleName.append(".");
            }
            i++;
        }
        
        String fullModuleName = moduleName.toString();
        if (!imports.contains(fullModuleName)) {
            imports.add(fullModuleName);
            
            TypeInference inference = new TypeInference();
            inference.setJavaImports(javaImports);
            inference.loadModuleInterface(fullModuleName);
            javaImports.putAll(inference.getJavaImports());
            
            Map<String, Type> moduleExports = inference.getEnvironment();
            for (Map.Entry<String, Type> entry : moduleExports.entrySet()) {
                if (!entry.getKey().contains(".")) {
                    typeEnvironment.put(entry.getKey(), entry.getValue());
                }
                typeEnvironment.put(fullModuleName + "." + entry.getKey(), entry.getValue());
            }
        }
        
        return new EvalResult(null, new Type.TUnit(), null, true);
    }
    
    private boolean isLetDeclaration(java.util.List<Token> tokens) {
        if (tokens.isEmpty() || tokens.get(0).type != Token.Type.LET) {
            return false;
        }
        for (Token token : tokens) {
            if (token.type == Token.Type.IN) {
                return false;
            }
        }
        return true;
    }
    
    private EvalResult evalLetDeclaration(String input, java.util.List<Token> tokens) throws Exception {
        int eofIndex = tokens.size() - 1;
        if (eofIndex >= 0 && tokens.get(eofIndex).type == Token.Type.EOF) {
            tokens.add(eofIndex, new Token(Token.Type.SEMICOLON, ";", 0, 0));
        } else {
            tokens.add(new Token(Token.Type.SEMICOLON, ";", 0, 0));
        }
        Parser parser = new Parser(tokens);
        Module.TopLevel.LetDecl letDecl = parser.parseTopLevelLet();
        
        if (typeEnvironment.containsKey(letDecl.name())) {
            throw new RuntimeException("Variable '" + letDecl.name() + "' is already defined with type " + typeEnvironment.get(letDecl.name()));
        }
        
        TypeInference inference = new TypeInference();
        inference.setJavaImports(javaImports);
        for (String importName : imports) {
            inference.loadModuleInterface(importName);
        }
        Type valueType = inference.infer(new HashMap<>(typeEnvironment), letDecl.value());
        valueType = inference.fullyResolve(valueType);
        
        Object value = letDecl.value().eval(environment);
        
        typeEnvironment.put(letDecl.name(), valueType);
        environment.define(letDecl.name(), value);
        letDeclarations.add(letDecl);
        
        return new EvalResult(value, valueType, null, true);
    }
    
    private EvalResult evalExpression(String input, java.util.List<Token> tokens) throws Exception {
        Parser parser = new Parser(tokens);
        Expr expr = parser.parseExpr();
        
        TypeInference inference = new TypeInference();
        inference.setJavaImports(javaImports);
        for (String importName : imports) {
            inference.loadModuleInterface(importName);
        }
        Type type = inference.infer(new HashMap<>(typeEnvironment), expr);
        type = inference.fullyResolve(type);
        
        Object value = expr.eval(environment);
        
        System.out.println(formatValue(value));
        
        String className = "Repl$Expr" + evalCount;
        Compiler compiler = new Compiler(className, inference.getTypeMap(), inference.getInstantiations());
        byte[] bytecode = compiler.compile(expr);
        
        return new EvalResult(value, type, bytecode, false);
    }
    
    public Type inferType(String input) throws Exception {
        Lexer lexer = new Lexer(input);
        java.util.List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Expr expr = parser.parseExpr();
        
        TypeInference inference = new TypeInference();
        inference.setJavaImports(javaImports);
        return inference.infer(new HashMap<>(typeEnvironment), expr);
    }
    
    public Expr parseExpr(String input) throws Exception {
        Lexer lexer = new Lexer(input);
        java.util.List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        return parser.parseExpr();
    }
    
    public String disassemble(byte[] bytecode) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ClassReader cr = new ClassReader(bytecode);
        TraceClassVisitor tcv = new TraceClassVisitor(pw);
        cr.accept(tcv, 0);
        return sw.toString();
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
