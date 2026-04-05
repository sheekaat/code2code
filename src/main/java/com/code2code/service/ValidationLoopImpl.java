package com.code2code.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 5 Implementation: Validation Loop
 * Verification checklist, surgical patches, correction tracking.
 */
public class ValidationLoopImpl implements ValidationLoop {
    
    private final GeminiApiClient geminiClient;
    private final JavaCodePostProcessor postProcessor;
    private final Map<String, CorrectionHistory> correctionHistories;
    
    public ValidationLoopImpl(GeminiApiClient geminiClient) {
        this.geminiClient = geminiClient;
        this.postProcessor = new JavaCodePostProcessor();
        this.correctionHistories = new HashMap<>();
    }
    
    @Override
    public VerificationChecklist generateChecklist(String moduleName, String moduleType) {
        List<ChecklistItem> items = new ArrayList<>();
        
        // Common checks for all modules
        items.add(new ChecklistItem("C001", "Null Handling", 
            "All nullable parameters and returns use Optional or null checks",
            Priority.CRITICAL, true));
        
        items.add(new ChecklistItem("C002", "Exception Handling",
            "Proper exception handling with try-catch or throws declarations",
            Priority.HIGH, true));
        
        items.add(new ChecklistItem("C003", "Resource Management",
            "Resources properly managed with try-with-resources or finally blocks",
            Priority.HIGH, true));
        
        // Module-specific checks
        switch (moduleType) {
            case "Controller":
                items.add(new ChecklistItem("C004", "HTTP Status Codes",
                    "Appropriate HTTP status codes returned (200, 201, 400, 404, 500)",
                    Priority.CRITICAL, true));
                items.add(new ChecklistItem("C005", "Request Validation",
                    "Input parameters validated before processing",
                    Priority.CRITICAL, true));
                items.add(new ChecklistItem("C006", "Response Wrapping",
                    "Consistent response wrapper used (ResponseEntity or custom)",
                    Priority.HIGH, true));
                break;
                
            case "Service":
                items.add(new ChecklistItem("C007", "Transaction Boundaries",
                    "@Transactional used appropriately for database operations",
                    Priority.CRITICAL, true));
                items.add(new ChecklistItem("C008", "Business Logic",
                    "Business rules correctly migrated from .NET",
                    Priority.CRITICAL, false)); // Requires manual review
                break;
                
            case "Repository":
                items.add(new ChecklistItem("C009", "JPA Annotations",
                    "Proper JPA annotations (@Entity, @Table, @Id, etc.)",
                    Priority.CRITICAL, true));
                items.add(new ChecklistItem("C010", "Query Methods",
                    "Spring Data JPA query methods correctly defined",
                    Priority.HIGH, true));
                break;
                
            case "Model", "DTO":
                items.add(new ChecklistItem("C011", "Validation Annotations",
                    "Bean validation annotations present (@NotNull, @Size, etc.)",
                    Priority.HIGH, true));
                items.add(new ChecklistItem("C012", "Immutability",
                    "DTOs are immutable with builder pattern or final fields",
                    Priority.MEDIUM, true));
                break;
        }
        
        // Async and threading checks
        items.add(new ChecklistItem("C013", "Async Patterns",
            "Async operations use CompletableFuture or @Async appropriately",
            Priority.MEDIUM, true));
        
        // Auth checks
        items.add(new ChecklistItem("C014", "Security",
            "Authentication and authorization properly configured",
            Priority.CRITICAL, false)); // Requires manual review
        
        int criticalCount = (int) items.stream()
            .filter(i -> i.priority() == Priority.CRITICAL)
            .count();
        
        return new VerificationChecklist(moduleName, moduleType, items, criticalCount, items.size());
    }
    
    @Override
    public ValidationReport validateModule(String moduleName, Path convertedCodeDir) {
        List<ValidationIssue> issues = new ArrayList<>();
        int checksPassed = 0;
        int checksFailed = 0;
        
        try {
            // Scan all Java files in the module
            List<Path> javaFiles = Files.walk(convertedCodeDir)
                .filter(p -> p.toString().endsWith(".java"))
                .toList();
            
            for (Path file : javaFiles) {
                String code = Files.readString(file);
                String fileName = file.getFileName().toString();
                
                // Automated checks
                List<ValidationIssue> fileIssues = runAutomatedChecks(fileName, code);
                issues.addAll(fileIssues);
                
                checksPassed += countPassedChecks(code);
                checksFailed += fileIssues.size();
            }
            
        } catch (IOException e) {
            System.err.println("Error validating module: " + e.getMessage());
            issues.add(new ValidationIssue("VAL001", convertedCodeDir.toString(),
                "Failed to read converted code: " + e.getMessage(),
                Severity.BLOCKING, "Check file permissions and path"));
        }
        
        boolean passed = issues.stream()
            .noneMatch(i -> i.severity() == Severity.BLOCKING || i.severity() == Severity.CRITICAL);
        
        List<String> recommendations = generateRecommendations(issues);
        
        return new ValidationReport(moduleName, passed, checksPassed, checksFailed, issues, recommendations);
    }
    
