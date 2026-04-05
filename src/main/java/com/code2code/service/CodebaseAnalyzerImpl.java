package com.code2code.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Phase 1 Implementation: Codebase Analysis
 * Analyzes project structure with minimal token usage.
 */
public class CodebaseAnalyzerImpl implements CodebaseAnalyzer {
    
    private final GeminiApiClient geminiClient;
    
    public CodebaseAnalyzerImpl(GeminiApiClient geminiClient) {
        this.geminiClient = geminiClient;
    }
    
    @Override
    public MigrationPlan analyzeProjectStructure(Path projectDir) {
        System.out.println("  Scanning project structure...");
        
        // Step 1: Generate file tree (minimal tokens)
        FileTreeNode fileTree = generateFileTree(projectDir);
        
        // Step 2: Detect technology stack
        String sourceTechnology = detectSourceTechnology(projectDir);
        String targetTechnology = determineTargetTechnology(sourceTechnology);
        
        // Step 3: Identify modules
        List<ModuleBoundary> modules = identifyModuleBoundaries(projectDir);
        
        // Step 4: Analyze dependencies
        Map<String, Set<String>> dependencyGraph = analyzeDependencies(projectDir);
        
        // Step 5: Create phased conversion plan
        List<ConversionPhase> phases = createPhasedPlan(modules, dependencyGraph);
        
        // Step 6: Estimate token budget
        int estimatedTokens = estimateTokens(modules);
        
        String projectName = projectDir.getFileName().toString();
        
        return new MigrationPlan(
            projectName,
            sourceTechnology,
            targetTechnology,
            modules,
            dependencyGraph,
            phases,
            estimatedTokens
        );
    }
    
    @Override
    public List<ModuleBoundary> identifyModuleBoundaries(Path projectDir) {
        List<ModuleBoundary> modules = new ArrayList<>();
        
        // Scan for common module patterns
        Map<String, List<String>> modulePatterns = Map.ofEntries(
            Map.entry("Controllers", List.of("*Controller*.cs", "*Controller*.vb")),
            Map.entry("Services", List.of("*Service*.cs", "*Service*.vb", "*Business*.cs")),
            Map.entry("Data", List.of("*Repository*.cs", "*Data*.cs", "*Context*.cs")),
            Map.entry("Models", List.of("*Model*.cs", "*Entity*.cs", "*DTO*.cs")),
            Map.entry("Auth", List.of("*Auth*.cs", "*Identity*.cs", "*Security*.cs")),
            Map.entry("Utils", List.of("*Util*.cs", "*Helper*.cs", "*Common*.cs")),
            Map.entry("UI", List.of("*.aspx", "*.cshtml", "*.xaml", "*.vbhtml")),
            // General C# project patterns
            Map.entry("Algorithms", List.of("*Sort*.cs", "*Search*.cs", "*Algorithm*.cs", "*Pathfinding*.cs")),
            Map.entry("DataStructures", List.of("*List*.cs", "*Tree*.cs", "*Graph*.cs", "*Queue*.cs", "*Stack*.cs", "*Collection*.cs")),
            Map.entry("Games", List.of("*Game*.cs", "*Quiz*.cs", "*TicTacToe*.cs")),
            Map.entry("ConsoleApps", List.of("Program.cs", "*Console*.cs")),
            Map.entry("IO", List.of("*Serializer*.cs", "*File*.cs", "*IO*.cs"))
        );
        
        for (Map.Entry<String, List<String>> entry : modulePatterns.entrySet()) {
            String moduleType = entry.getKey();
            List<String> patterns = entry.getValue();
            
            int estimatedFiles = countMatchingFiles(projectDir, patterns);
            if (estimatedFiles > 0) {
                Priority priority = determinePriority(moduleType, estimatedFiles);
                Set<String> deps = findModuleDependencies(projectDir, patterns);
                
                modules.add(new ModuleBoundary(
                    moduleType,
                    moduleType,
                    patterns,
                    deps,
                    priority,
                    estimatedFiles
                ));
            }
        }
        
        // Sort by priority
        modules.sort(Comparator.comparing(ModuleBoundary::priority));
        
        return modules;
    }
    
    @Override
    public Map<String, Set<String>> analyzeDependencies(Path projectDir) {
        Map<String, Set<String>> deps = new HashMap<>();
        
        // Analyze project files for dependencies
        // TODO: Implement dependency analysis using Gemini API
        
        return deps;
    }
    
    private FileTreeNode generateFileTree(Path projectDir) {
        try (Stream<Path> paths = Files.walk(projectDir)) {
            return buildTreeNode(projectDir, paths.collect(Collectors.toList()));
        } catch (IOException e) {
            System.err.println("Error generating file tree: " + e.getMessage());
            return new FileTreeNode(projectDir.getFileName().toString(), true, new ArrayList<>());
        }
    }
    
