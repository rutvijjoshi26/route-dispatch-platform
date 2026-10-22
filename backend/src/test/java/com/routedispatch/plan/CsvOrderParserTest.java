package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CsvOrderParserTest {

    static final String HEADER = "order_ref,customer,address,lat,lon,window_start,window_end,size\n";
    final CsvOrderParser parser = new CsvOrderParser(new ServiceArea(41.6, 42.2, -88.2, -87.5));

    CsvOrderParser.Result parse(String csv) {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesValidRows() {
        CsvOrderParser.Result result = parse(HEADER
                + "CHI-1,Maria Garcia,\"1600 W Fullerton Ave, Chicago, IL\",41.925376,-87.668033,09:00,11:00,medium\n"
                + "CHI-2,Bike Shop,2400 N Western Ave,41.9254,-87.6874,13:00,17:00,L\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.orders()).hasSize(2);
        OrderDraft first = result.orders().getFirst();
        assertThat(first.address()).isEqualTo("1600 W Fullerton Ave, Chicago, IL");
        assertThat(first.size()).isEqualTo(PackageSize.MEDIUM);
        assertThat(first.windowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.orders().get(1).size()).isEqualTo(PackageSize.LARGE);
    }

    @Test
    void headerColumnsMayComeInAnyOrderAndCase() {
        CsvOrderParser.Result result = parse("SIZE,Order_Ref,customer,address,lat,lon,window_start,window_end\n"
                + "S,CHI-1,Ana,1 N State St,41.88,-87.63,09:00,10:00\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.orders().getFirst().orderRef()).isEqualTo("CHI-1");
    }

    @Test
    void missingColumnIsReportedOnce() {
        CsvOrderParser.Result result = parse("order_ref,customer,address,lat,lon,window_start,size\nCHI-1,A,B,41.9,-87.6,09:00,S\n");

        assertThat(result.orders()).isEmpty();
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isZero();
            assertThat(e.message()).contains("window_end");
        });
    }

    @Test
    void everyInvalidRowIsReportedWithItsLineNumber() {
        CsvOrderParser.Result result = parse(HEADER
                + "CHI-1,Ana,1 N State St,41.88,-87.63,9am,10:00,S\n"
                + "CHI-2,Ana,1 N State St,41.88,-87.63,11:00,10:00,S\n"
                + "CHI-3,Ana,1 N State St,40.71,-74.00,09:00,10:00,S\n"
                + "CHI-4,,1 N State St,41.88,-87.63,09:00,10:00,S\n"
                + "CHI-5,Ana,1 N State St,41.88,-87.63,09:00,10:00,HUGE\n"
                + "CHI-6,Ana,1 N State St,north,-87.63,09:00,10:00,S\n"
                + "CHI-1,Ana,1 N State St,41.88,-87.63,09:00,10:00,S\n");

        assertThat(result.orders()).isEmpty();
        assertThat(result.errors()).extracting(CsvOrderParser.RowError::row).containsExactly(2, 3, 4, 5, 6, 7, 8);
        assertThat(result.errors().get(0).message()).contains("window_start");
        assertThat(result.errors().get(1).message()).contains("after");
        assertThat(result.errors().get(2).message()).contains("service area");
        assertThat(result.errors().get(3).message()).contains("customer");
        assertThat(result.errors().get(4).message()).contains("size");
        assertThat(result.errors().get(5).message()).contains("lat");
        assertThat(result.errors().get(6).message()).contains("Duplicate");
    }

    @Test
    void excelByteOrderMarkIsIgnored() {
        CsvOrderParser.Result result = parse("\uFEFF" + HEADER + "CHI-1,Ana,1 N State St,41.88,-87.63,09:00,10:00,S\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.orders()).hasSize(1);
    }

    @Test
    void emptyFileIsAnError() {
        assertThat(parse("").errors()).singleElement().satisfies(e -> assertThat(e.message()).contains("empty"));
        assertThat(parse(HEADER).errors()).singleElement().satisfies(e -> assertThat(e.message()).contains("no orders"));
    }
}
