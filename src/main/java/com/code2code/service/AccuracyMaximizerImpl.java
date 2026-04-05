package com.code2code.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 4 Implementation: Accuracy Maximizers
 * Interface-first, test migration, type mapping, diff reviews, style locking.
 */
public class AccuracyMaximizerImpl implements AccuracyMaximizer {
    
    private final GeminiApiClient geminiClient;
    private StyleGuide lockedStyle;
    private Map<String, String> typeMappingDict;
    
    public AccuracyMaximizerImpl(GeminiApiClient geminiClient) {
        this.geminiClient = geminiClient;
        this.typeMappingDict = new HashMap<>();
    }
    
    @Override
    public List<ConvertedInterface> convertInterfacesFirst(List<Path> interfaceFiles) {
        System.out.println("    Converting interfaces first...");
        List<ConvertedInterface> converted = new ArrayList<>();
        
        for (Path file : interfaceFiles) {
            try {
                String sourceCode = Files.readString(file);
                String fileName = file.getFileName().toString();
                String interfaceName = fileName.substring(0, fileName.lastIndexOf('.'));
                
                // Extract interface signature only
                String interfaceSignature = extractInterfaceSignature(sourceCode);
                
                // Convert interface
                String prompt = buildInterfaceConversionPrompt(interfaceName, interfaceSignature);
                String convertedInterface = geminiClient.generateText(prompt);
                
                // Find implementing classes (for reference, not conversion)
                List<String> implementingClasses = findImplementingClasses(file.getParent(), interfaceName);
                
                converted.add(new ConvertedInterface(
                    interfaceName,
                    extractPackageName(convertedInterface),
                    convertedInterface,
                    extractMethodSignatures(convertedInterface),
                    implementingClasses
                ));
                
            } catch (IOException e) {
                System.err.println("      Error converting interface: " + file + " - " + e.getMessage());
            }
        }
        
        return converted;
    }
    
    @Override
    public TestMigrationResult migrateTests(Path sourceTestFile, Path convertedCodeFile) {
        System.out.println("    Migrating tests...");
        
        try {
            String testCode = Files.readString(sourceTestFile);
            String convertedCode = Files.readString(convertedCodeFile);
            
            // Detect original test framework
            String originalFramework = detectTestFramework(testCode);
            
            // Migrate tests
            String prompt = buildTestMigrationPrompt(testCode, convertedCode, originalFramework);
            String convertedTest = geminiClient.generateText(prompt);
            
            // Extract test cases
            List<String> testCases = extractTestCases(convertedTest);
            
            // Estimate coverage
            double coverageEstimate = estimateCoverage(testCases.size(), convertedCode);
            
            // Validate tests
            List<String> validationIssues = validateTests(convertedTest, convertedCode);
            
            return new TestMigrationResult(
                originalFramework,
                "JUnit 5",
                convertedTest,
                testCases,
                coverageEstimate,
                validationIssues
            );
            
        } catch (IOException e) {
            System.err.println("      Error migrating tests: " + e.getMessage());
            return new TestMigrationResult("Unknown", "JUnit 5", "", 
                List.of(), 0.0, List.of("Failed to read files"));
        }
    }
    
    @Override
    public void buildTypeMappingDictionary(Map<String, String> newMappings) {
        typeMappingDict.putAll(newMappings);
        System.out.println("    Type dictionary updated: " + newMappings.size() + " new mappings");
    }
    
    @Override
    public DiffReviewResult reviewDiff(String previousVersion, String currentVersion) {
        // Generate diff
        int linesChanged = countLineChanges(previousVersion, currentVersion);
        int additions = countAdditions(previousVersion, currentVersion);
        int deletions = countDeletions(previousVersion, currentVersion);
        
        // Review changes
        List<String> concerns = new ArrayList<>();
        boolean approved = true;
        
        // Check for potential issues
        if (deletions > additions * 2) {
            concerns.add("Significant code removal detected");
        }
        if (linesChanged > 50) {
            concerns.add("Large change set - consider breaking into smaller chunks");
        }
        
        // Use Gemini to review if changes are significant
        if (linesChanged > 20) {
            String prompt = buildDiffReviewPrompt(previousVersion, currentVersion);
            String review = geminiClient.generateText(prompt);
            
            if (review.contains("CONCERN") || review.contains("WARNING")) {
                approved = false;
                concerns.addAll(extractConcerns(review));
            }
        }
        
        return new DiffReviewResult(
            linesChanged,
            additions,
            deletions,
            concerns,
            approved,
            approved ? "Changes look good" : "Please review concerns"
        );
    }
    
