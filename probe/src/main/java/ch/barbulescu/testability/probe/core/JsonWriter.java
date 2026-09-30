package ch.barbulescu.testability.probe.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal, dependency-free JSON writer. The probe jar must not pull in any
 * runtime dependency, so this hand-rolls just enough of JSON to serialize
 * the report: objects, arrays, strings, ints and booleans.
 */
public final class JsonWriter {

    private enum Scope {
        OBJECT, ARRAY
    }

    private static final class Frame {
        final Scope scope;
        boolean first = true;

        Frame(Scope scope) {
            this.scope = scope;
        }
    }

    private final StringBuilder out = new StringBuilder();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private boolean pendingName = false;

    public JsonWriter beginObject() {
        beforeValue();
        out.append('{');
        frames.push(new Frame(Scope.OBJECT));
        return this;
    }

    public JsonWriter endObject() {
        out.append('}');
        frames.pop();
        return this;
    }

    public JsonWriter beginArray() {
        beforeValue();
        out.append('[');
        frames.push(new Frame(Scope.ARRAY));
        return this;
    }

    public JsonWriter endArray() {
        out.append(']');
        frames.pop();
        return this;
    }

    public JsonWriter name(String name) {
        Frame frame = frames.peek();
        if (frame == null || frame.scope != Scope.OBJECT) {
            throw new IllegalStateException("name() called outside an object");
        }
        if (!frame.first) {
            out.append(',');
        }
        frame.first = false;
        appendQuoted(name);
        out.append(':');
        pendingName = true;
        return this;
    }

    public JsonWriter value(String value) {
        beforeValue();
        if (value == null) {
            out.append("null");
        } else {
            appendQuoted(value);
        }
        return this;
    }

    public JsonWriter value(int value) {
        beforeValue();
        out.append(value);
        return this;
    }

    public JsonWriter value(long value) {
        beforeValue();
        out.append(value);
        return this;
    }

    public JsonWriter value(boolean value) {
        beforeValue();
        out.append(value);
        return this;
    }

    private void beforeValue() {
        if (pendingName) {
            pendingName = false;
            return;
        }
        Frame frame = frames.peek();
        if (frame != null && frame.scope == Scope.ARRAY) {
            if (!frame.first) {
                out.append(',');
            }
            frame.first = false;
        }
    }

    private void appendQuoted(String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
