package com.code2code.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.code2code.service.converter.FormToReactConverter;
import java.util.stream.Collectors;

/**
 * Phase 3 Implementation: Chunked Conversion with Context Anchoring
 * Converts one module per conversation turn with compact context propagation.
 */
public class ChunkedConverterImpl implements ChunkedConverter {
    
    private final GeminiApiClient geminiClient;
    private final JavaCodePostProcessor postProcessor;
    private final FormToReactConverter structuredConverter;
    private boolean useWinFormsIntermediate = false;
    
    public ChunkedConverterImpl(GeminiApiClient geminiClient) {
        this.geminiClient = geminiClient;
        this.postProcessor = new JavaCodePostProcessor();
        this.structuredConverter = new FormToReactConverter();
    }
    
    /**
     * Enable WinForms intermediate step for VB6 form conversions.
     * When enabled, VB6 forms are converted to C# WinForms first, then to React.
     */
    public void setUseWinFormsIntermediate(boolean useIntermediate) {
        this.useWinFormsIntermediate = useIntermediate;
    }
    
    @Override
    public ConversionChunk convertModule(ModuleContext context, PatternExtractor.PatternLibrary library) {
        System.out.println("    Converting module: " + context.moduleName());
        
        List<ConvertedFile> convertedFiles = new ArrayList<>();
        List<String> exportedTypes = new ArrayList<>();
        List<String> exposedApis = new ArrayList<>();
        int tokensUsed = 0;
        boolean successful = true;
        
        // Convert each file in the module
        for (Path sourceFile : context.sourceFiles()) {
            try {
                // Skip project files (.vbp) - they don't contain actual code
                String fileNameLower = sourceFile.getFileName().toString().toLowerCase();
                if (fileNameLower.endsWith(".vbp") || fileNameLower.endsWith(".vbproj")) {
                    System.out.println("      Skipping project file: " + sourceFile.getFileName());
                    continue;
                }
                
                String sourceCode = readFileWithEncoding(sourceFile);
                String fileType = classifyFileType(sourceFile.getFileName().toString());
                
                // Get pattern template for this file type
                PatternExtractor.ConversionTemplate template = library.templates().get(fileType);
                
                // Build conversion prompt with context anchor
                String prompt = buildConversionPrompt(context, sourceCode, fileType, template, sourceFile);
                
                // Convert using Gemini with iterative correction for React files
                // OR use structured converter for WinForms intermediate step
                boolean isReactFile = context.anchor().targetStack().toLowerCase().contains("react") &&
                                     sourceFile.getFileName().toString().toLowerCase().endsWith(".frm");
                String convertedCode;
                
                if (isReactFile && useWinFormsIntermediate) {
                    // Use structured converter: VB6 -> FormSpecification -> React
                    System.out.println("      Using structured converter (VB6 -> WinForms spec -> React)");
                    try {
                        convertedCode = structuredConverter.convert(sourceFile, "react");
                        System.out.println("      DEBUG: Generated React code length: " + convertedCode.length());
                        System.out.println("      DEBUG: Last 100 chars: " + convertedCode.substring(Math.max(0, convertedCode.length() - 100)));
                        // Also generate WinForms intermediate for reference
                        String winFormsCode = structuredConverter.convert(sourceFile, "winforms");
                        Path winFormsPath = Paths.get("conversion/to/winforms", 
                            sourceFile.getFileName().toString().replace(".frm", ".Designer.cs"));
                        Files.createDirectories(winFormsPath.getParent());
                        Files.writeString(winFormsPath, winFormsCode);
                        System.out.println("      Generated WinForms intermediate: " + winFormsPath);
                    } catch (com.code2code.service.converter.ConversionException e) {
                        System.err.println("      Structured conversion failed, falling back to LLM: " + e.getMessage());
                        convertedCode = convertWithIterativeCorrection(context, prompt, sourceCode, sourceFile);
                    }
                } else if (isReactFile) {
                    convertedCode = convertWithIterativeCorrection(context, prompt, sourceCode, sourceFile);
                } else {
                    convertedCode = geminiClient.code2code(prompt, sourceCode);
                }
                tokensUsed += estimateTokens(sourceCode, convertedCode);
                
                // Intelligent post-processing: detect and split multiple public classes
                Path baseTargetPath = determineTargetPath(sourceFile, context.moduleName(), context.anchor().targetStack());
                List<JavaCodePostProcessor.ProcessedFile> processedFiles = postProcessor.processConvertedCode(
                    convertedCode, 
                    sourceFile.getFileName().toString(),
                    baseTargetPath.getParent(),
                    context.anchor().targetStack()
                );
                
                // If post-processor returned empty list, conversion had critical errors
                if (processedFiles.isEmpty()) {
                    System.err.println("      CRITICAL: File conversion failed due to syntax errors: " + sourceFile);
                    successful = false;
                    continue;  // Skip this file
                }
                
                // Validate and write each processed file
                for (JavaCodePostProcessor.ProcessedFile processed : processedFiles) {
                    // Validate syntax (should already be clean, but double-check)
                    List<String> validationIssues = postProcessor.validateSyntax(processed.content());
                    if (!validationIssues.isEmpty()) {
                        System.out.println("      Validation warnings for " + processed.fileName() + ":");
                        for (String issue : validationIssues) {
                            System.out.println("        - " + issue);
                        }
                    }
                    
                    // Write the file
                    Path targetPath = baseTargetPath.getParent().resolve(processed.fileName());
                    Files.createDirectories(targetPath.getParent());
                    Files.writeString(targetPath, processed.content());
                    
                    // Track exports and APIs
                    exportedTypes.addAll(processed.publicTypes());
                    exposedApis.addAll(extractExposedApis(processed.content()));
                    
                    convertedFiles.add(new ConvertedFile(
                        sourceFile.toString(),
                        targetPath.toString(),
                        fileType,
                        countLines(sourceCode),
                        countLines(processed.content()),
                        extractImports(processed.content()),
                        extractDependencies(processed.content())
                    ));
                }
                
            } catch (IOException e) {
                System.err.println("      Error converting file: " + sourceFile + " - " + e.getMessage());
                successful = false;
            }
        }
        
        return new ConversionChunk(
            context.moduleName(),
            context.moduleType(),
            convertedFiles,
            exportedTypes,
            exposedApis,
            tokensUsed,
            successful
        );
    }
    
