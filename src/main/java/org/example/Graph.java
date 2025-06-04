package org.example;

import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Graph {
    public final Map<Integer, Node> nodes = new HashMap<>();
    public final List<Edge> edges = new ArrayList<>();
    // for Dijkstra’s Algorithm faster
    Map<Integer, List<Edge>> adj = new HashMap<>();

    public Graph() {
    }

    public Graph(String filename) throws IOException {
        this.loadFromFMI(filename);
    }

    public List<Edge> getEdgesFrom(int nodeId) {
        return adj.getOrDefault(nodeId, Collections.emptyList());
    }

    public void addNode(Node node) {
        nodes.put(node.id, node);
    }

    public Node getNode(int id) {
        return nodes.get(id);
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
        // for Dijkstra’s Algorithm faster
        adj.computeIfAbsent(edge.start, k -> new ArrayList<>()).add(edge);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public void saveToFMI(String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("# FMI Graph File\n");
            writer.write(nodeCount() + "\n");
            writer.write(edgeCount() + "\n");

            // Write nodes
            for (Node node : nodes.values()) {
                writer.write(node.id + " " + node.lat + " " + node.lon + "\n");
            }

            // Write edges
            for (Edge edge : edges) {
                writer.write(edge.start + " " + edge.dest + " " + edge.dist + "\n");
            }
        }
    }

    public void loadFromFMI(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            // Skip comments
            do {
                line = reader.readLine();
            } while (line != null && line.startsWith("#"));

            if (line == null)
                throw new IOException("Empty file");

            int numNodes = Integer.parseInt(line.trim());
            int numEdges = Integer.parseInt(reader.readLine().trim());
            System.out.println("Number of nodes: " + numNodes);
            System.out.println("Number of edges: " + numEdges);
            // Clear existing data
            nodes.clear();
            edges.clear();

            // Read nodes
            for (int i = 0; i < numNodes; i++) {
                line = reader.readLine();
                String[] parts = line.split("\\s+");
                int id = Integer.parseInt(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                double lon = Double.parseDouble(parts[2]);
                addNode(new Node(id, lat, lon));
            }

            // Read edges
            for (int i = 0; i < numEdges; i++) {
                line = reader.readLine();
                String[] parts = line.split("\\s+");
                int start = Integer.parseInt(parts[0]);
                int dest = Integer.parseInt(parts[1]);
                int dist = Integer.parseInt(parts[2]);
                addEdge(new Edge(start, dest, dist));
            }
        }
        System.out.println("Graph Loaded");
    }
}
