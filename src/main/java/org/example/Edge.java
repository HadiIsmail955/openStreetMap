package org.example;

public class Edge {
    public int start, dest;
    public int dist;

    public Edge(int start, int dest, int dist) {
        this.start = start;
        this.dest = dest;
        this.dist = dist;
    }
}