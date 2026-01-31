package com.miniml;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String source;
    private final String filename;
    private int pos = 0;
    private int line = 1;
    private int column = 1;

    public Lexer(String source) {
        this(source, "<input>");
    }

    public Lexer(String source, String filename) {
        this.source = source;
        this.filename = filename;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) break;

            tokens.add(nextToken());
        }
        tokens.add(new Token(Token.Type.EOF, "", line, column));
        return tokens;
    }

    private void skipWhitespace() {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '#') {
                while (pos < source.length() && source.charAt(pos) != '\n') {
                    pos++;
                    column++;
                }
                continue;
            }
            if (c == '\n') {
                line++;
                column = 1;
                pos++;
            } else if (Character.isWhitespace(c)) {
                pos++;
                column++;
            } else {
                break;
            }
        }
    }

    private Token nextToken() {
        int startLine = line;
        int startColumn = column;
        char c = source.charAt(pos);

        if (Character.isDigit(c)) {
            return number(startLine, startColumn);
        }

        if (Character.isLetter(c) || c == '_') {
            return identOrKeyword(startLine, startColumn);
        }

        if (c == '"') {
            return string(startLine, startColumn);
        }

        pos++;
        column++;

        switch (c) {
            case '+': return new Token(Token.Type.PLUS, "+", startLine, startColumn);
            case '*': return new Token(Token.Type.STAR, "*", startLine, startColumn);
            case '/': return new Token(Token.Type.SLASH, "/", startLine, startColumn);
            case '%': return new Token(Token.Type.PERCENT, "%", startLine, startColumn);
            case '(': return new Token(Token.Type.LPAREN, "(", startLine, startColumn);
            case ')': return new Token(Token.Type.RPAREN, ")", startLine, startColumn);
            case ';': return new Token(Token.Type.SEMICOLON, ";", startLine, startColumn);
            case '|':
                if (pos < source.length() && source.charAt(pos) == '|') {
                    pos++;
                    column++;
                    return new Token(Token.Type.OR, "||", startLine, startColumn);
                }
                return new Token(Token.Type.PIPE, "|", startLine, startColumn);
            case '&':
                if (pos < source.length() && source.charAt(pos) == '&') {
                    pos++;
                    column++;
                    return new Token(Token.Type.AND, "&&", startLine, startColumn);
                }
                throw new LexerException("Unexpected character '&'", filename, startLine, startColumn);
            case '.': return new Token(Token.Type.DOT, ".", startLine, startColumn);
            case ',': return new Token(Token.Type.COMMA, ",", startLine, startColumn);
            case '[': return new Token(Token.Type.LBRACKET, "[", startLine, startColumn);
            case ']': return new Token(Token.Type.RBRACKET, "]", startLine, startColumn);
            case '!':
                if (pos < source.length() && source.charAt(pos) == '=') {
                    pos++;
                    column++;
                    return new Token(Token.Type.NE, "!=", startLine, startColumn);
                }
                throw new LexerException("Unexpected character '!'", filename, startLine, startColumn);
            case ':':
                if (pos < source.length() && source.charAt(pos) == ':') {
                    pos++;
                    column++;
                    return new Token(Token.Type.CONS, "::", startLine, startColumn);
                }
                return new Token(Token.Type.COLON, ":", startLine, startColumn);
            case '-':
                if (pos < source.length() && source.charAt(pos) == '>') {
                    pos++;
                    column++;
                    return new Token(Token.Type.ARROW, "->", startLine, startColumn);
                }
                return new Token(Token.Type.MINUS, "-", startLine, startColumn);
            case '=':
                if (pos < source.length() && source.charAt(pos) == '=') {
                    pos++;
                    column++;
                    return new Token(Token.Type.EQ, "==", startLine, startColumn);
                }
                return new Token(Token.Type.ASSIGN, "=", startLine, startColumn);
            case '<':
                if (pos < source.length() && source.charAt(pos) == '>') {
                    pos++;
                    column++;
                    return new Token(Token.Type.NE, "<>", startLine, startColumn);
                }
                if (pos < source.length() && source.charAt(pos) == '=') {
                    pos++;
                    column++;
                    return new Token(Token.Type.LE, "<=", startLine, startColumn);
                }
                return new Token(Token.Type.LT, "<", startLine, startColumn);
            case '>':
                if (pos < source.length() && source.charAt(pos) == '=') {
                    pos++;
                    column++;
                    return new Token(Token.Type.GE, ">=", startLine, startColumn);
                }
                return new Token(Token.Type.GT, ">", startLine, startColumn);
            default:
                throw new LexerException("Unexpected character '" + c + "'", filename, startLine, startColumn);
        }
    }

    private Token number(int startLine, int startColumn) {
        int start = pos;
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            pos++;
            column++;
        }

        if (pos < source.length() && source.charAt(pos) == '.') {
            pos++;
            column++;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
                column++;
            }
            return new Token(Token.Type.FLOAT, source.substring(start, pos), startLine, startColumn);
        }

        return new Token(Token.Type.INT, source.substring(start, pos), startLine, startColumn);
    }

    private Token identOrKeyword(int startLine, int startColumn) {
        int start = pos;
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            pos++;
            column++;
        }

        String text = source.substring(start, pos);
        Token.Type type = switch (text) {
            case "let" -> Token.Type.LET;
            case "fn" -> Token.Type.FN;
            case "in" -> Token.Type.IN;
            case "if" -> Token.Type.IF;
            case "then" -> Token.Type.THEN;
            case "else" -> Token.Type.ELSE;
            case "match" -> Token.Type.MATCH;
            case "with" -> Token.Type.WITH;
            case "true" -> Token.Type.TRUE;
            case "false" -> Token.Type.FALSE;
            case "Ok" -> Token.Type.OK;
            case "Error" -> Token.Type.ERROR;
            case "print" -> Token.Type.PRINT;
            case "java_call" -> Token.Type.JAVA_CALL;
            case "java_instance_call" -> Token.Type.JAVA_INSTANCE_CALL;
            case "import" -> Token.Type.IMPORT;
            case "int" -> Token.Type.TYPE_INT;
            case "double" -> Token.Type.TYPE_DOUBLE;
            case "string" -> Token.Type.TYPE_STRING;
            case "bool" -> Token.Type.TYPE_BOOL;
            default -> Token.Type.IDENT;
        };

        return new Token(type, text, startLine, startColumn);
    }

    private Token string(int startLine, int startColumn) {
        pos++;
        column++;
        int start = pos;
        StringBuilder sb = new StringBuilder();

        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '"') {
                pos++;
                column++;
                return new Token(Token.Type.STRING, sb.toString(), startLine, startColumn);
            }
            if (c == '\\' && pos + 1 < source.length()) {
                pos++;
                column++;
                char next = source.charAt(pos);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '{' -> sb.append('{');
                    default -> {
                        sb.append('\\');
                        sb.append(next);
                    }
                }
                pos++;
                column++;
            } else {
                sb.append(c);
                pos++;
                column++;
            }
        }
        throw new RuntimeException("Unterminated string");
    }
}
