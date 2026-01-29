package com.miniml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: miniml <source.ml>");
            System.exit(1);
        }

        String sourceFile = args[0];
        try {
            String source = Files.readString(Path.of(sourceFile));
            
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.tokenize();
            
            Parser parser = new Parser(tokens);
            Expr ast = parser.parse();
            
            String className = Path.of(sourceFile)
                .getFileName()
                .toString()
                .replace(".ml", "");
            
            Compiler compiler = new Compiler(className);
            byte[] bytecode = compiler.compile(ast);
            
            String outputFile = className + ".class";
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
}
