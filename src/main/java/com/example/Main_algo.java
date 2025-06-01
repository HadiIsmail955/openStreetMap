package com.example;

import java.io.IOException;

import org.example.Graph;
import org.example.Algorithms.Dijkstra;

public class Main_algo {
    public static void main(String[] args) throws IOException {
        Graph graph = new Graph("C:\\Users\\HadiIsmail\\Desktop\\study\\open street map\\graphs\\50K\\graphfile.txt");
        Dijkstra dijkstra = new Dijkstra(graph);
        dijkstra.run(1, 751, "C:\\Users\\HadiIsmail\\Desktop\\study\\open street map\\graphs\\50K\\route.json");
    }
}
