package com.code2code.service.converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses VB6 .frm files into FormSpecification objects.
 * Handles VB6's specific format with Begin/End blocks for controls.
 */
public class VB6FormParser implements FormParser {
    
    private static final Pattern BEGIN_FORM = Pattern.compile(
        "Begin\\s+VB\\.Form\\s+(\\w+)");
    private static final Pattern BEGIN_CONTROL = Pattern.compile(
        "Begin\\s+VB\\.(\\w+)\\s+(\\w+)");
    private static final Pattern PROPERTY = Pattern.compile(
        "(\\w+)\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE);
    
    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".frm");
    }
    
    @Override
    public FormSpecification parse(String content, String fileName) throws ParseException {
        try {
            String[] lines = content.split("\\r?\\n");
            int lineNum = 0;
            
            // Find form declaration
            String formName = null;
            for (int i = 0; i < lines.length; i++) {
                Matcher matcher = BEGIN_FORM.matcher(lines[i]);
                if (matcher.find()) {
                    formName = matcher.group(1);
                    lineNum = i;
                    break;
                }
            }
            
            if (formName == null) {
                throw new ParseException("No VB.Form declaration found", fileName, 1);
            }
            
            // Parse form properties and controls
            Map<String, String> formProps = new HashMap<>();
            List<FormSpecification.ControlDefinition> controls = new ArrayList<>();
            
            int i = lineNum + 1;
            while (i < lines.length) {
                String line = lines[i].trim();
                
                if (line.startsWith("Begin VB.")) {
                    // Parse control block - parseControl returns the next line after the End
                    ControlParseResult result = parseControl(lines, i, fileName);
                    controls.add(result.control());
                    i = result.nextLine(); // Already positioned at next line, continue
                    continue; // Skip the i++ at end of loop
                } else if (line.equals("End")) {
                    // End of form
                    break;
                } else if (!line.isEmpty() && !line.startsWith("'") && !line.startsWith("BeginProperty")) {
                    // Regular property
                    Matcher propMatcher = PROPERTY.matcher(line);
                    if (propMatcher.find()) {
                        formProps.put(propMatcher.group(1), propMatcher.group(2));
                    }
                }
                i++;
            }
            
            return new FormSpecification(
                formName,
                stripQuotes(formProps.getOrDefault("Caption", formName)),
                parseTwips(formProps.getOrDefault("ClientWidth", "0")),
                parseTwips(formProps.getOrDefault("ClientHeight", "0")),
                parseInt(formProps.getOrDefault("StartUpPosition", "0")),
                parseBoolean(formProps.getOrDefault("ControlBox", "True")),
                controls,
                Map.of(), // Event handlers - would need to parse .bas files for these
                List.of()
            );
            
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Unexpected error: " + e.getMessage(), fileName, e);
        }
    }
    
    private ControlParseResult parseControl(String[] lines, int startLine, String fileName) throws ParseException {
        String beginLine = lines[startLine].trim();
        Matcher matcher = BEGIN_CONTROL.matcher(beginLine);
        
        if (!matcher.find()) {
            throw new ParseException("Invalid control declaration: " + beginLine, fileName, startLine + 1);
        }
        
        String type = mapVB6ControlType(matcher.group(1));
        String name = matcher.group(2);
        
        Map<String, String> props = new HashMap<>();
        FormSpecification.FontProperties font = null;
        
        int i = startLine + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            
            if (line.equals("End")) {
                break;
            } else if (line.startsWith("BeginProperty Font")) {
                // Parse font properties
                font = parseFont(lines, i, fileName);
                // Skip to end of font block
                while (i < lines.length && !lines[i].trim().equals("EndProperty")) {
                    i++;
                }
            } else if (!line.isEmpty() && !line.startsWith("'") && !line.startsWith("BeginProperty")) {
                Matcher propMatcher = PROPERTY.matcher(line);
                if (propMatcher.find()) {
                    props.put(propMatcher.group(1), propMatcher.group(2));
                }
            }
            i++;
        }
        
        var control = new FormSpecification.ControlDefinition(
            type,
            name,
            parseTwips(props.getOrDefault("Left", "0")),
            parseTwips(props.getOrDefault("Top", "0")),
            parseTwips(props.getOrDefault("Width", "0")),
            parseTwips(props.getOrDefault("Height", "0")),
            stripQuotes(props.getOrDefault("Caption", "")),
            parseBoolean(props.getOrDefault("Visible", "True")),
            parseBoolean(props.getOrDefault("Enabled", "True")),
            props.getOrDefault("TabIndex", ""),
            font,
            List.of(),
            List.of()
        );
        
        return new ControlParseResult(control, i + 1);
    }
    
    private FormSpecification.FontProperties parseFont(String[] lines, int startLine, String fileName) {
        Map<String, String> fontProps = new HashMap<>();
        
        int i = startLine + 1;
        while (i < lines.length && !lines[i].trim().equals("EndProperty")) {
            String line = lines[i].trim();
            Matcher matcher = PROPERTY.matcher(line);
            if (matcher.find()) {
                fontProps.put(matcher.group(1), matcher.group(2));
            }
            i++;
        }
        
        return new FormSpecification.FontProperties(
            stripQuotes(fontProps.getOrDefault("Name", "Arial")),
            parseFloat(fontProps.getOrDefault("Size", "8")),
            parseBoolean(fontProps.getOrDefault("Weight", "0")) || parseInt(fontProps.getOrDefault("Weight", "0")) >= 700,
            parseInt(fontProps.getOrDefault("Italic", "0")) != 0,
            parseInt(fontProps.getOrDefault("Underline", "0")) != 0
        );
    }
    
    private String mapVB6ControlType(String vb6Type) {
        return switch (vb6Type.toLowerCase()) {
            case "commandbutton" -> "Button";
            case "textbox" -> "TextField";
            case "label" -> "Typography";
            case "checkbox" -> "Checkbox";
            case "combobox" -> "Select";
            case "frame" -> "Box";
            case "picturebox" -> "Box";
            case "timer" -> "Timer";
            default -> vb6Type;
        };
    }
    
    private String stripQuotes(String value) {
        if (value == null) return "";
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
    
    private int parseTwips(String value) {
        // VB6 stores coordinates in twips (1/20 of a point, 1/1440 of an inch)
        // We store as-is, conversion to pixels happens during generation
        return parseInt(value);
    }
    
    private int parseInt(String value) {
        try {
            return Integer.parseInt(stripQuotes(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private float parseFloat(String value) {
        try {
            return Float.parseFloat(stripQuotes(value).trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
    
    private boolean parseBoolean(String value) {
        value = stripQuotes(value).trim().toLowerCase();
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("-1");
    }
    
    private record ControlParseResult(FormSpecification.ControlDefinition control, int nextLine) {}
}
