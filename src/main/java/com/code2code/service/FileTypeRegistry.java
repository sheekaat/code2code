package com.code2code.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Registry for source and target language mappings.
 * Extensible system for supporting any source → destination conversion.
 * Supports dynamic loading from configuration and runtime registration.
 */
public class FileTypeRegistry {
    
    private final Map<String, LanguageDefinition> sourceLanguages;
    private final Map<String, LanguageDefinition> targetLanguages;
    private final Map<String, ConversionStrategy> conversionStrategies;
    private final DynamicConfigurationLoader configLoader;
    
    public FileTypeRegistry() {
        this.sourceLanguages = new HashMap<>();
        this.targetLanguages = new HashMap<>();
        this.conversionStrategies = new HashMap<>();
        this.configLoader = new DynamicConfigurationLoader();
        
        // Load dynamically from configuration
        loadDynamicConfiguration();
    }
    
    /**
     * Loads languages and strategies from configuration files.
     */
    private void loadDynamicConfiguration() {
        System.out.println("Loading language configurations...");
        
        // Load language definitions
        List<LanguageDefinition> languages = configLoader.loadLanguageDefinitions();
        for (LanguageDefinition lang : languages) {
            registerSourceLanguage(lang);
        }
        
        // If no languages loaded from config, use defaults
        if (sourceLanguages.isEmpty()) {
            System.out.println("No config files found. Using built-in language definitions.");
            initializeDefaults();
        }
        
        // Load conversion strategies
        List<ConversionStrategy> strategies = configLoader.loadConversionStrategies();
        for (ConversionStrategy strategy : strategies) {
            registerConversionStrategy(strategy);
        }
        
        System.out.println("Loaded " + sourceLanguages.size() + " source languages");
        System.out.println("Loaded " + targetLanguages.size() + " target languages");
        System.out.println("Loaded " + conversionStrategies.size() + " conversion strategies");
    }
    
    /**
     * Re-loads configuration dynamically at runtime.
     */
    public void reloadConfiguration() {
        System.out.println("Reloading configuration...");
        sourceLanguages.clear();
        targetLanguages.clear();
        conversionStrategies.clear();
        loadDynamicConfiguration();
    }
    
    /**
     * Attempts to infer an unknown file's language using heuristics and AI.
     */
    public LanguageDefinition inferAndRegisterLanguage(Path file, GeminiApiClient geminiClient) {
        try {
            String content = java.nio.file.Files.readString(file);
            String fileName = file.getFileName().toString();
            
            // Try heuristic detection first
            Optional<LanguageDefinition> inferred = configLoader.inferLanguageFromContent(fileName, content);
            
            // If heuristics are inconclusive, use AI
            if (inferred.isEmpty()) {
                System.out.println("Heuristic detection failed for " + fileName + ", trying AI inference...");
                inferred = configLoader.aiInferLanguage(geminiClient, fileName, content);
            }
            
            if (inferred.isPresent()) {
                LanguageDefinition lang = inferred.get();
                // Register dynamically
                registerSourceLanguage(lang);
                System.out.println("Inferred and registered language: " + lang.name() + " for " + fileName);
                return lang;
            }
            
        } catch (IOException e) {
            System.err.println("Could not read file for language inference: " + file);
        }
        
        return null;
    }
    
    /**
     * Generates a conversion strategy on-the-fly for unknown source-target pairs.
     */
    public ConversionStrategy getOrCreateStrategy(String sourceId, String targetId, 
                                                     GeminiApiClient geminiClient) {
        String key = sourceId + "->" + targetId;
        ConversionStrategy strategy = conversionStrategies.get(key);
        
        if (strategy == null) {
            System.out.println("No strategy found for " + sourceId + " -> " + targetId + ", generating on-the-fly...");
            
            // Generate strategy using AI
            strategy = configLoader.generateStrategyOnTheFly(
                geminiClient, sourceId, targetId, getAllSourceLanguages()
            );
            
            // Register for future use
            registerConversionStrategy(strategy);
        }
        
        return strategy;
    }
    
