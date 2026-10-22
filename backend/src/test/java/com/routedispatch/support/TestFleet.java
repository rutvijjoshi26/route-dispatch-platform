package com.routedispatch.support;

import com.routedispatch.fleet.Depot;
import com.routedispatch.fleet.DepotRepository;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.security.AppUser;
import com.routedispatch.security.AppUserRepository;
import com.routedispatch.security.Role;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates an isolated depot, vans, a dispatcher and one driver per van for each test. */
@TestComponent
public class TestFleet {

    public static final String PASSWORD = "test-password";

    public record Fleet(Depot depot, List<Vehicle> vans, String dispatcherEmail, List<String> driverEmails) {
    }

    private final DepotRepository depots;
    private final VehicleRepository vehicles;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public TestFleet(DepotRepository depots, VehicleRepository vehicles, AppUserRepository users, PasswordEncoder encoder) {
        this.depots = depots;
        this.vehicles = vehicles;
        this.users = users;
        this.encoder = encoder;
    }

    public Fleet create(int vanCount, int capacity) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Depot depot = depots.save(new Depot("Depot " + tag, "2500 W Carroll Ave, Chicago, IL 60612", new GeoPoint(41.8866, -87.6886)));
        List<Vehicle> vans = new ArrayList<>();
        List<String> drivers = new ArrayList<>();
        String hash = encoder.encode(PASSWORD);
        for (int i = 1; i <= vanCount; i++) {
            Vehicle van = vehicles.save(new Vehicle(depot, "Van " + i + " " + tag, capacity, LocalTime.of(8, 0), LocalTime.of(18, 0)));
            vans.add(van);
            String email = "driver" + i + "-" + tag + "@test.local";
            users.save(new AppUser(email, hash, "Driver " + i, Role.DRIVER, van.getId()));
            drivers.add(email);
        }
        String dispatcher = "dispatcher-" + tag + "@test.local";
        users.save(new AppUser(dispatcher, hash, "Dispatcher", Role.DISPATCHER, null));
        return new Fleet(depot, vans, dispatcher, drivers);
    }
}
