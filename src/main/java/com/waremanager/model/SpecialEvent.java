package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** A holiday or promotion period (for example Eid shopping week). F1 adds the learned uplift to these weeks. */
@Entity
@Table(name = "special_events")
public class SpecialEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected SpecialEvent() {
    }

    public SpecialEvent(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** True if this event overlaps the 7 days starting on {@code monday}. */
    public boolean overlapsWeek(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        return !startDate.isAfter(sunday) && !endDate.isBefore(monday);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
