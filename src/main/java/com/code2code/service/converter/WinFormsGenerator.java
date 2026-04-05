package com.code2code.service.converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates C# Windows Forms code from FormSpecification.
 * Creates both the Designer.cs file and the code-behind.
 */
public class WinFormsGenerator implements ComponentGenerator {
    
    @Override
    public boolean supports(String target) {
        return target != null && (target.toLowerCase().contains("winforms") || 
                                  target.toLowerCase().contains("csharp") ||
                                  target.toLowerCase().contains("c#"));
    }
    
    @Override
    public String generate(FormSpecification spec, GenerationOptions options) throws GenerationException {
        try {
            StringBuilder code = new StringBuilder();
            
            // Generate Designer.cs content (the visual layout)
            generateDesignerFile(code, spec);
            
            return code.toString();
            
        } catch (Exception e) {
            throw new GenerationException(
                "Failed to generate WinForms code: " + e.getMessage(),
                spec.formName(),
                options.targetFramework(),
                e
            );
        }
    }
    
    /**
     * Generates the .Designer.cs file content.
     */
    private void generateDesignerFile(StringBuilder code, FormSpecification spec) {
        String namespace = spec.formName().toLowerCase().replace("frm", "");
        String className = spec.formName();
        
        // Namespace and imports
        code.append("namespace ").append(capitalize(namespace)).append("\n");
        code.append("{\n");
        code.append("    partial class ").append(className).append("\n");
        code.append("    {\n");
        
        // Component container
        code.append("        /// <summary>\n");
        code.append("        /// Required designer variable.\n");
        code.append("        /// </summary>\n");
        code.append("        private System.ComponentModel.IContainer components = null;\n\n");
        
        // Control declarations
        code.append("        // Controls\n");
        for (var control : spec.controls()) {
            code.append("        private System.Windows.Forms.")
                .append(mapToWinFormsType(control.type()))
                .append(" ")
                .append(control.name())
                .append(";\n");
        }
        code.append("\n");
        
        // Dispose method
        code.append("        /// <summary>\n");
        code.append("        /// Clean up any resources being used.\n");
        code.append("        /// </summary>\n");
        code.append("        protected override void Dispose(bool disposing)\n");
        code.append("        {\n");
        code.append("            if (disposing && (components != null))\n");
        code.append("            {\n");
        code.append("                components.Dispose();\n");
        code.append("            }\n");
        code.append("            base.Dispose(disposing);\n");
        code.append("        }\n\n");
        
        // InitializeComponent method
        code.append("        #region Windows Form Designer generated code\n\n");
        code.append("        /// <summary>\n");
        code.append("        /// Required method for Designer support - do not modify\n");
        code.append("        /// </summary>\n");
        code.append("        private void InitializeComponent()\n");
        code.append("        {\n");
        code.append("            this.components = new System.ComponentModel.Container();\n");
        
        // Initialize each control
        for (var control : spec.controls()) {
            code.append("            this.")
                .append(control.name())
                .append(" = new System.Windows.Forms.")
                .append(mapToWinFormsType(control.type()))
                .append("();\n");
        }
        code.append("            this.SuspendLayout();\n");
        code.append("            // \n");
        
        // Configure each control
        for (var control : spec.controls()) {
            code.append("            // ").append(control.name()).append("\n");
            code.append("            this.")
                .append(control.name())
                .append(".Location = new System.Drawing.Point(")
                .append(twipsToPixels(control.left()))
                .append(", ")
                .append(twipsToPixels(control.top()))
                .append(");\n");
            
            if (control.width() > 0) {
                code.append("            this.")
                    .append(control.name())
                    .append(".Size = new System.Drawing.Size(")
                    .append(twipsToPixels(control.width()))
                    .append(", ")
                    .append(twipsToPixels(control.height()))
                    .append(");\n");
            }
            
            if (!control.caption().isEmpty()) {
                code.append("            this.")
                    .append(control.name())
                    .append(".Text = \"")
                    .append(escapeString(control.caption()))
                    .append("\";\n");
            }
            
            if (!control.visible()) {
                code.append("            this.")
                    .append(control.name())
                    .append(".Visible = false;\n");
            }
            
            if (!control.enabled()) {
                code.append("            this.")
                    .append(control.name())
                    .append(".Enabled = false;\n");
            }
            
            if (!control.tabIndex().isEmpty()) {
                code.append("            this.")
                    .append(control.name())
                    .append(".TabIndex = ")
                    .append(control.tabIndex())
                    .append(";\n");
            }
            
            // Font properties
            if (control.font() != null) {
                code.append("            this.")
                    .append(control.name())
                    .append(".Font = new System.Drawing.Font(\"");
                code.append(control.font().name()).append("\", ");
                code.append(control.font().size()).append("F");
                if (control.font().bold() || control.font().italic() || control.font().underline()) {
                    List<String> styles = new ArrayList<>();
                    if (control.font().bold()) styles.add("System.Drawing.FontStyle.Bold");
                    if (control.font().italic()) styles.add("System.Drawing.FontStyle.Italic");
                    if (control.font().underline()) styles.add("System.Drawing.FontStyle.Underline");
                    code.append(", (System.Drawing.FontStyle)(").append(String.join(" | ", styles)).append(")");
                }
                code.append(");\n");
            }
            
            code.append("            // \n");
        }
        
        // Form configuration
        code.append("            // Form\n");
        code.append("            this.AutoScaleDimensions = new System.Drawing.SizeF(6F, 13F);\n");
        code.append("            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;\n");
        code.append("            this.ClientSize = new System.Drawing.Size(")
            .append(twipsToPixels(spec.clientWidth()))
            .append(", ")
            .append(twipsToPixels(spec.clientHeight()))
            .append(");\n");
        
        // Add controls to form
        code.append("            this.Controls.AddRange(new System.Windows.Forms.Control[] {\n");
        List<String> controlNames = new ArrayList<>();
        for (var control : spec.controls()) {
            controlNames.add("this." + control.name());
        }
        code.append("                ").append(String.join(",\n                ", controlNames)).append("\n");
        code.append("            });\n");
        
        code.append("            this.Name = \"").append(className).append("\";\n");
        code.append("            this.Text = \"").append(escapeString(spec.caption())).append("\";\n");
        code.append("            this.ResumeLayout(false);\n");
        code.append("            this.PerformLayout();\n");
        code.append("        }\n\n");
        code.append("        #endregion\n");
        code.append("    }\n");
        code.append("}\n");
    }
    
