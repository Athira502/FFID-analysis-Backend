package com.analysis.ffid.model;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "transaction_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class transaction_usage {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "t_id")
    private Long t_id;

    @ManyToOne
    @JoinColumn(name = "analysis_id", referencedColumnName = "analysisID")
    private request_details requestDetails;

    @Column(name = "time")
    private String time;

    @Column(name = "tcode")
    private String tcode;

    @Column(name = "program")
    private String program;



}
