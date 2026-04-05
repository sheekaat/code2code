package com.code2code.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Phase 2: Pattern Extraction Service
 * Extracts reusable conversion rules from representative files.
 * Token multiplier - establishes patterns once, applies consistently.
 */
public interface PatternExtractor {
    
    /**
     * Extracts patterns from 2-3 representative files per pattern type.
     */
    PatternLibrary extractPatterns(List<Path> representativeFiles);
    
    /**
     * Creates a conversion template from established patterns.
     */
    ConversionTemplate createTemplate(String patternType, List<ExtractedPattern> patterns);
    
    /**
     * Applies established patterns to new files (token-efficient).
     */
    String applyPattern(String patternType, String sourceCode, PatternLibrary library);
    
    record PatternLibrary(
        String sourceTechnology,
        String targetTechnology,
        Map<String, List<ExtractedPattern>> patternsByType,
        Map<String, ConversionTemplate> templates,
        TypeMappingDictionary typeMappings,
        int patternCount
    ) {}
    
    record ExtractedPattern(
        String patternType,  // "Controller", "Service", "Model", "DTO", "Repository", etc.
        String sourcePattern,  // e.g., "[HttpGet] public IActionResult"
        String targetPattern,  // e.g., "@GetMapping public ResponseEntity"
        List<String> examples,
        int confidenceScore,  // 0-100
        String explanation
    ) {}
    
    record ConversionTemplate(
        String patternType,
        List<String> requiredImports,
        List<String> annotations,
        String classStructure,
        String methodStructure,
        Map<String, String> fieldMappings
    ) {}
    
    record TypeMappingDictionary(
        Map<String, String> typeMappings,  // "System.String" -> "java.lang.String"
        Map<String, String> methodMappings,  // "ToList()" -> "Collectors.toList()"
        Map<String, String> frameworkMappings  // "EntityFramework" -> "JPA/Hibernate"
    ) {}
}
