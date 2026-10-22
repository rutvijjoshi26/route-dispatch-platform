package com.routedispatch.tracking;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopEventRepository extends JpaRepository<StopEvent, UUID> {
}
