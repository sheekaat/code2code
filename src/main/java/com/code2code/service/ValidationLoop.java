package com.code2code.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Phase 5: Validation Loop Service
 * Post-conversion validation with:
 * - Verification checklists
 * - Surgical patch fixes (not full rewrites)
 * - Correction token minimization
 */
public interface ValidationLoop {
    
    /**
     * Produces verification checklist for converted module.
     */
    VerificationChecklist generateChecklist(String moduleName, String moduleType);
    
    /**
     * Runs validation and produces report.
     */
    ValidationReport validateModule(String moduleName, Path convertedCodeDir);
    
    /**
     * Applies surgical patches to fix specific issues (minimal tokens).
     */
    SurgicalFix applySurgicalFix(String filePath, String issue, String suggestedFix);
    
    /**
     * Tracks correction history to minimize repeated fixes.
     */
    CorrectionHistory trackCorrections(String moduleName, List<SurgicalFix> fixes);
    
    record VerificationChecklist(
        String moduleName,
        String moduleType,
        List<ChecklistItem> items,
        int criticalItems,
        int totalItems
    ) {}
    
    record ChecklistItem(
        String id,
        String category,  // "Null Handling", "Async Patterns", "Auth Middleware", "Type Safety"
        String description,
        Priority priority,
        boolean automated  // Can be checked automatically
    ) {}
    
    enum Priority {
        CRITICAL, HIGH, MEDIUM, LOW
    }
    
    record ValidationReport(
        String moduleName,
        boolean passed,
        int checksPassed,
        int checksFailed,
        List<ValidationIssue> issues,
        List<String> recommendations
    ) {}
    
    record ValidationIssue(
        String checklistItemId,
        String filePath,
        String issueDescription,
        Severity severity,
        String suggestedFix
    ) {}
    
    enum Severity {
        BLOCKING, CRITICAL, WARNING, INFO
    }
    
    record SurgicalFix(
        String filePath,
        int lineNumber,
        String originalCode,
        String fixedCode,
        String fixReason,
        int tokensUsed,
        boolean applied
    ) {}
    
    record CorrectionHistory(
        String moduleName,
        int totalFixes,
        Map<String, Integer> fixesByCategory,
        List<String> commonIssues,
        String lastUpdated
    ) {}
}
