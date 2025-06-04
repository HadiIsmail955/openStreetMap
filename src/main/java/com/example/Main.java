package com.example;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;

import org.example.CoastlineExtractorScratch;
import org.example.OceanGraphGenerator;
import org.example.helpers;

import de.topobyte.osm4j.core.access.OsmInputException;

public class Main {
        public static void main(String[] args) throws IOException {
                int numNodes = 1000; // or whatever you need
                double cellSize = 1.0; // in degrees
                double maxDistKm = 30.0; // 30 km max edge length
                int maxTries = numNodes * 10; // how many random samples before giving up
                helpers helper = new helpers();
                try {
                        CoastlineExtractorScratch cosastlineExtractor = new CoastlineExtractorScratch();
                        cosastlineExtractor.buildNodeAndWayLists(
                                        new File(
                                                        "C:\\Users\\HadiIsmail\\Desktop\\study\\open street map\\all pbf\\antarctica-latest.osm.pbf"));
                        List<List<Coordinate>> raw = cosastlineExtractor.buildRawCoastlineGeometries();
                        cosastlineExtractor.mergeRawSegments(raw);
                        helper.writeGeoJsonManually(cosastlineExtractor.mergedBorders,
                                        new File("C:\\Users\\HadiIsmail\\Desktop\\study\\open street map\\border.geojson"));
                        OceanGraphGenerator oceanGraphGenerator = new OceanGraphGenerator(numNodes,
                                        cellSize);
                        oceanGraphGenerator.generateNodes(cosastlineExtractor, maxTries);
                        oceanGraphGenerator.buildEdges(maxDistKm);
                        oceanGraphGenerator.writeGraphAsSeparateGeoJSON(
                                        "C:\\Users\\HadiIsmail\\Desktop\\study\\open street map\\graph_nodes.geojson",
                                        "C:\\Users\\HadiIsmail\\Desktop\\study\\open street map\\graph_edges.geojson");
                        oceanGraphGenerator.saveToFMI(
                                        "C:\\Users\\HadiIsmail\\Desktop\\study\\open street map\\graphfile.txt");
                } catch (IOException | OsmInputException e) {
                        e.printStackTrace();
                }
        }
}
