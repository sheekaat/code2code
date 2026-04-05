package com.code2code.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Language-agnostic file scanner.
 * Discovers all convertible files in a codebase regardless of source language.
 */
public class SourceFileAnalyzer {
    
    private final FileTypeRegistry fileTypeRegistry;
    private final Set<String> ignoredPatterns;
    
    public SourceFileAnalyzer(FileTypeRegistry fileTypeRegistry) {
        this.fileTypeRegistry = fileTypeRegistry;
        this.ignoredPatterns = Set.of(
            ".git", ".svn", ".hg",           // VCS
            "node_modules", "vendor",        // Dependencies
            "bin", "obj", "target", "build", // Build output
            ".vs", ".idea", ".vscode",        // IDE
            "*.min.js", "*.min.css",         // Minified
            "*.dll", "*.exe", "*.so", "*.dylib" // Binaries
        );
    }
    
    /**
     * Scans a codebase and produces a complete inventory.
     */
    public CodebaseInventory scanCodebase(Path projectDir) {
        System.out.println("  Scanning codebase: " + projectDir);
        
        Map<String, List<DiscoveredFile>> filesByLanguage = new HashMap<>();
        List<DiscoveredFile> unknownFiles = new ArrayList<>();
        List<DiscoveredFile> configFiles = new ArrayList<>();
        
        int totalFiles = 0;
        int convertibleFiles = 0;
        
        try (Stream<Path> paths = Files.walk(projectDir)) {
            for (Path path : paths.toList()) {
                if (shouldIgnore(path)) {
                    continue;
                }
                
                if (Files.isRegularFile(path)) {
                    totalFiles++;
                    DiscoveredFile file = analyzeFile(path, projectDir);
                    
                    if (file.isConfig()) {
                        configFiles.add(file);
                    } else if (file.sourceLanguage() != null) {
                        filesByLanguage
                            .computeIfAbsent(file.sourceLanguage(), k -> new ArrayList<>())
                            .add(file);
                        convertibleFiles++;
                    } else {
                        unknownFiles.add(file);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("    Error scanning codebase: " + e.getMessage());
        }
        
        // Detect primary source language
        String primaryLanguage = filesByLanguage.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().size()))
            .map(Map.Entry::getKey)
            .orElse("unknown");
        
        System.out.println("    Found " + totalFiles + " total files");
        System.out.println("    Convertible: " + convertibleFiles + " files in " + filesByLanguage.size() + " languages");
        System.out.println("    Primary language: " + primaryLanguage);
        
        return new CodebaseInventory(
            projectDir.getFileName().toString(),
            primaryLanguage,
            filesByLanguage,
            unknownFiles,
            configFiles,
            totalFiles,
            convertibleFiles
        );
    }
    
    /**
     * Groups files into logical modules based on naming patterns and structure.
     */
    public List<SourceModule> identifyModules(CodebaseInventory inventory, String targetLanguage) {
        List<SourceModule> modules = new ArrayList<>();
        
        for (Map.Entry<String, List<DiscoveredFile>> entry : inventory.filesByLanguage().entrySet()) {
            String sourceLang = entry.getKey();
            List<DiscoveredFile> files = entry.getValue();
            
            FileTypeRegistry.LanguageDefinition langDef = fileTypeRegistry.getSourceLanguage(sourceLang);
            FileTypeRegistry.ConversionStrategy strategy = fileTypeRegistry.getConversionStrategy(sourceLang, targetLanguage);
            
            // Group by directory structure and naming patterns
            Map<String, List<DiscoveredFile>> moduleGroups = groupFilesIntoModules(files, langDef);
            
            for (Map.Entry<String, List<DiscoveredFile>> moduleEntry : moduleGroups.entrySet()) {
                String moduleName = moduleEntry.getKey();
                List<DiscoveredFile> moduleFiles = moduleEntry.getValue();
                
                // Calculate complexity score
                int complexity = calculateModuleComplexity(moduleFiles, langDef);
                
                // Determine priority
                Priority priority = determinePriority(moduleName, moduleFiles, complexity);
                
                modules.add(new SourceModule(
                    moduleName,
                    sourceLang,
                    targetLanguage,
                    moduleFiles,
                    strategy,
                    complexity,
                    priority,
                    extractDependencies(moduleFiles)
                ));
            }
        }
        
        // Sort by priority
        modules.sort(Comparator.comparing(SourceModule::priority));
        
        return modules;
    }
    
    /**
     * Generates conversion prompts dynamically based on source and target.
     */
    public String buildDynamicPrompt(DiscoveredFile file, String targetLanguage, 
                                      FileTypeRegistry.ConversionStrategy strategy) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("=== Source File ===\n");
        prompt.append("Language: ").append(file.sourceLanguage()).append("\n");
        prompt.append("File: ").append(file.relativePath()).append("\n");
        prompt.append("Size: ").append(file.lineCount()).append(" lines\n\n");
        
        prompt.append("=== Target ===\n");
        prompt.append("Language: ").append(targetLanguage).append("\n\n");
        
        prompt.append("=== Conversion Rules ===\n");
        prompt.append(strategy.conversionRules()).append("\n\n");
        
        if (!strategy.typeMappings().isEmpty()) {
            prompt.append("=== Type Mappings ===\n");
            strategy.typeMappings().forEach((source, target) -> {
                prompt.append(source).append(" → ").append(target).append("\n");
            });
            prompt.append("\n");
        }
        
        prompt.append("=== File Constructs ===\n");
        for (Map.Entry<String, List<String>> construct : file.detectedConstructs().entrySet()) {
            prompt.append(construct.getKey()).append(": ");
            prompt.append(String.join(", ", construct.getValue())).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("=== Instructions ===\n");
        prompt.append("1. Convert the code to ").append(targetLanguage).append(" following the rules above\n");
        prompt.append("2. Maintain the same functionality and behavior\n");
        prompt.append("3. Follow idiomatic patterns for the target language\n");
        prompt.append("4. Include proper package/namespace declarations\n");
        prompt.append("5. Handle one public class per file (split if needed)\n");
        prompt.append("6. Return only the converted code, no explanations\n");
        
        return prompt.toString();
    }
    
    private DiscoveredFile analyzeFile(Path file, Path projectDir) {
        String fileName = file.getFileName().toString();
        String relativePath = projectDir.relativize(file).toString();
        
        // Detect source language
        FileTypeRegistry.LanguageDefinition langDef = fileTypeRegistry.detectSourceLanguage(file);
        String sourceLanguage = langDef != null ? langDef.id() : null;
        
        // Detect constructs
        Map<String, List<String>> constructs = new HashMap<>();
        int lineCount = 0;
        
        if (sourceLanguage != null && langDef != null) {
            try {
                String content = Files.readString(file);
                lineCount = content.split("\n").length;
                constructs = detectConstructs(content, langDef);
            } catch (IOException e) {
                // Skip content analysis
            }
        }
        
        // Determine if config file
        boolean isConfig = isConfigurationFile(fileName);
        
        return new DiscoveredFile(
            file,
            relativePath,
            fileName,
            sourceLanguage,
            constructs,
            lineCount,
            isConfig
        );
    }
    
    private Map<String, List<String>> detectConstructs(String content, 
                                                       FileTypeRegistry.LanguageDefinition langDef) {
        Map<String, List<String>> constructs = new HashMap<>();
        
        for (Map.Entry<String, String> pattern : langDef.constructPatterns().entrySet()) {
            List<String> matches = new ArrayList<>();
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern.getValue());
            java.util.regex.Matcher matcher = regex.matcher(content);
            
            while (matcher.find()) {
                String name = matcher.group("name");
                if (name != null && !matches.contains(name)) {
                    matches.add(name);
                }
            }
            
            if (!matches.isEmpty()) {
                constructs.put(pattern.getKey(), matches);
            }
        }
        
        return constructs;
    }
    
