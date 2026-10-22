package com.routedispatch.demo;

import com.routedispatch.plan.PackageSize;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reproducible synthetic delivery day in Chicago. Stops cluster in real neighborhoods, sit on real grid streets,
 * and get a realistic mix of delivery windows and package sizes. The same seed always yields the same day.
 */
public final class ChicagoDayGenerator {

    /** A neighborhood: grid center and radius (in address numbers) where its orders are placed. */
    private record Zone(String name, int centerEw, int centerNs, int radius, int weight) {
    }

    /** A grid street: N-S streets have a fixed east/west number, E-W streets a fixed north/south number. */
    private record Street(String name, boolean northSouth, int gridNumber) {
    }

    private static final List<Zone> ZONES = List.of(
            new Zone("Lincoln Park", -1300, 2200, 600, 12),
            new Zone("Lakeview", -1300, 3300, 600, 12),
            new Zone("Logan Square", -2900, 2600, 700, 10),
            new Zone("Wicker Park", -1900, 1500, 500, 10),
            new Zone("West Loop", -1200, 300, 500, 10),
            new Zone("Humboldt Park", -3200, 1200, 600, 7),
            new Zone("Avondale", -3200, 3300, 600, 7),
            new Zone("Albany Park", -3400, 4700, 600, 6),
            new Zone("Uptown", -1300, 4600, 500, 6),
            new Zone("Pilsen", -1700, -1800, 500, 8),
            new Zone("Little Village", -3000, -2700, 600, 6),
            new Zone("Bridgeport", -900, -3200, 500, 6),
            new Zone("Back of the Yards", -1700, -4800, 600, 5),
            new Zone("Hyde Park", 1200, -5300, 300, 6));

    private static final List<Street> STREETS = List.of(
            new Street("Cottage Grove Ave", true, 800), new Street("Ellis Ave", true, 1000),
            new Street("Woodlawn Ave", true, 1200), new Street("Kimbark Ave", true, 1300),
            new Street("Halsted St", true, -800), new Street("Sheffield Ave", true, -1000),
            new Street("Racine Ave", true, -1200), new Street("Southport Ave", true, -1400),
            new Street("Ashland Ave", true, -1600), new Street("Wolcott Ave", true, -1900),
            new Street("Damen Ave", true, -2000), new Street("Hoyne Ave", true, -2100),
            new Street("Leavitt St", true, -2200), new Street("Western Ave", true, -2400),
            new Street("Campbell Ave", true, -2500), new Street("California Ave", true, -2800),
            new Street("Sacramento Ave", true, -3000), new Street("Kedzie Ave", true, -3200),
            new Street("Kimball Ave", true, -3400), new Street("Central Park Ave", true, -3600),
            new Street("Pulaski Rd", true, -4000),
            new Street("Devon Ave", false, 6400), new Street("Lawrence Ave", false, 4800),
            new Street("Montrose Ave", false, 4400), new Street("Irving Park Rd", false, 4000),
            new Street("Addison St", false, 3600), new Street("Belmont Ave", false, 3200),
            new Street("Diversey Ave", false, 2800), new Street("Fullerton Ave", false, 2400),
            new Street("Armitage Ave", false, 2000), new Street("North Ave", false, 1600),
            new Street("Division St", false, 1200), new Street("Chicago Ave", false, 800),
            new Street("Grand Ave", false, 530), new Street("Lake St", false, 200),
            new Street("Madison St", false, 0), new Street("Jackson Blvd", false, -300),
            new Street("Harrison St", false, -600), new Street("Roosevelt Rd", false, -1200),
            new Street("18th St", false, -1800), new Street("Cermak Rd", false, -2200),
            new Street("26th St", false, -2600), new Street("31st St", false, -3100),
            new Street("35th St", false, -3500), new Street("Pershing Rd", false, -3900),
            new Street("47th St", false, -4700), new Street("51st St", false, -5100),
            new Street("55th St", false, -5500), new Street("59th St", false, -5900));

