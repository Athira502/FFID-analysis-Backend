package com.analysis.ffid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionUsageDTO {
    private String timestamp;
    private String transaction;
    private String description;
    private String user;
    private String client;
    private String system;
}