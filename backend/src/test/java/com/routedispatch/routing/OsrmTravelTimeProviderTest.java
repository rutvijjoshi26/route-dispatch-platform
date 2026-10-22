package com.routedispatch.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsrmTravelTimeProviderTest {

    static final String BASE = "http://osrm.test";
    static final GeoPoint A = new GeoPoint(41.88, -87.63);
    static final GeoPoint B = new GeoPoint(41.90, -87.65);
    static final GeoPoint C = new GeoPoint(41.92, -87.66);

    MockRestServiceServer server;
    OsrmTravelTimeProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OsrmTravelTimeProvider(builder.build(), 2);
    }

    @Test
    void matrixIsRequestedInChunksAndAssembled() {
        // 3 origins x 3 destinations with chunk size 2 -> blocks [0,1]x[0,1], [0,1]x[2], [2]x[0,1], [2]x[2]
        server.expect(requestTo(BASE + "/table/v1/driving/-87.63,41.88;-87.65,41.9;-87.63,41.88;-87.65,41.9"
                        + "?sources=0;1&destinations=2;3&annotations=duration,distance"))
                .andRespond(json("{\"code\":\"Ok\",\"durations\":[[0,100],[110,0]],\"distances\":[[0,1000],[1100,0]]}"));
        server.expect(requestTo(BASE + "/table/v1/driving/-87.63,41.88;-87.65,41.9;-87.66,41.92"
                        + "?sources=0;1&destinations=2&annotations=duration,distance"))
                .andRespond(json("{\"code\":\"Ok\",\"durations\":[[200],[90]],\"distances\":[[2000],[900]]}"));
        server.expect(requestTo(BASE + "/table/v1/driving/-87.66,41.92;-87.63,41.88;-87.65,41.9"
                        + "?sources=0&destinations=1;2&annotations=duration,distance"))
                .andRespond(json("{\"code\":\"Ok\",\"durations\":[[210,95.4]],\"distances\":[[2100,950.6]]}"));
        server.expect(requestTo(BASE + "/table/v1/driving/-87.66,41.92;-87.66,41.92"
                        + "?sources=0&destinations=1&annotations=duration,distance"))
                .andRespond(json("{\"code\":\"Ok\",\"durations\":[[0]],\"distances\":[[0]]}"));

        TravelMatrix matrix = provider.matrix(List.of(A, B, C), List.of(A, B, C));

        server.verify();
        assertThat(matrix.seconds(0, 1)).isEqualTo(100);
        assertThat(matrix.seconds(1, 2)).isEqualTo(90);
        assertThat(matrix.seconds(2, 1)).isEqualTo(95);
        assertThat(matrix.meters(2, 1)).isEqualTo(951);
        assertThat(matrix.meters(0, 2)).isEqualTo(2000);
    }

    @Test
    void unreachablePairsGetPenaltyValuesInsteadOfFailingThePlan() {
        server.expect(requestTo(BASE + "/table/v1/driving/-87.63,41.88;-87.65,41.9"
                        + "?sources=0&destinations=1&annotations=duration,distance"))
                .andRespond(json("{\"code\":\"Ok\",\"durations\":[[null]],\"distances\":[[null]]}"));

        TravelMatrix matrix = provider.matrix(List.of(A), List.of(B));

        assertThat(matrix.seconds(0, 0)).isEqualTo(TravelMatrix.UNREACHABLE_SECONDS);
        assertThat(matrix.meters(0, 0)).isEqualTo(TravelMatrix.UNREACHABLE_METERS);
    }

    @Test
    void routeSplitsStepGeometryIntoLegs() {
        server.expect(requestTo(BASE + "/route/v1/driving/-87.63,41.88;-87.65,41.9;-87.66,41.92"
                        + "?overview=false&steps=true&geometries=geojson"))
                .andRespond(json("""
                        {"code":"Ok","routes":[{"legs":[
                          {"distance":1200.4,"duration":150.6,"steps":[
                            {"geometry":{"coordinates":[[-87.63,41.88],[-87.64,41.89]]}},
                            {"geometry":{"coordinates":[[-87.64,41.89],[-87.65,41.90]]}}]},
                          {"distance":800,"duration":90,"steps":[
                            {"geometry":{"coordinates":[[-87.65,41.90],[-87.66,41.92]]}},
                            {"geometry":{"coordinates":[[-87.66,41.92]]}}]}
                        ]}]}"""));

        RouteGeometry route = provider.route(List.of(A, B, C));

        assertThat(route.legs()).hasSize(2);
        assertThat(route.legs().get(0).path()).containsExactly(A, new GeoPoint(41.89, -87.64), B);
        assertThat(route.legs().get(0).meters()).isEqualTo(1200);
        assertThat(route.legs().get(0).seconds()).isEqualTo(151);
        assertThat(route.legs().get(1).path()).containsExactly(B, C);
    }

    @Test
    void osrmErrorCodeBecomesRoutingException() {
        server.expect(requestTo(BASE + "/route/v1/driving/-87.63,41.88;-87.65,41.9"
                        + "?overview=false&steps=true&geometries=geojson"))
                .andRespond(json("{\"code\":\"NoRoute\",\"message\":\"Impossible route\"}"));

        assertThatThrownBy(() -> provider.route(List.of(A, B)))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("NoRoute");
    }

    @Test
    void serverFailureBecomesRoutingException() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/table/v1/driving/-87.63,41.88;-87.65,41.9"
                        + "?sources=0&destinations=1&annotations=duration,distance"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.matrix(List.of(A), List.of(B)))
                .isInstanceOf(RoutingException.class);
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
