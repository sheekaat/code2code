package com.code2code.service.converter;

/**
 * Options for component generation.
 */
public record GenerationOptions(
    String targetFramework,
    String stylingLibrary,    // "mui", "styled-components", "css-modules"
    String language,          // "typescript", "javascript"
    boolean includeComments,
    boolean useHooks,
    String componentType      // "functional", "class"
) {
    public GenerationOptions {
        if (targetFramework == null) targetFramework = "react";
        if (stylingLibrary == null) stylingLibrary = "mui";
        if (language == null) language = "typescript";
        if (componentType == null) componentType = "functional";
    }
    
    /**
     * Default options for React + TypeScript + MUI.
     */
    public static GenerationOptions reactDefaults() {
        return new GenerationOptions("react", "mui", "typescript", true, true, "functional");
    }
}
