package com.routedispatch.plan;

import com.routedispatch.routing.GeoPoint;
import java.time.LocalTime;

/** A validated order that has not been stored yet (from CSV import or the add-order form). */
public record OrderDraft(String orderRef, String customerName, String address, GeoPoint location,
                         LocalTime windowStart, LocalTime windowEnd, PackageSize size) {
}