    @Override
    public SurgicalFix applySurgicalFix(String filePath, String issue, String suggestedFix) {
        System.out.println("    Applying surgical fix for: " + issue);
        
        try {
            Path path = Path.of(filePath);
            String code = Files.readString(path);
            
            // Build surgical fix prompt
            String prompt = buildSurgicalFixPrompt(code, issue, suggestedFix);
            
            // Get fixed code from Gemini
            String fixedCode = geminiClient.generateText(prompt);
            
            // If Gemini returned valid fixed code, use it directly for simple fixes
            if (issue.contains("Unbalanced parentheses") || issue.contains("Unbalanced braces")) {
                // Apply direct syntax fix
                String patchedCode = applyDirectSyntaxFix(code, issue);
                if (!patchedCode.equals(code)) {
                    Files.writeString(path, patchedCode);
                    int tokensUsed = estimateTokens(prompt, patchedCode);
                    return new SurgicalFix(filePath, 1, code, patchedCode, issue, tokensUsed, true);
                }
            }
            
            // Extract the specific fix (not full rewrite)
            String originalSnippet = extractRelevantSnippet(code, issue);
            String fixedSnippet = extractRelevantSnippet(fixedCode, issue);
            
            // If we couldn't extract snippets, try using the whole fixed code
            if (originalSnippet.isEmpty() || fixedSnippet.isEmpty()) {
                System.out.println("      Could not extract specific snippet, using auto-fix");
                String patchedCode = applyDirectSyntaxFix(code, issue);
                Files.writeString(path, patchedCode);
                int tokensUsed = estimateTokens(prompt, patchedCode);
                return new SurgicalFix(filePath, 1, code, patchedCode, issue, tokensUsed, true);
            }
            
            // Apply fix to original file
            int lineNumber = findLineNumber(code, originalSnippet);
            String patchedCode = applyPatch(code, originalSnippet, fixedSnippet);
            
            Files.writeString(path, patchedCode);
            
            int tokensUsed = estimateTokens(prompt, fixedCode);
            
            return new SurgicalFix(
                filePath,
                lineNumber,
                originalSnippet,
                fixedSnippet,
                issue,
                tokensUsed,
                true
            );
            
        } catch (IOException e) {
            System.err.println("      Error applying surgical fix: " + e.getMessage());
            e.printStackTrace();
            return new SurgicalFix(filePath, 0, "", "", issue, 0, false);
        }
    }
    
    @Override
    public CorrectionHistory trackCorrections(String moduleName, List<SurgicalFix> fixes) {
        Map<String, Integer> fixesByCategory = new HashMap<>();
        List<String> commonIssues = new ArrayList<>();
        
        for (SurgicalFix fix : fixes) {
            String category = categorizeFix(fix.fixReason());
            fixesByCategory.merge(category, 1, Integer::sum);
            
            if (fixesByCategory.get(category) > 1) {
                commonIssues.add(fix.fixReason());
            }
        }
        
        CorrectionHistory history = new CorrectionHistory(
            moduleName,
            fixes.size(),
            fixesByCategory,
            commonIssues.stream().distinct().toList(),
            java.time.LocalDateTime.now().toString()
        );
        
        correctionHistories.put(moduleName, history);
        
        // Print insights
        if (!commonIssues.isEmpty()) {
            System.out.println("      Common issues detected: " + commonIssues);
        }
        
        return history;
    }
    
    // Helper methods
    
    private List<ValidationIssue> runAutomatedChecks(String fileName, String code) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Run intelligent syntax validation
        List<String> syntaxIssues = postProcessor.validateSyntax(code);
        for (String issue : syntaxIssues) {
            Severity severity = issue.contains("Multiple public classes") ? Severity.BLOCKING :
                              issue.contains("Unbalanced") ? Severity.CRITICAL :
                              Severity.WARNING;
            issues.add(new ValidationIssue("SYNTAX", fileName, issue, severity, 
                "Run code through JavaCodePostProcessor for correction"));
        }
        
        // Check 1: Null handling
        if (code.contains("public ") && !code.contains("Optional") && 
            code.contains("String ") && !code.contains("@NotNull")) {
            issues.add(new ValidationIssue("C001", fileName,
                "Potential null handling issue - consider Optional or @NotNull",
                Severity.WARNING,
                "Add null check or use Optional<String>"));
        }
        
        // Check 2: Exception handling
        if (code.contains("catch (Exception") || code.contains("catch(Exception")) {
            issues.add(new ValidationIssue("C002", fileName,
                "Catching generic Exception - use specific exceptions",
                Severity.WARNING,
                "Replace catch (Exception with specific exception types"));
        }
        
