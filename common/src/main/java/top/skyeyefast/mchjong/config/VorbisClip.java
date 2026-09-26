package top.skyeyefast.mchjong.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Checks Ogg Vorbis page bounds and the eight-second granule limit on the server. */
public final class VorbisClip {
    private VorbisClip() {}

    public static void validate(byte[] data) throws IOException {
        if (data == null || data.length < 58) throw new IOException("Empty Ogg Vorbis recording");
        var numbers = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 0, page = 0, rate = 0, serial = 0;
        boolean ended = false;
        while (offset < data.length) {
            if (data.length - offset < 27 || data[offset] != 'O' || data[offset + 1] != 'g'
                || data[offset + 2] != 'g' || data[offset + 3] != 'S' || data[offset + 4] != 0)
                throw new IOException("Invalid Ogg page");
            int flags = data[offset + 5] & 255;
            int stream = numbers.getInt(offset + 14);
            int sequence = numbers.getInt(offset + 18);
            int segments = data[offset + 26] & 255;
            if (data.length - offset < 27 + segments || sequence != page || page > 4096
                || page > 0 && stream != serial || ended)
                throw new IOException("Invalid Ogg page sequence");
            int payload = 0;
            for (int i = 0; i < segments; i++) payload += data[offset + 27 + i] & 255;
            int start = offset + 27 + segments;
            if (payload > data.length - start) throw new IOException("Truncated Ogg page");
            if (page == 0) {
                serial = stream;
                if ((flags & 2) == 0 || segments == 0 || (data[offset + 27] & 255) < 30
                    || payload < 30 || data[start] != 1
                    || data[start + 1] != 'v' || data[start + 2] != 'o' || data[start + 3] != 'r'
                    || data[start + 4] != 'b' || data[start + 5] != 'i' || data[start + 6] != 's'
                    || numbers.getInt(start + 7) != 0)
                    throw new IOException("Invalid Vorbis identification header");
                int channels = data[start + 11] & 255;
                rate = numbers.getInt(start + 12);
                if ((channels != 1 && channels != 2) || rate < 8000 || rate > 96000)
                    throw new IOException("Unsupported Vorbis format");
            }
            long granule = numbers.getLong(offset + 6);
            if (granule >= 0 && granule > rate * 8L) throw new IOException("Voice recording exceeds eight seconds");
            ended = (flags & 4) != 0;
            offset = start + payload;
            page++;
        }
        if (!ended || page < 2) throw new IOException("Incomplete Ogg Vorbis recording");
    }
}
