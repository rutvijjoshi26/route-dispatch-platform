package com.routedispatch.plan;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispatchPlanRepository extends JpaRepository<DispatchPlan, Long> {

    @EntityGraph(attributePaths = "depot")
    List<DispatchPlan> findAllByOrderByServiceDateDescIdDesc();

    List<DispatchPlan> findByStatus(PlanStatus status);

    /** Row lock that serializes decisions about the whole plan (for example completing the day). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DispatchPlan p where p.id = :id")
    Optional<DispatchPlan> findByIdForUpdate(@Param("id") long id);

    /** Dispatched plans of the van's depot, most recently dispatched first. */
    @Query("""
            select p.id from DispatchPlan p, Vehicle v
            where v.id = :vehicleId and p.depot = v.depot and p.status = com.routedispatch.plan.PlanStatus.DISPATCHED
            order by p.dispatchedAt desc""")
    List<Long> findActivePlanIdForVehicle(@Param("vehicleId") long vehicleId);
}
