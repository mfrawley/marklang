package com.miniml;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ReplSession {
    private int evalCount = 0;
    private final ReplClassLoader classLoader = new ReplClassLoader();
    private final Map<String, Type> typeEnvironment = new HashMap<>();
    private final Map<String, Object> valueEnvironment = new HashMap<>();
    private final java.util.List<Module.TopLevel.LetDecl> letDeclarations = new java.util.ArrayList<>();
    private final java.util.List<String> imports = new java.util.ArrayList<>();
    private final Interpreter interpreter = new Interpreter();
    
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
        int i = 1;
        while (i < tokens.size() && tokens.get(i).type != Token.Type.EOF) {
            if (tokens.get(i).type == Token.Type.IDENT) {
                String moduleName = tokens.get(i).value;
                if (!imports.contains(moduleName)) {
                    imports.add(moduleName);
                    
                    TypeInference inference = new TypeInference();
                    inference.loadModuleInterface(moduleName);
                    Map<String, Type> moduleExports = inference.getEnvironment();
                    
                    for (Map.Entry<String, Type> entry : moduleExports.entrySet()) {
                        if (!entry.getKey().contains(".")) {
                            typeEnvironment.put(entry.getKey(), entry.getValue());
                        }
                        typeEnvironment.put(moduleName + "." + entry.getKey(), entry.getValue());
                    }
                }
            }
            i++;
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
        for (String importName : imports) {
            inference.loadModuleInterface(importName);
        }
        Type valueType = inference.infer(new HashMap<>(typeEnvironment), letDecl.value());
        valueType = inference.fullyResolve(valueType);
        
        Interpreter interp = new Interpreter(valueEnvironment);
        Object value = interp.eval(letDecl.value());
        
        typeEnvironment.put(letDecl.name(), valueType);
        valueEnvironment.put(letDecl.name(), value);
        letDeclarations.add(letDecl);
        
        return new EvalResult(value, valueType, null, true);
    }
    
    private EvalResult evalExpression(String input, java.util.List<Token> tokens) throws Exception {
        Parser parser = new Parser(tokens);
        Expr expr = parser.parseExpr();
        
        TypeInference inference = new TypeInference();
        for (String importName : imports) {
            inference.loadModuleInterface(importName);
        }
        Type type = inference.infer(new HashMap<>(typeEnvironment), expr);
        type = inference.fullyResolve(type);
        
        Interpreter interp = new Interpreter(valueEnvironment);
        Object value = interp.eval(expr);
        
        System.out.println(Interpreter.formatValue(value));
        
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
}
