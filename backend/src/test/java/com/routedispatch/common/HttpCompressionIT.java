package com.routedispatch.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.support.PostgisTestcontainer;
import com.routedispatch.support.TestFleet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Large JSON responses (plan views with road geometry) are gzip-compressed by the real server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgisTestcontainer.class, TestFleet.class})
@ActiveProfiles("test")
class HttpCompressionIT {

    @LocalServerPort int port;

    @Test
    void jsonResponsesAreGzipped() throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                        .header("Accept-Encoding", "gzip").build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).hasValue("gzip");
    }
}
