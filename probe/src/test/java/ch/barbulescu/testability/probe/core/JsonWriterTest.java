package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

    @Test
    void writesNestedObjectWithArray() {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.name("schemaVersion").value(1);
        writer.name("tests").beginObject();
        writer.name("total").value(3);
        writer.endObject();
        writer.name("probeErrors").beginArray();
        writer.value("java.lang.RuntimeException");
        writer.endArray();
        writer.endObject();

        assertEquals(
                "{\"schemaVersion\":1,\"tests\":{\"total\":3},\"probeErrors\":[\"java.lang.RuntimeException\"]}",
                writer.toString());
    }

    @Test
    void escapesSpecialCharacters() {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.name("message").value("line1\n\"quoted\"\\backslash");
        writer.endObject();

        assertEquals("{\"message\":\"line1\\n\\\"quoted\\\"\\\\backslash\"}", writer.toString());
    }
}