    @Override
    public ModuleSummary createSummary(ConversionChunk chunk) {
        String status = chunk.successful() ? "converted" : 
                       (chunk.files().isEmpty() ? "failed" : "partial");
        
        List<String> knownIssues = new ArrayList<>();
        if (!chunk.successful()) {
            knownIssues.add("Some files failed to convert");
        }
        
        return new ModuleSummary(
            chunk.moduleName(),
            status,
            chunk.exportedTypes(),
            chunk.exposedApis(),
            knownIssues
        );
    }
    
    @Override
    public ContextAnchor buildContextAnchor(String targetStack, List<String> namingConventions,
                                           Map<String, String> establishedPatterns) {
        return new ContextAnchor(
            targetStack,
            namingConventions,
            establishedPatterns,
            new ArrayList<>(), // completed modules - filled during conversion
            java.time.LocalDateTime.now().toString()
        );
    }
    
    private String buildConversionPrompt(ModuleContext context, String sourceCode, 
                                         String fileType, PatternExtractor.ConversionTemplate template, 
                                         Path currentSourceFile) {
        StringBuilder prompt = new StringBuilder();
        
        // Context anchor (small, sent with every chunk)
        ContextAnchor anchor = context.anchor();
        String targetStack = anchor.targetStack().toLowerCase();
        
        prompt.append("=== Context Anchor ===\n");
        prompt.append("Target Stack: ").append(anchor.targetStack()).append("\n");
        prompt.append("Naming: ").append(String.join(", ", anchor.namingConventions())).append("\n");
        prompt.append("Patterns: ").append(anchor.establishedPatterns()).append("\n");
        prompt.append("Completed: ").append(anchor.completedModules()).append("\n\n");
        
        // Determine what to generate based on file type and target stack
        String sourceFileName = currentSourceFile.getFileName().toString().toLowerCase();
        boolean isFrmFile = sourceFileName.endsWith(".frm");
        boolean isReactTarget = targetStack.contains("react");
        boolean isDualTarget = isReactTarget && targetStack.contains("spring") || targetStack.contains("java");
        
        // Specific instructions for dual-target (React + Spring Boot)
        if (isDualTarget && isFrmFile) {
            prompt.append("=== CONVERSION TYPE: REACT FRONTEND COMPONENT ===\n\n");
            prompt.append("This VB6 Form file (.frm) MUST be converted to a React TypeScript component (.tsx).\n");
            prompt.append("DO NOT generate Java code for this file.\n\n");
            
            prompt.append("=== REACT COMPONENT STRUCTURE TEMPLATE ===\n");
            prompt.append("Follow this exact structure:\n\n");
            prompt.append("import React, { useState, useEffect } from 'react';\n");
            prompt.append("import { Box, Button, Typography, TextField, ... } from '@mui/material';\n");
            prompt.append("import axios from 'axios';\n\n");
            prompt.append("interface ComponentNameProps {\n");
            prompt.append("  // props here\n");
            prompt.append("}\n\n");
            prompt.append("interface ComponentNameState {\n");
            prompt.append("  // state properties here\n");
            prompt.append("}\n\n");
            prompt.append("const ComponentName: React.FC<ComponentNameProps> = (props) => {\n");
            prompt.append("  const [state, setState] = useState<ComponentNameState>({...});\n\n");
            prompt.append("  useEffect(() => {\n");
            prompt.append("    // initialization logic from Form_Load\n");
            prompt.append("  }, []);\n\n");
            prompt.append("  // Event handlers\n");
            prompt.append("  const handleClick = () => { ... };\n\n");
            prompt.append("  return (\n");
            prompt.append("    <Box>\n");
            prompt.append("      {/* JSX content matching VB6 form layout */}\n");
            prompt.append("    </Box>\n");
            prompt.append("  );\n");
            prompt.append("};\n\n");
            prompt.append("export default ComponentName;\n\n");
            
            prompt.append("=== COMPLETE VB6 TO REACT EXAMPLE ===\n");
            prompt.append("Here is a real working example of converting VB6 to React:\n\n");
            
            prompt.append("VB6 Source (.frm file):\n");
            prompt.append("```vb\n");
            prompt.append("VERSION 5.00\n");
            prompt.append("Begin VB.Form frmLogin\n");
            prompt.append("   Caption = \"Login Form\"\n");
            prompt.append("   ClientHeight = 3135\n");
            prompt.append("   ClientWidth = 4680\n");
            prompt.append("   Begin VB.TextBox txtUsername\n");
            prompt.append("      Height = 375\n");
            prompt.append("      Left = 1800\n");
            prompt.append("      Width = 2655\n");
            prompt.append("   End\n");
            prompt.append("   Begin VB.CommandButton cmdLogin\n");
            prompt.append("      Caption = \"Login\"\n");
            prompt.append("      Height = 495\n");
            prompt.append("      Left = 1800\n");
            prompt.append("      Width = 1215\n");
            prompt.append("   End\n");
            prompt.append("   Begin VB.Label lblUsername\n");
            prompt.append("      Caption = \"Username:\"\n");
            prompt.append("      Height = 255\n");
            prompt.append("      Left = 240\n");
            prompt.append("      Width = 1215\n");
            prompt.append("   End\n");
            prompt.append("End\n");
            prompt.append("```\n\n");
            
            prompt.append("Converted React TypeScript Component:\n");
            prompt.append("```typescript\n");
            prompt.append("import React, { useState, useEffect } from 'react';\n");
            prompt.append("import { Box, Button, Typography, TextField } from '@mui/material';\n");
            prompt.append("import axios from 'axios';\n\n");
            
            prompt.append("interface FrmLoginProps {\n");
            prompt.append("  onLoginSuccess?: () => void;\n");
            prompt.append("}\n\n");
            
            prompt.append("interface FrmLoginState {\n");
            prompt.append("  username: string;\n");
            prompt.append("  isLoading: boolean;\n");
            prompt.append("}\n\n");
            
            prompt.append("const FrmLogin: React.FC<FrmLoginProps> = ({ onLoginSuccess }) => {\n");
            prompt.append("  const [state, setState] = useState<FrmLoginState>({\n");
            prompt.append("    username: '',\n");
            prompt.append("    isLoading: false\n");
            prompt.append("  });\n\n");
            
            prompt.append("  // Equivalent to VB6 Form_Load\n");
            prompt.append("  useEffect(() => {\n");
            prompt.append("    // Initialize form - equivalent to Form_Load\n");
            prompt.append("    console.log('Login form initialized');\n");
            prompt.append("  }, []);\n\n");
            
            prompt.append("  // Converted from cmdLogin_Click event\n");
            prompt.append("  const handleLogin = async () => {\n");
            prompt.append("    setState(prev => ({ ...prev, isLoading: true }));\n");
            prompt.append("    try {\n");
            prompt.append("      // Make API call using axios\n");
            prompt.append("      const response = await axios.post('/api/login', {\n");
            prompt.append("        username: state.username\n");
            prompt.append("      });\n");
            prompt.append("      if (response.data.success) {\n");
            prompt.append("        onLoginSuccess?.();\n");
            prompt.append("      }\n");
            prompt.append("    } catch (error) {\n");
            prompt.append("      console.error('Login failed:', error);\n");
            prompt.append("    } finally {\n");
            prompt.append("      setState(prev => ({ ...prev, isLoading: false }));\n");
            prompt.append("    }\n");
            prompt.append("  };\n\n");
            
            prompt.append("  // Converted from txtUsername_Change event\n");
            prompt.append("  const handleUsernameChange = (event: React.ChangeEvent<HTMLInputElement>) => {\n");
            prompt.append("    setState(prev => ({ ...prev, username: event.target.value }));\n");
            prompt.append("  };\n\n");
            
            prompt.append("  // Helper to convert VB6 twips to pixels\n");
            prompt.append("  const twipsToPx = (twips: number): number => Math.round(twips / 15);\n\n");
            
            prompt.append("  return (\n");
            prompt.append("    <Box\n");
            prompt.append("      sx={{\n");
            prompt.append("        width: twipsToPx(4680),\n");
            prompt.append("        height: twipsToPx(3135),\n");
            prompt.append("        padding: 2,\n");
            prompt.append("        border: '1px solid #ccc',\n");
            prompt.append("        borderRadius: 1,\n");
            prompt.append("        backgroundColor: '#f5f5f5',\n");
            prompt.append("        position: 'relative'\n");
            prompt.append("      }}\n");
            prompt.append("    >\n");
            prompt.append("      {/* Label - converted from VB6 lblUsername */}\n");
            prompt.append("      <Typography\n");
            prompt.append("        variant=\"body1\"\n");
            prompt.append("        sx={{\n");
            prompt.append("          position: 'absolute',\n");
            prompt.append("          left: twipsToPx(240),\n");
            prompt.append("          top: twipsToPx(200),\n");
            prompt.append("          height: twipsToPx(255)\n");
            prompt.append("        }}\n");
            prompt.append("      >\n");
            prompt.append("        Username:\n");
            prompt.append("      </Typography>\n\n");
            
            prompt.append("      {/* TextField - converted from VB6 txtUsername */}\n");
            prompt.append("      <TextField\n");
            prompt.append("        value={state.username}\n");
            prompt.append("        onChange={handleUsernameChange}\n");
            prompt.append("        size=\"small\"\n");
            prompt.append("        sx={{\n");
            prompt.append("          position: 'absolute',\n");
            prompt.append("          left: twipsToPx(1800),\n");
            prompt.append("          top: twipsToPx(200),\n");
            prompt.append("          width: twipsToPx(2655)\n");
            prompt.append("        }}\n");
            prompt.append("      />\n\n");
            
            prompt.append("      {/* Button - converted from VB6 cmdLogin */}\n");
            prompt.append("      <Button\n");
            prompt.append("        variant=\"contained\"\n");
            prompt.append("        onClick={handleLogin}\n");
            prompt.append("        disabled={state.isLoading}\n");
            prompt.append("        sx={{\n");
            prompt.append("          position: 'absolute',\n");
            prompt.append("          left: twipsToPx(1800),\n");
            prompt.append("          top: twipsToPx(1000),\n");
            prompt.append("          width: twipsToPx(1215),\n");
            prompt.append("          height: twipsToPx(495)\n");
            prompt.append("        }}\n");
            prompt.append("      >\n");
            prompt.append("        {state.isLoading ? 'Loading...' : 'Login'}\n");
            prompt.append("      </Button>\n");
            prompt.append("    </Box>\n");
            prompt.append("  );\n");
            prompt.append("};\n\n");
            prompt.append("export default FrmLogin;\n");
            prompt.append("```\n\n");
            
            prompt.append("=== KEY LESSONS FROM THE EXAMPLE ===\n");
            prompt.append("1. Use absolute positioning in sx prop to match VB6 Left/Top coordinates\n");
            prompt.append("2. Convert VB6 twips to pixels using: px = twips / 15\n");
            prompt.append("3. Map VB6 events to React handlers:\n");
            prompt.append("   - CommandButton Click → Button onClick\n");
            prompt.append("   - TextBox Change → TextField onChange\n");
            prompt.append("   - Form Load → useEffect with empty dependency array []\n");
            prompt.append("4. Use useState for all VB6 control properties and form-level variables\n");
            prompt.append("5. Wrap component in Box with 'position: relative' for absolute child positioning\n");
            prompt.append("6. ALL opening braces MUST have closing braces - count them carefully!\n\n");
            
            prompt.append("=== CONVERSION REQUIREMENTS ===\n");
            prompt.append("1. Use React functional component with TypeScript (NOT class component)\n");
            prompt.append("2. Map VB6 controls to MUI components:\n");
            prompt.append("   - CommandButton → Button\n");
            prompt.append("   - TextBox → TextField\n");
            prompt.append("   - Label → Typography\n");
            prompt.append("   - Frame/Panel → Box with sx props\n");
            prompt.append("   - ComboBox → Select\n");
            prompt.append("   - CheckBox → Checkbox\n");
            prompt.append("   - Timer → useEffect with setInterval\n");
            prompt.append("3. Convert VB6 events to React handlers:\n");
            prompt.append("   - Click → onClick\n");
            prompt.append("   - Change → onChange\n");
            prompt.append("   - Form_Load → useEffect(() => {...}, [])\n");
            prompt.append("4. Use axios for backend API calls\n");
            prompt.append("5. ALL opening braces '{', brackets '[', parentheses '(' MUST have matching closing counterparts\n");
            prompt.append("6. Validate JSX syntax: every opening tag <Tag> must have closing </Tag> or be self-closing <Tag />\n\n");
            
            prompt.append("=== SYNTAX VALIDATION CHECKLIST ===\n");
            prompt.append("Before returning the code, verify:\n");
            prompt.append("[ ] All braces {} are balanced\n");
            prompt.append("[ ] All parentheses () are balanced\n");
            prompt.append("[ ] All brackets [] are balanced\n");
            prompt.append("[ ] All JSX tags are properly closed\n");
            prompt.append("[ ] No Java imports (import javax.*, import org.springframework.*)\n");
            prompt.append("[ ] No Java annotations (@SpringBootApplication, @Service, etc.)\n");
            prompt.append("[ ] Component is exported as default\n\n");
        } else if (isDualTarget && (sourceFileName.endsWith(".bas") || sourceFileName.endsWith(".cls"))) {
            prompt.append("=== CONVERSION TYPE: SPRING BOOT BACKEND SERVICE ===\n\n");
            prompt.append("This VB6 Module/Class file MUST be converted to a Spring Boot Java service.\n");
            prompt.append("DO NOT generate React code for this file.\n\n");
            prompt.append("Requirements:\n");
            prompt.append("1. Generate a Spring Boot Java class\n");
            prompt.append("2. Use @Service, @Component, or @Entity annotations as appropriate\n");
            prompt.append("3. Convert VB6 business logic to Java methods\n");
            prompt.append("4. Use Spring Data JPA for database operations\n");
            prompt.append("5. Expose REST endpoints with @RestController if needed\n\n");
        }
        
        // Dependency contracts (interface signatures only, not full code)
        if (!context.dependencyContracts().isEmpty()) {
            prompt.append("=== Dependency Contracts ===\n");
            for (InterfaceContract contract : context.dependencyContracts()) {
                prompt.append("Interface: ").append(contract.typeName()).append("\n");
                prompt.append("Package: ").append(contract.packageName()).append("\n");
                prompt.append("Signature: ").append(contract.interfaceSignature()).append("\n\n");
            }
        }
        
        // Pattern template
        if (template != null) {
            prompt.append("=== Pattern Template for ").append(fileType).append(" ===\n");
            prompt.append(template.classStructure()).append("\n\n");
        }
        
        // Source code
        prompt.append("=== Source Code ===\n");
        prompt.append(sourceCode).append("\n\n");
        
        // Instructions
        prompt.append("=== Instructions ===\n");
        if (isDualTarget && isFrmFile) {
            prompt.append("Convert this VB6 Form to a REACT TYPESCRIPT COMPONENT (.tsx).\n");
            prompt.append("Output ONLY React/TypeScript code. NO Java code.\n");
            prompt.append("Return the complete component file content.\n");
        } else if (isDualTarget) {
            prompt.append("Convert this VB6 code to SPRING BOOT JAVA.\n");
            prompt.append("Output ONLY Java code. NO React/TypeScript.\n");
        } else {
            prompt.append("Convert this ").append(fileType).append(" to ");
            prompt.append(anchor.targetStack()).append(".\n");
        }
        prompt.append("Follow the naming conventions and established patterns.\n");
        prompt.append("Return only the converted code without explanations.\n");
        
        return prompt.toString();
    }
    
