package org.example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.linemerge.LineMerger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.pbf.seq.PbfReader;

public class CoastlineExtractor {
    private final GeometryFactory gf = new GeometryFactory();
    private final Map<Long, Coordinate> nodeMap = new HashMap<>();
    private final List<LineString> coastlines = new ArrayList<>();
    private boolean showDetails = false;
    private final File inputPbf;
    private final File outputGeoJson;

    public CoastlineExtractor(File inputPbf, File outputGeoJson) {
        this.inputPbf = inputPbf;
        this.outputGeoJson = outputGeoJson;
    }

    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
    }

    private void buildNodeAndWayLists() throws IOException, OsmInputException {
        OsmReader reader = new PbfReader(inputPbf, false);
        reader.setHandler(new DefaultOsmHandler() {
            @Override
            public void handle(OsmNode node) {
                Coordinate coordinate = new Coordinate(node.getLongitude(), node.getLatitude());
                nodeMap.put(node.getId(), coordinate);
            }

            @Override
            public void handle(OsmWay way) {
                if (isCoastline(way)) {
                    int n = way.getNumberOfNodes();
                    Coordinate[] coords = new Coordinate[n];
                    for (int i = 0; i < n; i++) {
                        coords[i] = nodeMap.get(way.getNodeId(i));
                        if (showDetails) {
                            System.out.printf("Node %d: %s%n", way.getNodeId(i), coords[i]);
                        }
                    }
                    coastlines.add(gf.createLineString(coords));
                }
            }
        });

        reader.read();
        System.out.printf("Loaded %,d nodes → %,d raw coastline segments%n",
                nodeMap.size(), coastlines.size());
    }

    private boolean isCoastline(OsmWay way) {
        for (int i = 0; i < way.getNumberOfTags(); i++) {
            if ("natural".equals(way.getTag(i).getKey())
                    && "coastline".equals(way.getTag(i).getValue())) {
                return true;
            }
        }
        return false;
    }

    private List<Geometry> mergeLines(List<LineString> lines) {
        LineMerger merger = new LineMerger();
        merger.add(lines);
        @SuppressWarnings("unchecked")
        Collection<LineString> merged = (Collection<LineString>) merger.getMergedLineStrings();
        return new ArrayList<>(merged);
    }

    private void writeGeoJson(List<Geometry> geoms) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "FeatureCollection");

        ArrayNode feats = root.putArray("features");
        for (Geometry g : geoms) {
            ObjectNode feat = feats.addObject();
            feat.put("type", "Feature");

            // build geometry node
            ObjectNode geom = feat.putObject("geometry");
            geom.put("type", g.getGeometryType());

            ArrayNode coords = geom.putArray("coordinates");
            // assume only LineString geometries here
            Coordinate[] pts = g.getCoordinates();
            for (Coordinate c : pts) {
                ArrayNode p = coords.addArray();
                p.add(c.x).add(c.y);
            }

            // empty properties
            feat.set("properties", mapper.createObjectNode());
        }

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputGeoJson, root);
    }

    public void run() throws IOException, OsmInputException {
        buildNodeAndWayLists();
        List<Geometry> merged = mergeLines(coastlines);
        writeGeoJson(merged);
        System.out.println("Done. Wrote “" + outputGeoJson + "” with "
                + merged.size() + " feature(s).");
    }
}
