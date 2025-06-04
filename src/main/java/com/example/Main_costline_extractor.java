package com.example;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;

import org.example.CoastlineExtractorScratch;
import org.example.OceanGraphGenerator;
import org.example.helpers;

import de.topobyte.osm4j.core.access.OsmInputException;

public class Main_costline_extractor {
        public static void main(String[] args) throws IOException {
                int numNodes = 4000000; // or whatever you need
                double cellSize = 1.0; // in degrees
                double maxDistKm = 30.0; // 30 km max edge length
                int maxTries = numNodes * 10; // how many random samples before giving up
                helpers helper = new helpers();
                try {
                        CoastlineExtractorScratch cosastlineExtractor = new CoastlineExtractorScratch();
                        cosastlineExtractor.buildNodeAndWayLists(
                                        new File("/home/ismailhi/hadi/planet-coastlinespbf-cleanedosmpbf.sec.pbf"));
                        List<List<Coordinate>> raw = cosastlineExtractor.buildRawCoastlineGeometries();
                        cosastlineExtractor.mergeRawSegments(raw);
                        helper.writeGeoJsonManually(cosastlineExtractor.mergedBorders,
                                        new File("/home/ismailhi/hadi/border_v1.geojson"));
                        OceanGraphGenerator oceanGraphGenerator = new OceanGraphGenerator(numNodes,
                                        cellSize);
                        oceanGraphGenerator.generateNodes(cosastlineExtractor, maxTries);
                        oceanGraphGenerator.buildEdges(maxDistKm);
                        // oceanGraphGenerator.writeGraphAsGeoJSON(
                        // "/home/ismailhi/hadi/graph.geojson");
                        oceanGraphGenerator.writeGraphAsSeparateGeoJSON(
                                        "/home/ismailhi/hadi/graph_nodes_v1.geojson",
                                        "/home/ismailhi/hadi/graph_edges_v1.geojson");
                        oceanGraphGenerator.saveToFMI(
                                        "/home/ismailhi/hadi/graphfile_v1.txt");
                } catch (IOException | OsmInputException e) {
                        e.printStackTrace();
                }
        }
}
