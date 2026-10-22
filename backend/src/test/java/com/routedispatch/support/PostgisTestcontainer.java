package com.routedispatch.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One PostGIS container shared by every Spring test context in the run. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgisTestcontainer {

    static final DockerImageName IMAGE =
            DockerImageName.parse("imresamu/postgis:17-3.5").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgis() {
        return new PostgreSQLContainer<>(IMAGE).withReuse(false);
    }
}
