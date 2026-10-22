package com.routedispatch.plan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopRepository extends JpaRepository<Stop, Long> {

    List<Stop> findByPlanIdOrderById(Long planId);

    List<Stop> findByPlanIdAndVehicleIdOrderBySequence(Long planId, Long vehicleId);

    List<Stop> findByPlanIdAndStatus(Long planId, StopStatus status);

    boolean existsByPlanIdAndOrderRef(Long planId, String orderRef);

    Optional<Stop> findByIdAndPlanId(Long id, Long planId);

    long countByPlanId(Long planId);

    /** Whether any stop that is on a route is still open; orders that could not be routed do not count. */
    boolean existsByPlanIdAndVehicleIdIsNotNullAndStatusIn(Long planId, Collection<StopStatus> statuses);
}
