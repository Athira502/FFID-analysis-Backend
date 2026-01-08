package com.analysis.ffid.service;

import com.analysis.ffid.dto.AnalysisResponseDTO;
import com.analysis.ffid.model.*;
import com.analysis.ffid.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OllamaAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAnalysisService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final request_detailsRepository requestDetailsRepository;
    private final analysis_resultRepository analysisResultRepository;

    @Value("${ollama.api.url:http://localhost:11434/api/generate}")
    private String ollamaApiUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;



    public OllamaAnalysisService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            request_detailsRepository requestDetailsRepository,
            analysis_resultRepository analysisResultRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.requestDetailsRepository = requestDetailsRepository;
        this.analysisResultRepository = analysisResultRepository;
    }

    @Transactional
    public AnalysisResponseDTO analyzeFirefighterSession(String analysisId) throws Exception {
        log.info("Starting analysis for analysis_id: {}", analysisId);


        request_details requestDetails = requestDetailsRepository.findByIdWithRelations(analysisId)
                .orElseThrow(() -> new RuntimeException("Analysis ID not found: " + analysisId));


        String prompt = buildAnalysisPrompt(requestDetails);

        log.info("Generated prompt for Ollama: {}", prompt);


        String ollamaResponse = callOllamaAPI(prompt);

        log.info
                ("Received response from Ollama:{}",ollamaResponse);

        AnalysisResponseDTO analysisResponse = parseOllamaResponse(ollamaResponse);

        analysis_result result = saveAnalysisResult(requestDetails, analysisResponse);

        analysisResponse.setResultId(result.getResultID());
        analysisResponse.setAnalysisId(analysisId);
        analysisResponse.setAnalyzedTime(result.getAnalyzedTime());

        log.info("Analysis completed successfully for analysis_id: {}", analysisId);

        return analysisResponse;
    }

    private String buildAnalysisPrompt(request_details requestDetails) throws Exception {

        Map<String, Object> requestDetailsMap = new HashMap<>();
        requestDetailsMap.put("ITSM_number", requestDetails.getItsmNumber());

        List<String> tcodesList = new ArrayList<>();
        if (requestDetails.getTcodes() != null && !requestDetails.getTcodes().isEmpty()) {
            tcodesList = Arrays.asList(requestDetails.getTcodes().split("[,;\\s]+"));
        }
        requestDetailsMap.put("requested_tcodes", tcodesList);
        requestDetailsMap.put("reason", requestDetails.getReason());
        requestDetailsMap.put("activities", requestDetails.getActivities_to_be_performed());


        List<Map<String, String>> sm20Summary = new ArrayList<>();
        if (requestDetails.getSm20Entries() != null) {
            sm20Summary = requestDetails.getSm20Entries().stream()
                    .filter(sm20 -> {
                        String sourceTA = sm20.getSourceTA() != null ? sm20.getSourceTA().trim() : "";
                        String auditMsg = sm20.getAuditLogMsgText() != null ? sm20.getAuditLogMsgText().trim() : "";
                        String auditMsgLower = auditMsg.toLowerCase();


                        if ("S000".equalsIgnoreCase(sourceTA)) {
                            if (auditMsgLower.contains("transaction") && auditMsgLower.contains("started")) {
                                if (auditMsgLower.contains("session_manager")) {
                                    return false;
                                }
                                return true;
                            }
                            return false;
                        }


                        if ("SESSION_MANAGER".equalsIgnoreCase(sourceTA)) {
                            return false;
                        }


                        if ("SAPMSYST".equalsIgnoreCase(sourceTA)
                                || "RSRZLLG0".equalsIgnoreCase(sourceTA)
                                || sourceTA.isEmpty()) {
                            return false;
                        }


                        return true;
                    })
                    .map(sm20 -> {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("transaction", sm20.getSourceTA() != null ? sm20.getSourceTA() : "");
                        entry.put("program", sm20.getProgram() != null ? sm20.getProgram() : "");
                        entry.put("audit_msg", sm20.getAuditLogMsgText() != null ? sm20.getAuditLogMsgText() : "");
                        return entry;
                    })
                    .distinct()
                    .collect(Collectors.toList());
        }



        List<Map<String, String>> transactionUsageSummary = new ArrayList<>();
        if (requestDetails.getTransactionUsages() != null) {
            transactionUsageSummary = requestDetails.getTransactionUsages().stream()
                    .map(transaction_usage -> {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("transaction", transaction_usage.getTcode() != null ?transaction_usage.getTcode() : "");
                        entry.put("program", transaction_usage.getProgram() != null ?transaction_usage.getProgram()  : "");
                        return entry;
                    })
                    .distinct()
                    .collect(Collectors.toList());
        }

        List<String> allUniqueExecutedTCodes = extractUniqueTCodes(sm20Summary, transactionUsageSummary);

        List<Map<String, String>> cdposSummary = new ArrayList<>();
        if (requestDetails.getCdhdrEntries() != null) {
            cdposSummary = requestDetails.getCdhdrEntries().stream()
                    .map(cdpos -> {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("object", cdpos.getObject() != null ? cdpos.getObject() : "");
                        entry.put("object_value", cdpos.getObjectValue() != null ? cdpos.getObjectValue() : "");
                        entry.put("table", cdpos.getTableName() != null ? cdpos.getTableName() : "");
                        entry.put("field", cdpos.getFieldName() != null ? cdpos.getFieldName() : "");
                        return entry;
                    })
                    .distinct()
                    .collect(Collectors.toList());
        }

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("request_details", requestDetailsMap);
        inputData.put("sm20_summary", sm20Summary);
        inputData.put("transaction_usage_summary", transactionUsageSummary);
        inputData.put("all_executed_tcodes", allUniqueExecutedTCodes);
        inputData.put("cdpos_summary", cdposSummary);

        String jsonInput = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(inputData);


        String systemPrompt = getSystemPrompt();

        return systemPrompt + "\n\nInput Data:\n" + jsonInput;
    }

    private String getSystemPrompt() {
        return
                """
                        You are an expert SAP Security Auditor. Your goal is to compare requested access against actual system activity to identify any unauthorized actions or security risks.
                        
                        INPUT DATA DATA SETS:
                        
                        request_details: The authorized scope (Requested T-Codes & Business Reason).
                        
                        all_executed_tcodes: The source of truth for every transaction actually started.
                        
                        sm20_summary & transaction_usage_summary: Contextual logs (Programs/Audit messages).
                        
                        cdpos_summary: Actual data/configuration changes made.
                        
                        AUDIT RULES:
                        
                        ZERO TOLERANCE: Any transaction in 'all_executed_tcodes' that is not an EXACT character-for-character match to 'requested_tcodes' is a DEVIATION. (Exception: Ignore S000, SESSION_MANAGER, SAPMSYST).
                        
                        EVIDENCE-BASED: If a transaction appears in 'all_executed_tcodes', it was executed. Do not assume it was a mistake or a "test."
                        
                        TYPO HANDLING: If an executed code (e.g., FTXP) is a clear typo of a requested code (e.g., FXTP), flag it as a deviation but note the likely intent in 'key_insights'.
                        
                        RISK SCORING:
                        
                        0 Deviations: 0-20 Risk (Fully Justified)
                        
                        Low Risk Deviations (Display/Reporting): 30-50 Risk (Partially Justified)
                        
                        High Risk Deviations (SE16N, SM30, SU01, PFCG, SE38): 70-100 Risk (Not Justified)
                        
                        YOUR TASK:
                        
                        Identify every T-Code in 'all_executed_tcodes' that is missing from 'requested_tcodes'.
                        
                        Cross-reference these deviations with 'sm20_summary' to see what the user actually did.
                        
                        Check 'cdpos_summary' to see if the changes (tables/objects) match the requested business reason.
                        
                        Summarize the findings into the JSON format below.
                        
                        REQUIRED JSON OUTPUT FORMAT:
                        
                        (Respond ONLY with JSON. No intro, no markdown, no summary.)
                        
                        {
                        "activity_alignment": <integer 0-100: percentage of executed work that was authorized>,
                        "ownership": "<FI / Basis / Technical / Security / HR / MM>",
                        "justification": "<Fully Justified / Partially Justified / Not Justified>",
                        "risk_score": <integer 0-100>,
                        "red_flags": [
                        "List each unauthorized T-Code found and the specific risk it poses to the system."
                        ],
                        "recommendations": [
                        "Actionable steps for the reviewer (e.g., 'Revoke access', 'Ask for justification for T-Code X')."
                        ],
                        "key_insights": [
                        "A concise summary of the session: what was requested vs. what was actually done."
                        ]
                        }

                        """


                ;
    }

    private String callOllamaAPI(String prompt) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", ollamaModel);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("format", "json");


        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.1);
        options.put("top_p", 0.1);
        options.put("repeat_penalty", 1.2);
        options.put("num_ctx", 8192);

        requestBody.put("options", options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    ollamaApiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return jsonResponse.get("response").asText();
            } else {
                throw new RuntimeException("Ollama API returned non-200 status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling Ollama API", e);
            throw new RuntimeException("Failed to get response from Ollama: " + e.getMessage(), e);
        }
    }


    private AnalysisResponseDTO parseOllamaResponse(String ollamaResponse) throws Exception {

        String cleanedResponse = ollamaResponse.trim();
        log.info("Raw Ollama response: {}", ollamaResponse);

        if (cleanedResponse.startsWith("```")) {
            cleanedResponse = cleanedResponse.replaceAll("```[a-zA-Z]*", "").replace("```", "").trim();
        }

        JsonNode jsonNode = objectMapper.readTree(cleanedResponse);

        AnalysisResponseDTO response = new AnalysisResponseDTO();

        JsonNode safe;

        safe = jsonNode.get("activity_alignment");
        response.setActivityAlignment(safe != null ? safe.asInt() : 0);

        safe = jsonNode.get("ownership");
        response.setOwnership(safe != null ? safe.asText() : "Unknown");

        safe = jsonNode.get("justification");
        response.setJustification(safe != null ? safe.asText() : "Not Provided");

        safe = jsonNode.get("risk_score");
        response.setRiskScore(safe != null ? safe.asInt() : 0);

        response.setRedFlags(extractSafeList(jsonNode.get("red_flags")));
        response.setRecommendations(extractSafeList(jsonNode.get("recommendations")));
        response.setKeyInsights(extractSafeList(jsonNode.get("key_insights")));

        return response;
    }

    private List<String> extractSafeList(JsonNode node) {
        List<String> list = new ArrayList<>();

        if (node == null) return list;

        if (node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        } else if (node.isTextual()) {
            list.add(node.asText());
        }

        return list;
    }




    private analysis_result saveAnalysisResult(
            request_details requestDetails,
            AnalysisResponseDTO response) throws Exception {

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.debug("KeyInsight before seting: {}", response.getKeyInsights());

        analysis_result result = new analysis_result();
        result.setRequestDetails(requestDetails);
        result.setAnalyzed_time(timestamp);
        result.setActivity_alignment(String.valueOf(response.getActivityAlignment()));
        result.setOwnership(response.getOwnership());
        result.setJustification(response.getJustification());
        result.setRisk_score(String.valueOf(response.getRiskScore()));
        result.setRed_flags(objectMapper.writeValueAsString(response.getRedFlags()));
        result.setRecommendations(objectMapper.writeValueAsString(response.getRecommendations()));
        result.setKeyInsight(objectMapper.writeValueAsString(response.getKeyInsights()));
        log.debug("KeyInsight after setting: {}",result.getKeyInsight());

        return analysisResultRepository.save(result);
    }

    private List<String> extractUniqueTCodes(List<Map<String, String>> sm20Summary,
                                             List<Map<String, String>> transactionUsageSummary) {
        Set<String> excludedCodes = Set.of("S000", "SESSION_MANAGER", "SAPMSYST", "RSRZLLG0");
        return Stream.concat(
                        sm20Summary.stream().map(m -> m.get("transaction")),
                        transactionUsageSummary.stream().map(m -> m.get("transaction"))
                )

        .filter(tcode -> tcode != null && !tcode.trim().isEmpty())
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(tcode -> !excludedCodes.contains(tcode))
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}