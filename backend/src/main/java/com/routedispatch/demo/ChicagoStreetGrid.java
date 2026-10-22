package com.routedispatch.demo;

import com.routedispatch.routing.GeoPoint;

/**
 * Chicago's address grid: numbers start at State Street (east/west) and Madison Street (north/south) and
 * run 800 per mile. Converting grid numbers to coordinates keeps generated addresses consistent with where
 * they really are.
 */
final class ChicagoStreetGrid {

    static final double MADISON_LAT = 41.88195;
    static final double STATE_LON = -87.62780;
    static final double NUMBERS_PER_MILE = 800.0;
    private static final double MILES_PER_DEGREE_LAT = 69.05;
    private static final double MILES_PER_DEGREE_LON = 69.17 * Math.cos(Math.toRadians(MADISON_LAT));

    /**
     * @param eastWest  grid number east (positive) or west (negative) of State Street
     * @param northSouth grid number north (positive) or south (negative) of Madison Street
     */
    static GeoPoint toPoint(int eastWest, int northSouth) {
        double lat = MADISON_LAT + northSouth / NUMBERS_PER_MILE / MILES_PER_DEGREE_LAT;
        double lon = STATE_LON + eastWest / NUMBERS_PER_MILE / MILES_PER_DEGREE_LON;
        return new GeoPoint(Math.round(lat * 1e6) / 1e6, Math.round(lon * 1e6) / 1e6);
    }

    private ChicagoStreetGrid() {
    }
}
