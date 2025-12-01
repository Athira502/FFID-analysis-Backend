package com.analysis.ffid.model;

import jakarta.persistence.*;


@Entity
@Table(name = "clientSystem")
public class client_system {

    @Id
    @Column(name="CS_ID")
    private String CS_ID;

    @Column(name="client")
    private String client;

    @Column(name="system")
    private String system;

    public String getCS_ID() { return CS_ID; }
    public void setCS_ID(String CS_ID) { this.CS_ID = CS_ID; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }

    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }

}
