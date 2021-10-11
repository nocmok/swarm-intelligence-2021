package aco;

import com.opencsv.CSVReader;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {

    public static AntMiner.Dataset readDataset(String path) throws Exception {
        path = Main.class.getClassLoader().getResource(path).getPath();
        var rows = new ArrayList<String[]>();
        var clazz = new ArrayList<String>();
        try (CSVReader reader = new CSVReader(new FileReader(path))) {
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                rows.add(Arrays.copyOfRange(nextLine, 0, nextLine.length - 1));
                clazz.add(nextLine[nextLine.length - 1]);
            }
        }
        var columns = new String[rows.get(0).length][rows.size()];
        for (int c = 0; c < columns.length; ++c) {
            for (int r = 0; r < columns[0].length; ++r) {
                columns[c][r] = rows.get(r)[c];
            }
        }
        return new AntMiner.Dataset(columns, clazz.toArray(new String[0]));
    }

    public static void main(String[] args) throws Exception {

        double evaporation = 0.5d;
        double a = 1d;
        double b = 5d;
        int nAnts = 10;
        int nIterations = 5;
        double threshold = 0.2d;

        var model = new AntMiner(
                evaporation,
                a,
                b,
                nAnts,
                nIterations,
                threshold);

        var dataset = readDataset("yellow-small.data");
        var rules = model.extractRules(dataset);

        for (var rule : rules) {
            System.out.println("covering=" + rule.getCovering());
            System.out.println("accuracy=" + rule.getAccuracy());
            System.out.println(rule.getTerms());
        }
    }
}