    private static final String[] FIRST = {"Maria", "James", "Aisha", "Daniel", "Sofia", "Michael", "Priya", "Luis",
            "Emily", "Kevin", "Grace", "Omar", "Hannah", "Carlos", "Mei", "David", "Fatima", "Tyrone", "Elena", "Sam"};
    private static final String[] LAST = {"Garcia", "Johnson", "Nguyen", "Kowalski", "Patel", "Williams", "Rodriguez",
            "O'Brien", "Kim", "Hernandez", "Brown", "Novak", "Okafor", "Lopez", "Chen", "Murphy", "Ali", "Rivera"};
    private static final String[] BUSINESS = {"Corner Market", "Community Pantry", "Coffee Roasters", "Bike Shop",
            "Dental Studio", "Bakery", "Hardware", "Flower Shop", "Print Lab", "Yoga Studio", "Bookstore", "Deli"};

    public static GeneratedDay generate(long seed, int orderCount, int vehicleCount) {
        Random random = new Random(seed);
        GeneratedDepot depot = new GeneratedDepot("Kinzie Corridor Depot", "2500 W Carroll Ave, Chicago, IL 60612",
                ChicagoStreetGrid.toPoint(-2500, 340));
        List<GeneratedVehicle> vehicles = new ArrayList<>();
        for (int i = 1; i <= vehicleCount; i++) {
            boolean early = i % 2 == 1;
            vehicles.add(new GeneratedVehicle("Van " + i, i <= (vehicleCount + 1) / 2 ? 60 : 50,
                    early ? LocalTime.of(8, 0) : LocalTime.of(9, 0),
                    early ? LocalTime.of(16, 30) : LocalTime.of(17, 30)));
        }
        List<GeneratedOrder> orders = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            orders.add(order(random, i));
        }
        return new GeneratedDay(depot, vehicles, orders);
    }

    private static GeneratedOrder order(Random random, int index) {
        Zone zone = pickZone(random);
        List<Street> nearby = STREETS.stream().filter(s -> crossesZone(s, zone)).toList();
        Street street = nearby.get(random.nextInt(nearby.size()));
        int along = (street.northSouth() ? zone.centerNs() : zone.centerEw())
                + random.nextInt(2 * zone.radius() + 1) - zone.radius();
        // Real Chicago numbers: even on one side, odd on the other; avoid the 0 block line.
        int houseNumber = Math.max(100, Math.abs(along) / 2 * 2 + random.nextInt(2));
        String direction = street.northSouth() ? (along >= 0 ? "N" : "S") : (along >= 0 ? "E" : "W");
        int ew = street.northSouth() ? street.gridNumber() : Integer.signum(along) * houseNumber;
        int ns = street.northSouth() ? Integer.signum(along) * houseNumber : street.gridNumber();
        String customer = random.nextInt(4) == 0
                ? LAST[random.nextInt(LAST.length)] + " " + BUSINESS[random.nextInt(BUSINESS.length)]
                : FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
        LocalTime[] window = window(random);
        return new GeneratedOrder("CHI-%04d".formatted(1001 + index), customer,
                houseNumber + " " + direction + " " + street.name() + ", Chicago, IL",
                ChicagoStreetGrid.toPoint(ew, ns), window[0], window[1], size(random));
    }

    private static Zone pickZone(Random random) {
        int total = ZONES.stream().mapToInt(Zone::weight).sum();
        int roll = random.nextInt(total);
        for (Zone zone : ZONES) {
            roll -= zone.weight();
            if (roll < 0) {
                return zone;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static boolean crossesZone(Street street, Zone zone) {
        int center = street.northSouth() ? zone.centerEw() : zone.centerNs();
        return Math.abs(street.gridNumber() - center) <= zone.radius();
    }

    private static LocalTime[] window(Random random) {
        // 20% any time, 20% morning, 20% afternoon, 40% customer-booked two-hour slots.
        int roll = random.nextInt(100);
        if (roll < 20) {
            return new LocalTime[] {LocalTime.of(9, 0), LocalTime.of(17, 0)};
        }
        if (roll < 40) {
            return new LocalTime[] {LocalTime.of(9, 0), LocalTime.of(12, 0)};
        }
        if (roll < 60) {
            return new LocalTime[] {LocalTime.of(13, 0), LocalTime.of(17, 0)};
        }
        int start = 9 + random.nextInt(7);
        return new LocalTime[] {LocalTime.of(start, 0), LocalTime.of(start + 2, 0)};
    }

    private static PackageSize size(Random random) {
        int roll = random.nextInt(100);
        return roll < 55 ? PackageSize.SMALL : roll < 85 ? PackageSize.MEDIUM : PackageSize.LARGE;
    }

    private ChicagoDayGenerator() {
    }
}
