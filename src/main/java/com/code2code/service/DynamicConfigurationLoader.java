package com.code2code.service;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Dynamic Language and Strategy Loader.
 * Loads language definitions and conversion strategies from configuration files
 * and supports runtime registration without code changes.
 */
public class DynamicConfigurationLoader {
    
    private final Gson gson;
    private final List<ConfigSource> configSources;
    
    public DynamicConfigurationLoader() {
        this.gson = new Gson();
        this.configSources = new ArrayList<>();
        initializeDefaultSources();
    }
    
    private void initializeDefaultSources() {
        // Add built-in configuration source
        configSources.add(new ClasspathConfigSource("languages/"));
        
        // Add external configuration directory
        String userConfigDir = System.getenv("CONVERTER_CONFIG_DIR");
        if (userConfigDir != null) {
            configSources.add(new FilesystemConfigSource(Paths.get(userConfigDir)));
        }
        
        // Add current working directory config
        configSources.add(new FilesystemConfigSource(Paths.get(".converter")));
    }
    
    /**
     * Loads all language definitions from configuration sources.
     */
    public List<FileTypeRegistry.LanguageDefinition> loadLanguageDefinitions() {
        List<FileTypeRegistry.LanguageDefinition> languages = new ArrayList<>();
        
        for (ConfigSource source : configSources) {
            try {
                List<String> configFiles = source.listFiles("*.json");
                for (String configFile : configFiles) {
                    String content = source.readFile(configFile);
                    LanguageConfig config = gson.fromJson(content, LanguageConfig.class);
                    
                    if (config.isValid()) {
                        languages.add(config.toLanguageDefinition());
                    }
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not load config from " + source + ": " + e.getMessage());
            }
        }
        
        return languages;
    }
    
    /**
     * Loads all conversion strategies from configuration sources.
     */
    public List<FileTypeRegistry.ConversionStrategy> loadConversionStrategies() {
        List<FileTypeRegistry.ConversionStrategy> strategies = new ArrayList<>();
        
        for (ConfigSource source : configSources) {
            try {
                List<String> strategyFiles = source.listFiles("*-to-*.json");
                for (String strategyFile : strategyFiles) {
                    String content = source.readFile(strategyFile);
                    StrategyConfig config = gson.fromJson(content, StrategyConfig.class);
                    
                    if (config.isValid()) {
                        strategies.add(config.toConversionStrategy());
                    }
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not load strategies from " + source + ": " + e.getMessage());
            }
        }
        
        return strategies;
    }
    
    /**
     * Dynamically registers a new language at runtime.
     */
    public void registerLanguageDynamically(FileTypeRegistry fileTypeRegistry,
                                           String id,
                                           String name,
                                           List<String> extensions,
                                           List<String> projectFiles,
                                           Map<String, String> constructPatterns,
                                           List<String> commonLibraries) {
        
        FileTypeRegistry.LanguageDefinition lang = new FileTypeRegistry.LanguageDefinition(
            id, name, extensions, projectFiles, constructPatterns, commonLibraries
        );
        
        fileTypeRegistry.registerSourceLanguage(lang);
        System.out.println("Dynamically registered language: " + name + " (" + id + ")");
    }
    
    /**
     * Dynamically registers a conversion strategy at runtime.
     */
    public void registerStrategyDynamically(FileTypeRegistry fileTypeRegistry,
                                           String sourceId,
                                           String targetId,
                                           String conversionRules,
                                           Map<String, String> typeMappings) {
        
        FileTypeRegistry.ConversionStrategy strategy = new FileTypeRegistry.ConversionStrategy(
            sourceId, targetId, conversionRules, typeMappings
        );
        
        fileTypeRegistry.registerConversionStrategy(strategy);
        System.out.println("Dynamically registered strategy: " + sourceId + " -> " + targetId);
    }
    
    /**
     * Infers language from file content using heuristics when no config matches.
     */
    public Optional<FileTypeRegistry.LanguageDefinition> inferLanguageFromContent(
            String fileName, String content) {
        
        // Heuristic-based detection
        Map<String, Integer> scores = new HashMap<>();
        
        // Python detection
        if (content.contains("def ") && content.contains(":")) {
            scores.merge("python", 10, Integer::sum);
        }
        if (content.contains("import ") && !content.contains(";") && content.contains("#")) {
            scores.merge("python", 5, Integer::sum);
        }
        
        // JavaScript/TypeScript detection
        if (content.contains("const ") || content.contains("let ") || content.contains("var ")) {
            scores.merge("javascript", 5, Integer::sum);
        }
        if (content.contains("function ") || content.contains("=>")) {
            scores.merge("javascript", 5, Integer::sum);
        }
        if (content.contains("interface ") || content.contains(": type")) {
            scores.merge("typescript", 10, Integer::sum);
        }
        
        // Go detection
        if (content.contains("package ") && content.contains("func ")) {
            scores.merge("go", 10, Integer::sum);
        }
        if (content.contains("defer ") || content.contains("chan ")) {
            scores.merge("go", 5, Integer::sum);
        }
        
        // Rust detection
        if (content.contains("fn ") && content.contains("let ") && content.contains("mut ")) {
            scores.merge("rust", 10, Integer::sum);
        }
        if (content.contains("impl ") || content.contains("trait ")) {
            scores.merge("rust", 5, Integer::sum);
        }
        
        // C++ detection
        if (content.contains("#include") || content.contains("std::")) {
            scores.merge("cpp", 10, Integer::sum);
        }
        
        // PHP detection
        if (content.contains("<?php") || content.contains("$")) {
            scores.merge("php", 10, Integer::sum);
        }
        
        // Ruby detection
        if (content.contains("def ") && content.contains("end") && content.contains("@")) {
            scores.merge("ruby", 10, Integer::sum);
        }
        
        // Return the highest scoring language
        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .filter(e -> e.getValue() >= 10)  // Minimum confidence threshold
            .map(e -> createInferredLanguage(e.getKey(), fileName));
    }
    
    /**
     * Uses AI to infer language when heuristics are inconclusive.
     */
    public Optional<FileTypeRegistry.LanguageDefinition> aiInferLanguage(
            GeminiApiClient geminiClient, String fileName, String content) {
        
        String prompt = """
            Analyze this code file and identify the programming language.
            
            File name: %s
            
            Content (first 500 chars):
            ```
            %s
            ```
            
            Respond with ONLY the language identifier in this exact format:
            language_id|language_name|file_extensions|confidence_score
            
            Examples:
            python|Python|.py,.pyw|95
            rust|Rust|.rs,.toml|88
            swift|Swift|.swift,.xcodeproj|92
            
            If uncertain, respond with: unknown|Unknown||0
            """.formatted(fileName, content.substring(0, Math.min(500, content.length())));
        
        String response = geminiClient.generateText(prompt);
        
        // Parse response
        String[] parts = response.trim().split("\\|");
        if (parts.length >= 2 && !parts[0].equals("unknown")) {
            String langId = parts[0];
            String langName = parts[1];
            List<String> extensions = parts.length > 2 ? 
                Arrays.asList(parts[2].split(",")) : 
                List.of("." + fileName.substring(fileName.lastIndexOf('.') + 1));
            
            int confidence = parts.length > 3 ? Integer.parseInt(parts[3]) : 50;
            
            if (confidence >= 70) {
                return Optional.of(createInferredLanguage(langId, langName, extensions));
            }
        }
        
        return Optional.empty();
    }
    
    private FileTypeRegistry.LanguageDefinition createInferredLanguage(String id, String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.'));
        return new FileTypeRegistry.LanguageDefinition(
            id,
            id.substring(0, 1).toUpperCase() + id.substring(1),
            List.of(ext),
            List.of(),
            Map.of(),  // Patterns to be learned
            List.of()
        );
    }
    
    private FileTypeRegistry.LanguageDefinition createInferredLanguage(
            String id, String name, List<String> extensions) {
        return new FileTypeRegistry.LanguageDefinition(
            id, name, extensions, List.of(), Map.of(), List.of()
        );
    }
    
    /**
     * Generates a conversion strategy on-the-fly using AI.
     */
    public FileTypeRegistry.ConversionStrategy generateStrategyOnTheFly(
            GeminiApiClient geminiClient,
            String sourceId,
            String targetId,
            List<FileTypeRegistry.LanguageDefinition> knownLanguages) {
        
        String sourceName = knownLanguages.stream()
            .filter(l -> l.id().equals(sourceId))
            .findFirst()
            .map(FileTypeRegistry.LanguageDefinition::name)
            .orElse(sourceId);
        
        String targetName = knownLanguages.stream()
            .filter(l -> l.id().equals(targetId))
            .findFirst()
            .map(FileTypeRegistry.LanguageDefinition::name)
            .orElse(targetId);
        
        String prompt = """
            Generate a conversion strategy from %s to %s.
            
            Provide:
            1. Key conversion rules
            2. Common type mappings
            
            Format as JSON:
            {
              "rules": "1. Rule one\\n2. Rule two\\n...",
              "typeMappings": {"sourceType": "targetType", ...}
            }
            """.formatted(sourceName, targetName);
        
        String response = geminiClient.generateText(prompt);
        
        try {
            GeneratedStrategy generated = gson.fromJson(response, GeneratedStrategy.class);
            return new FileTypeRegistry.ConversionStrategy(
                sourceId,
                targetId,
                generated.rules(),
                generated.typeMappings()
            );
        } catch (Exception e) {
            // Fallback to generic strategy
            return new FileTypeRegistry.ConversionStrategy(
                sourceId,
                targetId,
                "Convert " + sourceName + " code to " + targetName + " following best practices.",
                Map.of()
            );
        }
    }
    
    public void addConfigSource(ConfigSource source) {
        configSources.add(source);
    }
    
    // Configuration sources
    public interface ConfigSource {
        List<String> listFiles(String pattern) throws IOException;
        String readFile(String path) throws IOException;
    }
    
    private static class ClasspathConfigSource implements ConfigSource {
        private final String basePath;
        
        ClasspathConfigSource(String basePath) {
            this.basePath = basePath;
        }
        
        @Override
        public List<String> listFiles(String pattern) throws IOException {
            // In real implementation, scan classpath resources
            return List.of();  // Placeholder
        }
        
        @Override
        public String readFile(String path) throws IOException {
            try (var is = getClass().getClassLoader().getResourceAsStream(basePath + path)) {
                if (is == null) throw new IOException("Resource not found: " + path);
                return new String(is.readAllBytes());
            }
        }
    }
    
    private static class FilesystemConfigSource implements ConfigSource {
        private final Path basePath;
        
        FilesystemConfigSource(Path basePath) {
            this.basePath = basePath;
        }
        
        @Override
        public List<String> listFiles(String pattern) throws IOException {
            if (!Files.exists(basePath)) return List.of();
            
            List<String> files = new ArrayList<>();
            String globPattern = pattern.replace("*", "**/*");
            
            try (var stream = Files.newDirectoryStream(basePath, globPattern)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p)) {
                        files.add(p.getFileName().toString());
                    }
                }
            }
            
            return files;
        }
        
