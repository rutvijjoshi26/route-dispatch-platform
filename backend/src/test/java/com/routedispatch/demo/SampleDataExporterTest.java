package com.routedispatch.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.plan.CsvOrderParser;
import com.routedispatch.plan.ServiceArea;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SampleDataExporterTest {

    @Test
    void exportedCsvImportsCleanlyWithTheSameOrders() {
        GeneratedDay day = ChicagoDayGenerator.generate(42, 200, 8);
        String csv = SampleDataExporter.toCsv(day);

        CsvOrderParser.Result result = new CsvOrderParser(new ServiceArea(41.4, 42.4, -88.4, -87.3))
                .parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.orders()).hasSize(200);
        assertThat(result.orders().getFirst().orderRef()).isEqualTo(day.orders().getFirst().orderRef());
        assertThat(result.orders().getFirst().location()).isEqualTo(day.orders().getFirst().point());
    }
}
