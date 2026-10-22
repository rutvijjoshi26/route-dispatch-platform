package com.routedispatch.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the seeded day as an order CSV for the upload flow:
 * {@code ./gradlew :backend:sampleData} produces {@code sample-data/chicago-200-orders.csv}.
 */
public final class SampleDataExporter {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "sample-data/chicago-200-orders.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, toCsv(ChicagoDayGenerator.generate(DemoDataLoader.SEED, 200, 8)));
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    static String toCsv(GeneratedDay day) {
        StringBuilder csv = new StringBuilder("order_ref,customer,address,lat,lon,window_start,window_end,size\n");
        for (GeneratedOrder o : day.orders()) {
            csv.append(o.orderRef()).append(',')
                    .append(quote(o.customerName())).append(',')
                    .append(quote(o.address())).append(',')
                    .append(o.point().lat()).append(',')
                    .append(o.point().lon()).append(',')
                    .append(o.windowStart()).append(',')
                    .append(o.windowEnd()).append(',')
                    .append(o.size()).append('\n');
        }
        return csv.toString();
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private SampleDataExporter() {
    }
}
