package aco;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Builder
@AllArgsConstructor
public class ATSPSolver {

    private static final Random rng = new Random();
    @Builder.Default
    private double q0 = 0.9;
    @Builder.Default
    private double beta = 2;
    @Builder.Default
    private double rho = 0.1;
    @Builder.Default
    private double alpha = 0.1;
    @Builder.Default
    private int nAnts = 10;
    @Builder.Default
    private double tau0 = 1e-3;
    @Builder.Default
    private int nIterations = 1;

    private Graph convertAdjacencyListToGraph(List<List<Link>> adjacencyList) {
        int nNodes = adjacencyList.size();
        var graph = new Graph(new ArrayList<>(), new double[nNodes][nNodes], new double[nNodes][nNodes]);
        for (int i = 0; i < adjacencyList.size(); ++i) {
            graph.adjacencyList.add(new ArrayList<>());
            for (var link : adjacencyList.get(i)) {
                graph.adjacencyList.get(i).add(link.node);
                graph.costs[i][link.node] = link.cost;
            }
        }
        return graph;
    }

    /**
     * @return Best found hamilton's cycle
     */
    public Tour solve(List<List<Link>> adjacencyList) {
        var graph = convertAdjacencyListToGraph(adjacencyList);
        for (double[] row : graph.pheromones) {
            Arrays.fill(row, tau0);
        }
        return solve(graph);
    }

    private Tour solve(Graph graph) {
        int nNodes = graph.adjacencyList.size();
        // TODO: размещать каждого каждого муравья на своем городе
        var ants = new ArrayList<Ant>();
        for (int i = 0; i < nAnts; ++i) {
            ants.add(new Ant(nNodes, rng.nextInt(nNodes)));
        }

        var bestTour = Collections.<Integer>emptyList();
        var bestTourCost = Double.POSITIVE_INFINITY;
        boolean[] antTerminated = new boolean[ants.size()];

        for (int k = 0; k < nIterations; ++k) {
            Arrays.fill(antTerminated, false);
            for (var ant : ants) {
                ant.tourCost = 0d;
                Arrays.fill(ant.visited, false);
                ant.tour.clear();
                ant.tour.add(ant.startNode);
            }

            for (int i = 1; i < nNodes; ++i) {
                for (int j = 0; j < ants.size(); ++j) {
                    if (antTerminated[j]) {
                        continue;
                    }
                    var ant = ants.get(j);
                    var nextNode = computeNextNode(ant, graph);

                    if (nextNode == -1) {
                        ant.tourCost = Double.POSITIVE_INFINITY;
                        antTerminated[j] = true;
                        continue;
                    }

                    ant.tour.add(nextNode);
                    ant.tourCost += graph.costs[ant.tour.get(i - 1)][ant.tour.get(i)];
                    ant.visited[nextNode] = true;

                    updateEdgePheromone(ant.tour.get(i - 1), ant.tour.get(i), graph);
                }
            }

            var bestAnt = ants.get(0);
            for (int i = 0; i < ants.size(); ++i) {
                if (antTerminated[i]) {
                    continue;
                }
                if (ants.get(i).tourCost < bestAnt.tourCost) {
                    bestAnt = ants.get(i);
                }
            }

            if (bestAnt.tourCost < bestTourCost) {
                bestTourCost = bestAnt.tourCost;
                bestTour = new ArrayList<>(bestAnt.tour);
            }

            updateTourPheromone(bestTour, bestTourCost, graph);
        }

        return new Tour(bestTour, bestTourCost);
    }

    /**
     * Compute "attractiveness" of edgeNode0 -> edgeNode1 transition
     */
    private double edgeProbability(int edgeNode0, int edgeNode1, Graph graph) {
        return Math.pow(graph.costs[edgeNode0][edgeNode1], -beta) * graph.pheromones[edgeNode0][edgeNode1];
    }

    /**
     * Selects next node for agent randomly, respective to probability distribution of available nodes
     */
    private int computeNextNodeStochastically(Ant ant, Graph graph) {
        int lastNode = ant.tour.get(ant.tour.size() - 1);

        double allProbabilitiesSum = 0d;

        for (var nextNode : graph.adjacencyList.get(lastNode)) {
            if (ant.visited[nextNode]) {
                continue;
            }
            allProbabilitiesSum += edgeProbability(lastNode, nextNode, graph);
        }

        double randomValue = rng.nextDouble() * allProbabilitiesSum;
        double sum = 0d;
        int luckyNode = -1;

        for (var nextNode : graph.adjacencyList.get(lastNode)) {
            if (ant.visited[nextNode]) {
                continue;
            }
            luckyNode = nextNode;
            sum += edgeProbability(lastNode, nextNode, graph);
            if (sum > randomValue) {
                break;
            }
        }

        return luckyNode;
    }

    private int computeNextNodeGreedy(Ant ant, Graph graph) {
        int lastNode = ant.tour.get(ant.tour.size() - 1);
        int bestNode = -1;
        double bestNodeProbability = 0d;
        for (var nextNode : graph.adjacencyList.get(lastNode)) {
            if (ant.visited[nextNode]) {
                continue;
            }
            double nextNodeProbability = edgeProbability(lastNode, nextNode, graph);
            if (nextNodeProbability > bestNodeProbability) {
                bestNodeProbability = nextNodeProbability;
                bestNode = nextNode;
            }
        }
        return bestNode;
    }

    private int computeNextNode(Ant ant, Graph graph) {
        return rng.nextDouble() <= q0 ? computeNextNodeGreedy(ant, graph) : computeNextNodeStochastically(ant, graph);
    }

    private void updateEdgePheromone(int edgeNode0, int edgeNode1, Graph graph) {
        graph.pheromones[edgeNode0][edgeNode1] = (1 - rho) * graph.pheromones[edgeNode0][edgeNode1] + rho * tau0;
    }

    private void updateTourPheromone(List<Integer> tour, double tourCost, Graph graph) {
        for (int i = 1; i < tour.size(); ++i) {
            graph.pheromones[tour.get(i - 1)][tour.get(i)] =
                    (1d - alpha) * graph.pheromones[tour.get(i - 1)][tour.get(i)] + alpha / tourCost;
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class Link {
        public int node;
        public double cost;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tour {
        public List<Integer> tour;
        public double cost;
    }

    @AllArgsConstructor
    private static class Graph {
        List<List<Integer>> adjacencyList;
        double[][] costs;
        double[][] pheromones;
    }

    private static class Ant {
        boolean[] visited;
        int startNode;
        List<Integer> tour;
        double tourCost;

        Ant(int nNodes, int startNode) {
            this.tour = new ArrayList<>();
            this.visited = new boolean[nNodes];
            this.startNode = startNode;
            tour.add(startNode);
        }
    }
}
