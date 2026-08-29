import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.Inflater;

/**
 * FBX 7.4 binary to wing JSON for decorativewings.
 */
public class FbxDump {
    private static byte[] data;
    private static int pos;
    private static boolean large;
    private static double[] vertices;
    private static int[] polygons;
    private static double[] uvs;
    private static int[] uvIndex;
    private static double rotX, rotY, rotZ;
    private static double scaleX = 1, scaleY = 1, scaleZ = 1;

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args.length > 0 ? args[0] : "C:\\MODSS\\decorativewings\\WingsTest.fbx");
        Path out = Path.of(args.length > 1 ? args[1] : "C:\\MODSS\\decorativewings\\src\\main\\resources\\assets\\decorativewings\\models\\wing_fbx.json");
        data = Files.readAllBytes(path);
        pos = 23;
        int version = u32();
        large = version >= 7500;
        System.out.println("FBX version " + version);
        while (pos < data.length) {
            if (!readNode()) {
                break;
            }
        }
        if (vertices == null || polygons == null) {
            throw new IllegalStateException("No mesh");
        }
        writeJson(out);
    }

    private static boolean readNode() throws IOException {
        long end = large ? u64() : u32();
        long nprop = large ? u64() : u32();
        long propLen = large ? u64() : u32();
        int nameLen = u8();
        if (end == 0 && nprop == 0 && propLen == 0 && nameLen == 0) {
            return false;
        }
        String name = str(nameLen);
        int propStart = pos;
        List<Object> props = new ArrayList<>();
        for (int i = 0; i < nprop; i++) {
            props.add(readProp());
        }
        pos = Math.max(pos, propStart + (int) propLen);

        if (name.equals("Vertices") && props.size() == 1 && props.get(0) instanceof double[] d) {
            vertices = d;
        }
        if (name.equals("PolygonVertexIndex") && props.get(0) instanceof int[] a) {
            polygons = a;
        }
        if (name.equals("UV") && props.get(0) instanceof double[] d) {
            uvs = d;
        }
        if (name.equals("UVIndex") && props.get(0) instanceof int[] a) {
            uvIndex = a;
        }
        if (name.equals("P") && props.size() >= 8 && props.get(0) instanceof String key) {
            if (key.equals("Lcl Rotation")) {
                rotX = toD(props.get(4));
                rotY = toD(props.get(5));
                rotZ = toD(props.get(6));
            } else if (key.equals("Lcl Scaling")) {
                scaleX = toD(props.get(4));
                scaleY = toD(props.get(5));
                scaleZ = toD(props.get(6));
            }
        }

        while (pos < end) {
            long peekEnd = large ? peek64() : peek32();
            if (peekEnd == 0) {
                pos += large ? 25 : 13;
                break;
            }
            if (!readNode()) {
                break;
            }
        }
        pos = (int) end;
        return true;
    }

    private static double toD(Object o) {
        if (o instanceof Double d) {
            return d;
        }
        if (o instanceof Integer i) {
            return i;
        }
        if (o instanceof Long l) {
            return l;
        }
        return 0;
    }

    private static void writeJson(Path out) throws IOException {
        int vc = vertices.length / 3;
        float[] px = new float[vc];
        float[] py = new float[vc];
        float[] pz = new float[vc];
        double rx = Math.toRadians(rotX);
        double cr = Math.cos(rx);
        double sr = Math.sin(rx);
        for (int i = 0; i < vc; i++) {
            double x = vertices[i * 3] * scaleX / 100.0;
            double y = vertices[i * 3 + 1] * scaleY / 100.0;
            double z = vertices[i * 3 + 2] * scaleZ / 100.0;
            double y2 = y * cr - z * sr;
            double z2 = y * sr + z * cr;
            px[i] = (float) x;
            py[i] = (float) y2;
            pz[i] = (float) z2;
        }
        float minx = Float.POSITIVE_INFINITY;
        float miny = minx;
        float minz = minx;
        float maxx = Float.NEGATIVE_INFINITY;
        float maxy = maxx;
        float maxz = maxx;
        for (int i = 0; i < vc; i++) {
            minx = Math.min(minx, px[i]);
            maxx = Math.max(maxx, px[i]);
            miny = Math.min(miny, py[i]);
            maxy = Math.max(maxy, py[i]);
            minz = Math.min(minz, pz[i]);
            maxz = Math.max(maxz, pz[i]);
        }
        System.out.println("verts=" + vc + " rot=" + rotX + "," + rotY + "," + rotZ + " scale=" + scaleX);
        System.out.println("bounds x " + minx + ".." + maxx + " y " + miny + ".." + maxy + " z " + minz + ".." + maxz);
        System.out.println("uvs=" + (uvs == null ? 0 : uvs.length / 2) + " uvIndex=" + (uvIndex == null ? 0 : uvIndex.length));
        int nonzeroUv = 0;
        if (uvs != null) {
            for (int i = 0; i < uvs.length; i += 2) {
                if (Math.abs(uvs[i]) > 1e-6 || Math.abs(uvs[i + 1]) > 1e-6) {
                    nonzeroUv++;
                }
            }
        }
        System.out.println("nonzeroUvPairs=" + nonzeroUv);

        float span = Math.max(maxx - minx, Math.max(maxy - miny, maxz - minz));
        float toPixels = 14.0f / span;
        System.out.println("spanMeters=" + span + " toPixels=" + toPixels);

        List<int[]> tris = new ArrayList<>();
        List<Integer> poly = new ArrayList<>();
        List<Integer> polyUv = new ArrayList<>();
        int uvCursor = 0;
        for (int i = 0; i < polygons.length; i++) {
            int idx = polygons[i];
            boolean last = idx < 0;
            int vi = last ? ~idx : idx;
            poly.add(vi);
            if (uvIndex != null && uvCursor < uvIndex.length) {
                polyUv.add(uvIndex[uvCursor++]);
            } else {
                polyUv.add(-1);
            }
            if (last) {
                for (int t = 1; t + 1 < poly.size(); t++) {
                    tris.add(new int[]{
                            poly.get(0), poly.get(t), poly.get(t + 1),
                            polyUv.get(0), polyUv.get(t), polyUv.get(t + 1)
                    });
                }
                poly.clear();
                polyUv.clear();
            }
        }
        System.out.println("tris=" + tris.size());

        StringBuilder json = new StringBuilder();
        json.append("{\"scale\":").append(toPixels).append(",\"cx\":").append((minx + maxx) * 0.5f * toPixels)
                .append(",\"cy\":").append((miny + maxy) * 0.5f * toPixels)
                .append(",\"cz\":").append((minz + maxz) * 0.5f * toPixels)
                .append(",\"minx\":").append(minx * toPixels)
                .append(",\"maxx\":").append(maxx * toPixels)
                .append(",\"miny\":").append(miny * toPixels)
                .append(",\"maxy\":").append(maxy * toPixels)
                .append(",\"minz\":").append(minz * toPixels)
                .append(",\"maxz\":").append(maxz * toPixels)
                .append(",\"tris\":[\n");
        for (int t = 0; t < tris.size(); t++) {
            int[] tr = tris.get(t);
            json.append("  {\"v\":[");
            for (int k = 0; k < 3; k++) {
                int vi = tr[k];
                float u = 0;
                float v = 0;
                int uvi = tr[3 + k];
                if (uvi >= 0 && uvs != null && uvi * 2 + 1 < uvs.length) {
                    u = (float) uvs[uvi * 2];
                    v = (float) uvs[uvi * 2 + 1];
                }
                if (k > 0) {
                    json.append(",");
                }
                json.append("[").append(px[vi] * toPixels).append(",")
                        .append(py[vi] * toPixels).append(",")
                        .append(pz[vi] * toPixels).append(",")
                        .append(u).append(",").append(v).append("]");
            }
            json.append("]}");
            if (t + 1 < tris.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("]}\n");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString());
        System.out.println("wrote " + out + " bytes=" + json.length());
    }

    private static Object readProp() throws IOException {
        int type = u8();
        return switch (type) {
            case 'Y' -> (short) u16();
            case 'C' -> u8();
            case 'I' -> u32();
            case 'F' -> Float.intBitsToFloat(u32());
            case 'D' -> Double.longBitsToDouble(u64());
            case 'L' -> u64();
            case 'S' -> str(u32());
            case 'R' -> {
                int n = u32();
                pos += n;
                yield "raw[" + n + "]";
            }
            case 'f' -> readFloatArray();
            case 'd' -> readDoubleArray();
            case 'i' -> readIntArray();
            case 'l' -> {
                skipArray(8);
                yield "long[]";
            }
            case 'b' -> {
                skipArray(1);
                yield "bool[]";
            }
            default -> throw new IOException("Unknown prop type " + (char) type + " at " + pos);
        };
    }

    private static void skipArray(int elem) {
        int len = u32();
        int enc = u32();
        int clen = u32();
        pos += enc == 1 ? clen : len * elem;
    }

    private static double[] readDoubleArray() throws IOException {
        byte[] raw = readArrayBytes(8);
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        double[] out = new double[raw.length / 8];
        for (int i = 0; i < out.length; i++) {
            out[i] = bb.getDouble();
        }
        return out;
    }

    private static float[] readFloatArray() throws IOException {
        byte[] raw = readArrayBytes(4);
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[raw.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = bb.getFloat();
        }
        return out;
    }

    private static int[] readIntArray() throws IOException {
        byte[] raw = readArrayBytes(4);
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[raw.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = bb.getInt();
        }
        return out;
    }

    private static byte[] readArrayBytes(int elem) throws IOException {
        int len = u32();
        int enc = u32();
        int clen = u32();
        byte[] payload = Arrays.copyOfRange(data, pos, pos + (enc == 1 ? clen : len * elem));
        pos += enc == 1 ? clen : len * elem;
        if (enc == 0) {
            return payload;
        }
        Inflater inf = new Inflater();
        inf.setInput(payload);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && inf.needsInput()) {
                    break;
                }
                bos.write(buf, 0, n);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
        inf.end();
        return bos.toByteArray();
    }

    private static int u8() {
        return data[pos++] & 0xFF;
    }

    private static int u16() {
        int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
        pos += 2;
        return v;
    }

    private static int u32() {
        int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8)
                | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
        pos += 4;
        return v;
    }

    private static long u64() {
        return (u32() & 0xFFFFFFFFL) | ((long) u32() << 32);
    }

    private static int peek32() {
        return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8)
                | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
    }

    private static long peek64() {
        long lo = peek32() & 0xFFFFFFFFL;
        int p = pos + 4;
        long hi = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8)
                | ((data[p + 2] & 0xFF) << 16) | ((data[p + 3] & 0xFF) << 24);
        return lo | (hi << 32);
    }

    private static String str(int n) {
        String s = new String(data, pos, n, StandardCharsets.UTF_8);
        pos += n;
        return s;
    }
}
