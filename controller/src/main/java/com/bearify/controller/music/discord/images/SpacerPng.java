package com.bearify.controller.music.discord.images;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public class SpacerPng {

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            writeChunk(out, "IHDR", new byte[]{
                    0x00, 0x00, 0x07, (byte) 0x80,  // width  = 1920
                    0x00, 0x00, 0x00, 0x01,           // height = 1
                    0x08, 0x06,                        // 8-bit RGBA
                    0x00, 0x00, 0x00                   // compression, filter, interlace = 0
            });
            byte[] scanline = new byte[1 + 1920 * 4]; // filter byte 0 + 1920 fully-transparent RGBA pixels
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (Deflater deflater = new Deflater()) {
                deflater.setInput(scanline);
                deflater.finish();
                byte[] buf = new byte[1024];
                while (!deflater.finished()) {
                    compressed.write(buf, 0, deflater.deflate(buf));
                }
            }
            writeChunk(out, "IDAT", compressed.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate spacer PNG", e);
        }
    }

    private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
        int len = data.length;
        out.write(new byte[]{(byte) (len >> 24), (byte) (len >> 16), (byte) (len >> 8), (byte) len});
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long v = crc.getValue();
        out.write(new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v});
    }
}
