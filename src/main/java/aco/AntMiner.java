package aco;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AntMiner {

    private static final String TRUE_CLASS = "T";

    private final double evaporation;
    private final double a;
    private final double b;
    private final int nAnts;
    private final int nIterations;
    private final double threshold;

    public AntMiner(double evaporation,
                    double a,
                    double b,
                    int nAnts,
                    int nIterations,
                    double threshold) {
        this.evaporation = evaporation;
        this.a = a;
        this.b = b;
        this.nAnts = nAnts;
        this.nIterations = nIterations;
        this.threshold = threshold;
    }

    public List<Rule> extractRules(Dataset dataset) {
        var rules = new ArrayList<Rule>();
        var nodes = extractNodesFromDataset(dataset);
        int sizeThreshold = (int) (dataset.nRows() * threshold);
        for (int i = 0; i < nIterations; ++i) {
            var rule = extractRule(nodes, dataset);

            var terms = rule.stream()
                    .map(this::mapNodeToTerm)
                    .collect(Collectors.toList());

            var accuracy = ruleAccuracy(rule, dataset);

            var totalMatch = (int) IntStream.range(0, dataset.nRows())
                    .filter(r -> rowMatchRule(rule, dataset, r))
                    .filter(r -> dataset.clazz[r].equals(TRUE_CLASS))
                    .count();

            rules.add(new Rule(totalMatch, terms, accuracy));

            for (int r = dataset.nRows() - 1; r >= 0; --r) {
                if (rowMatchRule(rule, dataset, r)) {
                    dataset.removeRow(r);
                }
            }

            if (dataset.nRows() <= sizeThreshold) {
                break;
            }
        }

        // TODO: Минимизировать набор правил. Если есть правила, которые следуют из других, убирать

        return rules;
    }

    private List<Node> extractRule(List<Node> nodes, Dataset dataset) {
        List<Node> bestRule = Collections.emptyList();
        double bestRuleaccuracy = 0d;

        setPheromone(nodes, 1d);
        refreshHeuristic(nodes, dataset);

        for (int ant = 0; ant < nAnts; ++ant) {

            var path = new ArrayList<Node>();
            double accuracy = 0d;
            boolean[] used = new boolean[dataset.nCols()];

            for (int i = 0; i < dataset.nCols(); ++i) {
                var nextNodes = nodes.stream()
                        .filter(n -> !used[n.var])
                        .collect(Collectors.toCollection(ArrayList::new));
                var probabilities = nextNodes.stream()
                        .mapToDouble(this::nodeProbability)
                        .toArray();
                var nextNode = nextNodes.get(random(probabilities));
                path.add(nextNode);
                var nextaccuracy = ruleAccuracy(path, dataset);
                if (nextaccuracy <= accuracy) {
                    path.remove(path.size() - 1);
                    break;
                }
                used[nextNode.var] = true;
                accuracy = nextaccuracy;
            }

            evaporate(nodes);
            addPheromone(path, accuracy);
            if (accuracy > bestRuleaccuracy || accuracy == bestRuleaccuracy && path.size() < bestRule.size()) {
                bestRuleaccuracy = accuracy;
                bestRule = path;
            }
        }

        return bestRule;
    }

    private Term mapNodeToTerm(Node node) {
        return new Term(node.var, node.val);
    }

    private double nodeProbability(Node node) {
        return Math.pow(node.pheromone + 1, a) * Math.pow(node.heuristic + 1, b);
    }

    private void evaporate(List<Node> nodes) {
        for (var node : nodes) {
            node.pheromone *= (1 - evaporation);
        }
    }

    private void setPheromone(List<Node> nodes, double pheromone) {
        for (var node : nodes) {
            node.pheromone = pheromone;
        }
    }

    private void addPheromone(List<Node> nodes, double pheromoneToAdd) {
        for (var node : nodes) {
            node.pheromone += pheromoneToAdd;
        }
    }

    private int upperBound(double[] arr, double val) {
        int index = Arrays.binarySearch(arr, val);
        if (index < 0) {
            index = -(index + 1);
        }
        return Integer.min(arr.length - 1, index);
    }

    private int random(double[] probabilities) {
        int n = probabilities.length;
        double[] cusum = new double[n];
        double sum = 0d;
        for (int i = 0; i < n; ++i) {
            cusum[i] = sum + probabilities[i];
            sum = cusum[i];
        }
        return upperBound(cusum, Math.random() * sum);
    }

    private void refreshHeuristic(List<Node> nodes, Dataset dataset) {
        for (var node : nodes) {
            Supplier<IntStream> stream = () -> IntStream.range(0, dataset.nRows())
                    .filter(row -> dataset.columns[node.var][row].equals(node.val));
            long total = stream.get()
                    .count();
            if (total == 0L) {
                node.heuristic = 0d;
                continue;
            }
            long trueTotal = stream.get()
                    .filter(row -> dataset.clazz[row].equals(TRUE_CLASS))
                    .count();
            node.heuristic = (double) trueTotal / total;
        }
    }

    private boolean rowMatchRule(List<Node> nodes, Dataset dataset, int row) {
        for (var node : nodes) {
            if (!dataset.columns[node.var][row].equals(node.val)) {
                return false;
            }
        }
        return !nodes.isEmpty();
    }

    private double ruleAccuracy(List<Node> nodes, Dataset dataset) {
        Supplier<IntStream> stream = () -> IntStream.range(0, dataset.nRows())
                .filter(row -> rowMatchRule(nodes, dataset, row));
        long total = stream.get()
                .count();
        if (total == 0L) {
            return 0d;
        }
        long trueTotal = stream.get()
                .filter(row -> dataset.clazz[row].equals(TRUE_CLASS))
                .count();
        return (double) trueTotal / total;
    }

    private List<Node> extractNodesFromDataset(Dataset dataset) {
        var nodes = new ArrayList<Node>();
        for (int c = 0; c < dataset.nCols(); ++c) {
            int column = c;
            var values = Arrays.stream(dataset.columns[c])
                    .distinct()
                    .collect(Collectors.toList());
            nodes.addAll(values.stream()
                    .map(v -> new Node(column, v))
                    .collect(Collectors.toList()));
        }
        return nodes;
    }

    public static class Dataset {
        private final String[][] columns;
        private final String[] clazz;
        private int rows;

        public Dataset(String[][] columns, String[] clazz) {
            this.columns = columns;
            this.clazz = clazz;
            this.rows = columns[0].length;
        }

        public void removeRow(int row) {
            for (int c = 0; c < nCols(); ++c) {
                columns[c][row] = columns[c][rows - 1];
            }
            clazz[row] = clazz[rows - 1];
            --rows;
        }

        public int nCols() {
            return columns.length;
        }

        public int nRows() {
            return rows;
        }
    }

    @AllArgsConstructor
    @Data
    public static class Rule {
        private int covering;
        private List<Term> terms;
        private double accuracy;
    }

    @AllArgsConstructor
    @Data
    public static class Term {
        private int var;
        private String val;
    }

    private static class Node {
        public double heuristic = 0d;
        public double pheromone = 0d;
        // Номер колонки которую представляет эта нода
        public int var;
        // Значение колонки
        public String val;

        public Node(int var, String val) {
            this.var = var;
            this.val = val;
        }
    }
}
