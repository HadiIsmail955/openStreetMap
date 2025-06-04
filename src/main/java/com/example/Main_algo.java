package com.example;

import java.io.IOException;

import org.example.Graph;
import org.example.Algorithms.Dijkstra;

public class Main_algo {
    public static void main(String[] args) throws IOException {
        Graph graph = new Graph("C:\\xampp\\htdocs\\Ocean_path_frontend\\graphfile.txt");
        Dijkstra dijkstra = new Dijkstra(graph);
        dijkstra.run(396953, 854755, "C:\\xampp\\htdocs\\Ocean_path_frontend\\route.json");
    }
}
