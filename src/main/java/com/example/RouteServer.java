// package com.example;

// import static spark.Spark.*;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.List;

// import org.example.Graph;
// import org.example.Algorithms.Dijkstra;

// public class RouteServer {
// public static void main(String[] args) throws IOException {
// Graph graph = new Graph("C:\\Users\\HadiIsmail\\Desktop\\study\\open street
// map\\graphs\\50K\\graphfile.txt");
// Dijkstra dijkstra = new Dijkstra(graph);
// port(4567);
// staticFiles.location("/public");
// dijkstra.run(1, 751, "C:\\Users\\HadiIsmail\\Desktop\\study\\open street
// map\\graphs\\50K\\route.json");
// get("/route", (req, res) -> {
// String startParam = req.queryParams("start");
// String endParam = req.queryParams("end");
// if (startParam == null || endParam == null) {
// res.status(400);
// return "{\"error\":\"Missing start or end param\"}";
// }

// int startId, endId;
// try {
// startId = Integer.parseInt(startParam);
// endId = Integer.parseInt(endParam);
// } catch (NumberFormatException ex) {
// res.status(400);
// return "{\"error\":\"Invalid integer for start or end\"}";
// }

// // Run Dijkstra on those node IDs
// try {
// dijkstra.run(1, 751, "C:\\Users\\HadiIsmail\\Desktop\\study\\open street
// map\\graphs\\50K\\route.json");
// } catch (IOException e) {
// res.status(500);
// return "{\"error\":\"Server exception: " + e.getMessage() + "\"}";
// }

// // Build a simple JSON response
// res.type("application/json");
// return {"message":"ok"}
// });

// System.out.println("RouteServer started on http://localhost:4567/");
// }
// }
