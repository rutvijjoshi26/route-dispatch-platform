package com.routedispatch.demo;

import com.routedispatch.fleet.Depot;
import com.routedispatch.fleet.DepotRepository;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.plan.DispatchPlan;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.plan.Stop;
import com.routedispatch.plan.StopRepository;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.security.AppUser;
import com.routedispatch.security.AppUserRepository;
import com.routedispatch.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local demo data (profile {@code demo}): the seeded Chicago depot, 8 vans, a dispatcher, one driver per van, and
 * today's 200-order day ready to optimize, created only on an empty database. Optionally also a load-test fleet.
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    static final long SEED = 42;

    private final DepotRepository depots;
    private final VehicleRepository vehicles;
    private final AppUserRepository users;
    private final DispatchPlanRepository plans;
    private final StopRepository stops;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String dispatcherPassword;
    private final String driverPassword;
    private final int loadTestDrivers;

    DemoDataLoader(DepotRepository depots, VehicleRepository vehicles, AppUserRepository users,
                   DispatchPlanRepository plans, StopRepository stops, PasswordEncoder encoder, Clock clock,
                   @Value("${dispatch.demo.dispatcher-password}") String dispatcherPassword,
                   @Value("${dispatch.demo.driver-password}") String driverPassword,
                   @Value("${dispatch.demo.load-test-drivers:0}") int loadTestDrivers) {
        this.depots = depots;
        this.vehicles = vehicles;
        this.users = users;
        this.plans = plans;
        this.stops = stops;
        this.encoder = encoder;
        this.clock = clock;
        this.dispatcherPassword = dispatcherPassword;
        this.driverPassword = driverPassword;
        this.loadTestDrivers = loadTestDrivers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (depots.count() == 0) {
            load();
        }
        if (loadTestDrivers > 0 && users.findByEmailIgnoreCase("load1@demo.local").isEmpty()) {
            loadTestFleet();
        }
    }

    void load() {
        GeneratedDay day = ChicagoDayGenerator.generate(SEED, 200, 8);
        Depot depot = depots.save(new Depot(day.depot().name(), day.depot().address(), day.depot().point()));
        users.save(new AppUser("dispatcher@demo.local", encoder.encode(dispatcherPassword), "Dana Dispatcher",
                Role.DISPATCHER, null));
        String driverHash = encoder.encode(driverPassword);
        for (int i = 0; i < day.vehicles().size(); i++) {
            GeneratedVehicle v = day.vehicles().get(i);
            Vehicle vehicle = vehicles.save(new Vehicle(depot, v.name(), v.capacity(), v.shiftStart(), v.shiftEnd()));
            users.save(new AppUser("driver" + (i + 1) + "@demo.local", driverHash, "Driver " + (i + 1), Role.DRIVER,
                    vehicle.getId()));
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Chicago")));
        DispatchPlan plan = plans.save(new DispatchPlan("Chicago demo day", today, depot));
        List<Stop> orders = new ArrayList<>();
        for (GeneratedOrder o : day.orders()) {
            orders.add(Stop.newOrder(plan, o.orderRef(), o.customerName(), o.address(), o.point(), o.windowStart(),
                    o.windowEnd(), o.size(), false));
        }
        stops.saveAll(orders);
        log.info("Demo data loaded: depot, {} vans, {} orders for {} (seed {})", day.vehicles().size(), orders.size(), today, SEED);
    }

    /**
     * A separate depot whose vans exist only to load-test GPS ingest: each simulated device is its own van and
     * driver ({@code load1@demo.local} ...), with an already dispatched plan so pings are accepted.
     */
    void loadTestFleet() {
        String driverHash = encoder.encode(driverPassword);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Chicago")));
        Depot depot = depots.save(new Depot("Load test depot", "2500 W Carroll Ave, Chicago, IL 60612",
                new GeoPoint(41.888105, -87.688481)));
        for (int i = 1; i <= loadTestDrivers; i++) {
            Vehicle vehicle = vehicles.save(new Vehicle(depot, "Load Van " + i, 50, LocalTime.of(8, 0),
                    LocalTime.of(18, 0)));
            users.save(new AppUser("load" + i + "@demo.local", driverHash, "Load Driver " + i, Role.DRIVER, vehicle.getId()));
        }
        DispatchPlan plan = new DispatchPlan("Load test", today, depot);
        plan.startPlanning();
        plan.recordSolution("0hard/0medium/0soft", 0, 0);
        plan.dispatch(clock.instant(), LocalTime.of(8, 0), 1);
        plans.save(plan);
        log.info("Load-test fleet loaded: {} vans with a dispatched plan", loadTestDrivers);
    }
}
