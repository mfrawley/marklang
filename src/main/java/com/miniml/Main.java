package com.miniml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.miniml.expr.Expr;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: miniml <source.mml>");
            System.exit(1);
        }
        
        String sourceFile = args[0];

        if (!sourceFile.startsWith("stdlib/") && !sourceFile.startsWith("tests/") && !sourceFile.startsWith("test_")) {
            try {
                compileStdLib();
            } catch (Exception e) {
                System.err.println("Error compiling standard library: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
        try {
            String source = Files.readString(Path.of(sourceFile));
            
            Lexer lexer = new Lexer(source, sourceFile);
            List<Token> tokens = lexer.tokenize();
            
            Parser parser = new Parser(tokens);
            Module module = parser.parseModule();
            
            TypeInference typeInf = new TypeInference();
            typeInf.setFilename(sourceFile);
            try {
                typeInf.inferModule(module);
                TypeDumper.dumpModule(module, typeInf.getTypeMap(), typeInf.getInstantiations());
            } catch (TypeInference.TypeException e) {
                String message = e.getFilename() + ": " + e.getMessage();
                System.err.println("Type error: " + message);
                System.exit(1);
            }
            
            String fileName = Path.of(sourceFile)
                .getFileName()
                .toString()
                .replace(".mml", "");
            
            StringBuilder className = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : fileName.toCharArray()) {
                if (c == '_') {
                    capitalizeNext = true;
                } else {
                    className.append(capitalizeNext ? Character.toUpperCase(c) : c);
                    capitalizeNext = false;
                }
            }
            String finalClassName = className.toString();
            
            Compiler compiler = new Compiler(finalClassName, typeInf.getTypeMap(), typeInf.getInstantiations());
            compiler.setLetRecTypes(typeInf.getLetRecTypes());
            byte[] bytecode = compiler.compileModule(module);
            
            Path targetDir;
            if (sourceFile.startsWith("tests/")) {
                targetDir = Path.of("target/minimltests");
            } else {
                targetDir = Path.of("target");
            }
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            
            String outputFile = targetDir + "/" + finalClassName + ".class";
            Files.write(Path.of(outputFile), bytecode);
            
            ModuleInterface moduleInterface = new ModuleInterface();
            Map<String, Type> env = typeInf.getEnvironment();
            for (Module.TopLevel decl : module.declarations()) {
                if (decl instanceof Module.TopLevel.FnDecl(String name, List<Module.Param> params, var returnType, Expr body)) {
                    Type fnType = env.get(name);
                    if (fnType != null) {
                        moduleInterface.addExport(name, fnType);
                    }
                }
            }
            String mliFile = targetDir + "/" + finalClassName + ".mli";
            moduleInterface.writeToFile(Path.of(mliFile));
            
            System.out.println("Compiled " + sourceFile + " -> " + outputFile);
            
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Compilation error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean containsUnresolvedTypeVars(Type type) {
        return switch (type) {
            case Type.TVar v -> true;
            case Type.TNumeric n -> true;
            case Type.TFun(Type param, Type result) ->
                containsUnresolvedTypeVars(param) || containsUnresolvedTypeVars(result);
            case Type.TList(Type elem) -> containsUnresolvedTypeVars(elem);
            case Type.TScheme(var vars, Type inner) -> containsUnresolvedTypeVars(inner);
            default -> false;
        };
    }
    
    private static void compileStdLib() throws Exception {
        Path stdlibDir = Path.of("stdlib");
        if (!Files.exists(stdlibDir)) {
            return;
        }
        
        Path buildFile = stdlibDir.resolve("BUILD");
        if (!Files.exists(buildFile)) {
            return;
        }
        
        List<String> moduleFiles = Files.readAllLines(buildFile);
        
        for (String moduleFile : moduleFiles) {
            moduleFile = moduleFile.trim();
            if (moduleFile.isEmpty() || moduleFile.startsWith("#")) {
                continue;
            }
            
            Path stdlibFile = stdlibDir.resolve(moduleFile);
            if (!Files.exists(stdlibFile)) {
                System.err.println("Warning: stdlib module not found: " + stdlibFile);
                continue;
            }
            
            try {
                String source = Files.readString(stdlibFile);
                
                Lexer lexer = new Lexer(source, stdlibFile.toString());
                List<Token> tokens = lexer.tokenize();
                
                Parser parser = new Parser(tokens);
                Module module = parser.parseModule();
                
                TypeInference typeInf = new TypeInference();
                try {
                    typeInf.inferModule(module);
                } catch (TypeInference.TypeException e) {
                    throw new RuntimeException("Type error in stdlib: " + e.getMessage(), e);
                }
                
                String fileName = stdlibFile.getFileName()
                    .toString()
                    .replace(".mml", "");
                
                Compiler compiler = new Compiler(fileName, typeInf.getTypeMap(), typeInf.getInstantiations());
                byte[] bytecode = compiler.compileModule(module);
                
                Path targetDir = Path.of("target");
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                
                String outputFile = "target/" + fileName + ".class";
                Files.write(Path.of(outputFile), bytecode);
                
                ModuleInterface moduleInterface = new ModuleInterface();
                Map<String, Type> env = typeInf.getEnvironment();
                for (Module.TopLevel decl : module.declarations()) {
                    if (decl instanceof Module.TopLevel.FnDecl(String name, List<Module.Param> params, var returnType, Expr body)) {
                        Type fnType = env.get(name);
                        if (fnType != null) {
                            moduleInterface.addExport(name, fnType);
                        }
                    }
                }
                String mliFile = "target/" + fileName + ".mli";
                moduleInterface.writeToFile(Path.of(mliFile));
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to compile stdlib module: " + stdlibFile, e);
            }
        }
    }
}