        @Override
        public String readFile(String path) throws IOException {
            return Files.readString(basePath.resolve(path));
        }
    }
    
    // Configuration record classes
    private static class LanguageConfig {
        String id;
        String name;
        List<String> extensions;
        List<String> projectFiles;
        Map<String, String> constructPatterns;
        List<String> commonLibraries;
        
        boolean isValid() {
            return id != null && name != null && extensions != null && !extensions.isEmpty();
        }
        
        FileTypeRegistry.LanguageDefinition toLanguageDefinition() {
            return new FileTypeRegistry.LanguageDefinition(
                id, name, extensions,
                projectFiles != null ? projectFiles : List.of(),
                constructPatterns != null ? constructPatterns : Map.of(),
                commonLibraries != null ? commonLibraries : List.of()
            );
        }
    }
    
    private static class StrategyConfig {
        String sourceId;
        String targetId;
        String conversionRules;
        Map<String, String> typeMappings;
        
        boolean isValid() {
            return sourceId != null && targetId != null && conversionRules != null;
        }
        
        FileTypeRegistry.ConversionStrategy toConversionStrategy() {
            return new FileTypeRegistry.ConversionStrategy(
                sourceId, targetId, conversionRules,
                typeMappings != null ? typeMappings : Map.of()
            );
        }
    }
    
    private record GeneratedStrategy(String rules, Map<String, String> typeMappings) {}
}