    /**
     * Generates the code-behind file (Form1.cs without Designer).
     */
    public String generateCodeBehind(FormSpecification spec) {
        StringBuilder code = new StringBuilder();
        String namespace = spec.formName().toLowerCase().replace("frm", "");
        String className = spec.formName();
        
        code.append("using System;\n");
        code.append("using System.Windows.Forms;\n\n");
        
        code.append("namespace ").append(capitalize(namespace)).append("\n");
        code.append("{\n");
        code.append("    public partial class ").append(className).append(" : Form\n");
        code.append("    {\n");
        code.append("        public ").append(className).append("()\n");
        code.append("        {\n");
        code.append("            InitializeComponent();\n");
        code.append("        }\n\n");
        
        // Generate event handler stubs
        for (var control : spec.controls()) {
            String handlerName = control.name() + "_Click";
            code.append("        private void ").append(handlerName).append("(object sender, EventArgs e)\n");
            code.append("        {\n");
            code.append("            // TODO: Implement click handler for ").append(control.name()).append("\n");
            code.append("        }\n\n");
        }
        
        code.append("    }\n");
        code.append("}\n");
        
        return code.toString();
    }
    
    private String mapToWinFormsType(String type) {
        return switch (type.toLowerCase()) {
            case "button" -> "Button";
            case "textfield" -> "TextBox";
            case "typography", "label" -> "Label";
            case "checkbox" -> "CheckBox";
            case "select", "combobox" -> "ComboBox";
            case "box", "frame" -> "GroupBox";
            case "picturebox" -> "PictureBox";
            default -> "Control";
        };
    }
    
    private int twipsToPixels(int twips) {
        // VB6 uses twips (1/20 of a point, 1/1440 of an inch)
        // At 96 DPI: 1 pixel = 15 twips (approximately)
        return Math.round(twips / 15.0f);
    }
    
    private String escapeString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
