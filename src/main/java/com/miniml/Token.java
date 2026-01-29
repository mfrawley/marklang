package com.miniml;

public class Token {
    public enum Type {
        INT, FLOAT, STRING, IDENT, 
        LET, FN, IN, IF, THEN, ELSE, MATCH, WITH, PRINT, JAVA_CALL,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, NE, LT, GT, LE, GE,
        ARROW, LPAREN, RPAREN, SEMICOLON, PIPE,
        EOF
    }

    public final Type type;
    public final String value;
    public final int line;
    public final int column;

    public Token(Type type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return "Token{" + type + ", '" + value + "'}";
    }
}