    private String classifyFileType(String fileName) {
        String lower = fileName.toLowerCase();
        
        if (lower.contains("controller")) return "Controller";
        if (lower.contains("service") || lower.contains("business")) return "Service";
        if (lower.contains("repository") || lower.contains("data")) return "Repository";
        if (lower.contains("model") || lower.contains("entity")) return "Model";
        if (lower.contains("dto")) return "DTO";
        if (lower.contains("test")) return "Test";
        
        return "General";
    }
    
    private Path determineTargetPath(Path sourcePath, String moduleName, String targetStack) {
        String fileName = sourcePath.getFileName().toString();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        
        // Handle dual-target (React + Spring Boot)
        if (targetStack.toLowerCase().contains("react")) {
            String ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
            
            // VB6 forms become React components
            if (ext.equals(".frm")) {
                // Check if the converted code is React or Java
                // For React components
                return Paths.get("conversion/to", "frontend", "src", "components", baseName + ".tsx");
            } else if (ext.equals(".bas") || ext.equals(".cls")) {
                // VB6 modules/classes become Spring Boot services
                return Paths.get("conversion/to", "backend", "src", "main", "java", 
                    moduleName.toLowerCase().replaceAll("[^a-z0-9]", ""), baseName + ".java");
            }
        }
        
        // Default Java conversion
        String javaFileName = baseName + ".java";
        String packagePath = moduleName.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        return Paths.get("conversion/to", packagePath, javaFileName);
    }
    
