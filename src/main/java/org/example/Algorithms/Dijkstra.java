package org.example.Algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.example.Edge;
import org.example.Graph;
import org.example.Node;
import org.example.Path;

public class Dijkstra {
    private final Graph graph;

    public Dijkstra(Graph graph) {
        this.graph = graph;
    }

    public void run(int sourceId, int targetId, String outputFile) throws IOException {
        System.out.println("Starting Dijkstra");
        Path path = findPath(sourceId, targetId);
        path.exportToJson(outputFile);
        System.out.println(path.getPath().isEmpty() ? "No path found!" : "Path found");
    }

    // Runs Dijkstra's algorithm from sourceId to targetId and exports result to a
    // JSON file
    public Path findPath(int sourceId, int targetId) {
        System.out.println("Starting Dijkstra");
        // Stores the shortest distance from the source to each node
        Map<Integer, Integer> dist = new HashMap<>();
        // Stores the predecessor of each node in the shortest path
        Map<Integer, Integer> prev = new HashMap<>();
        // Priority queue to select the next node with the smallest distance
        PriorityQueue<NodeDist> queue = new PriorityQueue<>(Comparator.comparingInt(n -> n.dist));

        dist.put(sourceId, 0);
        queue.add(new NodeDist(sourceId, 0));
        while (!queue.isEmpty()) {
            NodeDist current = queue.poll();

            // Early exit if we reached the target
            if (current.id == targetId) {
                List<Node> pathNodes = reconstructPath(prev, targetId);
                return new Path(pathNodes, current.dist);
            }

            // Skip outdated queue entries
            if (current.dist > dist.get(current.id))
                continue;

            for (Edge edge : graph.getEdgesFrom(current.id)) {
                int newDist = current.dist + edge.dist;

                if (newDist < dist.getOrDefault(edge.dest, Integer.MAX_VALUE)) {
                    dist.put(edge.dest, newDist);
                    prev.put(edge.dest, current.id);
                    queue.add(new NodeDist(edge.dest, newDist));
                }
            }
        }

        // If no path is found, export an empty result
        return new Path(Collections.emptyList(), -1);
    }

    private static class NodeDist {
        int id, dist;

        NodeDist(int id, int dist) {
            this.id = id;
            this.dist = dist;
        }
    }

    // Reconstructs the path from source to target using the prev map
    private List<Node> reconstructPath(Map<Integer, Integer> prev, int target) {
        List<Node> result = new ArrayList<>();
        for (Integer at = target; at != null; at = prev.get(at)) {
            Node node = graph.getNode(at);
            if (node != null)
                result.add(node);
        }
        Collections.reverse(result);
        return result;
    }
}
