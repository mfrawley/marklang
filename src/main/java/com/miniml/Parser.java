package com.miniml;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Module parseModule() {
        List<String> imports = new ArrayList<>();
        while (match(Token.Type.IMPORT)) {
            imports.add(expect(Token.Type.IDENT).value);
        }
        
        List<Module.TopLevel> declarations = new ArrayList<>();
        while ((peek().type == Token.Type.FN && isTopLevelFn()) || 
               (peek().type == Token.Type.LET && isTopLevelLet()) ||
               peek().type == Token.Type.TYPE) {
            if (peek().type == Token.Type.FN) {
                declarations.add(parseTopLevelFn());
            } else if (peek().type == Token.Type.TYPE) {
                declarations.add(parseTypeDef());
            } else {
                declarations.add(parseTopLevelLet());
            }
        }
        
        Expr mainExpr = null;
        if (peek().type != Token.Type.EOF) {
            mainExpr = expr();
        }
        return new Module(imports, declarations, mainExpr);
    }
    
    public Expr parseExpr() {
        return expr();
    }
    
    public Expr parse() {
        return expr();
    }
    
    private boolean isTopLevelFn() {
        int saved = pos;
        advance();
        while (peek().type == Token.Type.IDENT || peek().type == Token.Type.LPAREN) {
            if (peek().type == Token.Type.LPAREN) {
                advance();
                while (peek().type != Token.Type.RPAREN && peek().type != Token.Type.EOF) {
                    advance();
                }
                if (peek().type == Token.Type.RPAREN) {
                    advance();
                }
            } else {
                advance();
            }
        }
        if (peek().type == Token.Type.COLON) {
            advance();
            advance();
        }
        if (peek().type != Token.Type.ASSIGN) {
            pos = saved;
            return false;
        }
        advance();
        boolean result = !containsInKeyword();
        pos = saved;
        return result;
    }
    
    private boolean containsInKeyword() {
        int depth = 0;
        while (pos < tokens.size()) {
            Token.Type t = peek().type;
            if (t == Token.Type.IN && depth == 0) return true;
            if (t == Token.Type.EOF) return false;
            if (t == Token.Type.FN || t == Token.Type.LET) depth++;
            if (t == Token.Type.IN) {
                depth--;
                if (depth < 0) return false;
            }
            advance();
        }
        return false;
    }
    
    private Module.TopLevel parseTopLevelFn() {
        expect(Token.Type.FN);
        String name = expect(Token.Type.IDENT).value;
        List<Module.Param> params = new ArrayList<>();
        while (peek().type == Token.Type.IDENT || peek().type == Token.Type.LPAREN) {
            params.add(parseParam());
        }
        Optional<Type> returnType = Optional.empty();
        if (peek().type == Token.Type.COLON) {
            advance();
            returnType = Optional.of(parseType());
        }
        expect(Token.Type.ASSIGN);
        Expr body = expr();
        expect(Token.Type.SEMICOLON);
        return new Module.TopLevel.FnDecl(name, params, returnType, body);
    }
    
    private boolean isTopLevelLet() {
        int saved = pos;
        advance();
        if (peek().type != Token.Type.IDENT) {
            pos = saved;
            return false;
        }
        advance();
        if (peek().type != Token.Type.ASSIGN) {
            pos = saved;
            return false;
        }
        advance();
        boolean result = !containsInKeyword();
        pos = saved;
        return result;
    }
    
    public Module.TopLevel.LetDecl parseTopLevelLet() {
        expect(Token.Type.LET);
        String name = expect(Token.Type.IDENT).value;
        expect(Token.Type.ASSIGN);
        Expr value = expr();
        expect(Token.Type.SEMICOLON);
        return new Module.TopLevel.LetDecl(name, value);
    }
    
    private Module.TopLevel.TypeDef parseTypeDef() {
        expect(Token.Type.TYPE);
        String typeName = expect(Token.Type.IDENT).value;
        
        List<String> typeParams = new ArrayList<>();
        if (match(Token.Type.LT)) {
            do {
                typeParams.add(expect(Token.Type.IDENT).value);
            } while (match(Token.Type.COMMA));
            expect(Token.Type.GT);
        }
        
        expect(Token.Type.ASSIGN);
        
        List<Module.Constructor> constructors = new ArrayList<>();
        if (match(Token.Type.PIPE)) {
            // Optional leading pipe
        }
        
        do {
            String constructorName = expect(Token.Type.IDENT).value;
            Optional<Type> paramType = Optional.empty();
            if (match(Token.Type.OF)) {
                paramType = Optional.of(parseType());
            }
            constructors.add(new Module.Constructor(constructorName, paramType));
        } while (match(Token.Type.PIPE));
        
        return new Module.TopLevel.TypeDef(typeName, typeParams, constructors);
    }
    
    private Module.Param parseParam() {
        if (match(Token.Type.LPAREN)) {
            String paramName = expect(Token.Type.IDENT).value;
            expect(Token.Type.COLON);
            Type typeAnnotation = parseType();
            expect(Token.Type.RPAREN);
            return new Module.Param(paramName, Optional.of(typeAnnotation));
        } else {
            String paramName = expect(Token.Type.IDENT).value;
            return new Module.Param(paramName, Optional.empty());
        }
    }
    
    private Type parseType() {
        Token.Type tokenType = peek().type;
        if (tokenType == Token.Type.IDENT) {
            String name = advance().value;
            return new Type.TVar(name);
        }
        advance();
        return switch (tokenType) {
            case TYPE_INT -> new Type.TInt();
            case TYPE_DOUBLE -> new Type.TDouble();
            case TYPE_STRING -> new Type.TString();
            case TYPE_BOOL -> new Type.TBool();
            default -> throw new RuntimeException("Expected type annotation");
        };
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
        if (match(Token.Type.JAVA_INSTANCE_CALL)) {
            return javaInstanceCallExpr();
        }
        if (match(Token.Type.MATCH)) {
            return matchExpr();
        }
        return orExpr();
    }

    private Expr orExpr() {
        Expr left = andExpr();
        while (match(Token.Type.OR)) {
            Expr right = andExpr();
            left = new Expr.BinOp(Expr.Op.OR, left, right);
        }
        return left;
    }

    private Expr andExpr() {
        Expr left = comparisonExpr();
        while (match(Token.Type.AND)) {
            Expr right = comparisonExpr();
            left = new Expr.BinOp(Expr.Op.AND, left, right);
        }
        return left;
    }

    private Expr letExpr() {
        String name = expect(Token.Type.IDENT).value;
        expect(Token.Type.ASSIGN);
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
        expect(Token.Type.ASSIGN);
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
               peek().type != Token.Type.FN &&
               peek().type != Token.Type.SEMICOLON &&
               peek().type != Token.Type.RPAREN) {
            args.add(primaryExpr());
        }
        
        return new Expr.JavaCall(className, methodName, args);
    }

    private Expr javaInstanceCallExpr() {
        Token classToken = expect(Token.Type.STRING);
        String className = classToken.value;
        
        Token methodToken = expect(Token.Type.STRING);
        String methodName = methodToken.value;
        
        Expr instance = primaryExpr();
        
        List<Expr> args = new ArrayList<>();
        while (peek().type != Token.Type.EOF && 
               peek().type != Token.Type.IN &&
               peek().type != Token.Type.THEN &&
               peek().type != Token.Type.ELSE &&
               peek().type != Token.Type.FN &&
               peek().type != Token.Type.SEMICOLON &&
               peek().type != Token.Type.RPAREN) {
            args.add(primaryExpr());
        }
        
        return new Expr.JavaInstanceCall(className, methodName, instance, args);
    }

    private Expr matchExpr() {
        Expr scrutinee = expr();
        expect(Token.Type.WITH);
        List<Expr.MatchCase> cases = new ArrayList<>();
        
        match(Token.Type.PIPE);
        
        do {
            Pattern pattern = parsePattern();
            expect(Token.Type.ARROW);
            Expr body = expr();
            cases.add(new Expr.MatchCase(pattern, body));
        } while (match(Token.Type.PIPE));
        
        return new Expr.Match(scrutinee, cases);
    }

    private Expr comparisonExpr() {
        Expr left = consExpr();
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
            Expr right = consExpr();
            left = new Expr.BinOp(op, left, right);
        }
        return left;
    }

    private Expr consExpr() {
        Expr left = addExpr();
        if (match(Token.Type.CONS)) {
            Expr right = consExpr();
            return new Expr.Cons(left, right);
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
        Expr left = unaryExpr();
        while (true) {
            Expr.Op op = switch (peek().type) {
                case STAR -> Expr.Op.MUL;
                case SLASH -> Expr.Op.DIV;
                case PERCENT -> Expr.Op.MOD;
                default -> null;
            };
            if (op == null) break;
            advance();
            Expr right = unaryExpr();
            left = new Expr.BinOp(op, left, right);
        }
        return left;
    }

    private Expr unaryExpr() {
        if (match(Token.Type.MINUS)) {
            Expr operand = unaryExpr();
            return new Expr.UnaryOp(Expr.UnOp.NEG, operand);
        }
        return appExpr();
    }

    private Expr appExpr() {
        Expr func = primaryExpr();
        
        while (true) {
            if (peek().type == Token.Type.SLASH && peekAhead(1).type == Token.Type.IDENT) {
                advance();
                String methodName = expect(Token.Type.IDENT).value;
                List<Expr> args = new ArrayList<>();
                while (peek().type == Token.Type.INT || 
                       peek().type == Token.Type.FLOAT ||
                       peek().type == Token.Type.STRING ||
                       peek().type == Token.Type.IDENT ||
                       peek().type == Token.Type.LPAREN ||
                       peek().type == Token.Type.LBRACKET) {
                    args.add(primaryExpr());
                }
                func = new Expr.JavaInstanceCall("java.lang.Object", methodName, func, args);
            } else if (canBeFunction(func) && 
                (peek().type == Token.Type.INT || 
                 peek().type == Token.Type.FLOAT ||
                 peek().type == Token.Type.STRING ||
                 peek().type == Token.Type.IDENT ||
                 peek().type == Token.Type.LPAREN)) {
                List<Expr> args = new ArrayList<>();
                while (peek().type == Token.Type.INT || 
                       peek().type == Token.Type.FLOAT ||
                       peek().type == Token.Type.STRING ||
                       peek().type == Token.Type.IDENT ||
                       peek().type == Token.Type.LPAREN) {
                    args.add(primaryExpr());
                }
                if (func instanceof Expr.Constructor(String name, java.util.Optional<Expr> existingArg) && args.size() == 1) {
                    func = new Expr.Constructor(name, java.util.Optional.of(args.get(0)));
                } else {
                    func = new Expr.App(func, args);
                }
            } else {
                break;
            }
        }
        
        return func;
    }
    
    private boolean canBeFunction(Expr e) {
        return e instanceof Expr.Var ||
               e instanceof Expr.QualifiedVar ||
               e instanceof Expr.Constructor ||
               e instanceof Expr.Lambda ||
               e instanceof Expr.App ||
               e instanceof Expr.JavaCall ||
               e instanceof Expr.JavaStaticField;
    }

    private Expr primaryExpr() {
        if (match(Token.Type.INT)) {
            return new Expr.IntLit(Integer.parseInt(previous().value));
        }
        if (match(Token.Type.FLOAT)) {
            return new Expr.FloatLit(Double.parseDouble(previous().value));
        }
        if (match(Token.Type.TRUE)) {
            return new Expr.BoolLit(true);
        }
        if (match(Token.Type.FALSE)) {
            return new Expr.BoolLit(false);
        }
        if (match(Token.Type.STRING)) {
            return parseString(previous().value);
        }
        if (match(Token.Type.IDENT)) {
            String name = previous().value;
            if (peek().type == Token.Type.SLASH && Character.isUpperCase(name.charAt(0))) {
                advance();
                String memberName = expect(Token.Type.IDENT).value;
                String fullClassName = "java.lang." + name;
                
                if (Character.isUpperCase(memberName.charAt(0))) {
                    return new Expr.JavaStaticField(fullClassName, memberName);
                } else {
                    List<Expr> args = new ArrayList<>();
                    while (peek().type == Token.Type.INT || 
                           peek().type == Token.Type.FLOAT ||
                           peek().type == Token.Type.STRING ||
                           peek().type == Token.Type.IDENT ||
                           peek().type == Token.Type.LPAREN ||
                           peek().type == Token.Type.LBRACKET) {
                        args.add(primaryExpr());
                    }
                    return new Expr.JavaCall(fullClassName, memberName, args);
                }
            }
            if (match(Token.Type.DOT)) {
                String memberName = expect(Token.Type.IDENT).value;
                return new Expr.QualifiedVar(name, memberName);
            }
            if (Character.isUpperCase(name.charAt(0))) {
                return new Expr.Constructor(name, java.util.Optional.empty());
            }
            return new Expr.Var(name);
        }
        if (match(Token.Type.LPAREN)) {
            if (peek().type == Token.Type.RPAREN) {
                advance();
                return new Expr.Unit();
            }
            Expr e = expr();
            expect(Token.Type.RPAREN);
            return e;
        }
        if (match(Token.Type.LBRACKET)) {
            if (match(Token.Type.RBRACKET)) {
                return new Expr.ListLit(new ArrayList<>());
            }
            List<Expr> elements = new ArrayList<>();
            elements.add(expr());
            while (match(Token.Type.COMMA)) {
                elements.add(expr());
            }
            expect(Token.Type.RBRACKET);
            return new Expr.ListLit(elements);
        }
        throw new RuntimeException("Unexpected token: " + peek());
    }

    private Pattern parsePattern() {
        return parseConsPattern();
    }

    private Pattern parseConsPattern() {
        Pattern left = parsePrimaryPattern();
        if (match(Token.Type.CONS)) {
            Pattern right = parseConsPattern();
            return new Pattern.Cons(left, right);
        }
        return left;
    }

    private Pattern parsePrimaryPattern() {
        if (match(Token.Type.INT)) {
            return new Pattern.IntLit(Integer.parseInt(previous().value));
        }
        if (match(Token.Type.STRING)) {
            return new Pattern.StringLit(previous().value);
        }
        if (match(Token.Type.TRUE)) {
            return new Pattern.BoolLit(true);
        }
        if (match(Token.Type.FALSE)) {
            return new Pattern.BoolLit(false);
        }
        if (match(Token.Type.IDENT)) {
            String name = previous().value;
            if (Character.isUpperCase(name.charAt(0))) {
                if (peek().type == Token.Type.IDENT || 
                    peek().type == Token.Type.INT ||
                    peek().type == Token.Type.STRING ||
                    peek().type == Token.Type.TRUE ||
                    peek().type == Token.Type.FALSE ||
                    peek().type == Token.Type.LPAREN ||
                    peek().type == Token.Type.LBRACKET) {
                    Pattern arg = parsePrimaryPattern();
                    return new Pattern.Constructor(name, java.util.Optional.of(arg));
                }
                return new Pattern.Constructor(name, java.util.Optional.empty());
            }
            return new Pattern.Var(name);
        }
        if (match(Token.Type.LBRACKET)) {
            if (match(Token.Type.RBRACKET)) {
                return new Pattern.Nil();
            }
            throw new RuntimeException("List literal patterns not yet supported in match");
        }
        if (match(Token.Type.LPAREN)) {
            Pattern p = parsePattern();
            expect(Token.Type.RPAREN);
            return p;
        }
        throw new RuntimeException("Unexpected pattern token: " + peek());
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
    
    private Token peekAhead(int n) {
        int idx = pos + n;
        if (idx < tokens.size()) {
            return tokens.get(idx);
        }
        return tokens.get(tokens.size() - 1);
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
            if (type == Token.Type.IDENT && isReservedKeyword(peek().type)) {
                throw new RuntimeException("Cannot use '" + peek().value + "' as identifier - it's a reserved keyword");
            }
            throw new RuntimeException("Expected " + type + " but got " + peek());
        }
        return advance();
    }
    
    private static final java.util.Set<Token.Type> RESERVED_KEYWORDS = java.util.EnumSet.of(
        Token.Type.LET,
        Token.Type.IN,
        Token.Type.IF,
        Token.Type.THEN,
        Token.Type.ELSE,
        Token.Type.FN,
        Token.Type.TYPE_INT,
        Token.Type.TYPE_DOUBLE,
        Token.Type.TYPE_STRING,
        Token.Type.TYPE_BOOL,
        Token.Type.IMPORT,
        Token.Type.PRINT
    );
    
    private boolean isReservedKeyword(Token.Type type) {
        return RESERVED_KEYWORDS.contains(type);
    }
}
