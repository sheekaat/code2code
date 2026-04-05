package com.code2code.web;

import com.code2code.service.StructuredMigrationOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ConversionController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private static final Path UPLOAD_DIR = Paths.get("conversion/from");
    private static final Path OUTPUT_DIR = Paths.get("conversion/to");

    @Autowired
    public ConversionController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        // Ensure directories exist
        try {
            Files.createDirectories(UPLOAD_DIR);
            Files.createDirectories(OUTPUT_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directories", e);
        }
    }

    /**
     * Get available source and target languages
     */
    @GetMapping("/languages")
    public Map<String, List<String>> getLanguages() {
        Map<String, List<String>> languages = new HashMap<>();
        languages.put("source", Arrays.asList("Tibco BW", ".NET (C#)", "VB6"));
        languages.put("target", Arrays.asList(
            "Java with Spring Boot", 
            "React JS with Spring Boot + WinForms"
        ));
        return languages;
    }

    /**
     * Upload and extract ZIP file
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, String> response = new HashMap<>();
        
        try {
            // Clear previous uploads
            clearDirectory(UPLOAD_DIR);
            
            // Save and extract ZIP
            Path zipPath = UPLOAD_DIR.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), zipPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Extract if it's a ZIP
            if (file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
                extractZip(zipPath, UPLOAD_DIR);
                Files.delete(zipPath); // Delete ZIP after extraction
            }
            
            // Count files
            long fileCount = Files.walk(UPLOAD_DIR)
                .filter(Files::isRegularFile)
                .count();
            
            response.put("status", "success");
            response.put("message", "Uploaded and extracted " + fileCount + " files");
            response.put("uploadPath", UPLOAD_DIR.toString());
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            response.put("status", "error");
            response.put("message", "Failed to upload: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Start conversion with Server-Sent Events for progress
     */
    @GetMapping(value = "/convert", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter convert(
            @RequestParam String sourceLang,
            @RequestParam String targetLang,
            @RequestParam(defaultValue = "false") boolean winformsIntermediate) {
        
        SseEmitter emitter = new SseEmitter(600000L); // 10 minute timeout
        
        executor.execute(() -> {
            try {
                // Clear previous output
                clearDirectory(OUTPUT_DIR);
                
                // Send progress updates
                sendProgress(emitter, "Starting conversion from " + sourceLang + " to " + targetLang);
                
                // Run conversion
                StructuredMigrationOrchestrator orchestrator = new StructuredMigrationOrchestrator();
                
                // Enable WinForms intermediate for VB6 to React JS with Spring Boot + WinForms conversion
                boolean useWinForms = winformsIntermediate || targetLang.contains("WinForms");
                if (useWinForms) {
                    orchestrator.setUseWinFormsIntermediate(true);
                }
                
                sendProgress(emitter, "Phase 1: Analyzing codebase...");
                orchestrator.runStructuredMigration(UPLOAD_DIR, OUTPUT_DIR, targetLang);
                
                // Count converted files
                long convertedCount = Files.walk(OUTPUT_DIR)
                    .filter(Files::isRegularFile)
                    .count();
                
                sendProgress(emitter, "Conversion complete! Generated " + convertedCount + " files.");
                
                Map<String, Object> result = new HashMap<>();
                result.put("type", "complete");
                result.put("filesConverted", convertedCount);
                result.put("outputPath", OUTPUT_DIR.toString());
                emitter.send(SseEmitter.event().data(result));
                
                emitter.complete();
                
            } catch (Exception e) {
                sendProgress(emitter, "Error: " + e.getMessage());
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /**
     * Browse converted files
     */
    @GetMapping("/files")
    public Map<String, Object> browseFiles() throws IOException {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> files = new ArrayList<>();
        
        if (Files.exists(OUTPUT_DIR)) {
            Files.walk(OUTPUT_DIR)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("name", path.getFileName().toString());
                    fileInfo.put("path", OUTPUT_DIR.relativize(path).toString());
                    try {
                        fileInfo.put("size", String.valueOf(Files.size(path)));
                    } catch (IOException e) {
                        fileInfo.put("size", "0");
                    }
                    files.add(fileInfo);
                });
        }
        
        response.put("files", files);
        response.put("count", files.size());
        return response;
    }

    /**
     * Download a converted file
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String path) {
        try {
            Path filePath = OUTPUT_DIR.resolve(path).normalize();
            
            // Security check: ensure file is within output directory
            if (!filePath.startsWith(OUTPUT_DIR)) {
                return ResponseEntity.badRequest().build();
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName() + "\"")
                .body(resource);
                
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * View file content
     */
    @GetMapping("/view")
    public ResponseEntity<String> viewFile(@RequestParam("path") String path) {
        try {
            Path filePath = OUTPUT_DIR.resolve(path).normalize();
            
            // Security check
            if (!filePath.startsWith(OUTPUT_DIR)) {
                return ResponseEntity.badRequest().body("Invalid path");
            }
            
            String content = Files.readString(filePath);
            return ResponseEntity.ok(content);
            
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to read file: " + e.getMessage());
        }
    }

    private void sendProgress(SseEmitter emitter, String message) {
        try {
            Map<String, Object> progress = new HashMap<>();
            progress.put("type", "progress");
            progress.put("message", message);
            progress.put("timestamp", new Date());
            emitter.send(SseEmitter.event().data(progress));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());
                
                // Security: prevent zip slip
                if (!entryPath.normalize().startsWith(targetDir.normalize())) {
                    continue;
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void clearDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
        Files.createDirectories(dir);
    }
}
