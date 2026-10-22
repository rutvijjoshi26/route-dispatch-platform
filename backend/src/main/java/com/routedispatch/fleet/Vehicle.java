package com.routedispatch.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalTime;

/** A van based at a depot, with a package capacity and the driver's shift hours. */
@Entity
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id")
    private Depot depot;

    @Column(nullable = false, unique = true)
    private String name;

    private int capacity;

    @Column(nullable = false)
    private LocalTime shiftStart;

    @Column(nullable = false)
    private LocalTime shiftEnd;

    protected Vehicle() {
    }

    public Vehicle(Depot depot, String name, int capacity, LocalTime shiftStart, LocalTime shiftEnd) {
        if (!shiftEnd.isAfter(shiftStart)) {
            throw new IllegalArgumentException("Shift must end after it starts");
        }
        this.depot = depot;
        this.name = name;
        this.capacity = capacity;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
    }

    public Long getId() {
        return id;
    }

    public Depot getDepot() {
        return depot;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public LocalTime getShiftStart() {
        return shiftStart;
    }

    public LocalTime getShiftEnd() {
        return shiftEnd;
    }
}
