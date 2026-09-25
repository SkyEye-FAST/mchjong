package top.skyeyefast.mchjong.client;

import com.electronwill.nightconfig.core.Config;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.config.TomlFiles;
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

    private static TableSettings current;
    private EnumSet<Information> hiddenInformation = EnumSet.noneOf(Information.class);
    public DiscardMode discardMode = DiscardMode.SINGLE_CLICK;
    public TileLabels tileLabels = TileLabels.NAME;
    public GuideLines guideLines = GuideLines.HOVER;
    public boolean actionTiles = true;
    public boolean highlightTiles = true;
    public boolean convenienceHints = false;
    public boolean autoSeat = true;
    public boolean animations = true;
    public boolean showRiver = true;
    public VoiceSource voiceSource = VoiceSource.RESOURCE_PACK;
    public ResourceLocation riichiStickPreset = RiichiStickPresets.DEFAULT;
    public double effectsVolume = 0.7;
    public double voiceVolume = 0.8;
    public boolean countdownSounds = true;
    public static final double MIN_CAMERA_DISTANCE = 1.6;
    public static final double MAX_CAMERA_DISTANCE = 2.8;
    public static final double MIN_CAMERA_HEIGHT = 1.35;
    public static final double MAX_CAMERA_HEIGHT = 2.5;
    public static final double CAMERA_TARGET_Z = .20;
    public double cameraDistance = TableGeometry.STOOL_DISTANCE;
    public double cameraHeight = 2.10;
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
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/mchjong-client.toml");
    }

    public static TableSettings load(Path path) throws IOException {
        if (!java.nio.file.Files.exists(path)) return new TableSettings();
        var config = TomlFiles.read(path);
        var settings = new TableSettings();
        Object hidden = config.get("hiddenInformation");
        if (hidden != null) {
            if (!(hidden instanceof java.util.List<?> names)) throw new IllegalArgumentException("Invalid hiddenInformation");
            for (Object name : names) settings.hiddenInformation.add(enumValue(name, Information.class));
        }
        settings.discardMode = enumValue(config.getOrElse("discardMode", settings.discardMode.name()), DiscardMode.class);
        settings.tileLabels = enumValue(config.getOrElse("tileLabels", settings.tileLabels.name()), TileLabels.class);
        settings.guideLines = enumValue(config.getOrElse("guideLines", settings.guideLines.name()), GuideLines.class);
        settings.voiceSource = enumValue(config.getOrElse("voiceSource", settings.voiceSource.name()), VoiceSource.class);
        settings.riichiStickPreset = ResourceLocation.parse(config.getOrElse("riichiStickPreset", settings.riichiStickPreset.toString()));
        settings.actionTiles = bool(config.getOrElse("actionTiles", settings.actionTiles));
        settings.highlightTiles = bool(config.getOrElse("highlightTiles", settings.highlightTiles));
        settings.convenienceHints = bool(config.getOrElse("convenienceHints", settings.convenienceHints));
        settings.autoSeat = bool(config.getOrElse("autoSeat", settings.autoSeat));
        settings.animations = bool(config.getOrElse("animations", settings.animations));
        settings.showRiver = bool(config.getOrElse("showRiver", settings.showRiver));
        settings.countdownSounds = bool(config.getOrElse("countdownSounds", settings.countdownSounds));
        settings.effectsVolume = Math.clamp(number(config.getOrElse("effectsVolume", settings.effectsVolume)), 0, 1);
        settings.voiceVolume = Math.clamp(number(config.getOrElse("voiceVolume", settings.voiceVolume)), 0, 1);
        settings.cameraDistance = Math.clamp(number(config.getOrElse("cameraDistance", settings.cameraDistance)), MIN_CAMERA_DISTANCE, MAX_CAMERA_DISTANCE);
        settings.cameraHeight = Math.clamp(number(config.getOrElse("cameraHeight", settings.cameraHeight)), MIN_CAMERA_HEIGHT, MAX_CAMERA_HEIGHT);
        return settings;
    }

    public void save(Path path) throws IOException {
        Config config = Config.inMemory();
        config.set("hiddenInformation", hiddenInformation.stream().map(Enum::name).toList());
        config.set("discardMode", discardMode.name());
        config.set("tileLabels", tileLabels.name());
        config.set("guideLines", guideLines.name());
        config.set("voiceSource", voiceSource.name());
        config.set("riichiStickPreset", riichiStickPreset.toString());
        config.set("actionTiles", actionTiles);
        config.set("highlightTiles", highlightTiles);
        config.set("convenienceHints", convenienceHints);
        config.set("autoSeat", autoSeat);
        config.set("animations", animations);
        config.set("showRiver", showRiver);
        config.set("countdownSounds", countdownSounds);
        config.set("effectsVolume", effectsVolume);
        config.set("voiceVolume", voiceVolume);
        config.set("cameraDistance", cameraDistance);
        config.set("cameraHeight", cameraHeight);
        TomlFiles.write(path, config);
    }

    private static <E extends Enum<E>> E enumValue(Object value, Class<E> type) {
        if (!(value instanceof String name)) throw new IllegalArgumentException("Invalid " + type.getSimpleName());
        return Enum.valueOf(type, name);
    }
    private static boolean bool(Object value) {
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException("Expected boolean");
        return result;
    }
    private static double number(Object value) {
        if (!(value instanceof Number result) || !Double.isFinite(result.doubleValue()))
            throw new IllegalArgumentException("Expected finite number");
        return result.doubleValue();
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
        autoSeat = defaults.autoSeat;
        animations = defaults.animations;
        showRiver = defaults.showRiver;
        voiceSource = defaults.voiceSource;
        riichiStickPreset = defaults.riichiStickPreset;
        effectsVolume = defaults.effectsVolume;
        voiceVolume = defaults.voiceVolume;
        countdownSounds = defaults.countdownSounds;
        cameraDistance = defaults.cameraDistance;
        cameraHeight = defaults.cameraHeight;
        camera().reset(cameraDistance, cameraHeight);
    }
}
