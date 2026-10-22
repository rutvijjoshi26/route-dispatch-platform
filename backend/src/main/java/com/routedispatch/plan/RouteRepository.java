package com.routedispatch.plan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByPlanIdOrderByVehicleId(Long planId);

    Optional<Route> findByPlanIdAndVehicleId(Long planId, Long vehicleId);
}