    private void initializeDefaults() {
        // Source Language Definitions
        registerSourceLanguage(new LanguageDefinition(
            "csharp",
            "C#",
            List.of(".cs", ".cshtml", ".razor", ".aspx.cs"),
            List.of(".csproj", ".sln"),
            Map.of(
                "namespace", "namespace (?<name>[\\w.]+)",
                "class", "(public|internal|private|protected)?\\s*(static|abstract|sealed)?\\s*class\\s+(?<name>\\w+)",
                "interface", "(public|internal)?\\s*interface\\s+(?<name>\\w+)",
                "method", "(public|internal|private|protected)?\\s*(static|virtual|abstract|override)?\\s*(async\\s+)?(?<ret>[\\w<>,\\s]+)\\s+(?<name>\\w+)\\s*\\(",
                "property", "(public|internal|private|protected)?\\s*(?<type>[\\w<>,\\s]+)\\s+(?<name>\\w+)\\s*[{]"
            ),
            List.of("System", "Microsoft", "Newtonsoft")
        ));
        
        registerSourceLanguage(new LanguageDefinition(
            "vbnet",
            "VB.NET",
            List.of(".vb", ".vbhtml"),
            List.of(".vbproj", ".sln"),
            Map.of(
                "namespace", "Namespace\\s+(?<name>[\\w.]+)",
                "class", "(Public|Friend|Private)?\\s*(MustInherit|NotInheritable)?\\s*Class\\s+(?<name>\\w+)",
                "property", "(Public|Friend|Private)?\\s*(ReadOnly|WriteOnly)?\\s*Property\\s+(?<name>\\w+)"
            ),
            List.of("System", "Microsoft")
        ));
        
        registerSourceLanguage(new LanguageDefinition(
            "python",
            "Python",
            List.of(".py", ".pyw"),
            List.of("requirements.txt", "setup.py", "pyproject.toml"),
            Map.of(
                "class", "class\\s+(?<name>\\w+)\\s*(\\((?<base>[\\w,\\s]+)\\))?",
                "function", "def\\s+(?<name>\\w+)\\s*\\(",
                "import", "(import|from)\\s+(?<module>[\\w.]+)"
            ),
            List.of()
        ));
        
        registerSourceLanguage(new LanguageDefinition(
            "javascript",
            "JavaScript",
            List.of(".js", ".jsx", ".mjs"),
            List.of("package.json"),
            Map.of(
                "class", "class\\s+(?<name>\\w+)\\s*(extends\\s+(?<base>\\w+))?",
                "function", "(function|const|let|var)\\s*(?<name>\\w+)\\s*[=:]\\s*(async\\s*)?\\("
            ),
            List.of()
        ));
        
        registerSourceLanguage(new LanguageDefinition(
            "typescript",
            "TypeScript",
            List.of(".ts", ".tsx"),
            List.of("tsconfig.json", "package.json"),
            Map.of(
                "class", "class\\s+(?<name>\\w+)\\s*(extends\\s+(?<base>\\w+))?",
                "interface", "interface\\s+(?<name>\\w+)",
                "function", "(function|const|let)\\s*(?<name>\\w+)\\s*[=:]\\s*(async\\s*)?\\("
            ),
            List.of()
        ));
        
        registerSourceLanguage(new LanguageDefinition(
            "go",
            "Go",
            List.of(".go"),
            List.of("go.mod", "go.sum"),
            Map.of(
                "package", "package\\s+(?<name>\\w+)",
                "struct", "type\\s+(?<name>\\w+)\\s+struct",
                "function", "func\\s+((?<recv>\\([^)]+\\))\\s+)?(?<name>\\w+)\\s*\\("
            ),
            List.of()
        ));
        
        // VB6 Language Definition
        registerSourceLanguage(new LanguageDefinition(
            "vb6",
            "Visual Basic 6",
            List.of(".frm", ".bas", ".cls", ".ctl", ".vbp"),
            List.of(".vbp", ".vbproj"),
            Map.of(
                "class", "(?i)\\b(class|form|module)\\s+(?<name>\\w+)",
                "method", "(?i)\\b(sub|function|property)\\s+(?<name>\\w+)\\s*\\(",
                "variable", "(?i)\\b(dim|public|private)\\s+(?<name>\\w+)\\s+as",
                "event", "(?i)\\b(event|raiseevent)\\s+(?<name>\\w+)"
            ),
            List.of("VB", "MSVBVM60", "ADODB", "Scripting")
        ));
        
        // Tibco BW Language Definition
        registerSourceLanguage(new LanguageDefinition(
            "tibcobw",
            "Tibco BusinessWorks",
            List.of(".process", ".bw", ".xsd", ".wsdl", ".substvar", ".module", ".application"),
            List.of(".project", ".application", "META-INF/MANIFEST.MF"),
            Map.of(
                "process", "(?i)<\\w+:process[^>]*name=\"(?<name>[^\"]+)\"",
                "activity", "(?i)<\\w+:(?<type>\\w+)[^>]*name=\"(?<name>[^\"]+)\"",
                "transition", "(?i)<\\w+:transition[^>]*from=\"(?<from>[^\"]+)\"[^>]*to=\"(?<to>[^\"]+)\"",
                "inputBinding", "(?i)<\\w+:inputBinding",
                "outputBinding", "(?i)<\\w+:outputBinding",
                "jmsActivity", "(?i)<\\w+:(jms|ems).*[^>]*name=\"(?<name>[^\"]+)\"",
                "jdbcActivity", "(?i)<\\w+:(jdbc|sql).*[^>]*name=\"(?<name>[^\"]+)\""
            ),
            List.of("com.tibco", "com.tibco.bw", "java.util", "javax.jms", "javax.sql")
        ));
        
        // Target Language Definitions
        registerTargetLanguage(new LanguageDefinition(
            "java",
            "Java",
            List.of(".java"),
            List.of("pom.xml", "build.gradle"),
            Map.of(
                "package", "package\\s+(?<name>[\\w.]+);",
                "class", "(public|private|protected)?\\s*(abstract|final|static)?\\s*class\\s+(?<name>\\w+)",
                "interface", "(public)?\\s*interface\\s+(?<name>\\w+)",
                "method", "(public|private|protected)?\\s*(static|abstract|final|synchronized)?\\s*(?<ret>[\\w<>,\\s\\[\\]]+)\\s+(?<name>\\w+)\\s*\\("
            ),
            List.of("java", "javax", "org.springframework", "lombok")
        ));
        
        registerTargetLanguage(new LanguageDefinition(
            "kotlin",
            "Kotlin",
            List.of(".kt", ".kts"),
            List.of("pom.xml", "build.gradle.kts"),
            Map.of(
                "package", "package\\s+(?<name>[\\w.]+)",
                "class", "(data\\s+)?class\\s+(?<name>\\w+)",
                "function", "fun\\s+(?<name>\\w+)\\s*\\("
            ),
            List.of()
        ));
        
        // ReactJS Target Language
        registerTargetLanguage(new LanguageDefinition(
            "react",
            "ReactJS",
            List.of(".tsx", ".jsx", ".ts", ".js"),
            List.of("package.json", "tsconfig.json", "vite.config.ts", "webpack.config.js"),
            Map.of(
                "component", "(export\\s+)?(default\\s+)?function\\s+(?<name>\\w+)\\s*\\(",
                "classComponent", "class\\s+(?<name>\\w+)\\s+extends\\s+(React\\.)?Component",
                "hook", "const\\s+\\[(?<state>[^\\]]+)\\]\\s*=\\s*useState",
                "effect", "useEffect\\s*\\(",
                "props", "interface\\s+(?<name>\\w+)Props",
                "jsxElement", "<(?<tag>[A-Z][a-zA-Z0-9]*)[^>]*>"
            ),
            List.of("react", "react-dom", "react-router", "axios", "@mui/material")
        ));
        
        // Conversion Strategies
        registerConversionStrategy(new ConversionStrategy(
            "csharp",
            "java",
            "Convert C# .NET code to Java Spring Boot:\n" +
            "- Namespaces → packages (com.company.product.module)\n" +
            "- Properties → getters/setters or Lombok @Data\n" +
            "- LINQ → Java Streams\n" +
            "- async/await → CompletableFuture\n" +
            "- Entity Framework → Spring Data JPA\n" +
            "- Nullable<T> → Optional<T>\n" +
            "- out/ref parameters → return wrapper objects\n" +
            "- Extension methods → utility classes\n" +
            "- Events → Observer pattern or Spring Events\n" +
            "- Attributes → Annotations\n" +
            "- IEnumerable<T> → List<T> or Stream<T>",
            Map.of(
                "string", "String",
                "int", "int",
                "long", "long",
                "bool", "boolean",
                "DateTime", "java.time.LocalDateTime",
                "decimal", "BigDecimal",
                "List<T>", "List<T>",
                "Dictionary<K,V>", "Map<K,V>",
                "Nullable<T>", "Optional<T>"
            )
        ));
        
        // Tibco BW to Java Spring Boot Conversion Strategy
        registerConversionStrategy(new ConversionStrategy(
            "tibcobw",
            "java",
            "Convert Tibco BusinessWorks to Java Spring Boot:\n\n" +
            "PROCESS MAPPINGS:\n" +
            "- BW Process → Spring @Service class (business logic)\n" +
            "- BW Process with HTTP → Spring @RestController\n" +
            "- BW Process with JMS → Spring JMS Listener (@JmsListener)\n" +
            "- Sub-process → Private methods or separate @Component\n\n" +
            "ACTIVITY MAPPINGS:\n" +
            "- JDBC Query/Update → Spring Data JPA Repository or JdbcTemplate\n" +
            "- JMS Queue/Topic → JmsTemplate or @JmsListener\n" +
            "- HTTP Client → RestTemplate or WebClient\n" +
            "- Parse XML → JAXB or Jackson XML mapper\n" +
            "- Parse JSON → Jackson ObjectMapper\n" +
            "- Mapper activity → MapStruct or manual DTO mapping\n" +
            "- Invoke BW Process → Method calls or internal REST APIs\n" +
            "- Timer → Spring @Scheduled\n" +
            "- Wait/Sleep → CompletableFuture.delayedExecutor or Thread.sleep\n" +
            "- Transaction → Spring @Transactional\n" +
            "- Try-Catch scope → Java try-catch with Spring exception handling\n\n" +
            "DATA MAPPINGS:\n" +
            "- XSD Schema → Java classes with JAXB annotations\n" +
            "- WSDL → Java interfaces with @WebService\n" +
            "- Shared Variables → Spring @Value or @ConfigurationProperties\n" +
            "- Process Variables → Method parameters or ThreadLocal\n" +
            "- Substitution variables → application.properties\n\n" +
            "INTEGRATION PATTERNS:\n" +
            "- EMS JMS → Spring JMS with connection factory\n" +
            "- JDBC → Spring DataSource with connection pooling\n" +
            "- HTTP/SOAP → Spring Web Services or REST controllers\n" +
            "- File/FTP → Spring Integration File/FTP adapters\n\n" +
            "ERROR HANDLING:\n" +
            "- Catch scope → Java catch blocks\n" +
            "- Error transition → Exception throwing and handling\n" +
            "- Log activity → SLF4J/Logback logging\n" +
            "- Email on error → Spring Mail with @ExceptionHandler",
            Map.ofEntries(
                Map.entry("Process", "@Service or @RestController"),
                Map.entry("Activity", "Method or @Component"),
                Map.entry("xsd:string", "String"),
                Map.entry("xsd:int", "int"),
                Map.entry("xsd:boolean", "boolean"),
                Map.entry("xsd:dateTime", "java.time.LocalDateTime"),
                Map.entry("xsd:decimal", "BigDecimal"),
                Map.entry("SharedVariable", "@Value or ThreadLocal"),
                Map.entry("JMSQueue", "JmsTemplate"),
                Map.entry("JDBConnection", "DataSource"),
                Map.entry("HTTP", "RestTemplate or WebClient"),
                Map.entry("XMLElement", "JAXBElement or Jackson JsonNode"),
                Map.entry("anyType", "Object")
            )
        ));
        
        registerConversionStrategy(new ConversionStrategy(
            "python",
            "java",
            "Convert Python code to Java:\n" +
            "- Modules → packages\n" +
            "- Classes with __init__ → constructors\n" +
            "- list/dict → List/Map\n" +
            "- list comprehensions → streams\n" +
            "- decorators → annotations or aspect patterns\n" +
            "- duck typing → interfaces",
            Map.of(
                "list", "List",
                "dict", "Map",
                "str", "String",
                "int", "int",
                "None", "null"
            )
        ));
        
        registerConversionStrategy(new ConversionStrategy(
            "typescript",
            "java",
            "Convert TypeScript to Java:\n" +
            "- Interfaces → interfaces (preserve structure)\n" +
            "- Optional properties → Optional or nullable wrappers\n" +
            "- Union types → sealed classes or inheritance\n" +
            "- Type guards → instanceof checks\n" +
            "- Arrow functions → lambda expressions or method references",
            Map.of(
                "string", "String",
                "number", "int",
                "boolean", "boolean",
                "any", "Object",
                "undefined", "Optional.empty()",
                "null", "null"
            )
        ));
        
        registerConversionStrategy(new ConversionStrategy(
            "vb6",
            "java",
            "Convert VB6 to Java Spring Boot:\n" +
            "- Forms → Spring MVC @Controller classes or JavaFX/Swing UI\n" +
            "- Modules → Utility classes with static methods\n" +
            "- Classes → POJOs or Services\n" +
            "- Sub/Function → methods (void/return type)\n" +
            "- Dim/Public/Private → fields with access modifiers\n" +
            "- Events → Observer pattern or Spring Events\n" +
            "- Recordsets → JPA repositories or JDBC\n" +
            "- ADO → Spring Data JPA\n" +
            "- Variant → Object or specific wrapper\n" +
            "- On Error → try-catch blocks\n" +
            "- Option Explicit removed (Java is always explicit)",
            Map.of(
                "Integer", "int",
                "Long", "long",
                "String", "String",
                "Boolean", "boolean",
                "Date", "java.time.LocalDateTime",
                "Currency", "BigDecimal",
                "Variant", "Object",
                "Object", "Object",
                "Form", "@Controller",
                "Recordset", "List<Entity>"
            )
        ));
        
        // VB6 to ReactJS + Spring Boot (Dual Target - Frontend + Backend)
        registerConversionStrategy(new ConversionStrategy(
            "vb6",
            "react-java",
            "Split VB6 into ReactJS Frontend + Spring Boot Backend:\n\n" +
            "FRONTEND (React TypeScript):\n" +
            "- VB6 Forms → React functional components (.tsx)\n" +
            "- Form controls → JSX with Material-UI or Ant Design\n" +
            "- VB6 events (Click, Change) → React event handlers (onClick, onChange)\n" +
            "- Form_Load → useEffect with empty dependency []\n" +
            "- Timer controls → setInterval/setTimeout in useEffect\n" +
            "- DataGrid/ListView → React Table or DataGrid components\n" +
            "- Command buttons → Material-UI Button components\n" +
            "- TextBox → TextField with state binding\n" +
            "- ComboBox → Select with options\n" +
            "- CheckBox → Checkbox component\n" +
            "- Menu → AppBar with navigation\n" +
            "- StatusBar → Snackbar for notifications\n" +
            "- Use axios for HTTP calls to backend\n" +
            "- Use React Router for navigation\n\n" +
            "BACKEND (Spring Boot Java):\n" +
            "- VB6 Modules → Spring @Service classes\n" +
            "- VB6 Classes → Spring @Component or @Entity\n" +
            "- Recordset operations → Spring Data JPA repositories\n" +
            "- File I/O → Spring Resource handling or java.nio\n" +
            "- ADO connections → JPA EntityManager or JDBC\n" +
            "- SQL queries → JPA Query Methods or @Query\n" +
            "- Expose REST APIs with @RestController\n" +
            "- Use DTOs for request/response objects\n" +
            "- Apply CORS for frontend access\n\n" +
            "API CONTRACT:\n" +
            "- Define shared DTOs between frontend and backend\n" +
            "- Use JSON for data exchange\n" +
            "- Version APIs (v1, v2)\n" +
            "- Document with OpenAPI/Swagger",
            Map.of(
                "VB Form", "React Component + Spring Controller",
                "VB Control", "React JSX Element",
                "VB Event", "React Event Handler",
                "VB Module", "Spring @Service",
                "VB Class", "Spring @Entity or @Component",
                "ADODB.Recordset", "JPA Repository + React State",
                "Integer", "number (TS) / int (Java)",
                "String", "string (TS) / String (Java)",
                "Boolean", "boolean (both)",
                "Variant", "any (TS) / Object (Java)"
            )
        ));
    }
    
