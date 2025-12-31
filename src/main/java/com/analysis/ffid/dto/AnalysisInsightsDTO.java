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
public class AnalysisInsightsDTO {
    private Integer activityAlignment;
    private String ownership;
    private String justification;
    private Integer riskScore;
    private List<String> redFlags;
    private List<String> recommendations;
    private List<String> keyInsights;
}