package com.routedispatch.plan.api;

import com.routedispatch.plan.OrderDraft;
import com.routedispatch.plan.PackageSize;
import com.routedispatch.routing.GeoPoint;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/** Request bodies accepted by the plan API. */
public final class PlanRequests {

    public record CreatePlanRequest(@NotBlank @Size(max = 120) String name, @NotNull LocalDate serviceDate,
                                    @NotNull @Positive Long depotId) {
    }

    public record NewOrderRequest(
            @NotBlank @Size(max = 40) String orderRef,
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Size(max = 255) String address,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
            @NotNull LocalTime windowStart,
            @NotNull LocalTime windowEnd,
            @NotNull PackageSize size) {

        @AssertTrue(message = "windowEnd must be after windowStart")
        public boolean isWindowValid() {
            return windowStart == null || windowEnd == null || windowEnd.isAfter(windowStart);
        }

        OrderDraft toDraft() {
            return new OrderDraft(orderRef.trim(), customerName.trim(), address.trim(), new GeoPoint(lat, lon),
                    windowStart, windowEnd, size);
        }
    }

    public record DispatchRequest(@DecimalMin("1") @DecimalMax("600") double timeScale) {
    }

    private PlanRequests() {
    }
}
