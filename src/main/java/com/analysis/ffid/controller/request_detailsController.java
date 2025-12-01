package com.analysis.ffid.controller;

import com.analysis.ffid.model.request_details;
import com.analysis.ffid.service.request_detailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/firefighter")
@RequiredArgsConstructor
public class request_detailsController {

    private final request_detailsService service;

    @PostMapping("/request")
    public ResponseEntity<request_details> createRequest(@RequestBody request_details request) {
        request_details savedRequest = service.createRequest(request);
        return ResponseEntity.ok(savedRequest);
    }

    @PostMapping("/transaction-log/{requestId}")
    public ResponseEntity<String> uploadTransactionLog(
            @PathVariable String requestId,
            @RequestParam("file") MultipartFile file) {

        try {
            service.getRequestById(requestId).ifPresentOrElse(
                    request -> {
                        try {
                            service.uploadTransactionLog(file, request);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    },
                    () -> {
                        throw new IllegalArgumentException("Request ID not found: " + requestId);
                    }
            );
            return ResponseEntity.ok("Transaction log Excel uploaded and stored successfully!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error uploading transaction log: " + e.getMessage());
        }
    }

    @PostMapping("/sm20-log/{requestId}")
    public ResponseEntity<String> uploadSM20Log(
            @PathVariable String requestId,
            @RequestParam("file") MultipartFile file) {

        try {
            service.getRequestById(requestId).ifPresentOrElse(
                    request -> {
                        try {
                            service.uploadSM20Log(file, request);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    },
                    () -> {
                        throw new IllegalArgumentException("Request ID not found: " + requestId);
                    }
            );
            return ResponseEntity.ok("SM20 log Excel uploaded and stored successfully!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error uploading SM20 log: " + e.getMessage());
        }
    }
}