    public void registerSourceLanguage(LanguageDefinition lang) {
        sourceLanguages.put(lang.id(), lang);
    }
    
    public void registerTargetLanguage(LanguageDefinition lang) {
        targetLanguages.put(lang.id(), lang);
    }
    
    public void registerConversionStrategy(ConversionStrategy strategy) {
        String key = strategy.sourceId() + "->" + strategy.targetId();
        conversionStrategies.put(key, strategy);
    }
    
    public LanguageDefinition detectSourceLanguage(java.nio.file.Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        
        for (LanguageDefinition lang : sourceLanguages.values()) {
            // Check file extensions
            for (String ext : lang.fileExtensions()) {
                if (fileName.endsWith(ext)) {
                    return lang;
                }
            }
            // Check project files
            for (String projFile : lang.projectFiles()) {
                if (fileName.equalsIgnoreCase(projFile)) {
                    return lang;
                }
            }
        }
        
        return null;
    }
    
    public String detectProjectSourceLanguage(java.nio.file.Path projectDir) {
        Map<String, Integer> langScores = new HashMap<>();
        
        try (var stream = java.nio.file.Files.walk(projectDir)) {
            stream.filter(Files::isRegularFile)
                  .forEach(file -> {
                      LanguageDefinition lang = detectSourceLanguage(file);
                      if (lang != null) {
                          langScores.merge(lang.id(), 1, Integer::sum);
                      }
                  });
        } catch (java.io.IOException e) {
            // Ignore
        }
        
        return langScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");
    }
    
