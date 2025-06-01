package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

public class Path {
    private final List<Node> path = new ArrayList<>();
    private int distance;

    public Path(List<Node> path, int distance) {
        this.path.addAll(path);
        this.distance = distance;
    }

    public Path(int distance) {
        this.distance = distance;
    }

    public void updateDistance(int distance) {
        this.distance = distance;
    }

    public void addNode(Node node) {
        path.add(node);
    }

    public List<Node> getPath() {
        return Collections.unmodifiableList(path);
    }

    public int getDistance() {
        return distance;
    }

    public void exportToJson(String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("{\n");
            writer.write("  \"distance_meters\": " + distance + ",\n");
            writer.write("  \"path\": [\n");

            for (int i = 0; i < path.size(); i++) {
                Node node = path.get(i);
                writer.write("    { \"lat\": " + node.lat + ", \"lon\": " + node.lon + " }");
                if (i < path.size() - 1)
                    writer.write(",");
                writer.write("\n");
            }

            writer.write("  ]\n");
            writer.write("}\n");
        }
    }
}
