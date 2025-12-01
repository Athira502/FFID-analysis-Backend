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
                    .map(sm20 -> {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("transaction", sm20.getSourceTA() != null ? sm20.getSourceTA() : "");
                        entry.put("program", sm20.getProgram() != null ? sm20.getProgram() : "");
                        entry.put("audit_msg", sm20.getAuditLogMsgText() != null ? sm20.getAuditLogMsgText() : "");
                        return entry;
                    })
                    .collect(Collectors.toList());
        }


        List<Map<String, String>> cdposSummary = new ArrayList<>();
        if (requestDetails.getCdhdrEntries() != null) {
            cdposSummary = requestDetails.getCdhdrEntries().stream()
                    .map(cdpos -> {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("object", cdpos.getObject() != null ? cdpos.getObject() : "");
                        entry.put("object_value", cdpos.getObjectValue() != null ? cdpos.getObjectValue() : "");
                        entry.put("table", cdpos.getTableName() != null ? cdpos.getTableName() : "");
                        entry.put("field", cdpos.getFieldName() != null ? cdpos.getFieldName() : "");
                        entry.put("old_value", cdpos.getOldValue() != null ? cdpos.getOldValue() : "");
                        entry.put("new_value", cdpos.getNewValue() != null ? cdpos.getNewValue() : "");
                        return entry;
                    })
                    .collect(Collectors.toList());
        }

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("request_details", requestDetailsMap);
        inputData.put("sm20_summary", sm20Summary);
        inputData.put("cdpos_summary", cdposSummary);

        String jsonInput = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(inputData);


        String systemPrompt = getSystemPrompt();

        return systemPrompt + "\n\nInput Data:\n" + jsonInput;
    }

    private String getSystemPrompt() {
        return """
You are an SAP Security Auditor AI specializing in Firefighter session reviews. Your goal is to produce a contextual, risk-aware audit analysis of the provided session data.

The input is divided into three sections:
- request_details — what the firefighter user claimed they needed to do.
- sm20_summary — executed transactions with their corresponding program and audit message.
- cdpos_summary — configuration or master data changes from change documents.

Analysis Objectives:
1. Correlate each executed transaction (sm20_summary) with the intent stated in request_details.
2. Evaluate any deviations to see if they could still be justified by context.
3. Analyze cdpos_summary changes to confirm whether master data or configuration modifications are consistent with the scope of the request.
4. Identify unrequested or risky actions.
5. Assign ownership (Functional / Basis / Security / FI / HR / Technical) based on the nature of executed activities.
6. Assess justification level (Fully / Partially / Not Justified) based on correlation strength.
7. Calculate a balanced risk score (0-100) considering transaction sensitivity, business justification, and evidence of control closure.

Output Requirements:
Respond ONLY with valid JSON in this exact structure (no markdown, no explanation, no code blocks):
{
  "activity_alignment": 0-100,
  "ownership": "Functional / Technical / Basis / Security / FI / HR",
  "justification": "Fully / Partially / Not Justified",
  "risk_score": 0-100,
  "red_flags": ["List of clear anomalies or potential risks"],
  "recommendations": ["Short, actionable improvements"],
  "key_insights": ["Concise summary of what was done, why, and alignment"]
}

Context Notes:
- S000 and SESSION_MANAGER transactions are core session-handling and logon framework functions in SAP. Their presence in the log generally reflects routine UI or session initialization behavior and is not considered a deviation.
- Password changes for Firefighter IDs (including FDBD_SU / FDBC_SU) are a mandatory step during logon and should not be flagged as a deviation unless additional unrelated or risky actions occur afterward.
- Initial “password check failed” messages for Firefighter IDs (e.g., FDBD_SU, FDBC_SU) are expected during the mandatory password-reset logon sequence and must not be treated as red flags or deviations.
- Temporary assignment and removal of a test role show good control behavior.
- Risk increases if data viewing extends beyond necessary tables or if role changes impact production users.
""";
    }

    private String callOllamaAPI(String prompt) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", ollamaModel);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("format", "json");

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
}