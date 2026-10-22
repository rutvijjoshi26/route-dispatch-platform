package com.routedispatch.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

    @Test
    void transientFailuresAreRetriedUntilTheCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.call(3, 0, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new ApiClient.ApiException(503, "busy");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void clientErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.call(3, 0, () -> {
            calls.incrementAndGet();
            throw new ApiClient.ApiException(403, "not your stop");
        })).isInstanceOf(ApiClient.ApiException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.call(2, 0, () -> {
            calls.incrementAndGet();
            throw new ApiClient.ApiException(0, "connection refused");
        })).isInstanceOf(ApiClient.ApiException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void conflictsFromConcurrentUpdatesAreRetried() {
        assertThat(Retry.isTransient(new ApiClient.ApiException(409, "changed concurrently"))).isTrue();
        assertThat(Retry.isTransient(new ApiClient.ApiException(400, "bad"))).isFalse();
    }
}
