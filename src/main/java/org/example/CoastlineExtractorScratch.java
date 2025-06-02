package org.example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.pbf.seq.PbfReader;

public class CoastlineExtractorScratch {

    // Maps node ID to its coordinates
    private final Map<Long, Coordinate> nodeMap = new HashMap<>();

    // Stores coastline ways (each way is a list of node IDs)
    private final Map<Long, List<Long>> coastlineWays = new LinkedHashMap<>();

    // List of merged coastline segments forming complete borders
    public List<List<Coordinate>> mergedBorders;

    // Once built, this contains a single (or few) MultiPolygon representing all
    // land
    private PreparedGeometry preparedLand;

    // Use a single JTS GeometryFactory (SRID is optional; we only do lat/lon tests)
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    // Reads OSM data from a PBF file and builds maps of nodes and coastline ways
    public void buildNodeAndWayLists(File pbfFile) throws IOException, OsmInputException {
        OsmReader reader = new PbfReader(pbfFile, false);
        // Overrideing how to handle nodes and ways
        reader.setHandler(new DefaultOsmHandler() {
            @Override
            public void handle(OsmNode node) {
                Coordinate coordinate = new Coordinate(node.getLongitude(), node.getLatitude());
                nodeMap.put(node.getId(), coordinate);
            }

            @Override
            public void handle(OsmWay way) {
                if (isCoastline(way)) {
                    List<Long> refs = new ArrayList<>(way.getNumberOfNodes());
                    for (int i = 0; i < way.getNumberOfNodes(); i++) {
                        refs.add(way.getNodeId(i));
                    }
                    coastlineWays.put(way.getId(), refs);
                }
            }
        });
        reader.read();
        System.out.printf("Loaded %,d nodes, %,d coastline ways%n",
                nodeMap.size(), coastlineWays.size());
    }

    // Converts the coastline ways (by node IDs) into actual geographic lines
    // (Coordinates)
    public List<List<Coordinate>> buildRawCoastlineGeometries() {
        List<List<Coordinate>> raw = new ArrayList<>();
        for (List<Long> refs : coastlineWays.values()) {
            List<Coordinate> line = new ArrayList<>(refs.size());
            for (Long nid : refs) {
                Coordinate c = nodeMap.get(nid);
                if (c != null)
                    line.add(c);
            }
            raw.add(line);
        }
        return raw;
    }

    // Merges raw coastline segments into continuous border chains
    public void mergeRawSegments(List<List<Coordinate>> raw) {
        List<List<Coordinate>> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();

        // Build endpoint map
        Map<String, List<Integer>> endpointMap = new HashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            List<Coordinate> seg = raw.get(i);
            if (seg.isEmpty())
                continue;

            String startKey = coordKey(seg.get(0));
            String endKey = coordKey(seg.get(seg.size() - 1));

            endpointMap.computeIfAbsent(startKey, k -> new ArrayList<>()).add(i);
            endpointMap.computeIfAbsent(endKey, k -> new ArrayList<>()).add(i);
        }

        for (int i = 0; i < raw.size(); i++) {
            if (used.contains(i))
                continue;

            List<Coordinate> acc = new ArrayList<>(raw.get(i));
            used.add(i);

            boolean extended = true;
            while (extended) {
                extended = false;

                Coordinate start = acc.get(0);
                Coordinate end = acc.get(acc.size() - 1);

                extended |= tryExtend(acc, endpointMap, raw, used, coordKey(end), false);
                extended |= tryExtend(acc, endpointMap, raw, used, coordKey(start), true);
            }

            if (!acc.get(0).equals2D(acc.get(acc.size() - 1))) {
                acc.add(new Coordinate(acc.get(0)));
            }

            result.add(acc);
        }

