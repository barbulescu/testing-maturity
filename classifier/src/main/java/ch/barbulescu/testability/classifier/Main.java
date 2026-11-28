package ch.barbulescu.testability.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: classifier <report.json> [report2.json ...]");
            System.exit(2);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        for (String path : args) {
            JsonNode report = mapper.readTree(new File(path));
            StringBuilder line = new StringBuilder(path + ": ");
            if (Classifier.isMutationRun(report)) {
                line.append("not classified (a PIT minion's report: its failed tests are killed mutants)");
            } else {
                Level level = Classifier.classify(report);
                line.append("LEVEL_").append(level.number()).append(" (").append(level.description()).append(")");
            }
            Set<TestStyle> styles = Classifier.styles(report);
            if (!styles.isEmpty()) {
                line.append(" styles:");
                for (TestStyle style : styles) {
                    line.append(' ').append(style.label());
                }
            }
            System.out.println(line);
        }
    }
}
