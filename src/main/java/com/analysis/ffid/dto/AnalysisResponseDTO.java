package com.analysis.ffid.dto;

import java.util.List;

public class AnalysisResponseDTO {
    private Long resultId;
    private String analysisID;
    private String analyzedTime;
    private Integer activityAlignment;
    private String ownership;
    private String justification;
    private Integer riskScore;
    private List<String> redFlags;
    private List<String> recommendations;
    private List<String> keyInsights;

    public AnalysisResponseDTO() {}


    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public String getAnalysisId() {
        return analysisID;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisID = analysisId;
    }

    public String getAnalyzedTime() {
        return analyzedTime;
    }

    public void setAnalyzedTime(String analyzedTime) {
        this.analyzedTime = analyzedTime;
    }

    public Integer getActivityAlignment() {
        return activityAlignment;
    }

    public void setActivityAlignment(Integer activityAlignment) {
        this.activityAlignment = activityAlignment;
    }

    public String getOwnership() {
        return ownership;
    }

    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public List<String> getRedFlags() {
        return redFlags;
    }

    public void setRedFlags(List<String> redFlags) {
        this.redFlags = redFlags;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getKeyInsights() {
        return keyInsights;
    }

    public void setKeyInsights(List<String> keyInsights) {
        this.keyInsights = keyInsights;
    }
}