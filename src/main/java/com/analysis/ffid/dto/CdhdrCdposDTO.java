package com.analysis.ffid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdhdrCdposDTO {
    private String timestamp;
    private String table;
    private String field;
    private String oldValue;
    private String newValue;
    private String user;
}