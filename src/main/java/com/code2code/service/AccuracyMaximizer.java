package com.code2code.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Phase 4: Accuracy Maximizers Service
 * Techniques to maximize conversion accuracy:
 * - Interface-first conversion
 * - Test migration as validators
 * - Incremental type mapping
 * - Diff reviews
 * - One-shot examples
 */
public interface AccuracyMaximizer {
    
    /**
     * Converts interfaces/contracts before implementations.
     */
    List<ConvertedInterface> convertInterfacesFirst(List<Path> interfaceFiles);
    
    /**
     * Migrates unit tests alongside code as correctness validators.
     */
    TestMigrationResult migrateTests(Path sourceTestFile, Path convertedCodeFile);
    
    /**
     * Builds incremental type mapping dictionary (.NET → Java, VB6 → React).
     */
    void buildTypeMappingDictionary(Map<String, String> newMappings);
    
    /**
     * Performs diff review - only reviews changes, not full files.
     */
    DiffReviewResult reviewDiff(String previousVersion, String currentVersion);
    
    /**
     * Locks in style from one example file for consistent output.
     */
    void lockInStyle(Path exampleFile, String styleDescription);
    
    record ConvertedInterface(
        String interfaceName,
        String packageName,
        String convertedInterface,
        List<String> methodSignatures,
        List<String> implementingClasses
    ) {}
    
    record TestMigrationResult(
        String originalTestFramework,  // "NUnit", "xUnit", "MSTest"
        String targetTestFramework,     // "JUnit", "JUnit 5"
        String convertedTest,
        List<String> testCases,
        double coverageEstimate,
        List<String> validationIssues
    ) {}
    
    record DiffReviewResult(
        int linesChanged,
        int additions,
        int deletions,
        List<String> concerns,
        boolean approved,
        String reviewNotes
    ) {}
    
    record StyleGuide(
        String targetStack,
        String namingConvention,
        String indentationStyle,
        String importOrganization,
        String annotationStyle,
        List<String> preferredPatterns
    ) {}
}
