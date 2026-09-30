package ch.barbulescu.testability.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks that every field present in {@code expected} also exists, with
 * the same value, in {@code actual} - fields {@code actual} has that
 * {@code expected} doesn't are ignored. This is what lets each example's
 * {@code expected.json} assert only the fields that scenario cares about.
 */
final class JsonSubset {

    private JsonSubset() {
    }

    static void assertSubset(JsonNode expected, JsonNode actual, String path) {
        if (expected.isObject()) {
            assertTrue(actual.isObject(), path + ": expected an object, got " + actual.getNodeType());
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode actualValue = actual.get(field.getKey());
                if (actualValue == null) {
                    fail(path + "." + field.getKey() + ": missing from actual report");
                }
                assertSubset(field.getValue(), actualValue, path + "." + field.getKey());
            }
        } else if (expected.isArray()) {
            assertTrue(actual.isArray(), path + ": expected an array, got " + actual.getNodeType());
            assertEquals(expected.size(), actual.size(), path + ": array size mismatch");
            for (int i = 0; i < expected.size(); i++) {
                assertSubset(expected.get(i), actual.get(i), path + "[" + i + "]");
            }
        } else {
            assertEquals(expected, actual, path + ": value mismatch");
        }
    }
}
