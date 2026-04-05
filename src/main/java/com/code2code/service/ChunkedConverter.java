package com.code2code.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Phase 3: Chunked Conversion Service
 * Converts one module/feature per conversation turn.
 * Includes context anchoring to prevent drift.
 */
public interface ChunkedConverter {
    
    /**
     * Converts a single module (chunk) with context anchoring.
     */
    ConversionChunk convertModule(ModuleContext context, PatternExtractor.PatternLibrary library);
    
    /**
     * Creates a compact summary of converted output for context propagation.
     */
    ModuleSummary createSummary(ConversionChunk chunk);
    
    /**
     * Builds context anchor document for maintaining consistency.
     */
    ContextAnchor buildContextAnchor(String targetStack, List<String> namingConventions, 
                                     Map<String, String> establishedPatterns);
    
    /**
     * Enable/disable WinForms intermediate step for VB6 form conversions.
     * When enabled, VB6 forms are converted to C# WinForms first, then to React.
     * Default implementation does nothing (for backward compatibility).
     */
    default void setUseWinFormsIntermediate(boolean useIntermediate) {
        // Default implementation: do nothing
        // Implementations that support this feature should override
    }
    
    record ConversionChunk(
        String moduleName,
        String moduleType,
        List<ConvertedFile> files,
        List<String> exportedTypes,
        List<String> exposedApis,
        int tokensUsed,
        boolean successful
    ) {}
    
    record ConvertedFile(
        String originalPath,
        String convertedPath,
        String fileType,
        int originalLines,
        int convertedLines,
        List<String> imports,
        List<String> dependencies
    ) {}
    
    record ModuleContext(
        String moduleName,
        String moduleType,
        List<Path> sourceFiles,
        List<InterfaceContract> dependencyContracts,  // Interface contracts only, not full code
        ContextAnchor anchor,
        PatternExtractor.PatternLibrary patternLibrary
    ) {}
    
    record InterfaceContract(
        String typeName,
        String interfaceSignature,  // Method signatures only
        String packageName,
        List<String> dependencies
    ) {}
    
    record ModuleSummary(
        String moduleName,
        String status,  // "converted", "partial", "failed"
        List<String> exportedTypes,
        List<String> exposedApis,
        List<String> knownIssues
    ) {}
    
    record ContextAnchor(
        String targetStack,  // "Java Spring Boot 3.x / ReactJS 18"
        List<String> namingConventions,
        Map<String, String> establishedPatterns,  // "DTO structure", "error handling approach"
        List<String> completedModules,
        String lastUpdated
    ) {}
}
