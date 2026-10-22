package com.routedispatch.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathWalkerTest {

    // Two segments heading north then east, roughly 1.11 km each.
    final PathWalker walker = new PathWalker(List.of(
            new double[] {41.90, -87.70}, new double[] {41.91, -87.70}, new double[] {41.91, -87.6866}));

    @Test
    void lengthIsTheSumOfSegments() {
        assertThat(walker.lengthMeters()).isCloseTo(1112 + 1105, within(15.0));
    }

    @Test
    void positionInterpolatesAlongTheCurrentSegment() {
        PathWalker.Position halfwayUp = walker.at(556);

        assertThat(halfwayUp.lat()).isCloseTo(41.905, within(0.0002));
        assertThat(halfwayUp.lon()).isCloseTo(-87.70, within(0.00001));
        assertThat(halfwayUp.headingDeg()).isCloseTo(0, within(1.0));
    }

    @Test
    void secondSegmentHeadsEast() {
        PathWalker.Position onSecond = walker.at(1500);

        assertThat(onSecond.lat()).isCloseTo(41.91, within(0.00001));
        assertThat(onSecond.headingDeg()).isCloseTo(90, within(1.0));
    }

    @Test
    void distancesOutsideThePathClampToItsEnds() {
        assertThat(walker.at(-5).lat()).isEqualTo(41.90);
        assertThat(walker.at(1e9).lon()).isEqualTo(-87.6866);
    }

    @Test
    void singlePointPathStaysPut() {
        PathWalker point = new PathWalker(List.<double[]>of(new double[] {41.9, -87.7}));

        assertThat(point.lengthMeters()).isZero();
        assertThat(point.at(100).lat()).isEqualTo(41.9);
    }
}
