package com.code2code.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 2 Implementation: Pattern Extraction
 * Extracts reusable conversion rules from representative files.
 * Token multiplier - establish patterns once, apply consistently.
 */
public class PatternExtractorImpl implements PatternExtractor {
    
    private final GeminiApiClient geminiClient;
    
    public PatternExtractorImpl(GeminiApiClient geminiClient) {
        this.geminiClient = geminiClient;
    }
    
    @Override
    public PatternLibrary extractPatterns(List<Path> representativeFiles) {
        System.out.println("  Extracting patterns from " + representativeFiles.size() + " files...");
        
        Map<String, List<ExtractedPattern>> patternsByType = new HashMap<>();
        Map<String, ConversionTemplate> templates = new HashMap<>();
        TypeMappingDictionary typeMappings = new TypeMappingDictionary(
            new HashMap<>(), new HashMap<>(), new HashMap<>()
        );
        
        // Group files by pattern type
        Map<String, List<Path>> filesByType = groupFilesByType(representativeFiles);
        
        for (Map.Entry<String, List<Path>> entry : filesByType.entrySet()) {
            String patternType = entry.getKey();
            List<Path> files = entry.getValue();
            
            System.out.println("    Extracting " + patternType + " patterns...");
            
            // Take 2-3 representative files for this pattern type
            List<Path> sampleFiles = files.stream().limit(3).toList();
            
            List<ExtractedPattern> patterns = extractPatternsForType(patternType, sampleFiles);
            patternsByType.put(patternType, patterns);
            
            // Create template from patterns
            ConversionTemplate template = createTemplate(patternType, patterns);
            templates.put(patternType, template);
            
            // Build type mappings
            buildTypeMappings(patternType, sampleFiles, typeMappings);
        }
        
        int patternCount = patternsByType.values().stream()
            .mapToInt(List::size).sum();
        
        return new PatternLibrary(
            "Source",
            "Target",
            patternsByType,
            templates,
            typeMappings,
            patternCount
        );
    }
    
    @Override
    public ConversionTemplate createTemplate(String patternType, List<ExtractedPattern> patterns) {
        // Create a template from extracted patterns
        List<String> requiredImports = new ArrayList<>();
        List<String> annotations = new ArrayList<>();
        Map<String, String> fieldMappings = new HashMap<>();
        
        // Aggregate patterns into template structure
        for (ExtractedPattern pattern : patterns) {
            // Extract imports from pattern
            // Extract annotations
            // Extract field mappings
        }
        
        String classStructure = generateClassStructure(patternType, patterns);
        String methodStructure = generateMethodStructure(patternType, patterns);
        
        return new ConversionTemplate(
            patternType,
            requiredImports,
            annotations,
            classStructure,
            methodStructure,
            fieldMappings
        );
    }
    
    @Override
    public String applyPattern(String patternType, String sourceCode, PatternLibrary library) {
        ConversionTemplate template = library.templates().get(patternType);
        if (template == null) {
            System.err.println("No template found for pattern type: " + patternType);
            return sourceCode;
        }
        
        // Apply template to source code
        // Use Gemini API with established patterns
        String prompt = buildPatternApplicationPrompt(patternType, sourceCode, template);
        
        try {
            return geminiClient.code2code(prompt, sourceCode);
        } catch (Exception e) {
            System.err.println("Error applying pattern: " + e.getMessage());
            return sourceCode;
        }
    }
    
    private Map<String, List<Path>> groupFilesByType(List<Path> files) {
        Map<String, List<Path>> grouped = new HashMap<>();
        
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String type = classifyFileType(fileName);
            
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(file);
        }
        