    private Map<String, List<DiscoveredFile>> groupFilesIntoModules(
            List<DiscoveredFile> files, FileTypeRegistry.LanguageDefinition langDef) {
        
        Map<String, List<DiscoveredFile>> groups = new HashMap<>();
        
        for (DiscoveredFile file : files) {
            String moduleName = inferModuleName(file, langDef);
            groups.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(file);
        }
        
        return groups;
    }
    
    private String inferModuleName(DiscoveredFile file, FileTypeRegistry.LanguageDefinition langDef) {
        String name = file.fileName().toLowerCase();
        String path = file.relativePath().toLowerCase();
        
        // Check naming patterns
        if (name.contains("controller") || path.contains("controller")) return "Controllers";
        if (name.contains("service") || path.contains("service")) return "Services";
        if (name.contains("repository") || path.contains("repository")) return "Repositories";
        if (name.contains("model") || name.contains("entity") || path.contains("model")) return "Models";
        if (name.contains("dto") || path.contains("dto")) return "DTOs";
        if (name.contains("util") || name.contains("helper")) return "Utils";
        if (path.contains("test") || name.contains("test")) return "Tests";
        if (path.contains("config") || name.contains("config")) return "Config";
        
        // Group by directory
        Path dir = Paths.get(file.relativePath()).getParent();
        if (dir != null) {
            return dir.getFileName() != null ? dir.getFileName().toString() : "Root";
        }
        
        return "General";
    }
    
