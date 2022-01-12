package aco;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dumb parser for .atsp format
 */
public class ATSPParser {

    private List<String> readAllLines(InputStream in) throws IOException {
        var lines = new ArrayList<String>();
        try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private int findHeader(List<String> lines, String header) {
        for (int i = 0; i < lines.size(); ++i) {
            if (lines.get(i).trim().startsWith(header)) {
                return i;
            }
        }
        return -1;
    }

    private String getHeaderValue(List<String> lines, String header) {
        for (var line : lines) {
            if (line.trim().startsWith(header)) {
                if (line.indexOf(':') == -1) {
                    continue;
                }
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return null;
    }

    public List<List<ATSPSolver.Link>> parse(InputStream in) throws IOException {
        var lines = readAllLines(in);
        int nNodes = Integer.parseInt(Optional.ofNullable(getHeaderValue(lines, "DIMENSION")).orElse("-1"));
        if (nNodes == -1) {
            throw new RuntimeException("DIMENSION header missed");
        }
        int dataSectionStartIndex = findHeader(lines, "EDGE_WEIGHT_SECTION") + 1;
        var graph = new ArrayList<List<ATSPSolver.Link>>(nNodes);

        int nextLine = 0;
        var values = new ArrayDeque<Double>();

        for (int i = 0; i < nNodes; ++i) {
            graph.add(new ArrayList<>());
            for (int j = 0; j < nNodes; ++j) {
                if (values.isEmpty()) {
                    for (var value : lines.get(dataSectionStartIndex + nextLine).trim().split("\\s+")) {
                        values.offerLast(Double.parseDouble(value));
                    }
                    ++nextLine;
                }
                graph.get(i).add(new ATSPSolver.Link(j, values.pollFirst()));
            }
        }
        return graph;
    }
}
