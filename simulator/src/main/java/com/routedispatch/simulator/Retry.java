package com.routedispatch.simulator;

import java.util.function.Supplier;

/**
 * Retries calls that failed for transient reasons (network errors, 5xx, 409 from a concurrent update). Driver
 * calls carry idempotency keys, so repeating one is safe; client errors such as 403 are returned immediately.
 */
final class Retry {

    static <T> T call(int attempts, long backoffMillis, Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (ApiClient.ApiException e) {
                if (attempt >= attempts || !isTransient(e)) {
                    throw e;
                }
                sleep(backoffMillis * attempt);
            }
        }
    }

    static boolean isTransient(ApiClient.ApiException e) {
        return e.status == 0 || e.status == 409 || e.status >= 500;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiClient.ApiException(0, "interrupted");
        }
    }

    private Retry() {
    }
}
