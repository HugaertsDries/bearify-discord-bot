package com.bearify.controller.music.discord.images;

import java.io.IOException;
import java.io.InputStream;

public class DiskGIF {

    private static final int GOLDEN_DISK_FREQUENCY = 100;
    private static final String DISK_LOCATION = "/images/disk.gif";
    private static final String GOLDEN_DISK_LOCATION = "/images/golden-disk.gif";

    private final String location;

    public DiskGIF() {
        if ((int) (Math.random() * GOLDEN_DISK_FREQUENCY) == 0) {
            location = GOLDEN_DISK_LOCATION;
        } else {
            location = DISK_LOCATION;
        }
    }

    public byte[] toBytes() {
        try (InputStream stream = DiskGIF.class.getResourceAsStream(location)) {
            if (stream == null) throw new IllegalStateException("Disk GIF not found at " + location);
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read disk GIF", e);
        }
    }
}
