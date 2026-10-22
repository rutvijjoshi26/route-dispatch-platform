package com.routedispatch.plan;

import com.routedispatch.routing.GeoPoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounding box the road data covers. Orders outside it cannot be routed. */
@ConfigurationProperties("dispatch.service-area")
public record ServiceArea(double minLat, double maxLat, double minLon, double maxLon) {

    public boolean contains(GeoPoint point) {
        return point.lat() >= minLat && point.lat() <= maxLat && point.lon() >= minLon && point.lon() <= maxLon;
    }
}
