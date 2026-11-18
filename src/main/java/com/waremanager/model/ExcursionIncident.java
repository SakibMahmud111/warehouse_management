package com.waremanager.model;

import jakarta.persistence.*;

@Entity
@Table(name = "excursion_incidents")
public class ExcursionIncident {
    public enum Status { HELD, RELEASED, DISCARDED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private Batch batch;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private TemperatureReading reading;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.HELD;
    @Column(length = 500)
    private String decisionReason;
    protected ExcursionIncident() {}
    public ExcursionIncident(Batch batch, TemperatureReading reading) {
        this.batch = batch;
        this.reading = reading;
    }
    public Long getId() { return id; }
    public Batch getBatch() { return batch; }
    public TemperatureReading getReading() { return reading; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String reason) { decisionReason = reason; }
}
