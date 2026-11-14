package hanja.pack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Packs a dictionary (app/src/main/assets/NAME.tsv: "key\tvalue\n" lines sorted by the key's UTF-8 bytes) into
 * NAME.pk, which is what goes into the APK: the lines in blocks of about {@link #BLOCK} bytes, each deflated, behind
 * an index of every block's first key. The app (PackedTsv.kt) finds the block in the index and inflates only that
 * one, so the 25 MB of dictionaries ship as about 10 MB and a lookup still reads a few kilobytes.
 *
 * <pre>
 * "HJP1"  int blocks  int indexBytes
 * index:  per block  u16 keyLength, key, int packedLength, int rawLength
 * data:   the blocks, one after another (raw deflate)
 * </pre>
 *
 * In buildSrc so that the build (app/build.gradle.kts: PackAssetsTask) and a plain java command
 * (java hanja.pack.PackTsv OUT_DIR IN.tsv…) run the same code.
 */
public final class PackTsv {
    public static final int BLOCK = 16 * 1024;

    private PackTsv() {
    }

    public static void pack(File in, File out) throws IOException {
        byte[] data = Files.readAllBytes(in.toPath());
        List<byte[]> keys = new ArrayList<>();
        List<byte[]> blocks = new ArrayList<>();
        List<Integer> rawSizes = new ArrayList<>();
        int start = 0;
        int blockStart = 0;
        byte[] previous = null;
        byte[] firstKey = null;
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        while (start < data.length) {
            int end = start;
            while (end < data.length && data[end] != '\n') end++;
            int keyEnd = start;
            while (keyEnd < end && data[keyEnd] != '\t') keyEnd++;
            byte[] key = java.util.Arrays.copyOfRange(data, start, keyEnd);
            if (key.length == 0 || key.length > 0xFFFF) {
                throw new IOException(in.getName() + ": a line without a key at byte " + start);
            }
            if (previous != null && compare(previous, key) >= 0) {
                throw new IOException(in.getName() + ": keys are not in UTF-8 byte order at \""
                    + new String(key, java.nio.charset.StandardCharsets.UTF_8) + "\" (the app binary-searches them)");
            }
            previous = key;
            if (firstKey == null) firstKey = key;
            int next = Math.min(end + 1, data.length);
            if (next - blockStart >= BLOCK || next == data.length) {
                keys.add(firstKey);
                blocks.add(deflate(deflater, data, blockStart, next - blockStart));
                rawSizes.add(next - blockStart);
                blockStart = next;
                firstKey = null;
            }
            start = next;
        }
        deflater.end();
        ByteArrayOutputStream indexBytes = new ByteArrayOutputStream();
        DataOutputStream index = new DataOutputStream(indexBytes);
        for (int i = 0; i < keys.size(); i++) {
            index.writeShort(keys.get(i).length);
            index.write(keys.get(i));
            index.writeInt(blocks.get(i).length);
            index.writeInt(rawSizes.get(i));
        }
        index.flush();
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        try (DataOutputStream o = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(out.toPath())))) {
            o.write(new byte[] {'H', 'J', 'P', '1'});
            o.writeInt(keys.size());
            o.writeInt(indexBytes.size());
            indexBytes.writeTo(o);
            for (byte[] b : blocks) o.write(b);
        }
    }

    private static byte[] deflate(Deflater deflater, byte[] data, int from, int length) {
        deflater.reset();
        deflater.setInput(data, from, length);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(length / 2 + 64);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        return out.toByteArray();
    }

    /** As unsigned bytes; a key that another begins with comes first (what the app's search assumes). */
    private static int compare(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int d = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (d != 0) return d;
        }
        return a.length - b.length;
    }

    /** java hanja.pack.PackTsv OUT_DIR IN.tsv... */
    public static void main(String[] args) throws IOException {
        File dir = new File(args[0]);
        for (int i = 1; i < args.length; i++) {
            File in = new File(args[i]);
            File out = new File(dir, in.getName().replaceFirst("\\.tsv$", "") + ".pk");
            pack(in, out);
            System.out.println(in.getName() + " " + in.length() + " -> " + out.length());
        }
    }
}
