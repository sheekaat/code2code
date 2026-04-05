package com.code2code.service.converter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the conversion process from VB6 forms to React components.
 * Uses the intermediate representation (FormSpecification) pattern.
 */
public class FormToReactConverter {
    
    private final List<FormParser> parsers;
    private final List<ComponentGenerator> generators;
    
    public FormToReactConverter() {
        this.parsers = new ArrayList<>();
        this.generators = new ArrayList<>();
        
        // Register default parsers
        registerParser(new VB6FormParser());
        
        // Register default generators
        registerGenerator(new ReactComponentGenerator());
        registerGenerator(new WinFormsGenerator());
    }
    
    /**
     * Registers a form parser.
     */
    public void registerParser(FormParser parser) {
        parsers.add(parser);
    }
    
    /**
     * Registers a component generator.
     */
    public void registerGenerator(ComponentGenerator generator) {
        generators.add(generator);
    }
    
    /**
     * Converts a form file to a React component.
     *
     * @param formFile Path to the form file (.frm, etc.)
     * @param targetFramework Target framework (e.g., "react")
     * @param options Generation options
     * @return The generated component code
     * @throws ConversionException if conversion fails
     */
    public String convert(Path formFile, String targetFramework, GenerationOptions options) 
            throws ConversionException {
        
        try {
            String fileName = formFile.getFileName().toString();
            String content = readFileWithEncoding(formFile);
            
            FormParser parser = findParser(fileName);
            if (parser == null) {
                throw new ConversionException("No parser found for file: " + fileName, fileName, targetFramework);
            }
            
            // Parse to intermediate representation
            FormSpecification spec = parser.parse(content, fileName);
            
            // Find appropriate generator
            ComponentGenerator generator = findGenerator(targetFramework);
            if (generator == null) {
                throw new ConversionException("No generator found for target: " + targetFramework, fileName, targetFramework);
            }
            
            // Generate component
            return generator.generate(spec, options);
            
        } catch (ParseException e) {
            throw new ConversionException("Parse error: " + e.getMessage(), formFile.toString(), targetFramework, e);
        } catch (GenerationException e) {
            throw new ConversionException("Generation error: " + e.getMessage(), formFile.toString(), targetFramework, e);
        } catch (Exception e) {
            throw new ConversionException("Unexpected error: " + e.getMessage(), formFile.toString(), targetFramework, e);
        }
    }
    
    /**
     * Converts with default React options.
     */
    public String convert(Path formFile, String targetFramework) throws ConversionException {
        return convert(formFile, targetFramework, GenerationOptions.reactDefaults());
    }
    
    /**
     * Gets the intermediate specification without generating code.
     * Useful for debugging and manual inspection.
     */
    public FormSpecification parseOnly(Path formFile) throws ConversionException {
        try {
            String fileName = formFile.getFileName().toString();
            String content = readFileWithEncoding(formFile);
            
            FormParser parser = findParser(fileName);
            if (parser == null) {
                throw new ConversionException("No parser found", fileName, "parse");
            }
            
            return parser.parse(content, fileName);
            
        } catch (IOException e) {
            throw new ConversionException("Failed to read file: " + e.getMessage(), formFile.toString(), "parse", e);
        } catch (ParseException e) {
            throw new ConversionException("Parse error: " + e.getMessage(), formFile.toString(), "parse", e);
        }
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
    
    private FormParser findParser(String fileName) {
        for (FormParser parser : parsers) {
            if (parser.supports(fileName)) {
                return parser;
            }
        }
        return null;
    }
    
    private ComponentGenerator findGenerator(String target) {
        for (ComponentGenerator generator : generators) {
            if (generator.supports(target)) {
                return generator;
            }
        }
        return null;
    }
}
