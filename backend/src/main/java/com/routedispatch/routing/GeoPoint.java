package com.routedispatch.routing;

/** WGS84 coordinate. Latitude first, as people read coordinates; OSRM and GeoJSON use lon,lat. */
public record GeoPoint(double lat, double lon) {

    public GeoPoint {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180 || Double.isNaN(lat) || Double.isNaN(lon)) {
            throw new IllegalArgumentException("Invalid coordinate: " + lat + "," + lon);
        }
    }

    /** Cache key that treats points closer than about 1 m as the same place. */
    long cacheKey() {
        long latE5 = Math.round(lat * 1e5);
        long lonE5 = Math.round(lon * 1e5);
        return (latE5 << 32) ^ (lonE5 & 0xffffffffL);
    }
}
