
package com.analysis.ffid.service;

import com.analysis.ffid.model.*;
import com.analysis.ffid.repository.*;
import jakarta.persistence.Column;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.analysis.ffid.dto.*;
import com.analysis.ffid.model.*;
import com.analysis.ffid.repository.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.*;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class request_detailsService {

    private final request_detailsRepository requestRepo;
    private final transaction_usageRepository transactionRepo;
    private final sm20Repository sm20Repo;
    private final client_systemRepository clientSystemRepo;

    private final cdhdr_cdposRepository cdhdrCdposRepo;
    private final analysis_resultRepository analysisResultRepo;

    @Transactional  // ADD THIS
    public request_details createRequest(request_details request) {
        log.info("===== Creating Request =====");
        log.info("ITSM Number: {}", request.getItsmNumber());
        log.info("Client: {}, System: {}", request.getClient(), request.getSystem());

        Optional<client_system> existingCS = clientSystemRepo.findByClientAndSystem(
                request.getClient(),
                request.getSystem()
        );

        client_system csEntity;
        if (existingCS.isPresent()) {
            csEntity = existingCS.get();
            log.info("Found existing client_system with ID: {}", csEntity.getCS_ID());
        } else {
            csEntity = new client_system();
            csEntity.setCS_ID("CS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            csEntity.setClient(request.getClient());
            csEntity.setSystem(request.getSystem());
            csEntity = clientSystemRepo.saveAndFlush(csEntity);
            log.info("Created new client_system with ID: {}", csEntity.getCS_ID());
        }

        request.setClientSystem(csEntity);
        request_details saved = requestRepo.save(request);
        log.info("Request saved successfully with analysisID: {}", saved.getAnalysisID());

        return saved;
    }

    public Optional<request_details> getRequestById(String analysisId) {
        log.info("Fetching request by ID: {}", analysisId);
        return requestRepo.findById(analysisId);
    }

    public List<RequestListDTO> getAllRequests() {
        log.info("Fetching all requests");
        List<request_details> requests = requestRepo.findAll();

        return requests.stream()
                .map(req -> RequestListDTO.builder()
                        .analysisId(req.getAnalysisID())
                        .itsmNumber(req.getItsmNumber())
                        .client(req.getClient())
                        .system(req.getSystem())
                        .requestedFor(req.getRequestedFor())
                        .requestedDate(req.getRequestedDate())
                        .usedDate(req.getUsedDate())
                        .build())
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public RequestDetailsDTO getRequestDetails(String analysisId) {
        log.info("Fetching complete details for: {}", analysisId);

        request_details request = requestRepo.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + analysisId));


        List<TransactionUsageDTO> transactionUsage = transactionRepo
                .findByRequestDetails(request)
                .stream()
                .map(tu -> TransactionUsageDTO.builder()
                        .timestamp(tu.getTime())
                        .transaction(tu.getTcode())
                        .description(tu.getProgram())
                        .user(request.getRequestedFor())
                        .client(request.getClient())
                        .system(request.getSystem())
                        .build())
                .collect(Collectors.toList());


        List<SM20DTO> auditLogs = sm20Repo
                .findByRequestDetails(request)
                .stream()
                .map(sm -> SM20DTO.builder()
                        .timestamp(sm.getEntrydate() + " " + sm.getEntrytime())
                        .action(sm.getClient())
                        .terminal(sm.getTerminal())
                        .object(sm.getSourceTA())
                        .program(sm.getProgram())
                        .details(sm.getAuditLogMsgText())
                        .build())
                .collect(Collectors.toList());




        List<CdhdrCdposDTO> changeDocLogs = cdhdrCdposRepo
                .findByRequestDetails(request)
                .stream()
                .map(cd -> CdhdrCdposDTO.builder()
                        .timestamp(cd.getEntryDate() + " " + cd.getEntryTime())
                        .table(cd.getTableName())
                        .field(cd.getFieldName())
                        .oldValue(cd.getOldValue())
                        .newValue(cd.getNewValue())
                        .user(cd.getUsername())
                        .build())
                .collect(Collectors.toList());

        AnalysisInsightsDTO aiInsights = getAnalysisInsights(request);

        return RequestDetailsDTO.builder()
                .analysisId(request.getAnalysisID())
                .itsmNumber(request.getItsmNumber())
                .client(request.getClient())
                .system(request.getSystem())
                .requestedFor(request.getRequestedFor())
                .requestedOnBehalfOf(request.getRequested_on_behalfof())
                .requestedDate(request.getRequestedDate())
                .usedDate(request.getUsedDate())
                .tcodes(request.getTcodes())
                .reason(request.getReason())
                .activities(request.getActivities_to_be_performed())
                .transactionUsage(transactionUsage)
                .auditLogs(auditLogs)
                .changeDocLogs(changeDocLogs)
                .aiInsights(aiInsights)
                .build();
    }

    /**
     * Get AI Analysis Insights from analysis_result table
     */
    private AnalysisInsightsDTO getAnalysisInsights(request_details request) {
        List<analysis_result> results = analysisResultRepo.findByRequestDetails(request);

        if (results.isEmpty()) {
            log.info("No analysis results found for request: {}", request.getAnalysisID());
            return AnalysisInsightsDTO.builder()
                    .activityAlignment(0)
                    .ownership("Unknown")
                    .justification("Pending")
                    .riskScore(0)
                    .redFlags(List.of("Analysis pending"))
                    .recommendations(List.of("Analysis not yet completed"))
                    .keyInsights(List.of("No insights available yet"))
                    .build();
        }

        // Get the latest analysis result
        analysis_result latestResult = results.get(0);

        return AnalysisInsightsDTO.builder()
                .activityAlignment(parseActivityAlignment(latestResult.getActivityAlignment()))
                .ownership(latestResult.getOwnership() != null ? latestResult.getOwnership() : "Unknown")
                .justification(latestResult.getJustification() != null ? latestResult.getJustification() : "Pending")
                .riskScore(parseRiskScore(latestResult.getRisk_score()))
                .redFlags(parseCommaSeparatedList(latestResult.getRed_flags()))
                .recommendations(parseCommaSeparatedList(latestResult.getRecommendations()))
                .keyInsights(parseKeyInsights(latestResult.getKeyInsight()))
                .build();
    }

    /**
     * Parse activity alignment percentage from string
     */
    private Integer parseActivityAlignment(String activityAlignment) {
        if (activityAlignment == null || activityAlignment.trim().isEmpty()) {
            return 0;
        }
        try {
            // Remove any non-numeric characters except digits
            String numStr = activityAlignment.replaceAll("[^0-9]", "");
            if (!numStr.isEmpty()) {
                int value = Integer.parseInt(numStr);
                return Math.min(100, Math.max(0, value)); // Ensure 0-100 range
            }
        } catch (Exception e) {
            log.warn("Could not parse activity alignment: {}", activityAlignment, e);
        }
        return 0;
    }


    private Integer parseRiskScore(String riskScore) {
        if (riskScore == null || riskScore.trim().isEmpty()) {
            return 0;
        }
        try {
            // Remove any non-numeric characters
            String numStr = riskScore.replaceAll("[^0-9]", "");
            if (!numStr.isEmpty()) {
                int value = Integer.parseInt(numStr);
                return Math.min(100, Math.max(0, value)); // Ensure 0-100 range
            }
        } catch (Exception e) {
            log.warn("Could not parse risk score: {}", riskScore, e);
        }
        return 0;
    }


    private List<String> parseCommaSeparatedList(String input) {
        if (input == null || input.trim().isEmpty()) {
            return List.of();
        }
        String cleaned = input.replaceAll("[\\[\\]\"]", "");
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Parse key insights - could be comma-separated or newline-separated
     */
    private List<String> parseKeyInsights(String keyInsight) {
        if (keyInsight == null || keyInsight.trim().isEmpty()) {
            return List.of();
        }
        String cleaned = keyInsight.replaceAll("[\\[\\]\"]", "");



        if (keyInsight.contains("\n")) {
            return Arrays.stream(cleaned.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } else {
            return parseCommaSeparatedList(keyInsight);
        }
    }

    @Transactional
    public void uploadTransactionLog(MultipartFile file, request_details request) throws Exception {
        log.info("===== Uploading Transaction Log =====");
        log.info("Request ID: {}", request.getAnalysisID());
        log.info("File name: {}", file.getOriginalFilename());
        log.info("File size: {} bytes", file.getSize());

        int recordCount = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();


            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                log.info("Skipping header row");
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                transaction_usage log = transaction_usage.builder()
                        .requestDetails(request)
                        .time(getCellValue(row, 0))
                        .tcode(getCellValue(row, 1))
                        .program(getCellValue(row, 2))
                        .build();

                transactionRepo.save(log);
                recordCount++;
            }

            log.info("Transaction log upload completed: {} records saved", recordCount);
        } catch (Exception e) {
            log.error("Error uploading transaction log", e);
            throw new RuntimeException("Failed to upload transaction log: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void uploadSM20Log(MultipartFile file, request_details request) throws Exception {
        log.info("===== Uploading SM20 Log =====");
        log.info("Request ID: {}", request.getAnalysisID());
        log.info("File name: {}", file.getOriginalFilename());
        log.info("File size: {} bytes", file.getSize());

        int recordCount = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                log.info("Skipping header row");
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                sm20 logEntry = sm20.builder()
                        .requestDetails(request)
                        .sapSystem(getCellValue(row, 0))
                        .asInstance(getCellValue(row, 1))
                        .entrydate(getDateValue(row, 2))
                        .entrytime(getTimeValue(row, 3))
                        .client(getCellValue(row, 4))
                        .event(getCellValue(row, 5))
                        .username(getCellValue(row, 6))
                        .groupname(getCellValue(row, 7))
                        .terminal(getCellValue(row, 8))
                        .peer(getCellValue(row, 9))
                        .sourceTA(getCellValue(row, 10))
                        .program(getCellValue(row, 11))
                        .auditLogMsgText(getCellValue(row, 12))
                        .note(getCellValue(row, 13))
                        .variableMessageData(getCellValue(row, 14))
                        .variable2(getCellValue(row, 15))
                        .variableData(getCellValue(row, 16))
                        .build();

                sm20Repo.save(logEntry);
                recordCount++;
            }

            log.info("SM20 log upload completed: {} records saved", recordCount);
        } catch (Exception e) {
            log.error("Error uploading SM20 log", e);
            throw new RuntimeException("Failed to upload SM20 log: " + e.getMessage(), e);
        }
    }


private String getCellValue(Row row, int colIndex) {
    Cell cell = row.getCell(colIndex);
    if (cell == null) return "";

    DataFormatter formatter = new DataFormatter();
    return formatter.formatCellValue(cell).trim();
}

    private String getTimeValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";


        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                return timeFormat.format(date);
            } else {

                double timeValue = cell.getNumericCellValue();

                int totalSeconds = (int) (timeValue * 24 * 60 * 60);
                int hours = totalSeconds / 3600;
                int minutes = (totalSeconds % 3600) / 60;
                int seconds = totalSeconds % 60;
                return String.format("%02d:%02d:%02d", hours, minutes, seconds);
            }
        }


        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private String getDateValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
            return dateFormat.format(date);
        }


        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }
}