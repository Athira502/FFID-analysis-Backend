package com.analysis.ffid.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "analysis_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)


public class analysis_result {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resultID")
    private Long resultID;

    @ManyToOne
    @JoinColumn(name = "analysisID", referencedColumnName = "analysisID")
    private request_details requestDetails;

    @Column(name = "analyzedTime")
    private String analyzedTime;

    @Column(name = "activityAlignment")
    private String activityAlignment;

    @Column(name = "ownership")
    private String ownership;

    @Column(name = "justification")
    private String justification;

    @Column(name = "risk_score")
    private String risk_score;

    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "red_flags", columnDefinition = "TEXT")
    private String red_flags;

    @Column(name = "keyInsight", columnDefinition = "TEXT")
    private String keyInsight;


    public Long getResultID() {
        return resultID;
    }

    public void setResultID(Long resultID) {
        this.resultID = resultID;
    }

    public request_details getRequestDetails() {
        return requestDetails;
    }

    public void setRequestDetails(request_details requestDetails) {
        this.requestDetails = requestDetails;
    }

    public String getAnalyzedTime() {
        return analyzedTime;
    }

    public void setAnalyzed_time(String analyzedTime) {
        this.analyzedTime = analyzedTime;
    }

    public String getActivityAlignment() {
        return activityAlignment;
    }

    public void setActivity_alignment(String activityAlignment) {
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

    public String getRisk_score() {
        return risk_score;
    }

    public void setRisk_score(String risk_score) {
        this.risk_score = risk_score;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public String getRed_flags() {
        return red_flags;
    }

    public void setRed_flags(String red_flags) {
        this.red_flags = red_flags;
    }

    public String getKeyInsight() {
        return keyInsight;
    }

    public void setKeyInsight(String keyInsight) {
        this.keyInsight = keyInsight;
    }
}
