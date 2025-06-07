package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OceanGraphGenerator {
    // Number of nodes to generate
    private final int N;

    // Size of each grid cell in degrees for spatial partitioning
    private final double cellSizeDeg;
    // A spatial hash grid to efficiently find nearby nodes
    private final Map<Point, List<Integer>> grid = new HashMap<>();

    // The resulting graph (nodes and edges)
    public Graph graph;

    public OceanGraphGenerator(int nodeCount, double cellSizeDeg) {
        this.N = nodeCount;
        this.cellSizeDeg = cellSizeDeg;
        this.graph = new Graph();
    }

    // Generate ocean nodes randomly across the globe avoiding land
    public void generateNodes(CoastlineExtractorScratch coast, int maxTries) {
        // Try until we have N nodes or maxTries exceeded
        int i = 0, tries = 0;
        while (i < N && tries < maxTries) {
            tries++;

            // Uniform sampling on a spherical cap (polar-latitude bounded)
            double alpha = Math.toRadians(5.0); // limit latitude range
            double zMin = -Math.cos(alpha);
            double zMax = Math.cos(alpha);
            double u = Math.random();
            double z = zMin + u * (zMax - zMin);
            double phi = Math.asin(z);
            double lambda = (Math.random() * 2 - 1) * Math.PI;
            double lat = Math.toDegrees(phi);
            double lon = Math.toDegrees(lambda);

            // running code total unified
            // double u = Math.random();
            // double phi = u * Math.PI - Math.PI / 2;
            // double lambda = (Math.random() * 2 - 1) * Math.PI;
            // double lat = Math.toDegrees(phi), lon = Math.toDegrees(lambda);
            // end of running code

            // running code
            // double u = Math.random();
            // double phi = Math.asin(2 * u - 1);
            // double lambda = (Math.random() * 2 - 1) * Math.PI;
            // double lat = Math.toDegrees(phi), lon = Math.toDegrees(lambda);
            // end of running code

            // other running code
            // double theta = 2 * Math.PI * Math.random();
            // double z = 2 * Math.random() - 1;
            // double lat = Math.toDegrees(Math.asin(z));
            // double lon = Math.toDegrees(theta - Math.PI);
            // end of other running code

            // test code for 1/4 of earth
            // double u = Math.random();
            // double minSinLat = Math.sin(Math.toRadians(-90));
            // double maxSinLat = Math.sin(Math.toRadians(-20));

            // double sinLat = minSinLat + (maxSinLat - minSinLat) * u;
            // double lat = Math.toDegrees(Math.asin(sinLat));
            // double lon = 360 * Math.random() - 180;
            // end of test

            // if (lat >= 90 || lat <= -90) {
            // continue;
            // }

            // Skip land points using coastline data
            if (!coast.isLand(lat, lon)) {
                System.out.println("added point " + lon + " , " + lat + " , N = " + i);
                graph.addNode(new Node(i, lat, lon));
                insertIntoGrid(i, lat, lon);
                i++;
            }
        }

        // If not enough ocean points found, abort
        if (i < N) {
            throw new RuntimeException("Too few ocean points: got " + i);
        }
    }

    // Inserts a node index into its appropriate spatial grid cell
    private void insertIntoGrid(int idx, double lat, double lon) {
        int r = (int) Math.floor((lat + 90.0) / cellSizeDeg);
        int c = (int) Math.floor((lon + 180.0) / cellSizeDeg);
        Point cell = new Point(r, c);
        grid.computeIfAbsent(cell, k -> new ArrayList<>()).add(idx);
    }

    // Builds directional edges to nearest neighbors in NE, NW, SE, SW directions
    public void buildEdges(double maxDistanceKm) {
        for (int i = 0; i < N; i++) {
            Neighbor bestNE = new Neighbor(maxDistanceKm),
                    bestNW = new Neighbor(maxDistanceKm),
                    bestSE = new Neighbor(maxDistanceKm),
                    bestSW = new Neighbor(maxDistanceKm);
            Node nodeI = graph.getNode(i);
            if (nodeI == null)
                continue;

            // Determine current node's grid cell
            int r0 = (int) Math.floor((nodeI.lat + 90) / cellSizeDeg);
            int c0 = (int) Math.floor((nodeI.lon + 180) / cellSizeDeg);

            // Check surrounding 3x3 grid cells for nearby nodes
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    List<Integer> bucket = grid.get(new Point(r0 + dr, c0 + dc));
                    if (bucket == null)
                        continue;
                    for (int j : bucket) {
                        if (j == i)
                            continue;
                        Node nodeJ = graph.getNode(j);
                        if (nodeJ == null)
                            continue;
                        double d = haversineKm(nodeI.lat, nodeI.lon, nodeJ.lat, nodeJ.lon);
                        if (d > maxDistanceKm)
                            continue;
                        double dLat = nodeJ.lat - nodeI.lat;
                        double dLon = normalizeLonDiff(nodeJ.lon - nodeI.lon);

                        // Categorize by quadrant and keep best neighbor
                        if (dLat >= 0 && dLon >= 0)
                            bestNE.consider(j, d);
                        if (dLat >= 0 && dLon <= 0)
                            bestNW.consider(j, d);
                        if (dLat <= 0 && dLon >= 0)
                            bestSE.consider(j, d);
                        if (dLat <= 0 && dLon <= 0)
                            bestSW.consider(j, d);
                    }
                }
            }

            // Add edges to best neighbors in all four directions
            addEdges(i, bestNE);
            addEdges(i, bestNW);
            addEdges(i, bestSE);
            addEdges(i, bestSW);
        }
    }

    // Normalize longitude difference to range [-180, 180]
    private double normalizeLonDiff(double dLon) {
        // wrap to [-180,180]
        if (dLon > 180)
            return dLon - 360;
        if (dLon < -180)
            return dLon + 360;
        return dLon;
    }

    // Add an edge if not already present in the graph
    private void addEdges(int i, Neighbor nb) {
        if (nb.index >= 0) {
            int j = nb.index;
            int distM = (int) Math.round(nb.dist * 1000);
            // add both directions
            if (!edgeExists(i, j)) {
                graph.addEdge(new Edge(i, j, distM));
            }
            if (!edgeExists(j, i)) {
                graph.addEdge(new Edge(j, i, distM));
            }
        }
    }

    // Checks if an edge from -> to already exists
    private boolean edgeExists(int from, int to) {
        for (Edge e : graph.getEdgesFrom(from)) {
            if (e.dest == to)
                return true;
        }
        return false;
    }

    // Haversine formula to calculate distance (in km) between two lat/lon points
    private static double haversineKm(double φ1, double λ1, double φ2, double λ2) {
        double R = 6371.0;
        double dφ = Math.toRadians(φ2 - φ1), dλ = Math.toRadians(λ2 - λ1);
        double a = Math.sin(dφ / 2) * Math.sin(dφ / 2)
                + Math.cos(Math.toRadians(φ1)) * Math.cos(Math.toRadians(φ2))
                        * Math.sin(dλ / 2) * Math.sin(dλ / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // Represents a grid cell using row (r) and column (c) indices
    private static class Point {
        final int r, c;

        Point(int r, int c) {
            this.r = r;
            this.c = c;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Point))
                return false;
            Point p = (Point) o;
            return p.r == r && p.c == c;
        }

        @Override
        public int hashCode() {
            return 31 * r + c;
        }
    }

    // Stores the closest neighbor in one direction/quadrant
    private static class Neighbor {
        int index = -1;
        double dist;

        Neighbor(double max) {
            this.dist = max;
        }

        void consider(int j, double d) {
            if (d < dist) {
                dist = d;
                index = j;
            }
        }
    }

    public void writeGraphAsGeoJSON(String outputPath) throws IOException {
        try (FileWriter fw = new FileWriter(outputPath)) {
            fw.write("{\"type\":\"FeatureCollection\",\"features\":[\n");

            // Points
            int n = graph.nodeCount();
            for (int i = 0; i < n; i++) {
                Node node = graph.getNode(i);
                fw.write(" {\"type\":\"Feature\",\"geometry\":"
                        + "{\"type\":\"LineString\",\"coordinates\":[["
                        + node.lon + "," + node.lat + "],["
                        + node.lon + "," + node.lat + "]]},"
                        + "\"properties\":{"
                        + "\"id\":" + node.id
                        + ",\"type\":\"node\""
                        + "}"
                        + "},\n");
            }

            // Edges
            int eCount = graph.edgeCount();
            for (int k = 0; k < eCount; k++) {
                Edge edge = graph.edges.get(k);
                Node src = graph.getNode(edge.start);
                Node dst = graph.getNode(edge.dest);
                fw.write(" {\"type\":\"Feature\",\"geometry\":"
                        + "{\"type\":\"LineString\",\"coordinates\":[["
                        + src.lon + "," + src.lat + "],["
                        + dst.lon + "," + dst.lat + "]]},"
                        + "\"properties\":{\"source\":" + edge.start
                        + ",\"target\":" + edge.dest
                        + ",\"dist_m\":" + edge.dist + "}}"
                        + (k < eCount - 1 ? ",\n" : "\n"));
            }
            fw.write("]}");
        }
    }

    public void writeGraphAsSeparateGeoJSON(String nodeOutputPath, String edgeOutputPath) throws IOException {
        graph.writeGraphAsSeparateGeoJSON(nodeOutputPath, edgeOutputPath);
    }

    // Save the graph in a custom binary format (e.g., for later use or loading)
    public void saveToFMI(String filename) throws IOException {
        graph.saveToFMI(filename);
    }

}