    private int calculateModuleComplexity(List<DiscoveredFile> files, 
                                          FileTypeRegistry.LanguageDefinition langDef) {
        int totalLines = files.stream().mapToInt(DiscoveredFile::lineCount).sum();
        long classCount = files.stream()
            .flatMap(f -> f.detectedConstructs().getOrDefault("class", List.of()).stream())
            .distinct()
            .count();
        
        // Simple complexity formula: lines + (classes * 50)
        return totalLines + (int) (classCount * 50);
    }
    
    private Priority determinePriority(String moduleName, List<DiscoveredFile> files, int complexity) {
        // Critical modules
        if (List.of("Models", "DTOs", "Entities", "Domain").contains(moduleName)) {
            return Priority.CRITICAL;
        }
        
        // High priority
        if (List.of("Services", "Repositories", "Controllers", "Core").contains(moduleName)) {
            return Priority.HIGH;
        }
        
        // Medium complexity gets medium priority
        if (complexity > 500 && complexity < 2000) {
            return Priority.MEDIUM;
        }
        
        // Tests and utils are lower priority
        if (List.of("Tests", "Utils", "Helpers", "Config").contains(moduleName)) {
            return Priority.LOW;
        }
        
        return Priority.MEDIUM;
    }
    
    private Set<String> extractDependencies(List<DiscoveredFile> files) {
        Set<String> deps = new HashSet<>();
        
        for (DiscoveredFile file : files) {
            // Extract imports/usings from constructs
            deps.addAll(file.detectedConstructs().getOrDefault("import", List.of()));
            deps.addAll(file.detectedConstructs().getOrDefault("using", List.of()));
        }
        
        return deps;
    }
    
    private boolean shouldIgnore(Path path) {
        String name = path.getFileName().toString();
        String pathStr = path.toString();
        
        for (String pattern : ignoredPatterns) {
            if (pattern.startsWith("*")) {
                if (name.endsWith(pattern.substring(1))) return true;
            } else if (pattern.contains(".")) {
                if (name.equals(pattern)) return true;
            } else {
                if (pathStr.contains(pattern)) return true;
            }
        }
        
        return false;
    }
    
    private boolean isConfigurationFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".json") ||
               lower.endsWith(".xml") ||
               lower.endsWith(".yaml") ||
               lower.endsWith(".yml") ||
               lower.endsWith(".properties") ||
               lower.endsWith(".config") ||
               lower.endsWith(".ini") ||
               lower.endsWith(".toml");
    }
    
    // Record definitions
    public record DiscoveredFile(
        Path absolutePath,
        String relativePath,
        String fileName,
        String sourceLanguage,
        Map<String, List<String>> detectedConstructs,
        int lineCount,
        boolean isConfig
    ) {}
    
    public record CodebaseInventory(
        String projectName,
        String primaryLanguage,
        Map<String, List<DiscoveredFile>> filesByLanguage,
        List<DiscoveredFile> unknownFiles,
        List<DiscoveredFile> configFiles,
        int totalFiles,
        int convertibleFiles
    ) {}
    
    public record SourceModule(
        String name,
        String sourceLanguage,
        String targetLanguage,
        List<DiscoveredFile> files,
        FileTypeRegistry.ConversionStrategy conversionStrategy,
        int complexity,
        Priority priority,
        Set<String> dependencies
    ) {}
    
    public enum Priority {
        CRITICAL, HIGH, MEDIUM, LOW
    }
}
