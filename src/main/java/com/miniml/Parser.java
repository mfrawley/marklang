package com.miniml;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Expr parse() {
        return expr();
    }

    private Expr expr() {
        if (match(Token.Type.LET)) {
            return letExpr();
        }
        if (match(Token.Type.FN)) {
            return fnExpr();
        }
        if (match(Token.Type.IF)) {
            return ifExpr();
        }
        if (match(Token.Type.PRINT)) {
            return printExpr();
        }
        if (match(Token.Type.JAVA_CALL)) {
            return javaCallExpr();
        }
        return comparisonExpr();
    }

    private Expr letExpr() {
        String name = expect(Token.Type.IDENT).value;
        expect(Token.Type.EQ);
        Expr value = expr();
        expect(Token.Type.IN);
        Expr body = expr();
        return new Expr.Let(name, value, body);
    }

    private Expr fnExpr() {
        String name = expect(Token.Type.IDENT).value;
        List<String> params = new ArrayList<>();
        while (peek().type == Token.Type.IDENT) {
            params.add(advance().value);
        }
        expect(Token.Type.EQ);
        Expr value = expr();
        expect(Token.Type.IN);
        Expr body = expr();
        return new Expr.LetRec(name, params, value, body);
    }

    private Expr ifExpr() {
        Expr cond = expr();
        expect(Token.Type.THEN);
        Expr thenBranch = expr();
        expect(Token.Type.ELSE);
        Expr elseBranch = expr();
        return new Expr.If(cond, thenBranch, elseBranch);
    }

    private Expr printExpr() {
        Expr value = expr();
        return new Expr.Print(value);
    }

    private Expr javaCallExpr() {
        Token classToken = expect(Token.Type.STRING);
        String className = classToken.value;
        
        Token methodToken = expect(Token.Type.STRING);
        String methodName = methodToken.value;
        
        List<Expr> args = new ArrayList<>();
        while (peek().type != Token.Type.EOF && 
               peek().type != Token.Type.IN &&
               peek().type != Token.Type.THEN &&
               peek().type != Token.Type.ELSE &&
               peek().type != Token.Type.RPAREN) {
            args.add(primaryExpr());
        }
        
        return new Expr.JavaCall(className, methodName, args);
    }

    private Expr comparisonExpr() {
        Expr left = addExpr();
        while (true) {
            Token.Type type = peek().type;
            Expr.Op op = switch (type) {
                case EQ -> Expr.Op.EQ;
                case NE -> Expr.Op.NE;
                case LT -> Expr.Op.LT;
                case GT -> Expr.Op.GT;
                case LE -> Expr.Op.LE;
                case GE -> Expr.Op.GE;
                default -> null;
            };
            if (op == null) break;
            advance();
            Expr right = addExpr();
            left = new Expr.BinOp(op, left, right);
        }
        return left;
    }

    private Expr addExpr() {
        Expr left = mulExpr();
        while (true) {
            Expr.Op op = switch (peek().type) {
                case PLUS -> Expr.Op.ADD;
                case MINUS -> Expr.Op.SUB;
                default -> null;
            };
            if (op == null) break;
            advance();
            Expr right = mulExpr();
            left = new Expr.BinOp(op, left, right);
        }
        return left;
    }

    private Expr mulExpr() {
        Expr left = appExpr();
        while (true) {
            Expr.Op op = switch (peek().type) {
                case STAR -> Expr.Op.MUL;
                case SLASH -> Expr.Op.DIV;
                case PERCENT -> Expr.Op.MOD;
                default -> null;
            };
            if (op == null) break;
            advance();
            Expr right = appExpr();
            left = new Expr.BinOp(op, left, right);
        }
        return left;
    }

    private Expr appExpr() {
        Expr func = primaryExpr();
        List<Expr> args = new ArrayList<>();
        while (peek().type == Token.Type.INT || 
               peek().type == Token.Type.FLOAT ||
               peek().type == Token.Type.STRING ||
               peek().type == Token.Type.IDENT ||
               peek().type == Token.Type.LPAREN) {
            args.add(primaryExpr());
        }
        if (args.isEmpty()) {
            return func;
        }
        return new Expr.App(func, args);
    }

    private Expr primaryExpr() {
        if (match(Token.Type.INT)) {
            return new Expr.IntLit(Integer.parseInt(previous().value));
        }
        if (match(Token.Type.FLOAT)) {
            return new Expr.FloatLit(Double.parseDouble(previous().value));
        }
        if (match(Token.Type.STRING)) {
            return parseString(previous().value);
        }
        if (match(Token.Type.IDENT)) {
            return new Expr.Var(previous().value);
        }
        if (match(Token.Type.LPAREN)) {
            Expr e = expr();
            expect(Token.Type.RPAREN);
            return e;
        }
        throw new RuntimeException("Unexpected token: " + peek());
    }

    private Expr parseString(String str) {
        List<Object> parts = new ArrayList<>();
        int i = 0;
        StringBuilder sb = new StringBuilder();

        while (i < str.length()) {
            if (str.charAt(i) == '{' && i + 1 < str.length() && str.charAt(i + 1) != '{') {
                if (sb.length() > 0) {
                    parts.add(sb.toString());
                    sb = new StringBuilder();
                }
                i++;
                int start = i;
                while (i < str.length() && str.charAt(i) != '}') {
                    i++;
                }
                String varName = str.substring(start, i);
                parts.add(new Expr.Var(varName));
                i++;
            } else {
                sb.append(str.charAt(i));
                i++;
            }
        }

        if (sb.length() > 0) {
            parts.add(sb.toString());
        }

        if (parts.isEmpty()) {
            return new Expr.StringLit("");
        }
        if (parts.size() == 1 && parts.get(0) instanceof String) {
            return new Expr.StringLit((String) parts.get(0));
        }
        return new Expr.StringInterp(parts);
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }

    private Token advance() {
        if (pos < tokens.size()) pos++;
        return previous();
    }

    private boolean match(Token.Type... types) {
        for (Token.Type type : types) {
            if (peek().type == type) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token expect(Token.Type type) {
        if (peek().type != type) {
            throw new RuntimeException("Expected " + type + " but got " + peek());
        }
        return advance();
    }
}
