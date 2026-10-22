package com.routedispatch.plan;

import com.routedispatch.routing.GeoPoint;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parses an order CSV. Every row is validated and every problem is reported with its line number, so a
 * dispatcher can fix the whole file in one pass. Callers import nothing unless the result has no errors.
 */
public class CsvOrderParser {

    static final List<String> COLUMNS =
            List.of("order_ref", "customer", "address", "lat", "lon", "window_start", "window_end", "size");
    static final int MAX_ROWS = 2_000;

    public record RowError(int row, String message) {
    }

    public record Result(List<OrderDraft> orders, List<RowError> errors) {
    }

    private final ServiceArea serviceArea;

    public CsvOrderParser(ServiceArea serviceArea) {
        this.serviceArea = serviceArea;
    }

    public Result parse(InputStream input) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true)
                .setIgnoreEmptyLines(true).setIgnoreHeaderCase(true).get();
        try (Reader reader = withoutByteOrderMark(new InputStreamReader(input, StandardCharsets.UTF_8));
             CSVParser parser = CSVParser.parse(reader, format)) {
            Map<String, Integer> header = parser.getHeaderMap();
            if (header == null || header.isEmpty()) {
                return failure("The file is empty");
            }
            Set<String> present = new HashSet<>();
            header.keySet().forEach(h -> present.add(h.toLowerCase(Locale.ROOT)));
            List<String> missing = COLUMNS.stream().filter(c -> !present.contains(c)).toList();
            if (!missing.isEmpty()) {
                return failure("Missing column(s): " + String.join(", ", missing));
            }
            List<OrderDraft> orders = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            Set<String> refs = new HashSet<>();
            for (CSVRecord record : parser) {
                int line = (int) record.getRecordNumber() + 1;
                if (record.getRecordNumber() > MAX_ROWS) {
                    errors.add(new RowError(line, "Too many rows; the limit is " + MAX_ROWS));
                    break;
                }
                try {
                    String ref = required(record, "order_ref");
                    if (!refs.add(ref)) {
                        throw new IllegalArgumentException("Duplicate order_ref " + ref);
                    }
                    orders.add(row(record));
                } catch (IllegalArgumentException e) {
                    errors.add(new RowError(line, e.getMessage()));
                }
            }
            if (orders.isEmpty() && errors.isEmpty()) {
                return failure("The file has a header but no orders");
            }
            return errors.isEmpty() ? new Result(orders, List.of()) : new Result(List.of(), errors);
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            return failure("The file is not valid CSV: " + e.getMessage());
        }
    }

    /** Spreadsheet exports often start with a UTF-8 byte order mark, which would corrupt the first header. */
    private static Reader withoutByteOrderMark(Reader reader) throws IOException {
        PushbackReader pushback = new PushbackReader(reader, 1);
        int first = pushback.read();
        if (first != -1 && first != '\uFEFF') {
            pushback.unread(first);
        }
        return pushback;
    }

    private OrderDraft row(CSVRecord record) {
        String ref = required(record, "order_ref");
        String customer = required(record, "customer");
        String address = required(record, "address");
        double lat = number(record, "lat");
        double lon = number(record, "lon");
        GeoPoint location;
        try {
            location = new GeoPoint(lat, lon);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("lat/lon is not a valid coordinate");
        }
        if (!serviceArea.contains(location)) {
            throw new IllegalArgumentException("Location " + lat + "," + lon + " is outside the service area");
        }
        LocalTime start = time(record, "window_start");
        LocalTime end = time(record, "window_end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("window_end must be after window_start");
        }
        return new OrderDraft(ref, customer, address, location, start, end, size(required(record, "size")));
    }

    private static String required(CSVRecord record, String column) {
        String value = record.isMapped(column) && record.isSet(column) ? record.get(column) : "";
        if (value.isBlank()) {
            throw new IllegalArgumentException("Column " + column + " is empty");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Column " + column + " is longer than 255 characters");
        }
        return value;
    }

    private static double number(CSVRecord record, String column) {
        String value = required(record, column);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Column " + column + " is not a number: " + value);
        }
    }

    private static LocalTime time(CSVRecord record, String column) {
        String value = required(record, column);
        try {
            return LocalTime.parse(value.length() == 4 ? "0" + value : value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Column " + column + " must be a time like 09:30, got " + value);
        }
    }

    static PackageSize size(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "S", "SMALL" -> PackageSize.SMALL;
            case "M", "MEDIUM" -> PackageSize.MEDIUM;
            case "L", "LARGE" -> PackageSize.LARGE;
            default -> throw new IllegalArgumentException("Column size must be SMALL, MEDIUM or LARGE, got " + value);
        };
    }

    private static Result failure(String message) {
        return new Result(List.of(), List.of(new RowError(0, message)));
    }
}
