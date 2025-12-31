package com.analysis.ffid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestListDTO {
    private String analysisId;
    private String itsmNumber;
    private String client;
    private String system;
    private String requestedFor;
    private String requestedDate;
    private String usedDate;
}