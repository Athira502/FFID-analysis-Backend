package com.analysis.ffid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestDetailsDTO {
    // Request Information
    private String analysisId;
    private String itsmNumber;
    private String client;
    private String system;
    private String requestedFor;
    private String requestedOnBehalfOf;
    private String requestedDate;
    private String usedDate;
    private String tcodes;
    private String reason;
    private String activities;

    // Transaction Usage Logs
    private List<TransactionUsageDTO> transactionUsage;

    // SM20 Audit Logs
    private List<SM20DTO> auditLogs;

    // Change Document Logs
    private List<CdhdrCdposDTO> changeDocLogs;

    // AI Analysis Insights
    private AnalysisInsightsDTO aiInsights;
}
