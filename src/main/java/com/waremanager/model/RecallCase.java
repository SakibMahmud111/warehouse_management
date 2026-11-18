package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recall_cases")
public class RecallCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private Batch batch;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();
    @Column(nullable = false)
    private boolean active = true;
    protected RecallCase() {}
    public RecallCase(Batch batch, String reason) { this.batch = batch; this.reason = reason; }
    public Long getId() { return id; }
    public Batch getBatch() { return batch; }
    public String getReason() { return reason; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
