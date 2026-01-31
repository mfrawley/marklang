package com.miniml;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LexerTest {
    
    @Test
    void testIntegerLiteral() {
        Lexer lexer = new Lexer("42");
        List<Token> tokens = lexer.tokenize();
        assertEquals(2, tokens.size());
        assertEquals(Token.Type.INT, tokens.get(0).type);
        assertEquals("42", tokens.get(0).value);
        assertEquals(Token.Type.EOF, tokens.get(1).type);
    }
    
    @Test
    void testFloatLiteral() {
        Lexer lexer = new Lexer("3.14");
        List<Token> tokens = lexer.tokenize();
        assertEquals(2, tokens.size());
        assertEquals(Token.Type.FLOAT, tokens.get(0).type);
        assertEquals("3.14", tokens.get(0).value);
    }
    
    @Test
    void testStringLiteral() {
        Lexer lexer = new Lexer("\"hello\"");
        List<Token> tokens = lexer.tokenize();
        assertEquals(2, tokens.size());
        assertEquals(Token.Type.STRING, tokens.get(0).type);
        assertEquals("hello", tokens.get(0).value);
    }
    
    @Test
    void testIdentifier() {
        Lexer lexer = new Lexer("foo");
        List<Token> tokens = lexer.tokenize();
        assertEquals(2, tokens.size());
        assertEquals(Token.Type.IDENT, tokens.get(0).type);
        assertEquals("foo", tokens.get(0).value);
    }
    
    @Test
    void testKeywords() {
        Lexer lexer = new Lexer("let fn in if then else");
        List<Token> tokens = lexer.tokenize();
        assertEquals(7, tokens.size());
        assertEquals(Token.Type.LET, tokens.get(0).type);
        assertEquals(Token.Type.FN, tokens.get(1).type);
        assertEquals(Token.Type.IN, tokens.get(2).type);
        assertEquals(Token.Type.IF, tokens.get(3).type);
        assertEquals(Token.Type.THEN, tokens.get(4).type);
        assertEquals(Token.Type.ELSE, tokens.get(5).type);
    }
    
    @Test
    void testOperators() {
        Lexer lexer = new Lexer("+ - * / %");
        List<Token> tokens = lexer.tokenize();
        assertEquals(6, tokens.size());
        assertEquals(Token.Type.PLUS, tokens.get(0).type);
        assertEquals(Token.Type.MINUS, tokens.get(1).type);
        assertEquals(Token.Type.STAR, tokens.get(2).type);
        assertEquals(Token.Type.SLASH, tokens.get(3).type);
        assertEquals(Token.Type.PERCENT, tokens.get(4).type);
    }
    
    @Test
    void testComparisons() {
        Lexer lexer = new Lexer("== != < > <= >=");
        List<Token> tokens = lexer.tokenize();
        assertEquals(7, tokens.size());
        assertEquals(Token.Type.EQ, tokens.get(0).type);
        assertEquals(Token.Type.NE, tokens.get(1).type);
        assertEquals(Token.Type.LT, tokens.get(2).type);
        assertEquals(Token.Type.GT, tokens.get(3).type);
        assertEquals(Token.Type.LE, tokens.get(4).type);
        assertEquals(Token.Type.GE, tokens.get(5).type);
    }
    
    @Test
    void testDelimiters() {
        Lexer lexer = new Lexer("( ) [ ] , ; : =");
        List<Token> tokens = lexer.tokenize();
        assertEquals(9, tokens.size());
        assertEquals(Token.Type.LPAREN, tokens.get(0).type);
        assertEquals(Token.Type.RPAREN, tokens.get(1).type);
        assertEquals(Token.Type.LBRACKET, tokens.get(2).type);
        assertEquals(Token.Type.RBRACKET, tokens.get(3).type);
        assertEquals(Token.Type.COMMA, tokens.get(4).type);
        assertEquals(Token.Type.SEMICOLON, tokens.get(5).type);
        assertEquals(Token.Type.COLON, tokens.get(6).type);
        assertEquals(Token.Type.ASSIGN, tokens.get(7).type);
    }
    
    @Test
    void testSimpleExpression() {
        Lexer lexer = new Lexer("let x = 10 in x + 5");
        List<Token> tokens = lexer.tokenize();
        assertEquals(9, tokens.size());
        assertEquals(Token.Type.LET, tokens.get(0).type);
        assertEquals(Token.Type.IDENT, tokens.get(1).type);
        assertEquals("x", tokens.get(1).value);
        assertEquals(Token.Type.ASSIGN, tokens.get(2).type);
        assertEquals(Token.Type.INT, tokens.get(3).type);
        assertEquals("10", tokens.get(3).value);
        assertEquals(Token.Type.IN, tokens.get(4).type);
        assertEquals(Token.Type.IDENT, tokens.get(5).type);
        assertEquals("x", tokens.get(5).value);
        assertEquals(Token.Type.PLUS, tokens.get(6).type);
        assertEquals(Token.Type.INT, tokens.get(7).type);
        assertEquals("5", tokens.get(7).value);
    }
    
    @Test
    void testCommentsAreIgnored() {
        Lexer lexer = new Lexer("# this is a comment\n42");
        List<Token> tokens = lexer.tokenize();
        assertEquals(2, tokens.size());
        assertEquals(Token.Type.INT, tokens.get(0).type);
        assertEquals("42", tokens.get(0).value);
    }
    
    @Test
    void testListCons() {
        Lexer lexer = new Lexer("[1, 2, 3]");
        List<Token> tokens = lexer.tokenize();
        assertEquals(8, tokens.size());
        assertEquals(Token.Type.LBRACKET, tokens.get(0).type);
        assertEquals(Token.Type.INT, tokens.get(1).type);
        assertEquals(Token.Type.COMMA, tokens.get(2).type);
        assertEquals(Token.Type.INT, tokens.get(3).type);
        assertEquals(Token.Type.COMMA, tokens.get(4).type);
        assertEquals(Token.Type.INT, tokens.get(5).type);
        assertEquals(Token.Type.RBRACKET, tokens.get(6).type);
    }
}