    private FileTreeNode buildTreeNode(Path path, List<Path> allPaths) {
        boolean isDirectory = Files.isDirectory(path);
        List<FileTreeNode> children = new ArrayList<>();
        
        if (isDirectory) {
            try (Stream<Path> dirPaths = Files.list(path)) {
                dirPaths.forEach(child -> {
                    if (!child.getFileName().toString().startsWith(".")) {
                        children.add(buildTreeNode(child, allPaths));
                    }
                });
            } catch (IOException e) {
                // Skip directories we can't read
            }
        }
        
        return new FileTreeNode(
            path.getFileName().toString(),
            isDirectory,
            children
        );
    }
    
    private String detectSourceTechnology(Path projectDir) {
        // Check for VB6 files
        if (hasFileWithExtension(projectDir, ".frm", ".bas", ".cls")) {
            return "VB6";
        }
        
        // Check for .NET
        if (hasFileWithExtension(projectDir, ".csproj", ".vbproj", ".fsproj")) {
            if (hasFileWithExtension(projectDir, ".cs")) return ".NET (C#)";
            if (hasFileWithExtension(projectDir, ".vb")) return ".NET (VB.NET)";
        }
        
        // Check for legacy .NET
        if (hasFileWithExtension(projectDir, ".sln")) {
            return ".NET Framework";
        }
        
        return "Unknown";
    }
    
    private String determineTargetTechnology(String source) {
        return switch (source) {
            case "VB6" -> "ReactJS 18 / Java Spring Boot";
            case ".NET (C#)", ".NET (VB.NET)", ".NET Framework" -> "Java Spring Boot 3.x";
            default -> "Java Spring Boot 3.x";
        };
    }
    
    private boolean hasFileWithExtension(Path dir, String... extensions) {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.anyMatch(p -> {
                String name = p.toString().toLowerCase();
                for (String ext : extensions) {
                    if (name.endsWith(ext.toLowerCase())) return true;
                }
                return false;
            });
        } catch (IOException e) {
            return false;
        }
    }
    
    private int countMatchingFiles(Path dir, List<String> patterns) {
        try (Stream<Path> paths = Files.walk(dir)) {
            return (int) paths.filter(p -> {
                String name = p.getFileName().toString();
                return patterns.stream().anyMatch(pattern -> 
                    name.matches(pattern.replace("*", ".*")));
            }).count();
        } catch (IOException e) {
            return 0;
        }
    }
    
    private Priority determinePriority(String moduleType, int fileCount) {
        return switch (moduleType) {
            case "Models", "DTOs" -> Priority.CRITICAL;
            case "Auth", "Services" -> Priority.HIGH;
            case "Controllers", "Data" -> Priority.MEDIUM;
            case "DataStructures", "Algorithms" -> Priority.HIGH;  // Core logic
            case "Games", "ConsoleApps" -> Priority.MEDIUM;
            case "IO" -> Priority.MEDIUM;
            case "UI", "Utils" -> Priority.LOW;
            default -> Priority.MEDIUM;
        };
    }
    
    private Set<String> findModuleDependencies(Path projectDir, List<String> patterns) {
        // Analyze imports/usings in matching files
        Set<String> deps = new HashSet<>();
        // TODO: Implement dependency extraction
        return deps;
    }
    
    private List<ConversionPhase> createPhasedPlan(List<ModuleBoundary> modules, 
                                                     Map<String, Set<String>> deps) {
        List<ConversionPhase> phases = new ArrayList<>();
        
        // Phase 1: Pattern Extraction (representative files)
        phases.add(new ConversionPhase(
            1,
            "Pattern Extraction",
            List.of("Representative files from each module type"),
            "Pattern Extraction",
            5000  // ~5k tokens for pattern extraction
        ));
        
        // Phase 2+: Chunked conversion by priority
        int phaseNum = 2;
        for (Priority priority : Priority.values()) {
            List<String> priorityModules = modules.stream()
                .filter(m -> m.priority() == priority)
                .map(ModuleBoundary::name)
                .toList();
            
            if (!priorityModules.isEmpty()) {
                int estimatedTokens = priorityModules.size() * 8000; // ~8k per module
                phases.add(new ConversionPhase(
                    phaseNum++,
                    "Convert " + priority + " Priority Modules",
                    priorityModules,
                    "Chunked Conversion",
                    estimatedTokens
                ));
            }
        }
        
        // Final phase: Validation
        phases.add(new ConversionPhase(
            phaseNum,
            "Validation & Fixes",
            modules.stream().map(ModuleBoundary::name).toList(),
            "Validation",
            3000
        ));
        
        return phases;
    }
    
    private int estimateTokens(List<ModuleBoundary> modules) {
        // Structured approach: ~80k-200k tokens vs 500k-2M naive
        int baseTokens = 10000;  // Analysis + pattern extraction
        int moduleTokens = modules.stream()
            .mapToInt(m -> m.estimatedFiles() * 100)  // ~100 tokens per file structured
            .sum();
        return baseTokens + moduleTokens;
    }
    
    // Helper record for file tree
    private record FileTreeNode(String name, boolean isDirectory, List<FileTreeNode> children) {}
}
