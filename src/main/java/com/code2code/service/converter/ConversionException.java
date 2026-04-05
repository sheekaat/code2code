package com.code2code.service.converter;

/**
 * Exception thrown when the conversion process fails.
 */
public class ConversionException extends Exception {
    private final String sourceFile;
    private final String targetFramework;
    
    public ConversionException(String message, String sourceFile, String targetFramework) {
        super(String.format("Conversion error (%s -> %s): %s", sourceFile, targetFramework, message));
        this.sourceFile = sourceFile;
        this.targetFramework = targetFramework;
    }
    
    public ConversionException(String message, String sourceFile, String targetFramework, Throwable cause) {
        super(String.format("Conversion error (%s -> %s): %s", sourceFile, targetFramework, message), cause);
        this.sourceFile = sourceFile;
        this.targetFramework = targetFramework;
    }
    
    public String getSourceFile() {
        return sourceFile;
    }
    
    public String getTargetFramework() {
        return targetFramework;
    }
}