    @Override
    public void lockInStyle(Path exampleFile, String styleDescription) {
        System.out.println("    Locking in style from: " + exampleFile.getFileName());
        
        try {
            String exampleCode = Files.readString(exampleFile);
            
            lockedStyle = new StyleGuide(
                "Java Spring Boot",
                extractNamingConvention(exampleCode),
                extractIndentation(exampleCode),
                extractImportOrganization(exampleCode),
                extractAnnotationStyle(exampleCode),
                extractPreferredPatterns(exampleCode)
            );
            
        } catch (IOException e) {
            System.err.println("      Error reading style example: " + e.getMessage());
        }
    }
    
    public StyleGuide getLockedStyle() {
        return lockedStyle;
    }
    
    public Map<String, String> getTypeMappingDictionary() {
        return Collections.unmodifiableMap(typeMappingDict);
    }
    
    // Helper methods
    
    private String extractInterfaceSignature(String code) {
        // Extract interface declaration and method signatures
        StringBuilder signature = new StringBuilder();
        String[] lines = code.split("\n");
        boolean inInterface = false;
        
        for (String line : lines) {
            if (line.contains("interface ")) {
                inInterface = true;
            }
            if (inInterface) {
                signature.append(line).append("\n");
                if (line.contains("}")) {
                    break;
                }
            }
        }
        
        return signature.toString();
    }
    
    private String buildInterfaceConversionPrompt(String interfaceName, String interfaceSignature) {
        return """
            Convert this interface to Java.
            
            Interface: %s
            
            Source signature:
            ```
            %s
            ```
            
            Return the complete Java interface with:
            - Proper package declaration
            - Necessary imports
            - Javadoc comments
            - Method signatures only (no implementations)
            """.formatted(interfaceName, interfaceSignature);
    }
    
    private List<String> findImplementingClasses(Path dir, String interfaceName) {
        // Search for classes that implement this interface
        List<String> implementing = new ArrayList<>();
        // TODO: Implement search for implementing classes
        return implementing;
    }
    
    private String extractPackageName(String code) {
        // Extract package declaration
        String[] lines = code.split("\n");
        for (String line : lines) {
            if (line.startsWith("package ")) {
                return line.replace("package ", "").replace(";", "").trim();
            }
        }
        return "";
    }
    
    private List<String> extractMethodSignatures(String code) {
        List<String> signatures = new ArrayList<>();
        String[] lines = code.split("\n");
        
        for (String line : lines) {
            if (line.contains("(") && line.contains(")") && 
                (line.contains("public ") || line.contains("private ") || 
                 line.contains("protected ") || line.contains("void ") ||
                 line.contains("String ") || line.contains("int "))) {
                signatures.add(line.trim());
            }
        }
        
        return signatures;
    }
    
    private String detectTestFramework(String testCode) {
        if (testCode.contains("[Test]") || testCode.contains("Microsoft.VisualStudio.TestTools")) {
            return "MSTest";
        } else if (testCode.contains("[Fact]") || testCode.contains("xunit")) {
            return "xUnit";
        } else if (testCode.contains("[Test]") || testCode.contains("NUnit")) {
            return "NUnit";
        }
        return "Unknown";
    }
    
