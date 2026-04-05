package com.code2code.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates build configuration files (pom.xml, package.json) for target languages
 */
public class BuildFileGenerator {
    
    /**
     * Generate appropriate build files based on target language
     */
    public void generateBuildFiles(Path outputDir, String targetStack, List<String> detectedDependencies) throws IOException {
        String targetLower = targetStack.toLowerCase();
        
        if (targetLower.contains("java") || targetLower.contains("spring")) {
            generatePomXml(outputDir, detectedDependencies);
        }
        
        if (targetLower.contains("react")) {
            generatePackageJson(outputDir, detectedDependencies, true);
        }
    }
    
    /**
     * Generate pom.xml for Java Spring Boot projects
     */
    private void generatePomXml(Path outputDir, List<String> detectedDependencies) throws IOException {
        StringBuilder pom = new StringBuilder();
        pom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        pom.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        pom.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n");
        pom.append("         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        pom.append("    <modelVersion>4.0.0</modelVersion>\n\n");
        
        pom.append("    <parent>\n");
        pom.append("        <groupId>org.springframework.boot</groupId>\n");
        pom.append("        <artifactId>spring-boot-starter-parent</artifactId>\n");
        pom.append("        <version>3.2.0</version>\n");
        pom.append("        <relativePath/>\n");
        pom.append("    </parent>\n\n");
        
        pom.append("    <groupId>com.converted</groupId>\n");
        pom.append("    <artifactId>converted-application</artifactId>\n");
        pom.append("    <version>1.0.0</version>\n");
        pom.append("    <packaging>jar</packaging>\n\n");
        
        pom.append("    <properties>\n");
        pom.append("        <java.version>17</java.version>\n");
        pom.append("        <maven.compiler.source>17</maven.compiler.source>\n");
        pom.append("        <maven.compiler.target>17</maven.compiler.target>\n");
        pom.append("    </properties>\n\n");
        
        pom.append("    <dependencies>\n");
        
        // Core Spring Boot
        pom.append("        <!-- Spring Boot Starters -->\n");
        pom.append("        <dependency>\n");
        pom.append("            <groupId>org.springframework.boot</groupId>\n");
        pom.append("            <artifactId>spring-boot-starter-web</artifactId>\n");
        pom.append("        </dependency>\n");
        
        pom.append("        <dependency>\n");
        pom.append("            <groupId>org.springframework.boot</groupId>\n");
        pom.append("            <artifactId>spring-boot-starter-data-jpa</artifactId>\n");
        pom.append("        </dependency>\n");
        
        pom.append("        <dependency>\n");
        pom.append("            <groupId>org.springframework.boot</groupId>\n");
        pom.append("            <artifactId>spring-boot-starter-validation</artifactId>\n");
        pom.append("        </dependency>\n");
        
        pom.append("        <dependency>\n");
        pom.append("            <groupId>org.springframework.boot</groupId>\n");
        pom.append("            <artifactId>spring-boot-starter-test</artifactId>\n");
        pom.append("            <scope>test</scope>\n");
        pom.append("        </dependency>\n");
        
        // Database
        pom.append("\n        <!-- Database -->\n");
        pom.append("        <dependency>\n");
        pom.append("            <groupId>com.h2database</groupId>\n");
        pom.append("            <artifactId>h2</artifactId>\n");
        pom.append("            <scope>runtime</scope>\n");
        pom.append("        </dependency>\n");
        
        // Add detected dependencies
        if (detectedDependencies != null) {
            pom.append("\n        <!-- Detected Dependencies -->\n");
            for (String dep : detectedDependencies) {
                String dependencyXml = mapToMavenDependency(dep);
                if (dependencyXml != null) {
                    pom.append(dependencyXml);
                }
            }
        }
        
        pom.append("    </dependencies>\n\n");
        
        pom.append("    <build>\n");
        pom.append("        <plugins>\n");
        pom.append("            <plugin>\n");
        pom.append("                <groupId>org.springframework.boot</groupId>\n");
        pom.append("                <artifactId>spring-boot-maven-plugin</artifactId>\n");
        pom.append("            </plugin>\n");
        pom.append("        </plugins>\n");
        pom.append("    </build>\n");
        pom.append("</project>\n");
        
        // Write pom.xml
        Path pomPath = outputDir.resolve("pom.xml");
        Files.createDirectories(pomPath.getParent());
        Files.writeString(pomPath, pom.toString());
        System.out.println("Generated pom.xml at: " + pomPath.toAbsolutePath());
    }
    
    /**
     * Map detected dependency names to Maven XML
     */
    private String mapToMavenDependency(String dependency) {
        String lower = dependency.toLowerCase();
        
        return switch (lower) {
            case "lombok" -> "        <dependency>\n            <groupId>org.projectlombok</groupId>\n            <artifactId>lombok</artifactId>\n            <optional>true</optional>\n        </dependency>\n";
            case "mapstruct" -> "        <dependency>\n            <groupId>org.mapstruct</groupId>\n            <artifactId>mapstruct</artifactId>\n            <version>1.5.5.Final</version>\n        </dependency>\n";
            case "jackson" -> "        <dependency>\n            <groupId>com.fasterxml.jackson.core</groupId>\n            <artifactId>jackson-databind</artifactId>\n        </dependency>\n";
            case "security", "spring-security" -> "        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-security</artifactId>\n        </dependency>\n";
            case "webflux" -> "        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-webflux</artifactId>\n        </dependency>\n";
            case "kafka" -> "        <dependency>\n            <groupId>org.springframework.kafka</groupId>\n            <artifactId>spring-kafka</artifactId>\n        </dependency>\n";
            case "redis" -> "        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-data-redis</artifactId>\n        </dependency>\n";
            case "mongodb" -> "        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-data-mongodb</artifactId>\n        </dependency>\n";
            case "websocket" -> "        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-websocket</artifactId>\n        </dependency>\n";
            case "actuator" -> "        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-actuator</artifactId>\n        </dependency>\n";
            default -> null;
        };
    }
    
    /**
     * Generate package.json for ReactJS projects
     */
    private void generatePackageJson(Path outputDir, List<String> detectedDependencies, boolean isReact) throws IOException {
        StringBuilder pkg = new StringBuilder();
        pkg.append("{\n");
        pkg.append("  \"name\": \"converted-frontend\",\n");
        pkg.append("  \"version\": \"1.0.0\",\n");
        pkg.append("  \"private\": true,\n");
        pkg.append("  \"type\": \"module\",\n");
        pkg.append("  \"scripts\": {\n");
        pkg.append("    \"dev\": \"vite\",\n");
        pkg.append("    \"build\": \"tsc && vite build\",\n");
        pkg.append("    \"preview\": \"vite preview\",\n");
        pkg.append("    \"lint\": \"eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0\"\n");
        pkg.append("  },\n");
        pkg.append("  \"dependencies\": {\n");
        pkg.append("    \"react\": \"^18.2.0\",\n");
        pkg.append("    \"react-dom\": \"^18.2.0\",\n");
        pkg.append("    \"@mui/material\": \"^5.15.0\",\n");
        pkg.append("    \"@mui/icons-material\": \"^5.15.0\",\n");
        pkg.append("    \"@emotion/react\": \"^11.11.1\",\n");
        pkg.append("    \"@emotion/styled\": \"^11.11.0\",\n");
        pkg.append("    \"axios\": \"^1.6.2\"");
        
        // Add detected dependencies
        if (detectedDependencies != null) {
            for (String dep : detectedDependencies) {
                String npmDep = mapToNpmDependency(dep);
                if (npmDep != null) {
                    pkg.append(",\n").append(npmDep);
                }
            }
        }
        
        pkg.append("\n  },\n");
        pkg.append("  \"devDependencies\": {\n");
        pkg.append("    \"@types/react\": \"^18.2.43\",\n");
        pkg.append("    \"@types/react-dom\": \"^18.2.17\",\n");
        pkg.append("    \"@typescript-eslint/eslint-plugin\": \"^6.14.0\",\n");
        pkg.append("    \"@typescript-eslint/parser\": \"^6.14.0\",\n");
        pkg.append("    \"@vitejs/plugin-react\": \"^4.2.1\",\n");
        pkg.append("    \"eslint\": \"^8.55.0\",\n");
        pkg.append("    \"eslint-plugin-react-hooks\": \"^4.6.0\",\n");
        pkg.append("    \"eslint-plugin-react-refresh\": \"^0.4.5\",\n");
        pkg.append("    \"typescript\": \"^5.2.2\",\n");
        pkg.append("    \"vite\": \"^5.0.8\"\n");
        pkg.append("  }\n");
        pkg.append("}\n");
        
        Path pkgPath = outputDir.resolve("package.json");
        Files.writeString(pkgPath, pkg.toString());
        System.out.println("Generated package.json: " + pkgPath);
        
        // Also generate vite.config.ts
        generateViteConfig(outputDir);
        
        // Generate tsconfig.json
        generateTsConfig(outputDir);
    }
    
    /**
     * Map detected dependency to npm package
     */
    private String mapToNpmDependency(String dependency) {
        String lower = dependency.toLowerCase();
        
        return switch (lower) {
            case "react-router", "router" -> "    \"react-router-dom\": \"^6.21.0\"";
            case "redux" -> "    \"@reduxjs/toolkit\": \"^2.0.1\",\n    \"react-redux\": \"^9.0.4\"";
            case "query", "tanstack" -> "    \"@tanstack/react-query\": \"^5.13.4\"";
            case "formik", "forms" -> "    \"formik\": \"^2.4.5\"";
            case "yup" -> "    \"yup\": \"^1.3.3\"";
            case "lodash" -> "    \"lodash\": \"^4.17.21\"";
            case "date-fns" -> "    \"date-fns\": \"^3.0.0\"";
            case "recharts", "charts" -> "    \"recharts\": \"^2.10.3\"";
            case "styled-components" -> "    \"styled-components\": \"^6.1.1\"";
            default -> null;
        };
    }
    
    /**
     * Generate vite.config.ts for React projects
     */
    private void generateViteConfig(Path outputDir) throws IOException {
        String config = "import { defineConfig } from 'vite'\n" +
            "import react from '@vitejs/plugin-react'\n\n" +
            "export default defineConfig({\n" +
            "  plugins: [react()],\n" +
            "  server: {\n" +
            "    port: 3000,\n" +
            "    proxy: {\n" +
            "      '/api': {\n" +
            "        target: 'http://localhost:8080',\n" +
            "        changeOrigin: true\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "})\n";
        
