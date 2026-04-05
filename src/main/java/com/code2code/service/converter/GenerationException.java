package com.code2code.service.converter;

/**
 * Exception thrown when component generation fails.
 */
public class GenerationException extends Exception {
    private final String formName;
    private final String targetFramework;
    
    public GenerationException(String message, String formName, String targetFramework) {
        super(String.format("Generation error for %s -> %s: %s", formName, targetFramework, message));
        this.formName = formName;
        this.targetFramework = targetFramework;
    }
    
    public GenerationException(String message, String formName, String targetFramework, Throwable cause) {
        super(String.format("Generation error for %s -> %s: %s", formName, targetFramework, message), cause);
        this.formName = formName;
        this.targetFramework = targetFramework;
    }
    
    public String getFormName() {
        return formName;
    }
    
    public String getTargetFramework() {
        return targetFramework;
    }
}
