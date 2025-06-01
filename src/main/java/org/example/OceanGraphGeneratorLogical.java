// package org.example;

// import java.io.FileWriter;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.List;

// import org.locationtech.jts.geom.Coordinate;

// public class OceanGraphGeneratorLogical {
// private static final double EARTH_RADIUS = 6371000.0; // meters
// private static final double MAX_EDGE_M = 30000.0; // 30 km
// private final CoastlineExtractorScratch coast; // For isLand()
// private List<Coordinate> oceanNodes;

// public OceanGraphGeneratorLogical(CoastlineExtractorScratch coast) {
// this.coast = coast;
// this.oceanNodes = new ArrayList<>();
// }

// // Generates evenly distributed ocean nodes on a sphere using equal-area
// // latitude bands. skipping any points that fall on land according to the
// // coastline detection logic.
// public void generateOceanGrid(double deltaMeters) {
// this.oceanNodes.clear();
// List<Coordinate> oceanNodes = new ArrayList<>();

// // Calculate how many horizontal latitude bands to generate based on Earth
// // circumference. Earth circumference = 2πR, so πR gives half the globe;
// // dividing
// // by delta gives approximate number of bands
// int bands = (int) Math.ceil(Math.PI * EARTH_RADIUS / deltaMeters);

// for (int k = 0; k < bands; k++) {

// // formula creates evenly spaced latitude bands on a sphere
// double u = 1 - (2.0 * k + 1) / (double) bands;

// // Convert the vertical spacing u into latitude in degrees via arcsin
// double phi = Math.toDegrees(Math.asin(u));
// double latitudeRadians = Math.toRadians(phi);

// // Calculate the circumference of the current latitude circle. Used to
// determine
// // how many points to place along this band
// double circleLength = 2 * Math.PI * EARTH_RADIUS * Math.cos(latitudeRadians);

// // Compute number of longitude steps for this latitude such that points are
// // ~deltaMeters apart
// int longSteps = Math.max(1, (int) Math.round(circleLength / deltaMeters));

// for (int j = 0; j < longSteps; j++) {
// // Calculate longitude at this step; spreads points evenly from -180 to +180
// double lambda = -180.0 + 360.0 * j / longSteps;
// // Check if this coordinate is over ocean using the coastline
// if (!coast.isLand(phi, lambda)) {
// System.out.println("added point " + lambda + " , " + phi);
// this.oceanNodes.add(new Coordinate(lambda, phi));
// }
// }
// }
// System.out.printf("Generated %d ocean nodes at ~%.0fm spacing\n",
// this.oceanNodes.size(), deltaMeters);
// }

// public List<int[]> generateEdges(List<Coordinate> nodes) {
// int n = nodes.size();
// List<int[]> edges = new ArrayList<>();

// for (int i = 0; i < n; i++) {
// Coordinate a = nodes.get(i);
// Coordinate[] closest = new Coordinate[4];
// int[] closestIdx = { -1, -1, -1, -1 };
// double[] minDist = { MAX_EDGE_M, MAX_EDGE_M, MAX_EDGE_M, MAX_EDGE_M };

// for (int j = 0; j < n; j++) {
// if (i == j)
// continue;
// Coordinate b = nodes.get(j);
// double dy = b.y - a.y;
// double dx = b.x - a.x;
// int d = haversineDistance(a, b);
// if (d > MAX_EDGE_M)
// continue;

// if (dy >= 0 && dx >= 0 && d < minDist[0]) { // NE
// minDist[0] = d;
// closest[0] = b;
// closestIdx[0] = j;
// } else if (dy >= 0 && dx < 0 && d < minDist[1]) { // NW
// minDist[1] = d;
// closest[1] = b;
// closestIdx[1] = j;
// } else if (dy < 0 && dx >= 0 && d < minDist[2]) { // SE
// minDist[2] = d;
// closest[2] = b;
// closestIdx[2] = j;
// } else if (dy < 0 && dx < 0 && d < minDist[3]) { // SW
// minDist[3] = d;
// closest[3] = b;
// closestIdx[3] = j;
// }
// }

// for (int k = 0; k < 4; k++) {
// if (closestIdx[k] != -1 && i < closestIdx[k]) {
// edges.add(new int[] { i, closestIdx[k], (int) minDist[k] });
// }
// }
// }
// System.out.printf("Created %d edges\n", edges.size());
// return edges;
// }

// public void writePointsAsGeoJSON(String outputPath) throws IOException {
// try (FileWriter fw = new FileWriter(outputPath)) {
// fw.write("{\"type\":\"FeatureCollection\",\"features\":[\n");
// for (int i = 0; i < this.oceanNodes.size(); i++) {
// Coordinate c = this.oceanNodes.get(i);
// fw.write(" {\"type\":\"Feature\",\"geometry\":"
// + "{\"type\":\"Point\",\"coordinates\":["
// + c.x + "," + c.y + "]},\"properties\": { \"name\": \"Ocean\" }");
// fw.write(i < this.oceanNodes.size() - 1 ? "},\n" : "}\n");
// }
// fw.write("]}");
// }
// }
// }