    private List<String> extractExportedTypes(String code) {
        List<String> types = new ArrayList<>();
        // Extract public class/interface declarations
        String[] lines = code.split("\n");
        for (String line : lines) {
            if (line.matches(".*\\bpublic\\b.*\\b(class|interface|enum)\\b.*")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("class") || parts[i].equals("interface") || parts[i].equals("enum")) {
                        if (i + 1 < parts.length) {
                            types.add(parts[i + 1].replace("{", "").trim());
                        }
                        break;
                    }
                }
            }
        }
        return types;
    }
    
    private List<String> extractExposedApis(String code) {
        List<String> apis = new ArrayList<>();
        // Extract public method signatures
        String[] lines = code.split("\n");
        for (String line : lines) {
            if (line.matches(".*\\bpublic\\b.*\\(.*\\).*")) {
                apis.add(line.trim());
            }
        }
        return apis;
    }
    
    private List<String> extractImports(String code) {
        List<String> imports = new ArrayList<>();
        String[] lines = code.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("import ")) {
                imports.add(line.trim());
            }
        }
        return imports;
    }
    
    private List<String> extractDependencies(String code) {
        List<String> deps = new ArrayList<>();
        // Extract class references that might be dependencies
        // TODO: Implement dependency extraction
        return deps;
    }
    
    private int countLines(String code) {
        return code.split("\n").length;
    }
    
    private int estimateTokens(String source, String converted) {
        // Rough estimation: 1 token ~ 4 characters
        return (source.length() + converted.length()) / 4;
    }
    
    /**
     * Converts code with iterative validation and correction loop.
     * If validation finds errors, feeds them back to LLM for fixing.
     */
    private String convertWithIterativeCorrection(ModuleContext context, String prompt, 
                                                   String sourceCode, Path sourceFile) {
        final int MAX_ITERATIONS = 3;
        String convertedCode = geminiClient.code2code(prompt, sourceCode);
        
        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            // Validate the converted code
            List<String> issues = postProcessor.validateSyntax(convertedCode);
            
            // Also run React-specific validation
            if (context.anchor().targetStack().toLowerCase().contains("react")) {
                issues.addAll(validateReactSpecific(convertedCode));
            }
            
            if (issues.isEmpty()) {
                System.out.println("      Validation passed on iteration " + iteration);
                break;
            }
            
            if (iteration == MAX_ITERATIONS) {
                System.out.println("      Warning: Max iterations reached, returning best effort");
                break;
            }
            
            System.out.println("      Validation issues found (iteration " + iteration + "): " + issues.size());
            
            // Build correction prompt with specific errors
            StringBuilder correctionPrompt = new StringBuilder();
            correctionPrompt.append("=== CORRECTION REQUEST ===\n");
            correctionPrompt.append("The previously generated React code has the following compilation errors:\n\n");
            for (String issue : issues) {
                correctionPrompt.append("- ").append(issue).append("\n");
            }
            correctionPrompt.append("\n=== ORIGINAL CODE ===\n");
            correctionPrompt.append(sourceCode).append("\n\n");
            correctionPrompt.append("=== CURRENT GENERATED CODE (with errors) ===\n");
            correctionPrompt.append(convertedCode).append("\n\n");
            correctionPrompt.append("=== INSTRUCTIONS ===\n");
            correctionPrompt.append("Fix ALL the compilation errors listed above.\n");
            correctionPrompt.append("Return ONLY the corrected React TypeScript code.\n");
            correctionPrompt.append("Ensure all braces, parentheses, and JSX tags are balanced.\n");
            correctionPrompt.append("Do not add any explanations or markdown formatting.\n");
            
            // Re-convert with error feedback
            convertedCode = geminiClient.code2code(correctionPrompt.toString(), sourceCode);
        }
        
        return convertedCode;
    }
    
    /**
     * React-specific validation beyond basic syntax.
     */
    private List<String> validateReactSpecific(String code) {
        List<String> issues = new ArrayList<>();
        
        // Check for required imports
        if (!code.contains("import React")) {
            issues.add("Missing React import");
        }
        if (!code.contains("from '@mui/material'")) {
            issues.add("Missing MUI material import");
        }
        
        // Check for export default
        if (!code.contains("export default")) {
            issues.add("Missing export default statement");
        }
        
        // Check for TypeScript interfaces
        if (!code.contains("interface")) {
            issues.add("Missing TypeScript interfaces for props/state");
        }
        
        // Check for useState hook
        if (!code.contains("useState")) {
            issues.add("Missing useState hook for state management");
        }
        
        // Check for component function declaration
        if (!code.matches("(?s).*const\\s+\\w+.*React\\.FC.*")) {
            issues.add("Component not declared with proper React.FC type");
        }
        
        return issues;
    }
    
    /**
     * Reads file with proper encoding handling for legacy formats (VB6, etc.)
     */
    private String readFileWithEncoding(Path file) throws IOException {
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
                    // Last resort: ISO-8859-1 reads any byte
                    return Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1);
                }
            } else {
                throw new IOException("Failed to read file: " + e.getMessage());
            }
        }
    }
}