        return grouped;
    }
    
    private String classifyFileType(String fileName) {
        String lower = fileName.toLowerCase();
        
        if (lower.contains("controller")) return "Controller";
        if (lower.contains("service") || lower.contains("business")) return "Service";
        if (lower.contains("repository") || lower.contains("data")) return "Repository";
        if (lower.contains("model") || lower.contains("entity")) return "Model";
        if (lower.contains("dto")) return "DTO";
        if (lower.contains("view") || lower.contains("page")) return "View";
        if (lower.contains("test")) return "Test";
        if (lower.contains("config")) return "Config";
        
        return "General";
    }
    
    private List<ExtractedPattern> extractPatternsForType(String patternType, List<Path> sampleFiles) {
        List<ExtractedPattern> patterns = new ArrayList<>();
        
        // Read sample files
        List<String> fileContents = sampleFiles.stream()
            .map(this::readFileContent)
            .filter(Objects::nonNull)
            .toList();
        
        if (fileContents.isEmpty()) {
            return patterns;
        }
        
        // Use Gemini to extract patterns
        String prompt = buildPatternExtractionPrompt(patternType, fileContents);
        
        try {
            String response = geminiClient.generateText(prompt);
            System.out.println("    Pattern extraction response for " + patternType + ": " + response.substring(0, Math.min(200, response.length())));
            patterns = parseExtractedPatterns(response, patternType);
            System.out.println("    Extracted " + patterns.size() + " patterns for " + patternType);
            for (ExtractedPattern p : patterns) {
                System.out.println("      - " + p.sourcePattern() + " → " + p.targetPattern());
            }
        } catch (Exception e) {
            System.err.println("    Error extracting patterns for " + patternType + ": " + e.getMessage());
        }
        
        return patterns;
    }
    
    private String readFileContent(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        
        try {
            // Try UTF-8 first (default)
            return Files.readString(file);
        } catch (Exception e) {
            // VB6 and legacy files often use Windows-1252 encoding
            if (fileName.endsWith(".frm") || fileName.endsWith(".bas") || 
                fileName.endsWith(".cls") || fileName.endsWith(".ctl") ||
                fileName.endsWith(".vbp")) {
                try {
                    return Files.readString(file, java.nio.charset.Charset.forName("Windows-1252"));
                } catch (Exception e2) {
                    // Last resort: try ISO-8859-1 (reads any byte)
                    try {
                        return Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1);
                    } catch (IOException e3) {
                        System.err.println("Error reading file: " + file + " - " + e3.getMessage());
                        return null;
                    }
                }
            } else {
                System.err.println("Error reading file: " + file + " - " + e.getMessage());
                return null;
            }
        }
    }
    
    private void buildTypeMappings(String patternType, List<Path> files, 
                                   TypeMappingDictionary mappings) {
        // Analyze files and build type mappings
        // TODO: Implement type mapping extraction
    }
    
    private String generateClassStructure(String patternType, List<ExtractedPattern> patterns) {
        // Generate template class structure
        return """
            // Template for %s
            {imports}
            {annotations}
            public class {ClassName} {
                {fields}
                {constructors}
                {methods}
            }
            """.formatted(patternType);
    }
    
    private String generateMethodStructure(String patternType, List<ExtractedPattern> patterns) {
        // Generate template method structure
        return """
            {annotations}
            public {returnType} {methodName}({parameters}) {
                {methodBody}
            }
            """;
    }
    
    private String buildPatternExtractionPrompt(String patternType, List<String> fileContents) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Extract reusable conversion patterns from these ").append(patternType).append(" files.\n\n");
        prompt.append("For each pattern, identify:\n");
        prompt.append("1. Source pattern (e.g., '[HttpGet] public IActionResult')\n");
        prompt.append("2. Target pattern (e.g., '@GetMapping public ResponseEntity')\n");
        prompt.append("3. Explanation of the mapping\n\n");
        
        for (int i = 0; i < fileContents.size(); i++) {
            prompt.append("File ").append(i + 1).append(":\n");
            prompt.append(fileContents.get(i)).append("\n\n");
        }
        
        return prompt.toString();
    }
    
    private String buildPatternApplicationPrompt(String patternType, String sourceCode, 
                                                   ConversionTemplate template) {
        return """
            Convert this %s code using the established patterns.
            
            Template structure:
            %s
            
            Source code:
            ```
            %s
            ```
            
            Return only the converted code without explanations.
            """.formatted(patternType, template.classStructure(), sourceCode);
    }
    
    private List<ExtractedPattern> parseExtractedPatterns(String response, String patternType) {
        List<ExtractedPattern> patterns = new ArrayList<>();
        
        if (response == null || response.trim().isEmpty()) {
            return patterns;
        }
        
        // Parse Gemini response to extract patterns
        // Look for pattern descriptions in various formats
        String[] lines = response.split("\n");
        ExtractedPattern currentPattern = null;
        
        for (String line : lines) {
            line = line.trim();
            
            // Look for pattern headers
            if (line.toLowerCase().startsWith("pattern") || 
                line.toLowerCase().startsWith("mapping") ||
                line.contains("→") || line.contains("->") ||
                (line.startsWith("- ") && line.contains(":"))) {
                
                // Save previous pattern if exists
                if (currentPattern != null) {
                    patterns.add(currentPattern);
                }
                
                // Parse new pattern
                currentPattern = parsePatternLine(line, patternType);
                
            } else if (currentPattern != null && !line.isEmpty()) {
                // Add additional context to current pattern
                currentPattern = addContextToPattern(currentPattern, line);
            }
        }
        
        // Add final pattern
        if (currentPattern != null) {
            patterns.add(currentPattern);
        }
        
        // If no structured patterns found, try to extract from raw text
        if (patterns.isEmpty()) {
            patterns = extractPatternsFromRawText(response, patternType);
        }
        
        return patterns;
    }
    
    private ExtractedPattern parsePatternLine(String line, String patternType) {
        String sourcePattern = "";
        String targetPattern = "";
        String explanation = line;
        
        // Parse arrow patterns: "source → target"
        if (line.contains("→")) {
            String[] parts = line.split("→", 2);
            sourcePattern = parts[0].trim();
            targetPattern = parts.length > 1 ? parts[1].trim() : "";
        } else if (line.contains("->")) {
            String[] parts = line.split("->", 2);
            sourcePattern = parts[0].trim();
            targetPattern = parts.length > 1 ? parts[1].trim() : "";
        }
        
        // Clean up patterns
        sourcePattern = cleanPattern(sourcePattern);
        targetPattern = cleanPattern(targetPattern);
        
        return new ExtractedPattern(
            patternType,
            sourcePattern,
            targetPattern,
            List.of(line), // examples
            calculateConfidence(sourcePattern, targetPattern),
            explanation
        );
    }
    
    private ExtractedPattern addContextToPattern(ExtractedPattern pattern, String line) {
        List<String> updatedExamples = new ArrayList<>(pattern.examples());
        updatedExamples.add(line);
        
        return new ExtractedPattern(
            pattern.patternType(),
            pattern.sourcePattern(),
            pattern.targetPattern(),
            updatedExamples,
            pattern.confidenceScore(),
            pattern.explanation() + " " + line
        );
    }
    
    private List<ExtractedPattern> extractPatternsFromRawText(String response, String patternType) {
        List<ExtractedPattern> patterns = new ArrayList<>();
        
        // Common .NET to Java patterns to look for
        Map<String, String> commonMappings = Map.ofEntries(
            Map.entry("[HttpGet]", "@GetMapping"),
            Map.entry("[HttpPost]", "@PostMapping"), 
            Map.entry("[HttpPut]", "@PutMapping"),
            Map.entry("[HttpDelete]", "@DeleteMapping"),
            Map.entry("IActionResult", "ResponseEntity"),
            Map.entry("Task<IActionResult>", "ResponseEntity"),
            Map.entry("System.String", "String"),
            Map.entry("System.Int32", "int"),
            Map.entry("System.Boolean", "boolean"),
            Map.entry("List<T>", "List<T>"),
            Map.entry("IEnumerable<T>", "List<T>"),
            Map.entry("DbSet<T>", "JpaRepository<T, ID>"),
            Map.entry("[FromBody]", "@RequestBody"),
            Map.entry("[FromQuery]", "@RequestParam"),
            Map.entry("[FromRoute]", "@PathVariable")
        );
        
        // Look for these patterns in the response
        for (Map.Entry<String, String> mapping : commonMappings.entrySet()) {
            if (response.contains(mapping.getKey()) || response.contains(mapping.getValue())) {
                patterns.add(new ExtractedPattern(
                    patternType,
                    mapping.getKey(),
                    mapping.getValue(),
                    List.of("Common framework mapping"),
                    90, // High confidence for known mappings
                    "Standard " + patternType.toLowerCase() + " pattern mapping"
                ));
            }
        }
        
        // Extract any annotations or keywords found
        if (response.toLowerCase().contains("annotation") || response.contains("@")) {
            patterns.add(new ExtractedPattern(
                patternType,
                "C# attributes",
                "Java annotations",
                List.of("Attribute to annotation conversion"),
                85,
                "Convert C# attributes to Java annotations"
            ));
        }
        
        return patterns;
    }
    
    private String cleanPattern(String pattern) {
        // Remove common prefixes/suffixes
        pattern = pattern.replaceAll("^[-•*]\\s*", "");
        pattern = pattern.replaceAll("\\s*[:;]\\s*$", "");
        pattern = pattern.replaceAll("^Pattern\\s*\\d*:?\\s*", "");
        return pattern.trim();
    }
    
    private int calculateConfidence(String source, String target) {
        int confidence = 50; // Base confidence
        
        // Higher confidence for specific mappings
        if (source.startsWith("[") && source.endsWith("]") && target.startsWith("@")) {
            confidence = 95; // Attribute to annotation
        } else if (source.contains("IActionResult") && target.contains("ResponseEntity")) {
            confidence = 90; // Return type mapping
        } else if (source.contains("System.") && !target.contains("System.")) {
            confidence = 85; // Type mapping
        } else if (!source.isEmpty() && !target.isEmpty()) {
            confidence = 75; // General mapping
        }
        
        return confidence;
    }
}
