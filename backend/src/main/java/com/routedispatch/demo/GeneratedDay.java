package com.routedispatch.demo;

import java.util.List;

/** A synthetic delivery day: one depot, its fleet, and the orders in the sequence they were entered. */
public record GeneratedDay(GeneratedDepot depot, List<GeneratedVehicle> vehicles, List<GeneratedOrder> orders) {

    public GeneratedDay {
        vehicles = List.copyOf(vehicles);
        orders = List.copyOf(orders);
    }
}
