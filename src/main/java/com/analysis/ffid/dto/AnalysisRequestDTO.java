package com.analysis.ffid.dto;

public class AnalysisRequestDTO {
    private String analysisID;

    public AnalysisRequestDTO() {}

    public AnalysisRequestDTO(String analysisId) {
        this.analysisID = analysisId;
    }

    public String getAnalysisId() {
        return analysisID;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisID = analysisId;
    }
}