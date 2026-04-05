package com.code2code.service;

import com.code2code.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Language-Agnostic Migration Orchestrator
 * Implements 5-phase token-efficient migration strategy.
 * Now supports any source language → any target language.
 */
@Component
public class StructuredMigrationOrchestrator {
    
    private final AppConfig config;
    private final GeminiApiClient geminiClient;
    private final FileTypeRegistry fileTypeRegistry;
    private final SourceFileAnalyzer sourceAnalyzer;
    
    // Phase services
    private final PatternExtractor patternExtractor;
    private final ChunkedConverter chunkedConverter;
    private final ValidationLoop validationLoop;
    private final BuildFileGenerator buildFileGenerator;
    
    // Migration state
    private SourceFileAnalyzer.CodebaseInventory currentInventory;
    private List<SourceFileAnalyzer.SourceModule> currentModules;
    private PatternExtractor.PatternLibrary patternLibrary;
    private ChunkedConverter.ContextAnchor contextAnchor;
    
    @Autowired
    public StructuredMigrationOrchestrator(AppConfig config) {
        this.config = config;
        this.geminiClient = new GeminiApiClient(config.getGoogleApiKey(), config.getGeminiModel());
        
        // Initialize language-agnostic components
        this.fileTypeRegistry = new FileTypeRegistry();
        this.sourceAnalyzer = new SourceFileAnalyzer(fileTypeRegistry);
        
        // Initialize phase services
        this.patternExtractor = new PatternExtractorImpl(geminiClient);
        this.chunkedConverter = new ChunkedConverterImpl(geminiClient);
        this.validationLoop = new ValidationLoopImpl(geminiClient);
        this.buildFileGenerator = new BuildFileGenerator();
    }
    
    /**
     * Enable WinForms intermediate step for VB6 form conversions.
     * When enabled, VB6 forms are converted to C# WinForms first, then to React.
     */
    public void setUseWinFormsIntermediate(boolean useIntermediate) {
        if (chunkedConverter instanceof ChunkedConverterImpl impl) {
            impl.setUseWinFormsIntermediate(useIntermediate);
        }
    }
    
