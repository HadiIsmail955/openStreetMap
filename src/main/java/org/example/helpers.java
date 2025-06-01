package org.example;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

public class helpers {

    // Haversine Formula (Distance between two points)
    // Purpose: Finds the shortest distance over the Earth's surface — the
    // great-circle distance — between two latitude/longitude points. distance in
    // meter
    public static int haversine(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6_371_000.0;

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) (EARTH_RADIUS * c);
    }

    // Initial Bearing Formula (Direction to start traveling)
    // Purpose: Find out the compass direction (bearing/azimuth) you need to travel
    // from point A to point B.
    public static double initialBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);

        double theta = Math.atan2(y, x);
        double bearing = Math.toDegrees(theta);

        // Normalize to 0-360°
        bearing = (bearing + 360) % 360;
        return bearing;
    }

    // Destination Point Formula (Where you end up)
    // Purpose: Given a starting point, a bearing and a distance, where will you be?
    public static double[] destinationPoint(double lat1, double lon1, double distanceKm, double bearingDeg) {
        final double R = 6371.0; // Earth's radius in km

        double bearingRad = Math.toRadians(bearingDeg);
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);

        double angularDistance = distanceKm / R;

        double lat2Rad = Math.asin(Math.sin(lat1Rad) * Math.cos(angularDistance) +
                Math.cos(lat1Rad) * Math.sin(angularDistance) * Math.cos(bearingRad));

        double lon2Rad = lon1Rad + Math.atan2(
                Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1Rad),
                Math.cos(angularDistance) - Math.sin(lat1Rad) * Math.sin(lat2Rad));

        double lat2 = Math.toDegrees(lat2Rad);
        double lon2 = Math.toDegrees(lon2Rad);

        // Normalize lon to -180...+180
        lon2 = (lon2 + 540) % 360 - 180;

        return new double[] { lat2, lon2 };
    }

    public void writeGeoJsonManually(List<List<Coordinate>> merged, File out)
            throws IOException {
        try (FileWriter fw = new FileWriter(out)) {
            fw.write("{\"type\":\"FeatureCollection\",\"features\":[");
            for (int i = 0; i < merged.size(); i++) {
                List<Coordinate> line = merged.get(i);
                fw.write("{\"type\":\"Feature\",\"geometry\":{");
                fw.write("\"type\":\"LineString\",\"coordinates\":[");
                for (int j = 0; j < line.size(); j++) {
                    Coordinate c = line.get(j);
                    fw.write("[" + c.x + "," + c.y + "]");
                    if (j < line.size() - 1)
                        fw.write(",");
                }
                fw.write("]},\"properties\":{\"stroke\":\"#0000ff\",\"stroke-width\":2}}");
                if (i < merged.size() - 1)
                    fw.write(",");
            }
            fw.write("]}");
        }
        System.out.println("Wrote coastlines.geojson");
    }
}
