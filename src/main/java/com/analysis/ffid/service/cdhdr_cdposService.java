

package com.analysis.ffid.service;

import com.analysis.ffid.model.cdhdr_cdpos;
import com.analysis.ffid.model.request_details;
import com.analysis.ffid.repository.cdhdr_cdposRepository;
import com.analysis.ffid.repository.request_detailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class cdhdr_cdposService {

    private final cdhdr_cdposRepository cdhdrCdposRepo;
    private final request_detailsRepository requestDetailsRepo;

    private static final int BATCH_SIZE = 1000;

    public request_details findRequestById(String analysisId) {
        return requestDetailsRepo.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("Request not found with ID: " + analysisId));
    }

    private void validateFile(MultipartFile file, String fileType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fileType + " file is empty or null");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException(fileType + " file must be an Excel file (.xlsx or .xls)");
        }
    }

    private String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    @Transactional
    public int uploadCdhdrData(MultipartFile cdhdrFile, String analysisId) throws Exception {
        log.info("Starting CDHDR upload for analysis ID: {}", analysisId);

        validateFile(cdhdrFile, "CDHDR");
        request_details request = findRequestById(analysisId);

        List<cdhdr_cdpos> batchList = new ArrayList<>();
        int totalRecords = 0;

        try (InputStream is = cdhdrFile.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();


            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                log.info("CDHDR Header row: {}", getCellValue(headerRow, 0));
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();



                String docNumber = getCellValue(row, 3);
                String client = getCellValue(row, 0);

                log.info("Reading CDHDR row {}: docNumber={}, client={}",
                        row.getRowNum(), docNumber, client);


                if (docNumber.isEmpty()) {
                    log.warn("Skipping row {} due to empty doc_number", row.getRowNum());
                    continue;
                }

                cdhdr_cdpos entity = cdhdr_cdpos.builder()
                        .requestDetails(request)
                        .client(client)
                        .object(getCellValue(row, 1))
                        .objectValue(getCellValue(row, 2))
                        .docNumber(docNumber)
                        .username(getCellValue(row, 4))
                        .entryDate(getCellValue(row, 5))
                        .entryTime(getCellValue(row, 6))
                        .tcode(getCellValue(row, 7))

                        .build();

                batchList.add(entity);
                totalRecords++;

                if (batchList.size() >= BATCH_SIZE) {
                    cdhdrCdposRepo.saveAll(batchList);
                    batchList.clear();
                    log.info("Saved batch of {} CDHDR records", BATCH_SIZE);
                }
            }

            if (!batchList.isEmpty()) {
                cdhdrCdposRepo.saveAll(batchList);
                log.info("Saved final batch of {} CDHDR records", batchList.size());
            }
        }

        log.info("CDHDR upload completed. Total records: {}", totalRecords);
        return totalRecords;
    }

    @Transactional
    public int uploadCdposData(MultipartFile cdposFile, String analysisId) throws Exception {
        log.info("Starting CDPOS upload for analysis ID: {}", analysisId);

        validateFile(cdposFile, "CDPOS");
        request_details request = findRequestById(analysisId);

        List<cdhdr_cdpos> existingRecords = cdhdrCdposRepo.findByRequestDetails(request);

        log.info("Found {} existing CDHDR records for analysis ID: {}", existingRecords.size(), analysisId);

        if (existingRecords.isEmpty()) {
            throw new IllegalStateException("No CDHDR data found for analysis ID: " + analysisId +
                    ". Please upload CDHDR file first.");
        }

        Map<String, cdhdr_cdpos> cdhdrMap = new HashMap<>();
        for (cdhdr_cdpos record : existingRecords) {
            if (!cdhdrMap.containsKey(record.getDocNumber())) {
                cdhdrMap.put(record.getDocNumber(), record);
            }
        }

        log.info("Created lookup map with {} unique document numbers", cdhdrMap.size());

        List<cdhdr_cdpos> newRecordsBatch = new ArrayList<>();
        int totalRecords = 0;
        int matchedRecords = 0;

        try (InputStream is = cdposFile.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                log.info("CDPOS Header row: {}", getCellValue(headerRow, 0));
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();



                String docNumber = getCellValue(row, 3);  // CHANGENR column
                String tableName = getCellValue(row, 4);   // TABNAME column
                String tableKey = getCellValue(row, 5);    // TABKEY column

                log.info("Reading CDPOS row {}: docNumber={}, tableName={}, tableKey={}",
                        row.getRowNum(), docNumber, tableName, tableKey);

                if (docNumber.isEmpty()) {
                    log.warn("Skipping row {} due to empty doc_number", row.getRowNum());
                    continue;
                }

                cdhdr_cdpos headerData = cdhdrMap.get(docNumber);

                if (headerData != null) {
                    log.info("Found matching CDHDR for docNumber: {}", docNumber);

                    cdhdr_cdpos combinedEntity = cdhdr_cdpos.builder()
                            .requestDetails(request)
                            .client(headerData.getClient())
                            .object(headerData.getObject())
                            .objectValue(headerData.getObjectValue())
                            .docNumber(headerData.getDocNumber())
                            .username(headerData.getUsername())
                            .entryDate(headerData.getEntryDate())
                            .entryTime(headerData.getEntryTime())
                            .tcode(headerData.getTcode())
                            .tableName(tableName)
                            .tableKey(tableKey)
                            .fieldName(getCellValue(row, 6))
                            .changeId(getCellValue(row, 7))
                            .textFlag(getCellValue(row, 8))
                            .unit(getCellValue(row, 9))
                            .cuky(getCellValue(row, 10))
                            .newValue(getCellValue(row, 11))
                            .oldValue(getCellValue(row, 12))
                            .build();

                    newRecordsBatch.add(combinedEntity);
                    matchedRecords++;

                    log.info("Created combined entity #{} for docNumber: {}", matchedRecords, docNumber);
                } else {
                    log.warn("No matching CDHDR record found for doc_number: {}", docNumber);
                }

                totalRecords++;


                if (newRecordsBatch.size() >= BATCH_SIZE) {
                    cdhdrCdposRepo.saveAll(newRecordsBatch);
                    log.info("Saved batch of {} combined records", BATCH_SIZE);
                    newRecordsBatch.clear();
                }
            }


            if (!newRecordsBatch.isEmpty()) {
                cdhdrCdposRepo.saveAll(newRecordsBatch);
                log.info("Saved final batch of {} combined records", newRecordsBatch.size());
            }
        }

        log.info("Deleting {} old CDHDR-only records", existingRecords.size());
        cdhdrCdposRepo.deleteAll(existingRecords);

        log.info("CDPOS upload completed. Total CDPOS rows processed: {}, Successfully matched and created: {}",
                totalRecords, matchedRecords);
        return matchedRecords;
    }

    public Map<String, Long> getUploadStats(String analysisId) {
        request_details request = findRequestById(analysisId);
        List<cdhdr_cdpos> records = cdhdrCdposRepo.findByRequestDetails(request);

        long totalRecords = records.size();
        long recordsWithCdpos = records.stream()
                .filter(r -> r.getTableName() != null && !r.getTableName().isEmpty())
                .count();
        long recordsWithOnlyCdhdr = totalRecords - recordsWithCdpos;

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalRecords", totalRecords);
        stats.put("recordsWithCdpos", recordsWithCdpos);
        stats.put("recordsWithOnlyCdhdr", recordsWithOnlyCdhdr);

        return stats;
    }
}