    public ConversionStrategy getConversionStrategy(String sourceId, String targetId) {
        String key = sourceId + "->" + targetId;
        ConversionStrategy strategy = conversionStrategies.get(key);
        
        if (strategy == null) {
            // Fallback to generic strategy
            return new ConversionStrategy(
                sourceId,
                targetId,
                "Convert " + sourceId + " code to " + targetId + ".\n" +
                "Apply standard conversion patterns and best practices.",
                Map.of()
            );
        }
        
        return strategy;
    }
    
    public List<LanguageDefinition> getAllSourceLanguages() {
        return List.copyOf(sourceLanguages.values());
    }
    
    public List<LanguageDefinition> getAllTargetLanguages() {
        return List.copyOf(targetLanguages.values());
    }
    
    public LanguageDefinition getSourceLanguage(String id) {
        return sourceLanguages.get(id);
    }
    
    public LanguageDefinition getTargetLanguage(String id) {
        return targetLanguages.get(id);
    }
    
    // Record definitions
    public record LanguageDefinition(
        String id,
        String name,
        List<String> fileExtensions,
        List<String> projectFiles,
        Map<String, String> constructPatterns,  // regex patterns for detecting constructs
        List<String> commonLibraries
    ) {}
    
    public record ConversionStrategy(
        String sourceId,
        String targetId,
        String conversionRules,
        Map<String, String> typeMappings
    ) {}
}
