package ch.barbulescu.testability.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

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
            Level level = Classifier.classify(report);
            System.out.println(path + ": LEVEL_" + level.number() + " (" + level.description() + ")");
        }
    }
}
