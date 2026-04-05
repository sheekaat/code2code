package com.code2code.service.converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates React TypeScript components from FormSpecification.
 * Uses Material-UI (MUI) for styling and layout.
 */
public class ReactComponentGenerator implements ComponentGenerator {
    
    @Override
    public boolean supports(String target) {
        return target != null && target.toLowerCase().contains("react");
    }
    
    @Override
    public String generate(FormSpecification spec, GenerationOptions options) throws GenerationException {
        try {
            StringBuilder code = new StringBuilder();
            
            // Generate imports
            generateImports(code, spec, options);
            
            // Generate interfaces
            generateInterfaces(code, spec);
            
            // Generate helper functions
            generateHelpers(code);
            
            // Generate component
            generateComponent(code, spec, options);
            
            // Generate export
            code.append("\nexport default ").append(spec.formName()).append(";\n");
            
            return code.toString();
            
        } catch (Exception e) {
            throw new GenerationException(
                "Failed to generate component: " + e.getMessage(),
                spec.formName(),
                options.targetFramework(),
                e
            );
        }
    }
    
    private void generateImports(StringBuilder code, FormSpecification spec, GenerationOptions options) {
        code.append("import React, { useState, useEffect } from 'react';\n");
        
        // Determine which MUI components are needed
        List<String> muiComponents = new ArrayList<>();
        muiComponents.add("Box");
        
        for (var control : spec.controls()) {
            switch (control.type()) {
                case "Button" -> muiComponents.add("Button");
                case "TextField" -> muiComponents.add("TextField");
                case "Typography" -> muiComponents.add("Typography");
                case "Checkbox" -> muiComponents.add("Checkbox");
                case "Select" -> {
                    muiComponents.add("Select");
                    muiComponents.add("MenuItem");
                }
            }
        }
        
        code.append("import { ")
            .append(String.join(", ", muiComponents.stream().distinct().toList()))
            .append(" } from '@mui/material';\n");
        
        code.append("import axios from 'axios';\n\n");
    }
    
    private void generateInterfaces(StringBuilder code, FormSpecification spec) {
        // Props interface
        code.append("interface ").append(spec.formName()).append("Props {\n");
        code.append("  // Add any external props here\n");
        code.append("}\n\n");
        
        // State interface
        code.append("interface ").append(spec.formName()).append("State {\n");
        
        // Generate state fields for each control that needs it
        for (var control : spec.controls()) {
            switch (control.type()) {
                case "TextField" -> code.append("  ")
                    .append(control.name().toLowerCase())
                    .append(": string;\n");
                case "Checkbox" -> code.append("  ")
                    .append(control.name().toLowerCase())
                    .append(": boolean;\n");
            }
        }
        
        code.append("}\n\n");
    }
    
    private void generateHelpers(StringBuilder code) {
        code.append("// Helper to convert VB6 twips to pixels\n");
        code.append("const twipsToPx = (twips: number): number => Math.round(twips / 15);\n\n");
    }
    
    private void generateComponent(StringBuilder code, FormSpecification spec, GenerationOptions options) {
        // Component declaration
        code.append("const ")
            .append(spec.formName())
            .append(": React.FC<")
            .append(spec.formName())
            .append("Props> = (props) => {\n");
        
        // Initialize state
        code.append("  const [state, setState] = useState<")
            .append(spec.formName())
            .append("State>({\n");
        
        // Default state values
        for (var control : spec.controls()) {
            switch (control.type()) {
                case "TextField" -> code.append("    ")
                    .append(control.name().toLowerCase())
                    .append(": '',\n");
                case "Checkbox" -> code.append("    ")
                    .append(control.name().toLowerCase())
                    .append(": false,\n");
            }
        }
        
        code.append("  });\n\n");
        
        // useEffect for Form_Load equivalent
        code.append("  useEffect(() => {\n");
        code.append("    // Form initialization logic here\n");
        code.append("  }, []);\n\n");
        
        // Event handlers
        generateEventHandlers(code, spec);
        
        // JSX
        generateJSX(code, spec);
        
        code.append("};\n");
    }
    
