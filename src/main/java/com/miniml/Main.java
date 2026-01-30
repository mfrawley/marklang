package com.miniml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: miniml <source.ml>");
            System.exit(1);
        }

        String sourceFile = args[0];
        
        if (!sourceFile.startsWith("stdlib/")) {
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
            
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.tokenize();
            
            Parser parser = new Parser(tokens);
            Module module = parser.parseModule();
            
            TypeInference typeInf = new TypeInference();
            try {
                typeInf.inferModule(module);
            } catch (TypeInference.TypeException e) {
                System.err.println("Type error: " + e.getMessage());
                System.exit(1);
            }
            
            String fileName = Path.of(sourceFile)
                .getFileName()
                .toString()
                .replace(".ml", "");
            
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
            byte[] bytecode = compiler.compileModule(module);
            
            Path targetDir = Path.of("target");
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            
            String outputFile = "target/" + finalClassName + ".class";
            Files.write(Path.of(outputFile), bytecode);
            
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
                
                Lexer lexer = new Lexer(source);
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
                    .replace(".ml", "");
                
                Compiler compiler = new Compiler(fileName, typeInf.getTypeMap(), typeInf.getInstantiations());
                byte[] bytecode = compiler.compileModule(module);
                
                Path targetDir = Path.of("target");
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                
                String outputFile = "target/" + fileName + ".class";
                Files.write(Path.of(outputFile), bytecode);
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to compile stdlib module: " + stdlibFile, e);
            }
        }
    }
}
