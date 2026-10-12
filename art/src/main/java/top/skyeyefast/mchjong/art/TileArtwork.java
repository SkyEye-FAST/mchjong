package top.skyeyefast.mchjong.art;

import java.util.List;

/** Source names and tile order for the bundled face presets. */
final class TileArtwork {
    static final List<String> PRESETS = List.of("kansai", "kanto", "sichuan", "hong_kong", "taiwan", "fujian");
    static final int WIDTH = 256, HEIGHT = 384;
    static final int FACE_COUNT = 45, BACK = 0;
    static final List<String> FACE_KEYS = List.of(
            "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
            "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p",
            "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s",
            "1z", "2z", "3z", "4z", "5z", "6z", "7z",
            "0m", "0p", "0s",
            "1q", "2q", "3q", "4q", "5q", "6q", "7q", "8q");

    private TileArtwork() {}

    static String tileKey(int face) {
        if (face < 0 || face >= FACE_COUNT)
            throw new IllegalArgumentException("Tile face: " + face);
        return FACE_KEYS.get(face);
    }
}