        Path configPath = outputDir.resolve("vite.config.ts");
        Files.writeString(configPath, config);
        System.out.println("Generated vite.config.ts: " + configPath);
    }
    
    /**
     * Generate tsconfig.json for TypeScript projects
     */
    private void generateTsConfig(Path outputDir) throws IOException {
        String config = "{\n" +
            "  \"compilerOptions\": {\n" +
            "    \"target\": \"ES2020\",\n" +
            "    \"useDefineForClassFields\": true,\n" +
            "    \"lib\": [\"ES2020\", \"DOM\", \"DOM.Iterable\"],\n" +
            "    \"module\": \"ESNext\",\n" +
            "    \"skipLibCheck\": true,\n" +
            "    \"moduleResolution\": \"bundler\",\n" +
            "    \"allowImportingTsExtensions\": true,\n" +
            "    \"resolveJsonModule\": true,\n" +
            "    \"isolatedModules\": true,\n" +
            "    \"noEmit\": true,\n" +
            "    \"jsx\": \"react-jsx\",\n" +
            "    \"strict\": true,\n" +
            "    \"noUnusedLocals\": true,\n" +
            "    \"noUnusedParameters\": true,\n" +
            "    \"noFallthroughCasesInSwitch\": true\n" +
            "  },\n" +
            "  \"include\": [\"src\"],\n" +
            "  \"references\": [{ \"path\": \"./tsconfig.node.json\" }]\n" +
            "}\n";
        
        Path configPath = outputDir.resolve("tsconfig.json");
        Files.writeString(configPath, config);
        System.out.println("Generated tsconfig.json: " + configPath);
    }
}
