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
        Map<Integer, Integer> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();
        PriorityQueue<NodeDist> queue = new PriorityQueue<>(Comparator.comparingInt(n -> n.dist));

        dist.put(sourceId, 0);
        queue.add(new NodeDist(sourceId, 0));
        while (!queue.isEmpty()) {
            NodeDist current = queue.poll();

            // Early exit if we reached the target
            if (current.id == targetId) {
                List<Node> pathNodes = reconstructPath(prev, targetId);
                Path path = new Path(pathNodes, current.dist);
                path.exportToJson(outputFile);
                System.out.println("Path found");
                return;
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

        Path emptyPath = new Path(Collections.emptyList(), -1);
        emptyPath.exportToJson(outputFile);
        System.out.println("No path found!");
    }

    private static class NodeDist {
        int id, dist;

        NodeDist(int id, int dist) {
            this.id = id;
            this.dist = dist;
        }
    }

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