        System.out.printf("Merged into %,d border chains%n", result.size());
        this.mergedBorders = result;
    }

    private boolean tryExtend(List<Coordinate> acc,
            Map<String, List<Integer>> endpointMap,
            List<List<Coordinate>> raw,
            Set<Integer> used,
            String key,
            boolean prepend) {
        List<Integer> candidates = endpointMap.getOrDefault(key, Collections.emptyList());

        for (int idx : candidates) {
            if (used.contains(idx))
                continue;

            List<Coordinate> seg = raw.get(idx);
            if (seg.isEmpty())
                continue;

            Coordinate segStart = seg.get(0);
            Coordinate segEnd = seg.get(seg.size() - 1);

            if (coordKey(segStart).equals(key)) {
                used.add(idx);
                if (prepend) {
                    List<Coordinate> reversed = new ArrayList<>(seg);
                    Collections.reverse(reversed);
                    acc.addAll(0, reversed.subList(0, reversed.size() - 1));
                } else {
                    acc.addAll(seg.subList(1, seg.size()));
                }
                return true;
            } else if (coordKey(segEnd).equals(key)) {
                used.add(idx);
                if (prepend) {
                    acc.addAll(0, seg.subList(0, seg.size() - 1));
                } else {
                    List<Coordinate> reversed = new ArrayList<>(seg);
                    Collections.reverse(reversed);
                    acc.addAll(reversed.subList(1, reversed.size()));
                }
                return true;
            }
        }

        return false;
    }

    private String coordKey(Coordinate c) {
        return String.format("%.7f_%.7f", c.x, c.y);
    }

    public void buildPreparedLand() {
        if (mergedBorders == null) {
            throw new IllegalStateException("Must call mergeRawSegments(...) first.");
        }
        List<Polygon> polygonList = new ArrayList<>();
        for (List<Coordinate> border : mergedBorders) {
            // Skip degenerate chains
            if (border.size() < 4)
                continue;

            // JTS expects coordinates as [ (x=lon,y=lat), ... ]
            Coordinate[] coords = border.toArray(new Coordinate[0]);
            // Ensure closed ring: first == last
            if (!coords[0].equals2D(coords[coords.length - 1])) {
                throw new IllegalStateException("Border must be closed: " + border);
            }
            LinearRing lr = GF.createLinearRing(coords);
            Polygon poly = GF.createPolygon(lr, null);
            polygonList.add(poly);
        }

        // Union them all into a single MultiPolygon (or single Polygon if possible)
        Geometry union = CascadedPolygonUnion.union(polygonList);
        this.preparedLand = PreparedGeometryFactory.prepare(union);

        System.out.printf(
                "Built PreparedGeometry land‐mask: %s%n", union.getGeometryType());
    }

    // Performs a ray-casting algorithm to determine whether a given lat/lon point
    // is on land. If the vertical ray from the point crosses an odd number of
    // borders, it's land.
    // public boolean isLand(double lat, double lon) {
    // int crosses = 0;
    // for (List<Coordinate> border : mergedBorders) {
    // for (int i = 0; i < border.size() - 1; i++) {
    // if (border.size() < 2)
    // continue;
    // // Count how many times the vertical meridian from this point crosses border
    // // segments. We check that if the it crosses in bound box of two consecutive
    // // points on border.
    // if (meridianCrossesSegment(lat, lon, border.get(i), border.get(i + 1))) {
    // crosses++;
    // }
    // }
    // }
    // return (crosses % 2) == 1;
    // }
    public boolean isLand(double lat, double lon) {
        if (preparedLand == null) {
            throw new IllegalStateException("Call buildPreparedLand() before isLand().");
        }
        // JTS Point expects (x=lon,y=lat)
        return preparedLand.contains(GF.createPoint(new Coordinate(lon, lat)));
    }

    // helper functions

    // Determines whether a vertical line from the test point crosses the line
    // segment AB. Used for ray-casting in point-in-polygon tests.
    // private boolean meridianCrossesSegment(
    // double latTest, double lonTest,
    // Coordinate A, Coordinate B) {
    // // Normalize longitudes to the [-180, 180] range
    // double λ0 = normLon(A.x), λ1 = normLon(B.x), λt = normLon(lonTest);

    // // Check if the segment crosses the two points longitude
    // if (!((λ0 < λt && λ1 >= λt) || (λ1 < λt && λ0 >= λt))) {
    // return false;
    // }

    // // Calculate the latitude at which the meridian intersects the segment
    // double frac = (λt - λ0) / (λ1 - λ0);
    // double latI = A.y + frac * (B.y - A.y);

    // // Return true if intersection is north of the test point
    // return latI >= latTest;
    // }
    // private boolean meridianCrossesSegment(
    // double latTest, double lonTest,
    // Coordinate A, Coordinate B) {

    // // Normalize longitudes
    // double λ0 = normLon(A.x), λ1 = normLon(B.x), λt = normLon(lonTest);
    // // Handle antimeridian-crossing segments
    // if (Math.abs(λ1 - λ0) > 180.0) {
    // if (λ0 > 0)
    // λ0 -= 360;
    // else
    // λ1 -= 360;
    // }

    // // If segment is effectively vertical or degenerate, no crossing
    // if (Math.abs(λ1 - λ0) < 1e-9) {
    // return false;
    // }

    // // Quick lon‐range check
    // if (!((λ0 < λt && λ1 >= λt) || (λ1 < λt && λ0 >= λt))) {
    // return false;
    // }

    // // Compute intersection latitude
    // double frac = (λt - λ0) / (λ1 - λ0);
    // double latI = A.y + frac * (B.y - A.y);

    // // Only count if intersection is on or above the test lat
    // return latI >= latTest - 1e-9;
    // }
    private boolean meridianCrossesSegment(double latTest, double lonTest,
            Coordinate A, Coordinate B) {
        double λ0 = normLon(A.x);
        double λ1 = normLon(B.x);
        double λt = normLon(lonTest);
        // Harmonize antimeridian wrap
        if (λ1 - λ0 > 180)
            λ1 -= 360;
        else if (λ0 - λ1 > 180)
            λ0 -= 360;

        if (Math.abs(λ1 - λ0) < 1e-9) {
            return false;
        }
        if (!((λ0 < λt && λ1 >= λt) || (λ1 < λt && λ0 >= λt))) {
            return false;
        }
        double frac = (λt - λ0) / (λ1 - λ0);
        double latI = A.y + frac * (B.y - A.y);
        return latI >= latTest - 1e-9;
    }

    // Identifies if a given OSM way is tagged as a natural coastline.
    private boolean isCoastline(OsmWay way) {
        for (int i = 0; i < way.getNumberOfTags(); i++) {
            if ("natural".equals(way.getTag(i).getKey())
                    && "coastline".equals(way.getTag(i).getValue())) {
                return true;
            }
        }
        return false;
    }

    // Normalizes longitude values to the standard -180 to +180 range.
    private double normLon(double λ) {
        λ = ((λ + 180) % 360 + 360) % 360 - 180;
        return λ;
    }

    private void printNode() {
        nodeMap.forEach((id, coord) -> System.out.printf("Node %d: (%.6f, %.6f)%n", id, coord.y, coord.x));
    }

    private void printCoastlineWays() {
        System.out.println("\n=== Coastline Ways (Full Expanded) ===");
        coastlineWays.forEach((wayId, nodeIds) -> {
            System.out.printf("Way ID: %d (Total %d nodes)%n", wayId, nodeIds.size());
            for (int i = 0; i < nodeIds.size(); i++) {
                System.out.printf("  Node %d: %d%n", i + 1, nodeIds.get(i));
            }
        });
    }

    private void printRaw(List<List<Coordinate>> raw) {
        System.out.println("\n=== Raw Coastline Geometries ===");
        for (int i = 0; i < raw.size(); i++) {
            List<Coordinate> line = raw.get(i);
            System.out.printf("Line %d (%d points):%n", i + 1, line.size());
            for (int j = 0; j < line.size(); j++) {
                Coordinate coord = line.get(j);
                System.out.printf("  Point %d: (%.6f, %.6f)%n", j + 1, coord.y, coord.x);
            }
        }
    }
}
