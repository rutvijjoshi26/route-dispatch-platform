package com.routedispatch.plan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRevisionRepository extends JpaRepository<PlanRevision, Long> {

    List<PlanRevision> findByPlanIdOrderByVersionDesc(Long planId);
}
