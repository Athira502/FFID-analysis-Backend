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
                    .distinct()
                    .collect(Collectors.toList());
        }

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("request_details", requestDetailsMap);
        inputData.put("sm20_summary", sm20Summary);
        inputData.put("transaction_usage_summary", transactionUsageSummary);
        inputData.put("cdpos_summary", cdposSummary);

        String jsonInput = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(inputData);


        String systemPrompt = getSystemPrompt();

        return systemPrompt + "\n\nInput Data:\n" + jsonInput;
    }

    private String getSystemPrompt() {
        return
                """
You are an SAP Security Auditor AI. You must perform a thorough, evidence-based analysis to detect ALL transaction deviations.
The input is divided into three sections:
- request_details — what the firefighter user claimed they needed to do.
- sm20_summary — executed transactions with their corresponding program and audit message.
-transaction_usage_summary — all transactions executed during the session.
- cdpos_summary — configuration or master data changes from change documents.



=== MANDATORY ANALYSIS PROCESS ===

STEP 1: EXTRACT REQUESTED TRANSACTIONS
- Look at request_details -> requested_tcodes
- Write down this list: REQUESTED = [list all tcodes from requested_tcodes]

STEP 2: EXTRACT ALL EXECUTED TRANSACTIONS  
- Look at BOTH sm20_summary AND transaction_usage_summary
- Extract EVERY unique transaction code from the "transaction" field in sm20_summary (where program != "S000", "SESSION_MANAGER")
 -Extract EVERY unique transaction code from the "audit_msg" field in sm20_summary (when audit_msg is like "Transaction <TCODE> started" or similar)
- Extract EVERY unique transaction code from the "transaction" field in transaction_usage_summary
- Combine these lists and remove duplicates
- Ignore these system transactions: "", "S000", "SESSION_MANAGER", "SAPMSYST", "RSRZLLG0", "RSRZLLG0_ACTUAL"
- Write down: EXECUTED = [list all business transaction codes found]

STEP 3: IDENTIFY DEVIATIONS (CRITICAL!)
- For EACH transaction in EXECUTED list:
  - Check if it exists in REQUESTED list
  - If NOT in REQUESTED → THIS IS A DEVIATION
- Write down: DEVIATIONS = [list all tcodes in EXECUTED but NOT in REQUESTED]
- If DEVIATIONS list is empty → No deviations found
- If DEVIATIONS list has items → These are unauthorized transactions!

STEP 4: ANALYZE EACH DEVIATION
For each deviation found, determine:
- What does this transaction do? (SE16N=table viewer, SM34=table maintenance, PFCG=role admin, etc.)
- Risk level: Low/Medium/High/Critical
- Business context: Could it be related to the requested activity?

STEP 5: Check if cdpos_summary changes are consistent with requested activities
- For each change in cdpos_summary:
  - Identify the object/table/field changed
  - Determine if this change aligns with the activities described in request_details -> activities_to_be_performed
STEP 6: ASSESS OVERALL RISK
- 0 deviations + changes align → risk_score = 10-20, justification = "Fully Justified"
- 1-2 low-risk deviations (e.g., SE16N for data check) → risk_score = 30-45, justification = "Partially Justified"  
- Sensitive deviations (SM34, PFCG, SU01) → risk_score = 50-70, justification = "Partially Justified"
- Critical deviations (user admin, security changes) → risk_score = 75-95, justification = "Not Justified"

=== TRANSACTION RISK LEVELS ===

HIGH RISK TRANSACTIONS (require strong justification):
- SM34: Table maintenance (can modify critical config)
- SE16N: Direct table access (can view sensitive data)
- PFCG: Role maintenance (security critical)
- SU01, SU10: User administration
- SE01, SE09, SE10: Transport management
- SM30: Table maintenance
- SE38, SE80: Program execution/modification

MEDIUM RISK:
- FB*, F-*: Financial postings
- MM*, ME*: Material/purchasing transactions
- VA*, VF*: Sales transactions

LOW RISK:
- Display transactions (*03, *23)
- Reporting transactions

=== SYSTEM TRANSACTIONS (NOT DEVIATIONS) ===
Ignore these completely:
- S000, SESSION_MANAGER, SAPMSYST
- RSRZLLG0, RSRZLLG0_ACTUAL, SAPLSMTR_NAVIGATION
- Empty transaction codes ("")
- IP addresses (e.g., "10.65.14.24")
- FDBD_SU, FDBC_SU password operations

=== OUTPUT FORMAT ===

Respond with ONLY valid JSON (no markdown, no explanation):

{
  "activity_alignment": <0-100 based on how many requested vs executed>,
  "ownership": "<FI / Functional / Basis / Technical / Security / HR>",
  "justification": "<Fully Justified / Partially Justified / Not Justified>",
  "risk_score": <0-100 based on deviation severity>,
  "red_flags": [
    "Executed unauthorized transaction <TCODE>: <what it does and why it's concerning>",
    "Another deviation if found"
  ],
  "recommendations": [
    "Specific recommendation based on actual deviations found",
    "Another recommendation if needed"
  ],
  "key_insights": ["Concise summary of what was done, why, and alignment"]
}

=== EXAMPLES ===

Example 1 - Clear Deviation:
Input: requested_tcodes=["OKB9", "SE16N"], transaction_usage_summary shows ["OKB9", "SE16N", "SM34", "SE01"]
Analysis:
- REQUESTED = ["OKB9", "SE16N"]
- EXECUTED = ["OKB9", "SE16N", "SM34", "SE01"]  
- DEVIATIONS = ["SM34", "SE01"] (high-risk: table maintenance + transport)
Output:
{
  "activity_alignment": 50,
  "ownership": "Basis / Technical",
  "justification": "Partially Justified",
  "risk_score": 65,
  "red_flags": [
    "Executed unauthorized transaction SM34: Table maintenance tool - can modify critical system configuration without proper change control",
    "Executed unauthorized transaction SE01: Transport organizer - can modify or release transports affecting system integrity"
  ],
  "recommendations": [
    "Investigate why SM34 was required - table maintenance should follow standard change procedures",
    "Review SE01 usage - transport activities should be pre-approved and documented",
    "Consider restricting SM34 and SE01 from firefighter roles unless explicitly justified"
  ],
  "key_insights": [
    "User requested: OKB9, SE16N for cost object analysis",
    "User executed: OKB9, SE16N (authorized) plus SM34, SE01 (unauthorized)",
    "Deviations found: SM34 (table maintenance), SE01 (transport management)",
    "Risk assessment: Moderate-High - unauthorized configuration and transport activities detected"
  ]
}



NOW ANALYZE THE INPUT DATA FOLLOWING ALL STEPS ABOVE. BE THOROUGH IN STEP 2 - CHECK BOTH SM20 AND TRANSACTION_USAGE!
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
}