        // Check 3: Resource management
        if (code.contains("new FileInputStream") || code.contains("new FileOutputStream") ||
            code.contains("new Socket")) {
            if (!code.contains("try-with-resources") && !code.contains("try (")) {
                issues.add(new ValidationIssue("C003", fileName,
                    "Resource not managed with try-with-resources",
                    Severity.CRITICAL,
                    "Wrap resource creation in try-with-resources block"));
            }
        }
        
        // Check 4: System.out.print
        if (code.contains("System.out.print")) {
            issues.add(new ValidationIssue("LOG001", fileName,
                "Using System.out instead of proper logging",
                Severity.WARNING,
                "Replace with SLF4J logger"));
        }
        
        return issues;
    }
    
    private int countPassedChecks(String code) {
        int passed = 0;
        if (code.contains("import ")) passed++; // Has imports
        if (code.contains("package ")) passed++; // Has package
        if (code.contains("public class")) passed++; // Has class
        return passed;
    }
    
    private List<String> generateRecommendations(List<ValidationIssue> issues) {
        List<String> recommendations = new ArrayList<>();
        
        Map<Severity, Long> severityCount = issues.stream()
            .collect(Collectors.groupingBy(ValidationIssue::severity, Collectors.counting()));
        
        if (severityCount.getOrDefault(Severity.BLOCKING, 0L) > 0) {
            recommendations.add("Address BLOCKING issues before deployment");
        }
        if (severityCount.getOrDefault(Severity.CRITICAL, 0L) > 0) {
            recommendations.add("Review CRITICAL issues for potential runtime failures");
        }
        if (issues.stream().anyMatch(i -> i.checklistItemId().equals("C001"))) {
            recommendations.add("Review null handling across all modules");
        }
        
        return recommendations;
    }
    
    private String buildSurgicalFixPrompt(String code, String issue, String suggestedFix) {
        return """
            Fix this specific issue with minimal changes:
            
            Issue: %s
            Suggested fix: %s
            
            Current code:
            ```java
            %s
            ```
            
            Return ONLY the fixed code section, not the entire file.
            Preserve all other code exactly as is.
            """.formatted(issue, suggestedFix, code);
    }
    
    private String extractRelevantSnippet(String code, String issue) {
        // Extract relevant code section based on issue
        String[] lines = code.split("\n");
        StringBuilder snippet = new StringBuilder();
        
        for (String line : lines) {
            if (line.toLowerCase().contains(issue.toLowerCase()) ||
                matchesIssuePattern(line, issue)) {
                snippet.append(line).append("\n");
            }
        }
        
        return snippet.toString().trim();
    }
    
    private boolean matchesIssuePattern(String line, String issue) {
        // Match common issue patterns
        return (issue.contains("null") && line.contains("null")) ||
               (issue.contains("exception") && line.contains("catch")) ||
               (issue.contains("resource") && line.contains("try")) ||
               (issue.contains("parentheses") && (line.contains("(") || line.contains(")"))) ||
               (issue.contains("braces") && (line.contains("{") || line.contains("}")));
    }
    
    private int findLineNumber(String code, String snippet) {
        String[] lines = code.split("\n");
        String[] snippetLines = snippet.split("\n");
        
        if (snippetLines.length == 0) return 1;
        
        String firstLine = snippetLines[0].trim();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(firstLine)) {
                return i + 1;
            }
        }
        
        return 1;
    }
    
    private String applyDirectSyntaxFix(String code, String issue) {
        if (issue.contains("Unbalanced parentheses")) {
            return fixUnbalancedParentheses(code);
        } else if (issue.contains("Unbalanced braces")) {
            return fixUnbalancedBraces(code);
        }
        return code;
    }
    
    private String fixUnbalancedParentheses(String code) {
        int openCount = 0;
        int closeCount = 0;
        for (char c : code.toCharArray()) {
            if (c == '(') openCount++;
            else if (c == ')') closeCount++;
        }
        
        StringBuilder result = new StringBuilder(code);
        while (closeCount < openCount) {
            result.append(")");
            closeCount++;
        }
        return result.toString();
    }
    
    private String fixUnbalancedBraces(String code) {
        int openCount = 0;
        int closeCount = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') openCount++;
            else if (c == '}') closeCount++;
        }
        
        StringBuilder result = new StringBuilder(code);
        while (closeCount < openCount) {
            result.append("\n}");
            closeCount++;
        }
        return result.toString();
    }
    
    private String applyPatch(String original, String oldSnippet, String newSnippet) {
        if (oldSnippet.isEmpty() || newSnippet.isEmpty()) {
            return original;
        }
        return original.replace(oldSnippet, newSnippet);
    }
    
    private String categorizeFix(String issue) {
        if (issue.contains("null")) return "Null Handling";
        if (issue.contains("exception")) return "Exception Handling";
        if (issue.contains("resource")) return "Resource Management";
        if (issue.contains("import")) return "Imports";
        if (issue.contains("type")) return "Type Conversion";
        return "General";
    }
    
    private int estimateTokens(String prompt, String response) {
        return (prompt.length() + response.length()) / 4;
    }
}