    private void generateEventHandlers(StringBuilder code, FormSpecification spec) {
        for (var control : spec.controls()) {
            String handlerName = "handle" + capitalize(control.name());
            
            switch (control.type()) {
                case "Button" -> {
                    code.append("  const ")
                        .append(handlerName)
                        .append(" = () => {\n");
                    code.append("    // TODO: Implement button click handler\n");
                    code.append("    console.log('")
                        .append(control.name())
                        .append(" clicked');\n");
                    code.append("  };\n\n");
                }
                case "TextField" -> {
                    code.append("  const ")
                        .append(handlerName)
                        .append(" = (event: React.ChangeEvent<HTMLInputElement>) => {\n");
                    code.append("    setState(prev => ({ ...prev, ")
                        .append(control.name().toLowerCase())
                        .append(": event.target.value }));\n");
                    code.append("  };\n\n");
                }
                case "Checkbox" -> {
                    code.append("  const ")
                        .append(handlerName)
                        .append(" = (event: React.ChangeEvent<HTMLInputElement>) => {\n");
                    code.append("    setState(prev => ({ ...prev, ")
                        .append(control.name().toLowerCase())
                        .append(": event.target.checked }));\n");
                    code.append("  };\n\n");
                }
            }
        }
    }
    
    private void generateJSX(StringBuilder code, FormSpecification spec) {
        code.append("  return (\n");
        code.append("    <Box\n");
        code.append("      sx={{\n");
        code.append("        position: 'relative',\n");
        code.append("        width: twipsToPx(").append(spec.clientWidth()).append("),\n");
        code.append("        height: twipsToPx(").append(spec.clientHeight()).append("),\n");
        code.append("        backgroundColor: '#f5f5f5',\n");
        code.append("        border: '1px solid #ccc',\n");
        code.append("        borderRadius: 1,\n");
        code.append("        padding: 2,\n");
        code.append("      }}\n");
        code.append("    >\n");
        
        // Generate each control
        for (var control : spec.controls()) {
            generateControl(code, control);
        }
        
        code.append("    </Box>\n");
        code.append("  );\n");
    }
    
    private void generateControl(StringBuilder code, FormSpecification.ControlDefinition control) {
        code.append("\n      {/* ")
            .append(control.name())
            .append(" - converted from VB6 ")
            .append(control.type())
            .append(" */}\n");
        
        String left = String.format("twipsToPx(%d)", control.left());
        String top = String.format("twipsToPx(%d)", control.top());
        String width = String.format("twipsToPx(%d)", control.width());
        String height = control.height() > 0 
            ? String.format("twipsToPx(%d)", control.height()) 
            : "'auto'";
        
        switch (control.type()) {
            case "Button" -> {
                code.append("      <Button\n");
                code.append("        variant=\"contained\"\n");
                code.append("        onClick={handle").append(capitalize(control.name())).append("}\n");
                generateSxProp(code, left, top, width, height, control.font());
                code.append("      >\n");
                code.append("        ").append(control.caption()).append("\n");
                code.append("      </Button>\n");
            }
            case "TextField" -> {
                code.append("      <TextField\n");
                code.append("        value={state.").append(control.name().toLowerCase()).append("}\n");
                code.append("        onChange={handle").append(capitalize(control.name())).append("}\n");
                code.append("        size=\"small\"\n");
                code.append("        placeholder=\"").append(control.caption()).append("\"\n");
                generateSxProp(code, left, top, width, height, control.font());
                code.append("      />\n");
            }
            case "Typography", "Label" -> {
                code.append("      <Typography\n");
                code.append("        variant=\"body1\"\n");
                generateSxProp(code, left, top, width, "'auto'", control.font());
                code.append("      >\n");
                code.append("        ").append(control.caption()).append("\n");
                code.append("      </Typography>\n");
            }
            case "Checkbox" -> {
                code.append("      <Checkbox\n");
                code.append("        checked={state.").append(control.name().toLowerCase()).append("}\n");
                code.append("        onChange={handle").append(capitalize(control.name())).append("}\n");
                code.append("        sx={{ position: 'absolute', left: ").append(left).append(", top: ").append(top).append(" }}\n");
                code.append("      />\n");
            }
            default -> {
                // Generic Box fallback
                code.append("      <Box\n");
                generateSxProp(code, left, top, width, height, control.font());
                code.append("      />\n");
            }
        }
    }
    
    private void generateSxProp(StringBuilder code, String left, String top, 
                                 String width, String height, FormSpecification.FontProperties font) {
        code.append("        sx={{\n");
        code.append("          position: 'absolute',\n");
        code.append("          left: ").append(left).append(",\n");
        code.append("          top: ").append(top).append(",\n");
        code.append("          width: ").append(width).append(",\n");
        if (!height.equals("'auto'")) {
            code.append("          height: ").append(height).append(",\n");
        }
        
        if (font != null) {
            code.append("          fontFamily: '").append(font.name()).append("',\n");
            code.append("          fontSize: ").append(font.size()).append(" * 1.33");
            if (font.bold()) {
                code.append(",\n          fontWeight: 'bold'");
            }
            if (font.italic()) {
                code.append(",\n          fontStyle: 'italic'");
            }
            code.append("\n");
        }
        
        code.append("        }}\n");
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
