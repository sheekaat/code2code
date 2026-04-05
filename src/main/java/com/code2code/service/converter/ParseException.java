package com.code2code.service.converter;

/**
 * Exception thrown when form parsing fails.
 */
public class ParseException extends Exception {
    private final String fileName;
    private final int lineNumber;
    
    public ParseException(String message, String fileName, int lineNumber) {
        super(String.format("Parse error in %s at line %d: %s", fileName, lineNumber, message));
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }
    
    public ParseException(String message, String fileName, Throwable cause) {
        super(String.format("Parse error in %s: %s", fileName, message), cause);
        this.fileName = fileName;
        this.lineNumber = -1;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
}
