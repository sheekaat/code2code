package com.code2code.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1: Codebase Analysis Service
 * Analyzes project structure, dependencies, and patterns without loading full codebase.
 * Produces a migration map with prioritized conversion order.
 */
public interface CodebaseAnalyzer {
    
    /**
     * Analyzes project structure and returns a migration plan.
     * Minimal token usage - only sends file tree and key metadata.
     */
    MigrationPlan analyzeProjectStructure(Path projectDir);
    
    /**
     * Identifies natural module boundaries (auth, data layer, UI, services, etc.)
     */
    List<ModuleBoundary> identifyModuleBoundaries(Path projectDir);
    
    /**
     * Analyzes dependencies between modules for correct conversion ordering.
     */
    Map<String, Set<String>> analyzeDependencies(Path projectDir);
    
    record MigrationPlan(
        String projectName,
        String sourceTechnology,  // "VB6", ".NET Framework", ".NET Core", etc.
        String targetTechnology,  // "Java Spring Boot", "ReactJS", etc.
        List<ModuleBoundary> modules,
        Map<String, Set<String>> dependencyGraph,
        List<ConversionPhase> phases,
        int estimatedTokens
    ) {}
    
    record ModuleBoundary(
        String name,
        String type,  // "UI", "Service", "Data", "Auth", "Utils", etc.
        List<String> filePatterns,
        Set<String> dependencies,
        Priority priority,
        int estimatedFiles
    ) {}
    
    enum Priority {
        CRITICAL, HIGH, MEDIUM, LOW
    }
    
    record ConversionPhase(
        int phaseNumber,
        String name,
        List<String> moduleNames,
        String approach,  // "Pattern Extraction", "Chunked Conversion", "Validation"
        int estimatedTokens
    ) {}
}
