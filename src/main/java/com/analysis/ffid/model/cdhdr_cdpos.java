package com.analysis.ffid.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cdhdr_cdpos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)

public class cdhdr_cdpos {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ANALYSIS_ID", referencedColumnName = "analysisID")
    private request_details requestDetails;

    @Column(name = "client")
    private String client;

    @Column(name = "object")
    private String object;

    @Column(name = "object_value")
    private String objectValue;

    @Column(name = "doc_number")
    private String docNumber;

    @Column(name = "username")
    private String username;

    @Column(name = "entrydate")
    private String entryDate;

    @Column(name = "entrytime")
    private String entryTime;

    @Column(name = "tcode")
    private String tcode;

    // CDPOS fields
    @Column(name = "tableName")
    private String tableName;

    @Column(name = "tableKey")
    private String tableKey;

    @Column(name = "fieldName")
    private String fieldName;

    @Column(name = "changeID")
    private String changeId;

    @Column(name = "textFlag")
    private String textFlag;

    @Column(name = "Unit")
    private String unit;

    @Column(name = "CUKY")
    private String cuky;

    @Column(name = "newValue")
    private String newValue;

    @Column(name = "oldValue")
    private String oldValue;
}
