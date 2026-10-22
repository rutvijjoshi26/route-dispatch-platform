package com.routedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ApplicationSmokeIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsCreateSchemaWithPostgis() {
        String version = jdbc.queryForObject("SELECT postgis_lib_version()", String.class);
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name IN "
                        + "('depot','vehicle','stop','gps_ping','vehicle_position','stop_event')",
                Integer.class);
        assertThat(version).startsWith("3.5");
        assertThat(tables).isEqualTo(6);
    }
}
