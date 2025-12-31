package com.analysis.ffid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SM20DTO {
    private String timestamp;
    private String action;
    private String terminal;
    private String object;
    private String program;
    private String details;
}
