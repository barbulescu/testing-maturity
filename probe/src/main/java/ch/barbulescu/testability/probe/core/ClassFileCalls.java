package ch.barbulescu.testability.probe.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads from a class file which methods each of its methods calls, without
 * a bytecode library: only the constant pool and the invoke instructions of
 * each method's code are decoded.
 */
public final class ClassFileCalls {

    /** A method call as the bytecode names it: internal class name, method name, descriptor. */
    public static final class Call {
        public final String owner;
        public final String name;
        public final String descriptor;

        Call(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private static final int CONSTANT_UTF8 = 1;
    private static final int CONSTANT_LONG = 5;
    private static final int CONSTANT_DOUBLE = 6;
    private static final int CONSTANT_CLASS = 7;
    private static final int CONSTANT_METHODREF = 10;
    private static final int CONSTANT_INTERFACE_METHODREF = 11;
    private static final int CONSTANT_NAME_AND_TYPE = 12;
    private static final int CONSTANT_METHOD_HANDLE = 15;

    private ClassFileCalls() {
    }

    /** Keyed by method name and descriptor, e.g. {@code "run()V"}. Abstract and native methods have no calls. */
    public static Map<String, List<Call>> read(InputStream classFile) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(readFully(classFile)));
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int poolSize = in.readUnsignedShort();
        int[] tags = new int[poolSize];
        int[] firstIndex = new int[poolSize];
        int[] secondIndex = new int[poolSize];
        String[] utf8 = new String[poolSize];
        for (int i = 1; i < poolSize; i++) {
            int tag = in.readUnsignedByte();
            tags[i] = tag;
            switch (tag) {
                case CONSTANT_UTF8:
                    utf8[i] = in.readUTF();
                    break;
                case CONSTANT_CLASS:
                case 8: // String
                case 16: // MethodType
                case 19: // Module
                case 20: // Package
                    firstIndex[i] = in.readUnsignedShort();
                    break;
                case CONSTANT_METHOD_HANDLE:
                    in.readUnsignedByte();
                    firstIndex[i] = in.readUnsignedShort();
                    break;
                case 3: // Integer
                case 4: // Float
                    in.readInt();
                    break;
                case CONSTANT_LONG:
                case CONSTANT_DOUBLE:
                    in.readLong();
                    i++;
                    break;
                case 9: // Fieldref
                case CONSTANT_METHODREF:
                case CONSTANT_INTERFACE_METHODREF:
                case CONSTANT_NAME_AND_TYPE:
                case 17: // Dynamic
                case 18: // InvokeDynamic
                    firstIndex[i] = in.readUnsignedShort();
                    secondIndex[i] = in.readUnsignedShort();
                    break;
                default:
                    throw new IOException("unknown constant pool tag " + tag);
            }
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        in.readUnsignedShort();
        skip(in, 2 * in.readUnsignedShort());
        int fieldCount = in.readUnsignedShort();
        for (int i = 0; i < fieldCount; i++) {
            skip(in, 6);
            skipAttributes(in);
        }
        Map<String, List<Call>> callsByMethod = new HashMap<>();
        int methodCount = in.readUnsignedShort();
        for (int i = 0; i < methodCount; i++) {
            in.readUnsignedShort();
            String key = utf8[in.readUnsignedShort()] + utf8[in.readUnsignedShort()];
            List<Call> calls = Collections.emptyList();
            int attributeCount = in.readUnsignedShort();
            for (int a = 0; a < attributeCount; a++) {
                String attributeName = utf8[in.readUnsignedShort()];
                byte[] attribute = new byte[in.readInt()];
                in.readFully(attribute);
                if ("Code".equals(attributeName)) {
                    calls = callsIn(attribute, tags, firstIndex, secondIndex, utf8);
                }
            }
            callsByMethod.put(key, calls);
        }
        return callsByMethod;
    }

    private static List<Call> callsIn(byte[] codeAttribute, int[] tags, int[] firstIndex, int[] secondIndex,
            String[] utf8) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(codeAttribute));
        skip(in, 4);
        byte[] code = new byte[in.readInt()];
        in.readFully(code);
        List<Call> calls = new ArrayList<>();
        for (int pc = 0; pc < code.length; pc += instructionLength(code, pc)) {
            int opcode = code[pc] & 0xFF;
            // invokevirtual, invokespecial, invokestatic, invokeinterface
            if (opcode >= 0xB6 && opcode <= 0xB9) {
                int ref = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
                if (tags[ref] == CONSTANT_METHODREF || tags[ref] == CONSTANT_INTERFACE_METHODREF) {
                    int nameAndType = secondIndex[ref];
                    calls.add(new Call(utf8[firstIndex[firstIndex[ref]]],
                            utf8[firstIndex[nameAndType]], utf8[secondIndex[nameAndType]]));
                }
            }
        }
        return calls;
    }

    /** The length of the instruction at {@code pc}, its operands included (JVM spec, chapter 6). */
    static int instructionLength(byte[] code, int pc) {
        int opcode = code[pc] & 0xFF;
        switch (opcode) {
            case 0x10: // bipush
            case 0x12: // ldc
            case 0xA9: // ret
            case 0xBC: // newarray
                return 2;
            case 0x11: // sipush
            case 0x13: // ldc_w
            case 0x14: // ldc2_w
            case 0x84: // iinc
                return 3;
            case 0xC5: // multianewarray
                return 4;
            case 0xB9: // invokeinterface
            case 0xBA: // invokedynamic
            case 0xC8: // goto_w
            case 0xC9: // jsr_w
                return 5;
            case 0xC4: // wide
                return (code[pc + 1] & 0xFF) == 0x84 ? 6 : 4;
            case 0xAA: { // tableswitch
                int operands = pc + 1 + padding(pc);
                int low = readInt(code, operands + 4);
                int high = readInt(code, operands + 8);
                return operands - pc + 12 + 4 * (high - low + 1);
            }
            case 0xAB: { // lookupswitch
                int operands = pc + 1 + padding(pc);
                int pairs = readInt(code, operands + 4);
                return operands - pc + 8 + 8 * pairs;
            }
            default:
                break;
        }
        if (opcode >= 0x15 && opcode <= 0x19 || opcode >= 0x36 && opcode <= 0x3A) {
            return 2; // load and store with a local variable index
        }
        if (opcode >= 0x99 && opcode <= 0xA8 || opcode >= 0xB2 && opcode <= 0xB8
                || opcode == 0xBB || opcode == 0xBD || opcode == 0xC0 || opcode == 0xC1
                || opcode == 0xC6 || opcode == 0xC7) {
            return 3; // branches, field access, invocations, new, anewarray, checkcast, instanceof, ifnull
        }
        return 1;
    }

    /** Switch operands start at the next multiple of four from the start of the code. */
    private static int padding(int pc) {
        return (4 - (pc + 1) % 4) % 4;
    }

    private static int readInt(byte[] code, int at) {
        return (code[at] & 0xFF) << 24 | (code[at + 1] & 0xFF) << 16 | (code[at + 2] & 0xFF) << 8 | code[at + 3] & 0xFF;
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort();
            skip(in, in.readInt());
        }
    }

    /** Reading from a byte array, {@code skipBytes} skips all of them unless the class file is cut short. */
    private static void skip(DataInputStream in, int bytes) throws IOException {
        if (in.skipBytes(bytes) != bytes) {
            throw new IOException("class file is truncated");
        }
    }
}
