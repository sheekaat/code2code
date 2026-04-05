package com.code2code.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Simple accuracy scoring for converted code.
 * Hooks into existing validation to produce 0-100% scores.
 */
public class AccuracyScorer {
    
    private final JavaCodePostProcessor postProcessor;
    private final ValidationLoop validationLoop;
    
    public AccuracyScorer(JavaCodePostProcessor postProcessor, ValidationLoop validationLoop) {
        this.postProcessor = postProcessor;
        this.validationLoop = validationLoop;
    }
    
    /**
     * Calculates accuracy score for a single file.
     * Returns 0-100 based on syntax, structure, and validation.
     */
    public FileAccuracyScore scoreFile(String fileName, String code, String targetLanguage) {
        double syntaxScore = calculateSyntaxScore(code);
        double structureScore = calculateStructureScore(code);
        double importScore = calculateImportScore(code);
        double compilationScore = 0.0; // Will be set by external compilation check
        
        // Weighted average
        double overall = (syntaxScore * 0.4) + (structureScore * 0.3) + (importScore * 0.3);
        
        return new FileAccuracyScore(
            fileName,
            overall,
            syntaxScore,
            structureScore,
            importScore,
            compilationScore,
            targetLanguage
        );
    }
    
    /**
     * Updates score with compilation result.
     */
    public FileAccuracyScore withCompilationResult(FileAccuracyScore score, boolean compiledSuccessfully) {
        double compilationScore = compiledSuccessfully ? 1.0 : 0.0;
        // Recalculate with compilation weighted heavily
        double overall = (score.syntaxScore() * 0.25) + 
                        (score.structureScore() * 0.20) + 
                        (score.importScore() * 0.15) +
                        (compilationScore * 0.40);
        
        return new FileAccuracyScore(
            score.fileName(),
            overall,
            score.syntaxScore(),
            score.structureScore(),
            score.importScore(),
            compilationScore,
            score.targetLanguage()
        );
    }
    
    private double calculateSyntaxScore(String code) {
        List<String> syntaxIssues = postProcessor.validateSyntax(code);
        
        if (syntaxIssues.isEmpty()) {
            return 1.0;
        }
        
        // Deduct for each issue
        double penalty = 0.0;
        for (String issue : syntaxIssues) {
            if (issue.contains("Unbalanced braces") || issue.contains("Unbalanced parentheses")) {
                penalty += 0.3; // Critical
            } else if (issue.contains("Multiple public classes") || issue.contains("Multiple package")) {
                penalty += 0.25; // Major
            } else if (issue.contains("Markdown") || issue.contains("Typo")) {
                penalty += 0.1; // Minor
            } else {
                penalty += 0.15; // General
            }
        }
        
        return Math.max(0.0, 1.0 - penalty);
    }
    
    private double calculateStructureScore(String code) {
        double score = 0.0;
        
        // Check for package declaration
        if (code.contains("package ")) {
            score += 0.25;
        }
        
        // Check for class declaration
        if (code.contains("public class") || code.contains("public interface")) {
            score += 0.25;
        }
        
        // Check for proper class structure (opening brace)
        if (code.contains("{")) {
            score += 0.25;
        }
        
        // Check for imports
        if (code.contains("import ")) {
            score += 0.15;
        }
        
        // Check for closing brace
        if (code.contains("}")) {
            score += 0.10;
        }
        
        return score;
    }
    
    private double calculateImportScore(String code) {
        // Use GeminiApiClient's cleanup to detect duplicates and unused
        String cleaned = GeminiApiClient.cleanUpImports(code);
        
        int originalImportCount = countImports(code);
        int cleanedImportCount = countImports(cleaned);
        
        if (originalImportCount == 0) {
            return 1.0; // No imports to check
        }
        
        // Score based on how many imports were valid
        double validRatio = (double) cleanedImportCount / originalImportCount;
        
        // Check for duplicates
        boolean hasDuplicates = hasDuplicateImports(code);
        
        if (validRatio == 1.0 && !hasDuplicates) {
            return 1.0;
        } else if (hasDuplicates) {
            return Math.max(0.0, validRatio - 0.2);
        } else {
            return validRatio;
        }
    }
    
    private int countImports(String code) {
        int count = 0;
        for (String line : code.split("\n")) {
            if (line.trim().startsWith("import ")) {
                count++;
            }
        }
        return count;
    }
    
    private boolean hasDuplicateImports(String code) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String line : code.split("\n")) {
            if (line.trim().startsWith("import ")) {
                String normalized = line.trim().toLowerCase().replaceAll("\\s+", " ");
                if (seen.contains(normalized)) {
                    return true;
                }
                seen.add(normalized);
            }
        }
        return false;
    }
    
    /**
     * Aggregates scores for a module.
     */
    public ModuleAccuracyScore aggregateModuleScores(String moduleName, List<FileAccuracyScore> fileScores) {
        if (fileScores.isEmpty()) {
            return new ModuleAccuracyScore(moduleName, 0.0, 0, List.of());
        }
        
        double totalScore = 0.0;
        int compiledCount = 0;
        
        for (FileAccuracyScore score : fileScores) {
            totalScore += score.overall();
            if (score.compilationScore() > 0.5) {
                compiledCount++;
            }
        }
        
        double averageScore = totalScore / fileScores.size();
        
        return new ModuleAccuracyScore(
            moduleName,
            averageScore,
            compiledCount,
            fileScores
        );
    }
    
    // Record classes for scores
    public record FileAccuracyScore(
        String fileName,
        double overall,
        double syntaxScore,
        double structureScore,
        double importScore,
        double compilationScore,
        String targetLanguage
    ) {
        @Override
        public String toString() {
            return String.format("%s: %.0f%% (syntax: %.0f%%, structure: %.0f%%, imports: %.0f%%)",
                fileName, overall * 100, syntaxScore * 100, structureScore * 100, importScore * 100);
        }
    }
    
    public record ModuleAccuracyScore(
        String moduleName,
        double overall,
        int filesCompiled,
        List<FileAccuracyScore> fileScores
    ) {
        @Override
        public String toString() {
            return String.format("Module %s: %.0f%% (%d/%d files compiled)",
                moduleName, overall * 100, filesCompiled, fileScores.size());
        }
    }
}
