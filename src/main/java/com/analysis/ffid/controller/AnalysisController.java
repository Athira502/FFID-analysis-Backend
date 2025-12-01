package com.analysis.ffid.controller;

import com.analysis.ffid.dto.AnalysisRequestDTO;
import com.analysis.ffid.dto.AnalysisResponseDTO;
import com.analysis.ffid.service.OllamaAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final OllamaAnalysisService analysisService;

    public AnalysisController(OllamaAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Analyze a firefighter session
     * POST /api/analysis/analyze
     * Body: { "analysisId": "REQ-123456" }
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeSession(@RequestBody AnalysisRequestDTO request) {
        try {
            log.info("Received analysis request for analysis_id: {}", request.getAnalysisId());

            if (request.getAnalysisId() == null || request.getAnalysisId().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("analysis_id is required"));
            }

            AnalysisResponseDTO response = analysisService.analyzeFirefighterSession(
                    request.getAnalysisId()
            );


            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Error during analysis", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during analysis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Analysis failed: " + e.getMessage()));
        }
    }

    /**
     * Analyze by path parameter
     * POST /api/analysis/analyze/{analysisId}
     */
    @PostMapping("/analyze/{analysisId}")
    public ResponseEntity<?> analyzeSessionByPath(@PathVariable String analysisId) {
        AnalysisRequestDTO request = new AnalysisRequestDTO(analysisId);
        return analyzeSession(request);
    }

    /**
     * Health check endpoint
     * GET /api/analysis/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Ollama Analysis Service");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }

    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return error;
    }
}