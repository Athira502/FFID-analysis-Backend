package com.analysis.ffid.controller;

import com.analysis.ffid.service.cdhdr_cdposService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cdhdr-cdpos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class cdhdr_cdposController {

    private final cdhdr_cdposService cdhdrCdposService;

    @PostMapping("/upload-cdhdr")
    public ResponseEntity<Map<String, Object>> uploadCdhdr(
            @RequestParam("file") MultipartFile file,
            @RequestParam("analysisId") String analysisId) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Received CDHDR upload request for analysis ID: {}, filename: {}",
                    analysisId, file.getOriginalFilename());

            if (analysisId == null || analysisId.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Analysis ID is required");
                return ResponseEntity.badRequest().body(response);
            }

            int recordsUploaded = cdhdrCdposService.uploadCdhdrData(file, analysisId);

            response.put("success", true);
            response.put("message", "CDHDR file uploaded successfully");
            response.put("recordsUploaded", recordsUploaded);
            response.put("analysisId", analysisId);
            response.put("nextStep", "Upload CDPOS file to complete the data merge");
            response.put("timestamp", System.currentTimeMillis());

            log.info("CDHDR upload successful for analysis ID: {}, records: {}",
                    analysisId, recordsUploaded);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error during CDHDR upload: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorType", "VALIDATION_ERROR");
            return ResponseEntity.badRequest().body(response);

        } catch (RuntimeException e) {
            log.error("Runtime error during CDHDR upload: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorType", "NOT_FOUND");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            log.error("Error uploading CDHDR file for analysis ID: {}", analysisId, e);
            response.put("success", false);
            response.put("error", "Failed to upload CDHDR file: " + e.getMessage());
            response.put("errorType", "INTERNAL_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/upload-cdpos")
    public ResponseEntity<Map<String, Object>> uploadCdpos(
            @RequestParam("file") MultipartFile file,
            @RequestParam("analysisId") String analysisId) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Received CDPOS upload request for analysis ID: {}, filename: {}",
                    analysisId, file.getOriginalFilename());

            if (analysisId == null || analysisId.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Analysis ID is required");
                return ResponseEntity.badRequest().body(response);
            }

            int recordsMatched = cdhdrCdposService.uploadCdposData(file, analysisId);

            response.put("success", true);
            response.put("message", "CDPOS file uploaded and merged successfully");
            response.put("recordsMatched", recordsMatched);
            response.put("analysisId", analysisId);
            response.put("timestamp", System.currentTimeMillis());

            log.info("CDPOS upload successful for analysis ID: {}, matched records: {}",
                    analysisId, recordsMatched);

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.error("State error during CDPOS upload: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorType", "PRECONDITION_FAILED");
            response.put("hint", "Please upload CDHDR file first before uploading CDPOS");
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error during CDPOS upload: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorType", "VALIDATION_ERROR");
            return ResponseEntity.badRequest().body(response);

        } catch (RuntimeException e) {
            log.error("Runtime error during CDPOS upload: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorType", "NOT_FOUND");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            log.error("Error uploading CDPOS file for analysis ID: {}", analysisId, e);
            response.put("success", false);
            response.put("error", "Failed to upload CDPOS file: " + e.getMessage());
            response.put("errorType", "INTERNAL_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUploadStats(
            @RequestParam("analysisId") String analysisId) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Fetching upload statistics for analysis ID: {}", analysisId);

            if (analysisId == null || analysisId.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Analysis ID is required");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Long> stats = cdhdrCdposService.getUploadStats(analysisId);

            response.put("success", true);
            response.put("analysisId", analysisId);
            response.put("statistics", stats);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Error fetching stats for analysis ID: {}", analysisId, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorType", "NOT_FOUND");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }


    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "CDHDR-CDPOS Upload Service");
        response.put("version", "1.0.0");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getApiInfo() {
        Map<String, Object> response = new HashMap<>();

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("POST /upload-cdhdr", "Upload CDHDR header file (must be first)");
        endpoints.put("POST /upload-cdpos", "Upload CDPOS position file (must be second)");
        endpoints.put("GET /stats", "Get upload statistics for an analysis");
        endpoints.put("GET /unmatched", "Get unmatched CDHDR records");
        endpoints.put("DELETE /delete", "Delete all data for an analysis");
        endpoints.put("GET /health", "Health check endpoint");
        endpoints.put("GET /info", "API information (this endpoint)");

        response.put("serviceName", "CDHDR-CDPOS Upload Service");
        response.put("version", "1.0.0");
        response.put("description", "Service for uploading and merging SAP Change Document data");
        response.put("endpoints", endpoints);
        response.put("uploadSequence", new String[]{"1. Upload CDHDR", "2. Upload CDPOS", "3. Check stats"});

        return ResponseEntity.ok(response);
    }


}