    private String buildTestMigrationPrompt(String testCode, String convertedCode, String originalFramework) {
        return """
            Migrate these %s tests to JUnit 5.
            
            Converted code under test:
            ```java
            %s
            ```
            
            Original tests:
            ```csharp
            %s
            ```
            
            Return complete JUnit 5 test class with:
            - @Test, @BeforeEach, @AfterEach annotations
            - Assertions.assertEquals, assertTrue, etc.
            - Mock setup if needed (use Mockito)
            - Proper test method naming following Java conventions
            """.formatted(originalFramework, convertedCode, testCode);
    }
    
    private List<String> extractTestCases(String testCode) {
        List<String> cases = new ArrayList<>();
        String[] lines = testCode.split("\n");
        
        for (String line : lines) {
            if (line.contains("@Test") || line.contains("void test")) {
                cases.add(line.trim());
            }
        }
        
        return cases;
    }
    
    private double estimateCoverage(int testCount, String code) {
        // Rough estimate based on method count
        int methodCount = (int) Arrays.stream(code.split("\n"))
            .filter(l -> l.contains("public") && l.contains("("))
            .count();
        
        return methodCount > 0 ? Math.min(100.0, (testCount / (double) methodCount) * 100) : 0.0;
    }
    
    private List<String> validateTests(String testCode, String codeUnderTest) {
        List<String> issues = new ArrayList<>();
        
        // Check for common issues
        if (!testCode.contains("@Test")) {
            issues.add("No @Test annotations found");
        }
        if (!testCode.contains("import org.junit")) {
            issues.add("JUnit imports missing");
        }
        
        return issues;
    }
    
    private int countLineChanges(String old, String new_) {
        return countAdditions(old, new_) + countDeletions(old, new_);
    }
    
    private int countAdditions(String old, String new_) {
        Set<String> oldLines = new HashSet<>(Arrays.asList(old.split("\n")));
        Set<String> newLines = new HashSet<>(Arrays.asList(new_.split("\n")));
        newLines.removeAll(oldLines);
        return newLines.size();
    }
    
    private int countDeletions(String old, String new_) {
        Set<String> oldLines = new HashSet<>(Arrays.asList(old.split("\n")));
        Set<String> newLines = new HashSet<>(Arrays.asList(new_.split("\n")));
        oldLines.removeAll(newLines);
        return oldLines.size();
    }
    
    private String buildDiffReviewPrompt(String old, String new_) {
        return """
            Review these code changes for:
            - Correctness
            - Style consistency
            - Potential bugs
            - Breaking changes
            
            Previous:
            ```
            %s
            ```
            
            Current:
            ```
            %s
            ```
            
            Return review as:
            APPROVED - if changes look good
            CONCERN: [description] - for any issues found
            """.formatted(old, new_);
    }
    
    private List<String> extractConcerns(String review) {
        return Arrays.stream(review.split("\n"))
            .filter(l -> l.startsWith("CONCERN:"))
            .collect(Collectors.toList());
    }
    
    private String extractNamingConvention(String code) {
        // Analyze code for naming patterns
        if (code.contains("class ") && code.contains("Controller")) {
            return "PascalCase for classes, camelCase for methods";
        }
        return "Standard Java naming conventions";
    }
    
    private String extractIndentation(String code) {
        // Detect indentation style
        if (code.contains("    ")) {
            return "4 spaces";
        } else if (code.contains("\t")) {
            return "Tabs";
        }
        return "4 spaces (default)";
    }
    
    private String extractImportOrganization(String code) {
        return "java.* first, then third-party, then project imports";
    }
    
    private String extractAnnotationStyle(String code) {
        if (code.contains("@RestController") || code.contains("@GetMapping")) {
            return "Spring Boot annotations above methods";
        }
        return "Standard annotation placement";
    }
    
    private List<String> extractPreferredPatterns(String code) {
        List<String> patterns = new ArrayList<>();
        
        if (code.contains("ResponseEntity")) {
            patterns.add("ResponseEntity for HTTP responses");
        }
        if (code.contains("Optional")) {
            patterns.add("Optional for nullable returns");
        }
        if (code.contains("Stream")) {
            patterns.add("Java Streams for collections");
        }
        
        return patterns;
    }
}
