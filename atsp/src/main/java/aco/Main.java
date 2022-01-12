package aco;

import java.io.IOException;
import java.util.List;

public class Main {

    private static void runDataset(String dsName) throws IOException {
        var model = ATSPSolver.builder()
                .nAnts(100)
                .nIterations(100)
                .build();

        var parser = new ATSPParser();
        var graph = parser.parse(Main.class.getClassLoader().getResourceAsStream(dsName));
        var tour = model.solve(graph);

        System.out.println("dataset: " + dsName);
        System.out.println("nodes number: " + graph.size());
        System.out.println("best tour found: " + tour.tour);
        System.out.println("best tour cost: " + tour.cost);
    }

    public static void main(String[] args) throws IOException {
        for(var dsName : List.of("br17", "ry48p", "ft53", "ft70")) {
            runDataset(dsName);
            System.out.println();
        }
    }
}