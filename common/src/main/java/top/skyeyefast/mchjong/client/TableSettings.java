package top.skyeyefast.mchjong.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Local presentation preferences. Never sent to the server or stored in a table save. */
public final class TableSettings {
    public enum Information {
        RULES, ROUND, REMAINING, DEPOSITS, TURN, DORA, FOCUS,
        NAMES, WINDS, POINTS, RANKS, STATUS, COUNTS, MELDS, RESULTS, HELP;

        public String key() { return "settings.mchjong.info." + name().toLowerCase(Locale.ROOT); }
    }
    public enum DiscardMode { SINGLE_CLICK, DOUBLE_CLICK, CONFIRM }
    public enum TileLabels { NAME, MPSZ }
    public enum GuideLines { ALWAYS, HOVER, OFF }
    public enum VoiceSource { RESOURCE_PACK, OFF }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static TableSettings current;
    private EnumSet<Information> hiddenInformation = EnumSet.noneOf(Information.class);
    public DiscardMode discardMode = DiscardMode.SINGLE_CLICK;
    public TileLabels tileLabels = TileLabels.NAME;
    public GuideLines guideLines = GuideLines.HOVER;
    public boolean actionTiles = true;
    public boolean highlightTiles = true;
    public boolean convenienceHints = false;
    public boolean animations = true;
    public boolean showRiver = true;
    public VoiceSource voiceSource = VoiceSource.RESOURCE_PACK;
    public double effectsVolume = 0.7;
    public double voiceVolume = 0.8;
    public boolean countdownSounds = true;
    public static final double MIN_CAMERA_DISTANCE = 1.6;
    public static final double MAX_CAMERA_DISTANCE = 2.8;
    public static final double MIN_CAMERA_HEIGHT = 1.35;
    public static final double MAX_CAMERA_HEIGHT = 2.5;
    public static final double CAMERA_TARGET_Z = .20;
    public double cameraDistance = TableGeometry.STOOL_DISTANCE;
    public double cameraHeight = 2.20;
    private transient SeatedCameraState camera;

    public SeatedCameraState camera() {
        if (camera == null) camera = new SeatedCameraState(cameraDistance, cameraHeight);
        return camera;
    }

    public Vec3 cameraPosition(SeatEntity seat) {
        return TableGeometry.world(seat.tablePos(), SeatedCamera.state(seat).eye(seat.seat()));
    }

    public float cameraPitch() {
        return (float) Math.toDegrees(Math.atan2(cameraHeight - TableGeometry.FELT_Y,
            cameraDistance - CAMERA_TARGET_Z));
    }

    /** Fit the near playing-surface corners at the reset angle, independently of free looking. */
    public double cameraFov(double requested, double aspectRatio) {
        double pitch = Math.toRadians(cameraPitch());
        double tileTop = TableGeometry.FELT_Y + (0.081 + TileMesh.HEIGHT / 2.0) * TableScene.TILE_SCALE;
        double nearestDepth = (cameraDistance - TableGeometry.FELT_HALF_WIDTH) * Math.cos(pitch)
            + (cameraHeight - tileTop) * Math.sin(pitch);
        // Leave 2.5% of the viewport on each side of the complete tile envelopes.
        double required = Math.toDegrees(2 * Math.atan(TableGeometry.FELT_HALF_WIDTH
            / (nearestDepth * aspectRatio * .95)));
        return Math.max(requested, required);
    }

    public static TableSettings get() {
        if (current == null) {
            try { current = load(configPath()); }
            catch (IOException | RuntimeException failure) {
                LoggerFactory.getLogger("mchjong").warn("Cannot read table settings; using defaults without replacing the file", failure);
                current = new TableSettings();
            }
        }
        return current;
    }

    public static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/mchjong-client.json");
    }

    public static TableSettings load(Path path) throws IOException {
        if (!Files.exists(path)) return new TableSettings();
        try (var reader = Files.newBufferedReader(path)) {
            TableSettings settings = Objects.requireNonNull(JSON.fromJson(reader, TableSettings.class), "Empty settings");
            Objects.requireNonNull(settings.hiddenInformation, "Missing information flags");
            Objects.requireNonNull(settings.discardMode, "Unknown discard mode");
            Objects.requireNonNull(settings.tileLabels, "Unknown tile label format");
            Objects.requireNonNull(settings.guideLines, "Unknown guide-line mode");
            Objects.requireNonNull(settings.voiceSource, "Unknown voice source");
            if (!Double.isFinite(settings.effectsVolume) || !Double.isFinite(settings.voiceVolume))
                throw new IllegalArgumentException("Sound volumes must be finite");
            settings.effectsVolume = Math.clamp(settings.effectsVolume, 0, 1);
            settings.voiceVolume = Math.clamp(settings.voiceVolume, 0, 1);
            if (!Double.isFinite(settings.cameraDistance) || !Double.isFinite(settings.cameraHeight))
                throw new IllegalArgumentException("Camera settings must be finite");
            settings.cameraDistance = Math.clamp(settings.cameraDistance, MIN_CAMERA_DISTANCE, MAX_CAMERA_DISTANCE);
            settings.cameraHeight = Math.clamp(settings.cameraHeight, MIN_CAMERA_HEIGHT, MAX_CAMERA_HEIGHT);
            return settings;
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "mchjong-client-", ".json.tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { JSON.toJson(this, writer); }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    public boolean show(Information information) {
        return information == Information.REMAINING && !showRiver || !hiddenInformation.contains(information);
    }
    public void toggle(Information information) {
        if (!hiddenInformation.remove(information)) hiddenInformation.add(information);
    }
    public void reset() {
        TableSettings defaults = new TableSettings();
        hiddenInformation.clear();
        discardMode = defaults.discardMode;
        tileLabels = defaults.tileLabels;
        guideLines = defaults.guideLines;
        actionTiles = defaults.actionTiles;
        highlightTiles = defaults.highlightTiles;
        convenienceHints = defaults.convenienceHints;
        animations = defaults.animations;
        showRiver = defaults.showRiver;
        voiceSource = defaults.voiceSource;
        effectsVolume = defaults.effectsVolume;
        voiceVolume = defaults.voiceVolume;
        countdownSounds = defaults.countdownSounds;
        cameraDistance = defaults.cameraDistance;
        cameraHeight = defaults.cameraHeight;
        camera().reset(cameraDistance, cameraHeight);
    }
}
