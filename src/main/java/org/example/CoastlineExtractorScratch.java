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

    // A spatial index for speeding up geographic queries
    private final Map<Integer, List<LineSegment>> gridIndex = new HashMap<>();
    // ~1 km grid size
    private final double GRID_SIZE = 0.01;

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
        Map<CoordKey, List<Integer>> endpointMap = new HashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            List<Coordinate> seg = raw.get(i);
            if (seg.isEmpty())
                continue;

            CoordKey startKey = new CoordKey(seg.get(0));
            CoordKey endKey = new CoordKey(seg.get(seg.size() - 1));

            endpointMap.computeIfAbsent(startKey, k -> new ArrayList<>()).add(i);
            endpointMap.computeIfAbsent(endKey, k -> new ArrayList<>()).add(i);
        }

        // Try to extend each segment with matching others to form longer chains
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

                extended |= tryExtend(acc, endpointMap, raw, used, new CoordKey(end), false);
                extended |= tryExtend(acc, endpointMap, raw, used, new CoordKey(start), true);
            }

            // Close the loop if not already closed
            if (!equals2D(acc.get(0), acc.get(acc.size() - 1))) {
                acc.add(new Coordinate(acc.get(0)));
            }

            result.add(acc);
        }
        System.out.printf("Merged into %,d border chains%n", result.size());
        this.mergedBorders = result;
        buildSpatialIndex();
    }

    // Attempts to extend the current line with matching endpoints from other
    // segments.
    private boolean tryExtend(List<Coordinate> acc,
            Map<CoordKey, List<Integer>> endpointMap,
            List<List<Coordinate>> raw,
            Set<Integer> used,
            CoordKey key,
            boolean prepend) {
        List<Integer> candidates = endpointMap.getOrDefault(key, Collections.emptyList());

        for (int idx : candidates) {
            if (used.contains(idx))
                continue;

            List<Coordinate> seg = raw.get(idx);
            if (seg.isEmpty())
                continue;

            CoordKey segStart = new CoordKey(seg.get(0));
            CoordKey segEnd = new CoordKey(seg.get(seg.size() - 1));

            used.add(idx);

            if (segStart.equals(key)) {
                if (prepend) {
                    List<Coordinate> reversed = reverseTrim(seg, true);
                    acc.addAll(0, reversed);
                } else {
                    acc.addAll(seg.subList(1, seg.size()));
                }
                return true;
            } else if (segEnd.equals(key)) {
                if (prepend) {
                    acc.addAll(0, seg.subList(0, seg.size() - 1));
                } else {
                    List<Coordinate> reversed = reverseTrim(seg, false);
                    acc.addAll(reversed);
                }
                return true;
            }

            used.remove(idx); // rollback if unused
        }

        return false;
    }

    // Builds a spatial index (grid) to accelerate land/sea point checks. of 1 km
    // grid size
    private void buildSpatialIndex() {
        for (List<Coordinate> border : mergedBorders) {
            for (int i = 0; i < border.size() - 1; i++) {
                Coordinate a = border.get(i);
                Coordinate b = border.get(i + 1);

                // Get min/max longitude for the segment
                double minLon = Math.min(normLon(a.x), normLon(b.x));
                double maxLon = Math.max(normLon(a.x), normLon(b.x));

                int startBucket = (int) Math.floor(minLon / GRID_SIZE);
                int endBucket = (int) Math.floor(maxLon / GRID_SIZE);

                for (int bucket = startBucket; bucket <= endBucket; bucket++) {
                    gridIndex.computeIfAbsent(bucket, k -> new ArrayList<>())
                            .add(new LineSegment(a, b));
                }
            }
        }
    }

    // Performs a ray-casting algorithm to determine whether a given lat/lon point
    // is on land. If the vertical ray from the point crosses an odd number of
    // borders, it's land. uses the grid that decrease speed from weeks to 30 min
    public boolean isLand(double lat, double lon) {
        int bucket = (int) Math.floor(normLon(lon) / GRID_SIZE);
        List<LineSegment> candidates = gridIndex.getOrDefault(bucket, Collections.emptyList());

        int crosses = 0;
        for (LineSegment seg : candidates) {
            if (meridianCrossesSegment(lat, lon, seg.a, seg.b)) {
                crosses++;
            }
        }
        // Odd number of crossings → point is inside (on land)
        return (crosses % 2) == 1;
    }

    // Ray-casting logic to test if a vertical line from the point crosses a
    // segment.
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

        // Skip if the line segment does not cross the longitude
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

    // Reverses and trims one endpoint of a segment
    private List<Coordinate> reverseTrim(List<Coordinate> seg, boolean trimEnd) {
        List<Coordinate> reversed = new ArrayList<>();
        for (int i = seg.size() - 1; i >= 0; i--) {
            if (trimEnd && i == seg.size() - 1)
                continue;
            reversed.add(new Coordinate(seg.get(i)));
        }
        return reversed;
    }

    // Checks if two coordinates are equal within a small margin
    private boolean equals2D(Coordinate a, Coordinate b) {
        return Math.abs(a.x - b.x) < 1e-7 && Math.abs(a.y - b.y) < 1e-7;
    }

    // Normalizes longitude values to the standard -180 to +180 range.
    private double normLon(double λ) {
        λ = ((λ + 180) % 360 + 360) % 360 - 180;
        return λ;
    }

    public void printNode() {
        nodeMap.forEach((id, coord) -> System.out.printf("Node %d: (%.6f, %.6f)%n", id, coord.y, coord.x));
    }

    public void printCoastlineWays() {
        System.out.println("\n=== Coastline Ways (Full Expanded) ===");
        coastlineWays.forEach((wayId, nodeIds) -> {
            System.out.printf("Way ID: %d (Total %d nodes)%n", wayId, nodeIds.size());
            for (int i = 0; i < nodeIds.size(); i++) {
                System.out.printf("  Node %d: %d%n", i + 1, nodeIds.get(i));
            }
        });
    }

    public void printRaw(List<List<Coordinate>> raw) {
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

    // Represents a line segment between two coordinates
    private static class LineSegment {
        Coordinate a, b;

        LineSegment(Coordinate a, Coordinate b) {
            this.a = a;
            this.b = b;
        }
    }
}
