package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassFileCallsTest {

    @SuppressWarnings("unused")
    static class Fixture {
        private long total;

        void calls(List<String> list) {
            list.add("a");
            String.valueOf(1.5d);
            new StringBuilder().append(total);
        }

        // tableswitch and lookupswitch have padding and variable length: the calls after them must still be found.
        int switches(int dense, int sparse) {
            int result;
            switch (dense) {
                case 1: result = 10; break;
                case 2: result = 20; break;
                case 3: result = 30; break;
                default: result = 0;
            }
            switch (sparse) {
                case 1: result += 1; break;
                case 1_000: result += 2; break;
                case 1_000_000: result += 3; break;
                default: break;
            }
            Math.abs(result);
            return result;
        }

        // More than 255 locals make javac use wide loads and stores.
        long wide() {
            long l0 = 0, l1 = 1, l2 = 2, l3 = 3, l4 = 4, l5 = 5, l6 = 6, l7 = 7, l8 = 8, l9 = 9;
            long m0 = 0, m1 = 1, m2 = 2, m3 = 3, m4 = 4, m5 = 5, m6 = 6, m7 = 7, m8 = 8, m9 = 9;
            long n0 = 0, n1 = 1, n2 = 2, n3 = 3, n4 = 4, n5 = 5, n6 = 6, n7 = 7, n8 = 8, n9 = 9;
            long o0 = 0, o1 = 1, o2 = 2, o3 = 3, o4 = 4, o5 = 5, o6 = 6, o7 = 7, o8 = 8, o9 = 9;
            long p0 = 0, p1 = 1, p2 = 2, p3 = 3, p4 = 4, p5 = 5, p6 = 6, p7 = 7, p8 = 8, p9 = 9;
            long q0 = 0, q1 = 1, q2 = 2, q3 = 3, q4 = 4, q5 = 5, q6 = 6, q7 = 7, q8 = 8, q9 = 9;
            long r0 = 0, r1 = 1, r2 = 2, r3 = 3, r4 = 4, r5 = 5, r6 = 6, r7 = 7, r8 = 8, r9 = 9;
            long s0 = 0, s1 = 1, s2 = 2, s3 = 3, s4 = 4, s5 = 5, s6 = 6, s7 = 7, s8 = 8, s9 = 9;
            long t0 = 0, t1 = 1, t2 = 2, t3 = 3, t4 = 4, t5 = 5, t6 = 6, t7 = 7, t8 = 8, t9 = 9;
            long u0 = 0, u1 = 1, u2 = 2, u3 = 3, u4 = 4, u5 = 5, u6 = 6, u7 = 7, u8 = 8, u9 = 9;
            long v0 = 0, v1 = 1, v2 = 2, v3 = 3, v4 = 4, v5 = 5, v6 = 6, v7 = 7, v8 = 8, v9 = 9;
            long w0 = 0, w1 = 1, w2 = 2, w3 = 3, w4 = 4, w5 = 5, w6 = 6, w7 = 7, w8 = 8, w9 = 9;
            long x0 = 0, x1 = 1, x2 = 2, x3 = 3, x4 = 4, x5 = 5, x6 = 6, x7 = 7, x8 = 8, x9 = 9;
            int last = 0;
            last++;
            Long.valueOf(x9 + last);
            return l0 + m0 + n0 + o0 + p0 + q0 + r0 + s0 + t0 + u0 + v0 + w0 + x0;
        }

        Runnable lambda() {
            return () -> Thread.yield();
        }
    }

    @Test
    void findsEveryKindOfInvocation() throws IOException {
        Map<String, List<ClassFileCalls.Call>> calls = read(Fixture.class);

        List<String> found = describe(calls.get("calls(Ljava/util/List;)V"));

        assertTrue(found.contains("java/util/List.add(Ljava/lang/Object;)Z"), found::toString);
        assertTrue(found.contains("java/lang/String.valueOf(D)Ljava/lang/String;"), found::toString);
        assertTrue(found.contains("java/lang/StringBuilder.<init>()V"), found::toString);
        assertTrue(found.contains("java/lang/StringBuilder.append(J)Ljava/lang/StringBuilder;"), found::toString);
    }

    @Test
    void findsCallsAfterSwitches() throws IOException {
        List<String> found = describe(read(Fixture.class).get("switches(II)I"));

        assertEquals(1, found.size(), found::toString);
        assertEquals("java/lang/Math.abs(I)I", found.get(0));
    }

    @Test
    void findsCallsAfterWideInstructions() throws IOException {
        List<String> found = describe(read(Fixture.class).get("wide()J"));

        assertTrue(found.contains("java/lang/Long.valueOf(J)Ljava/lang/Long;"), found::toString);
    }

    @Test
    void aLambdaBodyIsItsOwnMethod() throws IOException {
        Map<String, List<ClassFileCalls.Call>> calls = read(Fixture.class);

        List<String> lambdaCalls = new ArrayList<>();
        calls.forEach((method, methodCalls) -> {
            if (method.startsWith("lambda$lambda$")) {
                lambdaCalls.addAll(describe(methodCalls));
            }
        });

        assertEquals(1, lambdaCalls.size(), calls.keySet()::toString);
        assertEquals("java/lang/Thread.yield()V", lambdaCalls.get(0));
    }

    @Test
    void rejectsWhatIsNoClassFile() {
        assertThrows(IOException.class, () -> ClassFileCalls.read(new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
    }

    private static Map<String, List<ClassFileCalls.Call>> read(Class<?> type) throws IOException {
        try (InputStream in = type.getClassLoader().getResourceAsStream(type.getName().replace('.', '/') + ".class")) {
            return ClassFileCalls.read(in);
        }
    }

    private static List<String> describe(List<ClassFileCalls.Call> calls) {
        List<String> described = new ArrayList<>();
        for (ClassFileCalls.Call call : calls) {
            described.add(call.owner + "." + call.name + call.descriptor);
        }
        return described;
    }
}
