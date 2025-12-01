package com.analysis.ffid.model;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Random;
import java.util.Set;

@Entity
@Table(name = "request_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class request_details {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "analysisID")
    private String analysisID;

    @Column(name="itsmNumber")
    private String itsmNumber;

    @ManyToOne
    @JoinColumn(name="CS_ID", referencedColumnName = "CS_ID")
    private client_system clientSystem;

    @Column(name="client")
    private String client;

    @Column(name="system")
    private String system;

    @Column(name="requestedFor")
    private String requestedFor;

    @Column(name="requested_on_behalfof")
    private String requested_on_behalfof;

    @Column(name="requestedDate")
    private String requestedDate;

    @Column(name="usedDate")
    private String usedDate;

    @Column(name="tcodes")
    private String tcodes;

    @Column(name="reason")
    private String reason;

    @Column(name="activities_to_be_performed")
    private String activities_to_be_performed;

    @OneToMany(mappedBy = "requestDetails", cascade = CascadeType.ALL)
    private List<transaction_usage> transactionUsages;

    @OneToMany(mappedBy = "requestDetails", fetch = FetchType.LAZY)
    private Set<sm20> sm20Entries;

    @OneToMany(mappedBy = "requestDetails", fetch = FetchType.LAZY)
    private Set<cdhdr_cdpos> cdhdrEntries;

    @OneToMany(mappedBy = "requestDetails", cascade = CascadeType.ALL)
    private List<analysis_result> analysisResults;

    @PrePersist
    public void generateAnalysisId() {
        if (this.analysisID == null || this.analysisID.isEmpty()) {
            long randomNum = 100000 + new Random().nextInt(900000);
            this.analysisID = "REQ-" + randomNum;
        }
    }
}
