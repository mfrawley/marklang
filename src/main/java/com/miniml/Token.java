package com.miniml;

public class Token {
    public enum Type {
        INT, FLOAT, STRING, IDENT, TRUE, FALSE,
        LET, FN, IN, IF, THEN, ELSE, MATCH, WITH, TYPE, OF, PRINT, JAVA_CALL, JAVA_INSTANCE_CALL, IMPORT, DOT,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, NE, LT, GT, LE, GE,
        AND, OR,
        ARROW, LPAREN, RPAREN, SEMICOLON, PIPE, COLON, ASSIGN,
        LBRACKET, RBRACKET, COMMA, CONS,
        TYPE_INT, TYPE_DOUBLE, TYPE_STRING, TYPE_BOOL,
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
