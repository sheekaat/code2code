package com.code2code.service;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent post-processor for converted Java code.
 * Handles multi-class files, package inference, and syntax validation.
 */
public class JavaCodePostProcessor {
    
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile(
        "public\\s+(class|interface|enum|record)\\s+(\\w+)",
        Pattern.MULTILINE
    );
    
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
        "package\\s+([a-zA-Z_][a-zA-Z0-9_.]*);",
        Pattern.MULTILINE
    );
    
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "import\\s+(static\\s+)?([a-zA-Z_][a-zA-Z0-9_.]*);",
        Pattern.MULTILINE
    );
    
    /**
     * Result of processing converted code.
     */
    public record ProcessedFile(
        String fileName,
        String content,
        String packageDeclaration,
        List<String> imports,
        List<String> publicTypes,
        List<String> warnings
    ) {}
    
    /**
     * Processes converted code - validates, fixes syntax, splits multi-class files.
     * Now supports both Java and React/TypeScript output.
     * 
     * @param convertedCode The raw converted code from the LLM
     * @param originalFileName The original source file name
     * @param targetDir The target directory for output
     * @param targetLanguage The target language (e.g., "java", "react", "react-java")
     * @return List of processed files ready to be written (empty if critical errors)
     */
    public List<ProcessedFile> processConvertedCode(String convertedCode, 
                                                       String originalFileName,
                                                       Path targetDir,
                                                       String targetLanguage) {
        // For React/TypeScript files, use different processing
        if (targetLanguage != null && 
            (targetLanguage.toLowerCase().contains("react") || 
             targetLanguage.toLowerCase().contains("typescript") ||
             targetLanguage.toLowerCase().contains("jsx"))) {
            return processReactCode(convertedCode, originalFileName, targetDir, targetLanguage);
        }
        
        // Default Java processing
        return processJavaCode(convertedCode, originalFileName, targetDir);
    }
    
    /**
     * Processes React/TypeScript code - minimal validation, proper file extensions.
     */
    private List<ProcessedFile> processReactCode(String convertedCode, 
                                                    String originalFileName,
                                                    Path targetDir,
                                                    String targetLanguage) {
        List<ProcessedFile> result = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Clean up artifacts
        convertedCode = cleanupArtifacts(convertedCode);
        
        // Fix common TypeScript/React syntax issues
        convertedCode = fixReactSyntaxIssues(convertedCode, warnings);
        
        // Determine proper extension based on target
        String ext = ".tsx";
        if (targetLanguage.toLowerCase().contains("jsx") && !targetLanguage.toLowerCase().contains("ts")) {
            ext = ".jsx";
        }
        
        // For dual-target (React + Spring Boot), forms become React components
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String fileName = baseName + ext;
        
        // Smart brace/parentheses fixing for React
        // DISABLED: smartFixReactBraces corrupts valid JSX by miscounting braces in sx={{}}
        // convertedCode = smartFixReactBraces(convertedCode, warnings);
        convertedCode = smartFixReactParentheses(convertedCode, warnings);
        
        // Validate and record any remaining issues
        List<String> reactIssues = validateReactSyntax(convertedCode);
        warnings.addAll(reactIssues);
        
        // Extract component name (look for function/component declaration)
        List<String> exports = extractReactExports(convertedCode);
        
        result.add(new ProcessedFile(
            fileName,
            convertedCode,
            null, // no package for React
            extractReactImports(convertedCode),
            exports,
            warnings
        ));
        
        return result;
    }
    
    /**
     * Fixes common React/TypeScript syntax issues.
     */
    private String fixReactSyntaxIssues(String code, List<String> warnings) {
        // DISABLED: This regex incorrectly converts opening tags to self-closing
        // code = code.replaceAll("<([A-Z][a-zA-Z0-9]*)\\s+([^>]*[^/])>", "<$1 $2/>");
        
        // Fix missing semicolons after interface declarations (but not for type properties)
        code = code.replaceAll("(interface\\s+\\w+\\s*\\{[^}]*\\})(?!;)", "$1;");
        
        // Fix arrow functions with missing braces in JSX
        code = code.replaceAll("(on[A-Z]\\w+=\\{)\\s*([a-zA-Z_]+)\\s*=>\\s*([^{};]+)\\}(?!\\})", "$1($2) => { $3 }\\}");
        
        return code;
    }
    
    /**
     * Smart brace fixing for React - understands JSX, objects, and blocks.
     */
    private String smartFixReactBraces(String code, List<String> warnings) {
        int depth = 0;
        int jsxDepth = 0;
        boolean inString = false;
        char stringChar = 0;
        
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            
            // Skip strings
            if (inString) {
                if (c == stringChar && (i == 0 || code.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            
            if (c == '"' || c == '\'' || c == '`') {
                inString = true;
                stringChar = c;
                continue;
            }
            
            // Track JSX depth (opening/closing tags)
            if (c == '<' && i + 1 < code.length()) {
                char next = code.charAt(i + 1);
                if (next == '/') {
                    jsxDepth--;
                } else if (Character.isUpperCase(next) || next == 'd' || next == 's' || next == 'p') {
                    jsxDepth++;
                }
            }
            if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '>') {
                jsxDepth--;
            }
            
            // Count braces only outside JSX
            if (jsxDepth == 0) {
                if (c == '{') depth++;
                if (c == '}') depth--;
            }
        }
        
        // Add missing closing braces
        if (depth > 0) {
            warnings.add("Auto-fixed: Added " + depth + " missing closing brace(s)");
            for (int i = 0; i < depth; i++) {
                code = code + "\n}";
            }
        }
        
        return code;
    }
    
    /**
     * Smart parentheses fixing for React - handles JSX expressions, function calls, and types.
     */
    private String smartFixReactParentheses(String code, List<String> warnings) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        boolean inTemplateLiteral = false;
        
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            
            // Handle template literals with ${} interpolation
            if (c == '`') {
                inTemplateLiteral = !inTemplateLiteral;
                continue;
            }
            if (inTemplateLiteral && c == '$' && i + 1 < code.length() && code.charAt(i + 1) == '{') {
                // This is interpolation - skip the ${ and track the inner braces
                i++;
                continue;
            }
            
            // Skip strings
            if (inString) {
                if (c == stringChar && (i == 0 || code.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            
            if (c == '"' || c == '\'' || c == '`') {
                inString = true;
                stringChar = c;
                continue;
            }
            
            // Count parentheses
            if (c == '(') depth++;
            if (c == ')') depth--;
        }
        
        // Add missing closing parentheses
        if (depth > 0) {
            warnings.add("Auto-fixed: Added " + depth + " missing closing parenthese(s)");
            for (int i = 0; i < depth; i++) {
                code = code + ")";
            }
        }
        
        return code;
    }
    
    /**
     * Validates React/TypeScript syntax - JSX-aware.
     */
    private List<String> validateReactSyntax(String code) {
        List<String> issues = new ArrayList<>();
        
        // Count braces (excluding JSX)
        int braceDepth = 0;
        int parenDepth = 0;
        int jsxDepth = 0;
        boolean inString = false;
        char stringChar = 0;
        
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            
            // Skip strings
            if (inString) {
                if (c == stringChar && (i == 0 || code.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                inString = true;
                stringChar = c;
                continue;
            }
            
            // Track JSX depth
            if (c == '<' && i + 1 < code.length()) {
                char next = code.charAt(i + 1);
                if (next == '/' || Character.isUpperCase(next) || 
                    next == 'd' || next == 's' || next == 'p' || next == 'D' || next == 'S') {
                    // Check if it's a valid tag start
                    if (next == '/') {
                        jsxDepth = Math.max(0, jsxDepth - 1);
                    } else {
                        jsxDepth++;
                    }
                }
            }
            
            // Count braces and parentheses only outside JSX
            if (jsxDepth == 0) {
                if (c == '{') braceDepth++;
                if (c == '}') braceDepth--;
                if (c == '(') parenDepth++;
                if (c == ')') parenDepth--;
            }
        }
        
        if (braceDepth != 0) {
            issues.add("Unbalanced braces: " + (braceDepth > 0 ? braceDepth + " unclosed" : Math.abs(braceDepth) + " extra") + " (outside JSX)");
        }
        if (parenDepth != 0) {
            issues.add("Unbalanced parentheses: " + (parenDepth > 0 ? parenDepth + " unclosed" : Math.abs(parenDepth) + " extra") + " (outside JSX)");
        }
        
        // Check for unclosed JSX tags
        if (jsxDepth > 0) {
            issues.add("Unclosed JSX tags: " + jsxDepth + " unclosed");
        }
        
        return issues;
    }
    
    /**
     * Original Java code processing logic.
     */
    private List<ProcessedFile> processJavaCode(String convertedCode, 
                                                 String originalFileName,
                                                 Path targetDir) {
        List<ProcessedFile> result = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Pre-process: clean up common conversion artifacts
        convertedCode = cleanupArtifacts(convertedCode);
        
        // Validate before processing
        List<String> validationIssues = validateSyntax(convertedCode);
        
        // Separate critical vs warning issues
        List<String> criticalIssues = new ArrayList<>();
        List<String> warningIssues = new ArrayList<>();
        
        for (String issue : validationIssues) {
            if (issue.contains("Unbalanced braces") || 
                issue.contains("Unbalanced parentheses") ||
                issue.contains("Multiple package declarations")) {
                criticalIssues.add(issue);
            } else {
                warningIssues.add(issue);
            }
        }
        
        // Attempt to fix critical issues
        if (!criticalIssues.isEmpty()) {
            System.out.println("    Critical syntax issues detected, attempting auto-fix...");
            convertedCode = attemptSyntaxFix(convertedCode, criticalIssues);
            
            // Re-validate after fix attempt
            validationIssues = validateSyntax(convertedCode);
            criticalIssues.clear();
            for (String issue : validationIssues) {
                if (issue.contains("Unbalanced braces") || 
                    issue.contains("Unbalanced parentheses")) {
                    criticalIssues.add(issue);
                }
            }
        }
        
        // If still has critical issues, return empty with error marker
        if (!criticalIssues.isEmpty()) {
            System.err.println("    CRITICAL: Could not fix syntax errors in " + originalFileName);
            for (String issue : criticalIssues) {
                System.err.println("      - " + issue);
            }
            // Return empty to signal conversion failure
            return Collections.emptyList();
        }
        
        warnings.addAll(warningIssues);
        
        // Extract package declaration
        String packageDeclaration = extractPackage(convertedCode);
        
        // Extract all imports
        List<String> imports = extractAllImports(convertedCode);
        
        // Find all public classes
        List<ClassInfo> publicClasses = findPublicClasses(convertedCode);
        
        if (publicClasses.isEmpty()) {
            warnings.add("No public classes found in converted code");
            // Return the code as-is
            result.add(new ProcessedFile(
                determineFileName(originalFileName, "Unknown"),
                convertedCode,
                packageDeclaration,
                imports,
                Collections.emptyList(),
                warnings
            ));
            return result;
        }
        
        // If there's only one public class, return as-is (with cleanup)
        if (publicClasses.size() == 1) {
            ClassInfo mainClass = publicClasses.get(0);
            String cleanedCode = cleanupCode(convertedCode, packageDeclaration, imports, mainClass);
            
            result.add(new ProcessedFile(
                mainClass.name + ".java",
                cleanedCode,
                packageDeclaration,
                imports,
                List.of(mainClass.name),
                warnings
            ));
            return result;
        }
        
        // Multiple public classes - need to split
        warnings.add("Detected " + publicClasses.size() + " public classes, splitting into separate files");
        
        // Determine the primary class (matches filename or first one)
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        ClassInfo primaryClass = publicClasses.stream()
            .filter(c -> c.name.equalsIgnoreCase(baseName))
            .findFirst()
            .orElse(publicClasses.get(0));
        
        // Extract the full code sections for each class
        Map<String, String> classCodeMap = extractClassCodeSections(convertedCode, publicClasses);
        
        // Create separate files for each public class
        for (ClassInfo classInfo : publicClasses) {
            String classCode = classCodeMap.get(classInfo.name);
            if (classCode == null) {
                warnings.add("Could not extract code for class: " + classInfo.name);
                continue;
            }
            
            // For non-primary classes, make them package-private if they're in the same file
            // or keep them public if they're split to separate files
            String finalCode = buildStandaloneClass(
                classCode, 
                packageDeclaration, 
                imports,
                classInfo,
                classInfo.equals(primaryClass)
            );
            
            result.add(new ProcessedFile(
                classInfo.name + ".java",
                finalCode,
                packageDeclaration,
                filterImportsForClass(imports, classCode),
                List.of(classInfo.name),
                warnings
            ));
        }
        
        return result;
    }
    
    /**
     * Validates that the Java code has valid syntax (basic checks).
     */
    public List<String> validateSyntax(String code) {
        List<String> issues = new ArrayList<>();
        
        // Check for balanced braces
        int openBraces = countOccurrences(code, '{');
        int closeBraces = countOccurrences(code, '}');
        if (openBraces != closeBraces) {
            issues.add("Unbalanced braces: " + openBraces + " open, " + closeBraces + " close");
        }
        
        // Check for balanced parentheses
        int openParens = countOccurrences(code, '(');
        int closeParens = countOccurrences(code, ')');
        if (openParens != closeParens) {
            issues.add("Unbalanced parentheses: " + openParens + " open, " + closeParens + " close");
        }
        
        // Check for common conversion artifacts
        if (code.contains("```")) {
            issues.add("Markdown code fences detected in output");
        }
        
        if (code.contains("pulic ") || code.contains("pubic ")) {
            issues.add("Typo in 'public' keyword detected");
        }
        
        // Check for multiple package declarations
        long packageCount = PACKAGE_PATTERN.matcher(code).results().count();
        if (packageCount > 1) {
            issues.add("Multiple package declarations found: " + packageCount);
        }
        
        // Validate that filename would match public class
        List<ClassInfo> publicClasses = findPublicClasses(code);
        if (publicClasses.size() > 1) {
            issues.add("Multiple public classes in single file: " + 
                      publicClasses.stream().map(c -> c.name).toList());
        }
        
        return issues;
    }
    
    /**
     * Infers package name from C# namespace.
     */
    public String inferPackageFromNamespace(String csNamespace, String defaultPackage) {
        if (csNamespace == null || csNamespace.isBlank()) {
            return defaultPackage;
        }
        
        // Convert C# namespace (Company.Product.Module) to Java package (com.company.product.module)
        String[] parts = csNamespace.split("\\.");
        StringBuilder packageBuilder = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) packageBuilder.append(".");
            
            String part = parts[i];
            // Convert to lowercase
            part = part.toLowerCase();
            
            // Handle common TLD conventions
            if (i == 0 && part.equals("com")) {
                packageBuilder.append("com");
            } else if (i == 0 && isCommonTLD(part)) {
                packageBuilder.append(part);
            } else {
                packageBuilder.append(sanitizePackagePart(part));
            }
        }
        
        return packageBuilder.toString();
    }
    
    private String extractPackage(String code) {
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        return matcher.find() ? matcher.group(0) : null;
    }
    
    private List<String> extractAllImports(String code) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(code);
        while (matcher.find()) {
            imports.add(matcher.group(0));
        }
        return imports;
    }
    
    private List<ClassInfo> findPublicClasses(String code) {
        List<ClassInfo> classes = new ArrayList<>();
        Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(code);
        
        while (matcher.find()) {
            String typeKind = matcher.group(1);
            String className = matcher.group(2);
            int position = matcher.start();
            classes.add(new ClassInfo(className, typeKind, position));
        }
        
        return classes;
    }
    
    private Map<String, String> extractClassCodeSections(String code, List<ClassInfo> classes) {
        Map<String, String> sections = new LinkedHashMap<>();
        
        // Sort by position
        List<ClassInfo> sorted = new ArrayList<>(classes);
        sorted.sort(Comparator.comparingInt(c -> c.position));
        
        for (int i = 0; i < sorted.size(); i++) {
            ClassInfo current = sorted.get(i);
            int startPos = current.position;
            int endPos;
            
            if (i < sorted.size() - 1) {
                // End at the next class declaration
                endPos = sorted.get(i + 1).position;
            } else {
                // Last class - go to end of code
                endPos = code.length();
            }
            
            // Find the actual end of this class by brace matching
            String classSection = code.substring(startPos, endPos);
            int braceIndex = classSection.indexOf('{');
            if (braceIndex >= 0) {
                int actualEnd = findMatchingBrace(code, startPos + braceIndex);
                if (actualEnd > 0 && actualEnd < endPos) {
                    endPos = actualEnd + 1;
                }
            }
            
            sections.put(current.name, code.substring(startPos, endPos).trim());
        }
        
        return sections;
    }
    
    private int findMatchingBrace(String code, int openBracePos) {
        int depth = 1;
        int pos = openBracePos + 1;
        
        while (pos < code.length() && depth > 0) {
            char c = code.charAt(pos);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            pos++;
        }
        
        return depth == 0 ? pos - 1 : -1;
    }
    
    private String buildStandaloneClass(String classCode, String packageDeclaration,
                                       List<String> imports, ClassInfo classInfo,
                                       boolean isPrimary) {
        StringBuilder sb = new StringBuilder();
        
        // Add package declaration
        if (packageDeclaration != null) {
            sb.append(packageDeclaration).append("\n\n");
        }
        
        // Add imports
        for (String imp : imports) {
            sb.append(imp).append("\n");
        }
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        
        // Add class code
        sb.append(classCode);
        
        return sb.toString();
    }
    
    private String cleanupCode(String code, String packageDeclaration, 
                               List<String> imports, ClassInfo mainClass) {
        StringBuilder sb = new StringBuilder();
        
        if (packageDeclaration != null) {
            sb.append(packageDeclaration).append("\n\n");
        }
        
        for (String imp : imports) {
            sb.append(imp).append("\n");
        }
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        
        // Remove markdown fences if present
        String cleaned = code.replaceAll("(?s)```java\\s*", "")
                            .replaceAll("(?s)```\\s*", "");
        
        // Remove duplicate package declarations
        cleaned = PACKAGE_PATTERN.matcher(cleaned).replaceFirst("");
        
        sb.append(cleaned.trim());
        
        return sb.toString();
    }
    
    private List<String> filterImportsForClass(List<String> allImports, String classCode) {
        // For now, return all imports - could be optimized to only include used ones
        return allImports;
    }
    
    private String determineFileName(String originalFileName, String className) {
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        if (!className.equals("Unknown")) {
            return className + ".java";
        }
        return baseName + ".java";
    }
    
    private int countOccurrences(String str, char c) {
        int count = 0;
        for (char ch : str.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }
    
    private boolean isCommonTLD(String part) {
        return List.of("com", "org", "net", "edu", "gov", "io", "co").contains(part);
    }
    
    private String sanitizePackagePart(String part) {
        // Remove invalid characters
        part = part.replaceAll("[^a-zA-Z0-9_]", "");
        // Ensure starts with letter
        if (!part.isEmpty() && Character.isDigit(part.charAt(0))) {
            part = "_" + part;
        }
        return part.toLowerCase();
    }
    
    /**
     * Cleans up common conversion artifacts before processing.
     */
    private String cleanupArtifacts(String code) {
        // Remove markdown code fences
        code = code.replaceAll("(?s)```java\\s*", "");
        code = code.replaceAll("(?s)```\\s*", "");
        
        // Fix common typos
        code = code.replace("pulic ", "public ");
        code = code.replace("pubic ", "public ");
        code = code.replace("statc ", "static ");
        code = code.replace("privte ", "private ");
        code = code.replace("clss ", "class ");
        code = code.replace("clsss ", "class ");
        
        return code;
    }
    
    /**
     * Attempts to fix critical syntax issues.
     */
    private String attemptSyntaxFix(String code, List<String> issues) {
        for (String issue : issues) {
            if (issue.contains("Unbalanced braces")) {
                code = fixUnbalancedBraces(code);
            } else if (issue.contains("Unbalanced parentheses")) {
                code = fixUnbalancedParentheses(code);
            } else if (issue.contains("Multiple package declarations")) {
                code = fixMultiplePackages(code);
            }
        }
        return code;
    }
    
    private String fixUnbalancedBraces(String code) {
        int openCount = countOccurrences(code, '{');
        int closeCount = countOccurrences(code, '}');
        
        // Add missing closing braces at end of class/method
        while (closeCount < openCount) {
            // Find a good place to add - before any trailing whitespace/comments
            int insertPos = code.length();
            // Try to find the last non-whitespace char
            for (int i = code.length() - 1; i >= 0; i--) {
                if (!Character.isWhitespace(code.charAt(i))) {
                    insertPos = i + 1;
                    break;
                }
            }
            code = code.substring(0, insertPos) + "\n}" + code.substring(insertPos);
            closeCount++;
        }
        
        // Remove extra closing braces from beginning
        while (closeCount > openCount && code.trim().startsWith("}")) {
            code = code.trim().substring(1);
            closeCount--;
        }
        
        return code;
    }
    
    private String fixUnbalancedParentheses(String code) {
        int openCount = countOccurrences(code, '(');
        int closeCount = countOccurrences(code, ')');
        
        // Add missing closing parentheses at the end
        while (closeCount < openCount) {
            code = code + ")";
            closeCount++;
        }
        
        return code;
    }
    
    private String fixMultiplePackages(String code) {
        // Keep only the first package declaration
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        if (matcher.find()) {
            String firstPackage = matcher.group(0);
            // Remove all package declarations
            code = PACKAGE_PATTERN.matcher(code).replaceAll("");
            // Add back the first one at the beginning
            code = firstPackage + "\n\n" + code.trim();
        }
        return code;
    }
    
    /**
     * Extracts React component/function exports from code.
     */
    private List<String> extractReactExports(String code) {
        List<String> exports = new ArrayList<>();
        // Match export function/component declarations
        Pattern exportPattern = Pattern.compile(
            "export\\s+(default\\s+)?(?:function|const|class)\\s+(\\w+)",
            Pattern.MULTILINE
        );
        Matcher matcher = exportPattern.matcher(code);
        while (matcher.find()) {
            exports.add(matcher.group(2));
        }
        return exports;
    }
    
    /**
     * Extracts TypeScript/React imports from code.
     */
    private List<String> extractReactImports(String code) {
        List<String> imports = new ArrayList<>();
        // Match ES6 import statements
        Pattern importPattern = Pattern.compile(
            "import\\s+.*?\\s+from\\s+['\"][^'\"]+['\"];?",
            Pattern.MULTILINE
        );
        Matcher matcher = importPattern.matcher(code);
        while (matcher.find()) {
            imports.add(matcher.group(0));
        }
        return imports;
    }
    
    private record ClassInfo(String name, String typeKind, int position) {}
}
