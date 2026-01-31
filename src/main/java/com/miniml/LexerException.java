package com.miniml;

public class LexerException extends RuntimeException {
    private final String filename;
    private final int line;
    private final int column;

    public LexerException(String message, String filename, int line, int column) {
        super(filename + ":" + line + ":" + column + ": " + message);
        this.filename = filename;
        this.line = line;
        this.column = column;
    }

    public String getFilename() {
        return filename;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