    /**
     * Executes the complete structured migration workflow.
     */
    public void runStructuredMigration(Path sourceDir, Path targetDir, String targetStack) {
        System.out.println("========================================");
        System.out.println("Language-Agnostic Migration");
        System.out.println("Target Stack: " + targetStack);
        System.out.println("========================================\n");
        
        // Phase 1: Codebase Analysis (Language-Agnostic)
        System.out.println("=== Phase 1: Codebase Analysis ===");
        currentInventory = sourceAnalyzer.scanCodebase(sourceDir);
        currentModules = sourceAnalyzer.identifyModules(currentInventory, targetStack);
        printInventory(currentInventory);
        
        // Phase 2: Pattern Extraction
        System.out.println("\n=== Phase 2: Pattern Extraction ===");
        List<Path> representativeFiles = selectRepresentativeFiles(currentModules);
        patternLibrary = patternExtractor.extractPatterns(representativeFiles);
        printPatternLibrary(patternLibrary);
        
        // Initialize context anchor
        Map<String, String> establishedPatterns = patternLibrary.templates().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().patternType()
            ));
        contextAnchor = chunkedConverter.buildContextAnchor(
            targetStack,
            List.of("camelCase for methods", "PascalCase for classes", "lowercase_with_underscores for packages"),
            establishedPatterns
        );
        
        // Phase 3 & 4 & 5: Convert modules in chunks
        System.out.println("\n=== Phase 3-5: Chunked Conversion with Validation ===");
        List<String> completedModules = new java.util.ArrayList<>();
        
        for (SourceFileAnalyzer.SourceModule module : currentModules) {
            System.out.println("\n--- Converting Module: " + module.name() + " ---");
            
            // Get proper target language name from registry
            String targetDisplay = module.targetLanguage();
            if (targetDisplay.toLowerCase().contains("react")) {
                targetDisplay = "ReactJS" + (targetDisplay.toLowerCase().contains("java") ? " + Spring Boot" : "");
            } else if (targetDisplay.toLowerCase().contains("java")) {
                targetDisplay = "Java Spring Boot";
            }
            
            System.out.println("  Source: " + module.sourceLanguage() + " → Target: " + targetDisplay);
            System.out.println("  Files: " + module.files().size() + " | Complexity: " + module.complexity());
            
            // Update context anchor with completed modules
            contextAnchor = new ChunkedConverter.ContextAnchor(
                contextAnchor.targetStack(),
                contextAnchor.namingConventions(),
                contextAnchor.establishedPatterns(),
                completedModules,
                java.time.LocalDateTime.now().toString()
            );
            
            // Build module context with dynamic conversion strategy
            ChunkedConverter.ModuleContext moduleContext = buildModuleContext(module, contextAnchor, patternLibrary);
            
            // Convert module
            ChunkedConverter.ConversionChunk chunk = chunkedConverter.convertModule(moduleContext, patternLibrary);
            
            // Phase 5: Validation
            ValidationLoop.VerificationChecklist checklist = validationLoop.generateChecklist(module.name(), module.sourceLanguage());
            ValidationLoop.ValidationReport report = validationLoop.validateModule(module.name(), targetDir);
            
            if (!report.passed()) {
                System.out.println("  Issues found: " + report.issues().size());
                for (ValidationLoop.ValidationIssue issue : report.issues()) {
                    if (issue.severity() == ValidationLoop.Severity.BLOCKING || 
                        issue.severity() == ValidationLoop.Severity.CRITICAL) {
                        System.out.println("  Applying surgical fix for: " + issue.issueDescription());
                        ValidationLoop.SurgicalFix fix = validationLoop.applySurgicalFix(
                            issue.filePath(), 
                            issue.issueDescription(), 
                            issue.suggestedFix()
                        );
                    }
                }
            }
            
            completedModules.add(module.name());
            System.out.println("  Module " + module.name() + " completed");
        }
        
        // Generate build files for target language
        System.out.println("\n=== Generating Build Files ===");
        try {
            buildFileGenerator.generateBuildFiles(targetDir, targetStack, List.of());
        } catch (Exception e) {
            System.err.println("  Warning: Could not generate build files: " + e.getMessage());
        }
        
        System.out.println("\n========================================");
        System.out.println("Migration Complete!");
        System.out.println("Modules converted: " + completedModules.size());
        System.out.println("Languages handled: " + currentInventory.filesByLanguage().size());
        System.out.println("========================================");
    }
    
    private void printInventory(SourceFileAnalyzer.CodebaseInventory inventory) {
        System.out.println("Project: " + inventory.projectName());
        System.out.println("Primary Language: " + inventory.primaryLanguage());
        System.out.println("Total Files: " + inventory.totalFiles());
        System.out.println("Convertible Files: " + inventory.convertibleFiles());
        System.out.println("\nFiles by Language:");
        inventory.filesByLanguage().forEach((lang, files) -> {
            System.out.println("  " + lang + ": " + files.size() + " files");
        });
        if (!inventory.unknownFiles().isEmpty()) {
            System.out.println("  Unknown: " + inventory.unknownFiles().size() + " files");
        }
    }
    
    private void printPatternLibrary(PatternExtractor.PatternLibrary library) {
        System.out.println("Patterns extracted: " + library.patternCount());
        for (Map.Entry<String, List<PatternExtractor.ExtractedPattern>> entry : library.patternsByType().entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue().size() + " patterns");
        }
    }
    
    private List<Path> selectRepresentativeFiles(List<SourceFileAnalyzer.SourceModule> modules) {
        List<Path> representatives = new java.util.ArrayList<>();
        
        // Select 1-2 representative files per module type
        for (SourceFileAnalyzer.SourceModule module : modules) {
            List<SourceFileAnalyzer.DiscoveredFile> files = module.files();
            int count = Math.min(files.size(), 2);
            for (int i = 0; i < count; i++) {
                representatives.add(files.get(i).absolutePath());
            }
        }
        
        return representatives;
    }
    
    private ChunkedConverter.ModuleContext buildModuleContext(SourceFileAnalyzer.SourceModule module,
                                                                 ChunkedConverter.ContextAnchor anchor,
                                                                 PatternExtractor.PatternLibrary library) {
        // Build dependency contracts from detected constructs
        List<ChunkedConverter.InterfaceContract> contracts = new java.util.ArrayList<>();
        
        for (SourceFileAnalyzer.DiscoveredFile file : module.files()) {
            // Extract class names as interface contracts
            List<String> classes = file.detectedConstructs().getOrDefault("class", List.of());
            for (String className : classes) {
                contracts.add(new ChunkedConverter.InterfaceContract(
                    className,
                    "class " + className,  // Simple signature
                    file.sourceLanguage() != null ? file.sourceLanguage() : "unknown",
                    List.of()
                ));
            }
        }
        
        // Get source file paths
        List<Path> sourceFiles = module.files().stream()
            .map(SourceFileAnalyzer.DiscoveredFile::absolutePath)
            .toList();
        
        return new ChunkedConverter.ModuleContext(
            module.name(),
            module.sourceLanguage(),
            sourceFiles,
            contracts,
            anchor,
            library
        );
    }
    
    private int calculateTokenSavings(List<SourceFileAnalyzer.SourceModule> modules) {
        // Compare structured approach vs naive full-codebase approach
        int naiveEstimate = modules.stream()
            .mapToInt(m -> m.files().size() * 500)  // ~500 tokens per file naive
            .sum();
        int structuredEstimate = modules.size() * 8000;  // ~8k per module structured
        return (int) ((1.0 - (double) structuredEstimate / naiveEstimate) * 100);
    }
}
