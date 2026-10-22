package com.routedispatch.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StompFramesTest {

    @Test
    void encodesCommandHeadersAndNullTerminator() {
        String frame = StompFrames.encode("SUBSCRIBE", Map.of("destination", "/topic/plans/1/positions"), "");

        assertThat(frame).isEqualTo("SUBSCRIBE\ndestination:/topic/plans/1/positions\n\n\u0000");
    }

    @Test
    void decodesAMessageFrame() {
        StompFrames.Frame frame = StompFrames.decode(
                "MESSAGE\ndestination:/topic/plans/1/positions\ncontent-type:application/json\n\n{\"vehicleId\":3}\u0000");

        assertThat(frame.command()).isEqualTo("MESSAGE");
        assertThat(frame.headers()).containsEntry("destination", "/topic/plans/1/positions");
        assertThat(frame.body()).isEqualTo("{\"vehicleId\":3}");
    }

    @Test
    void heartbeatNewlineIsNotAFrame() {
        assertThat(StompFrames.decode("\n")).isNull();
    }
}
