package com.routedispatch.demo;

import com.routedispatch.plan.PackageSize;
import com.routedispatch.routing.GeoPoint;
import java.time.LocalTime;

public record GeneratedOrder(String orderRef, String customerName, String address, GeoPoint point,
                             LocalTime windowStart, LocalTime windowEnd, PackageSize size) {
}
