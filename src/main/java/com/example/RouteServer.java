package com.example;

import static spark.Spark.*;
import com.google.gson.Gson;
import java.io.IOException;

import org.example.Graph;
import org.example.Algorithms.Dijkstra;
import org.example.Path;

public class RouteServer {
    public static void main(String[] args) throws IOException {
        // Load graph once at startup
        Graph graph = new Graph("src/main/resources/graphfile.txt");
        Dijkstra dijkstra = new Dijkstra(graph);

        port(4567);
        // Serve static files from src/main/resources/public
        staticFiles.location("/public");

        // JSON serializer
        Gson gson = new Gson();

        // Enable CORS
        options("/*", (request, response) -> {
            String reqHeaders = request.headers("Access-Control-Request-Headers");
            if (reqHeaders != null) {
                response.header("Access-Control-Allow-Headers", reqHeaders);
            }
            String reqMethod = request.headers("Access-Control-Request-Method");
            if (reqMethod != null) {
                response.header("Access-Control-Allow-Methods", reqMethod);
            }
            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Credentials", "true");
        });

        // Route API: /route?start=<id>&end=<id>
        get("/route", (req, res) -> {
            String startParam = req.queryParams("start");
            String endParam = req.queryParams("end");

            // Validate parameters
            if (startParam == null || endParam == null) {
                res.status(400);
                return gson.toJson(new ErrorResponse("Missing start or end parameter"));
            }

            int startId, endId;
            try {
                startId = Integer.parseInt(startParam);
                endId = Integer.parseInt(endParam);
            } catch (NumberFormatException ex) {
                res.status(400);
                return gson.toJson(new ErrorResponse("Invalid integer for start or end"));
            }

            // Compute shortest path
            Path path;
            try {
                path = dijkstra.findPath(startId, endId);
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(new ErrorResponse("Path computation error: " + e.getMessage()));
            }

            // Return path as JSON
            res.type("application/json");
            return gson.toJson(path);
        });

        System.out.println("RouteServer started on http://localhost:4567/");
    }

    // Simple error response wrapper
    static class ErrorResponse {
        final String error;

        ErrorResponse(String msg) {
            this.error = msg;
        }
    }
}