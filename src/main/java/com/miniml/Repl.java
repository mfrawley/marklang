package com.miniml;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public class Repl {
    private final ReplSession session = new ReplSession();
    
    public void run() throws IOException {
        System.out.println("MiniML REPL v0.1");
        System.out.println("Type :help for available commands, :quit to exit");
        System.out.println();
        
        Terminal terminal = TerminalBuilder.builder().build();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
        
        while (true) {
            String input;
            try {
                input = reader.readLine("MiniML> ");
            } catch (UserInterruptException e) {
                continue;
            } catch (EndOfFileException e) {
                System.out.println("Goodbye!");
                break;
            }
            
            if (input == null || input.equals(":quit") || input.equals(":exit")) {
                System.out.println("Goodbye!");
                break;
            }
            
            input = input.trim();
            if (input.isEmpty()) {
                continue;
            }
            
            try {
                if (input.startsWith(":")) {
                    handleCommand(input);
                } else {
                    ReplSession.EvalResult result = session.eval(input);
                    if (result.isDeclaration) {
                        System.out.println("Defined: " + result.type);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
    
    private void handleCommand(String input) throws Exception {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0];
        String arg = parts.length > 1 ? parts[1] : "";
        
        switch (command) {
            case ":help" -> showHelp();
            case ":type" -> {
                if (arg.isEmpty()) {
                    System.err.println("Usage: :type <expression>");
                } else {
                    Type type = session.inferType(arg);
                    System.out.println(type);
                }
            }
            case ":bytecode" -> {
                if (arg.isEmpty()) {
                    System.err.println("Usage: :bytecode <expression>");
                } else {
                    ReplSession.EvalResult result = session.eval(arg);
                    System.out.println(session.disassemble(result.bytecode));
                }
            }
            default -> System.err.println("Unknown command: " + command + ". Type :help for available commands.");
        }
    }
    
    private void showHelp() {
        System.out.println("Available commands:");
        System.out.println("  <expression>           Evaluate and print result");
        System.out.println("  :type <expression>     Show inferred type");
        System.out.println("  :bytecode <expression> Show JVM bytecode");
        System.out.println("  :help                  Show this help");
        System.out.println("  :quit, :exit           Exit REPL");
    }
    
    public static void main(String[] args) throws IOException {
        new Repl().run();
    }
}
