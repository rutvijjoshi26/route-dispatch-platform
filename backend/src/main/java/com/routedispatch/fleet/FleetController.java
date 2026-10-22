package com.routedispatch.fleet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet")
@Tag(name = "Fleet")
public class FleetController {

    public record DepotView(long id, String name, String address, double lat, double lon) {
    }

    public record VehicleView(long id, long depotId, String name, int capacity, LocalTime shiftStart, LocalTime shiftEnd) {
    }

    private final DepotRepository depots;
    private final VehicleRepository vehicles;

    public FleetController(DepotRepository depots, VehicleRepository vehicles) {
        this.depots = depots;
        this.vehicles = vehicles;
    }

    @GetMapping("/depots")
    @Operation(summary = "Depots")
    public List<DepotView> depots() {
        return depots.findAll().stream()
                .map(d -> new DepotView(d.getId(), d.getName(), d.getAddress(), d.getLocation().lat(), d.getLocation().lon()))
                .toList();
    }

    @GetMapping("/vehicles")
    @Transactional(readOnly = true)
    @Operation(summary = "Vans with capacity and shift hours")
    public List<VehicleView> vehicles() {
        return vehicles.findAll().stream()
                .map(v -> new VehicleView(v.getId(), v.getDepot().getId(), v.getName(), v.getCapacity(), v.getShiftStart(),
                        v.getShiftEnd()))
                .toList();
    }
}
