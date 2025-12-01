package com.analysis.ffid.service;

import com.analysis.ffid.model.client_system;
import com.analysis.ffid.model.request_details;
import com.analysis.ffid.model.sm20;
import com.analysis.ffid.model.transaction_usage;
import com.analysis.ffid.repository.client_systemRepository;
import com.analysis.ffid.repository.request_detailsRepository;
import com.analysis.ffid.repository.sm20Repository;
import com.analysis.ffid.repository.transaction_usageRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class request_detailsService {

    private final request_detailsRepository requestRepo;
    private final transaction_usageRepository transactionRepo;
    private final sm20Repository sm20Repo;
    private final client_systemRepository clientSystemRepo;


    public request_details createRequest(request_details request) {
        Optional<client_system> existingCS = clientSystemRepo.findByClientAndSystem(
                request.getClient(),
                request.getSystem()
        );

        client_system csEntity;
        if (existingCS.isPresent()) {
            csEntity = existingCS.get();
        } else {

            csEntity = new client_system();
            csEntity.setCS_ID("CS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            csEntity.setClient(request.getClient());
            csEntity.setSystem(request.getSystem());
            clientSystemRepo.save(csEntity);
        }

        request.setClientSystem(csEntity);
        return requestRepo.save(request);
    }
    public Optional<request_details> getRequestById(String analysisId) {
        return requestRepo.findById(analysisId);
    }

    public void uploadTransactionLog(MultipartFile file, request_details request) throws Exception {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) rowIterator.next();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                transaction_usage log = transaction_usage.builder()
                        .requestDetails(request)
                        .time(getCellValue(row, 0))
                        .tcode(getCellValue(row, 1))
                        .program(getCellValue(row, 2))
                        .build();

                transactionRepo.save(log);
            }
        }
    }

    public void uploadSM20Log(MultipartFile file, request_details request) throws Exception {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) rowIterator.next();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                sm20 log = sm20.builder()
                        .requestDetails(request)
                        .sapSystem(getCellValue(row, 0))
                        .asInstance(getCellValue(row, 1))
                        .entrydate(getCellValue(row, 2))
                        .entrytime(getCellValue(row, 3))
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

                sm20Repo.save(log);
            }
        }
    }

    private String getCellValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }
}
