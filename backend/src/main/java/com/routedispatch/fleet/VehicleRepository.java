package com.routedispatch.fleet;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByDepotIdOrderById(Long depotId);
}
