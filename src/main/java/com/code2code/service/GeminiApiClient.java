package com.code2code.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for Google Gemini API to convert code between various languages.
 */
public class GeminiApiClient {
    
    private static final String GEMINI_API_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    
    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private final String model;
    
    public GeminiApiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "gemini-2.5-flash";
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        this.gson = new Gson();
    }
    
    /**
     * Converts .NET code to Java using Gemini API.
     * 
     * @param dotNetCode The .NET source code to convert
     * @param fileName The original file name
     * @return The converted Java code
     */
    public String convertToJava(String dotNetCode, String fileName) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your_gemini_api_key_here")) {
            throw new IllegalStateException("Gemini API key not configured. Set google.api.key in application.properties.");
        }
        
        String prompt = buildConversionPrompt(dotNetCode, fileName);
        
        try {
            String response = callGeminiApi(prompt);
            return extractJavaCode(response);
        } catch (Exception e) {
            System.err.println("Gemini API call failed: " + e.getMessage());
            // Return placeholder code on failure
            return generatePlaceholderCode(dotNetCode, fileName);
        }
    }
    
    private String buildConversionPrompt(String dotNetCode, String fileName) {
        return """
            You are an expert .NET to Java code converter.
            
            Convert the following .NET code file to valid Java 17 code.
            
            File: %s
            
            Original code:
            ```
            %s
            ```
            
            Conversion rules:
            1. Convert C# types to Java equivalents (string -> String, int -> int, bool -> boolean, etc.)
            2. Convert C# properties to Java getters/setters
            3. Convert C# events to Java Observer pattern or listeners
            4. Convert LINQ to Java Streams where applicable
            5. Convert C# collections (List<T>, Dictionary<K,V>) to Java equivalents (List<T>, Map<K,V>)
            6. Convert C# generics syntax to Java generics
            7. Convert C# namespaces to Java packages using proper package naming (com.company.project)
            8. Convert C# attributes to Java annotations
            9. Convert async/await to CompletableFuture where applicable
            10. Convert Entity Framework to Spring Data JPA patterns
            
            IMPORTANT - Multiple Classes Handling:
            - If the file contains multiple class definitions (e.g., public class BreadthFirstSearch AND public class BFSVertex),
            - Extract the PRIMARY class that matches the filename as the main public class
            - For nested/dependent classes (like BFSVertex, helper classes), convert them as static inner classes OR 
              create them as package-private separate classes within the same file
            - Follow Java convention: ONE public class per file, named after the file
            - Use proper OOP: favor composition over inheritance, use interfaces appropriately
            - Make helper classes static inner classes or package-private top-level classes
            
            Output ONLY the converted Java code with proper package declaration.
            Do not include any explanations, markdown formatting, or code blocks.
            Just the raw Java code that can be compiled directly.
            """.formatted(fileName, dotNetCode);
    }
    
    private String callGeminiApi(String prompt) throws Exception {
        String url = GEMINI_API_URL.formatted(model, apiKey);
        
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);
        
        // Configure generation parameters
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);
        generationConfig.addProperty("maxOutputTokens", 65536);
        requestBody.add("generationConfig", generationConfig);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("API returned status " + response.statusCode() + ": " + response.body());
        }
        
        return response.body();
    }
    
    private String extractJavaCode(String apiResponse) {
        try {
            JsonObject root = gson.fromJson(apiResponse, JsonObject.class);
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            
            // Check for truncation
            String finishReason = candidate.has("finishReason") ? candidate.get("finishReason").getAsString() : "";
            if ("MAX_TOKENS".equals(finishReason)) {
                System.err.println("WARNING: Gemini API response was truncated (MAX_TOKENS). Consider increasing maxOutputTokens or reducing input size.");
            }
            
            JsonObject content = candidate.getAsJsonObject("content");
            if (content == null) {
                return null;
            }
            
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.isEmpty()) {
                return null;
            }
            
            String text = parts.get(0).getAsJsonObject().get("text").getAsString();
            
            // Clean up the response - remove markdown code blocks if present
            text = text.trim();
            if (text.startsWith("```java")) {
                text = text.substring(7);
            } else if (text.startsWith("```")) {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            
            return text.trim();
        } catch (Exception e) {
            System.err.println("Failed to parse API response: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Generates text using Gemini API with a custom prompt.
     * Used for pattern extraction, validation, and other non-conversion tasks.
     */
    public String generateText(String prompt) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your_gemini_api_key_here")) {
            System.err.println("Gemini API key not configured. Returning empty response.");
            return "";
        }
        
        try {
            String response = callGeminiApi(prompt);
            return extractText(response);
        } catch (Exception e) {
            System.err.println("Gemini API text generation failed: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Converts code with a custom prompt (used by chunked converter).
     */
    public String code2code(String prompt, String sourceCode) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your_google_api_key_here") || apiKey.equals("your_gemini_api_key_here")) {
            System.err.println("ERROR: Gemini API key not configured. Cannot convert code without AI assistance.");
            System.err.println("Please set a valid google.api.key in application.properties to enable code conversion.");
            return sourceCode; // Return original source unchanged
        }
        
        try {
            String fullPrompt = prompt + "\n\nSource code to convert:\n```\n" + sourceCode + "\n```\n\nIMPORTANT: Return the COMPLETE converted code with ALL imports fully qualified (e.g., 'import io.swagger.v3.oas.models.OpenAPI;' not 'import io.'). Do not truncate or abbreviate any import statements. Include the entire file content from package declaration to closing brace.";
            String response = callGeminiApi(fullPrompt);
            String converted = extractJavaCode(response);
            return converted != null ? converted : sourceCode;
        } catch (Exception e) {
            System.err.println("Gemini API code conversion failed: " + e.getMessage());
            return sourceCode;
        }
    }
    
    private String extractText(String apiResponse) {
        try {
            JsonObject root = gson.fromJson(apiResponse, JsonObject.class);
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "";
            }
            
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject content = candidate.getAsJsonObject("content");
            if (content == null) {
                return "";
            }
            
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.isEmpty()) {
                return "";
            }
            
            return parts.get(0).getAsJsonObject().get("text").getAsString();
        } catch (Exception e) {
            System.err.println("Failed to extract text from API response: " + e.getMessage());
            return "";
        }
    }
    
    private String generatePlaceholderCode(String dotNetCode, String fileName) {
        String className = fileName.replace(".cs", "").replace(".vb", "").replace(".fs", "");
        StringBuilder javaCode = new StringBuilder();
        javaCode.append("// Converted from: ").append(fileName).append("\n");
        javaCode.append("// TODO: Manual conversion required (AI conversion failed)\n\n");
        javaCode.append("public class ").append(className).append(" {\n");
        javaCode.append("    /*\n");
        javaCode.append("    Original code:\n");
        javaCode.append(dotNetCode.replace("*/", "* /"));
        javaCode.append("    */\n");
        javaCode.append("}\n");
        return javaCode.toString();
    }
}
