package com.analysis.ffid.model;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "sm20")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class sm20 {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ENTRY_ID")
    private Long entryId;

    @ManyToOne
    @JoinColumn(name = "ANALYSIS_ID", referencedColumnName = "analysisID")
    private request_details requestDetails;

    @Column(name = "SAP System")
    private String sapSystem;

    @Column(name = "AS Instance")
    private String asInstance;


    @Column(name = "entryDate")
    private String entrydate;

    @Column(name = "entryTime")
    private String entrytime;

    @Column(name = "Client")
    private String client;

    @Column(name = "Event")
    private String event;

    @Column(name = "Username")
    private String username;

    @Column(name = "Groupname")
    private String groupname;

    @Column(name = "Terminal")
    private String terminal;

    @Column(name = "Peer")
    private String peer;

    @Column(name = "Source TA")
    private String sourceTA;

    @Column(name = "Program")
    private String program;

    @Column(name = "Audit_Log_Msg_Text")
    private String auditLogMsgText;

    @Column(name = "Note")
    private String note;

    @Column(name = "Variable_Message_Data")
    private String variableMessageData;

    @Column(name = "Variable2")
    private String variable2;

    @Column(name = "Variable_Data")
    private String variableData;


}
