package com.groundzero.audioclarity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.groundzero.audioclarity.AudioClarity.LOGGER;

/**
 * config/audio-clarity.json. Ships with the Emberwild tuning; every value can be edited in the
 * file, and is picked up about a second after the file is saved (the file is checked from the
 * client tick). EQ, compressor, category mix and sound adjustments change live; master_bus and
 * latency need the sound engine restarted (F3+T or a game restart).
 *
 * <p>The file is only written when it is missing or lacks keys (which are filled in with the
 * defaults). A file that doesn't parse is left alone and the last good settings stay in use.
 */
public final class ClarityConfig {

    /** How far ahead the sound card mixes. Lower = less delay, but needs a steadier PC. */
    public enum Latency {
        LOW(100), NORMAL(50), SAFE(25);

        /** Mixing updates per second (OpenAL's ALC_REFRESH); one update = 1000 / hz ms. */
        public final int refreshHz;

        Latency(int refreshHz) {
            this.refreshHz = refreshHz;
        }
    }

    public record Compressor(boolean enabled, float thresholdDb, float ratio, float attackMs, float releaseMs,
                             float kneeDb, float makeupDb, float outputDb, boolean limiter, Latency latency) {}

    /** One EQ band. gainDb is ignored by highpass / lowpass. */
    public record Band(String type, float freqHz, float gainDb, float q) {}

    public record Eq(boolean enabled, List<Band> bands) {}

    public static final Set<String> BAND_TYPES = Set.of("highpass", "lowpass", "lowshelf", "highshelf", "peak");

    /** Tuned by ear for Emberwild, 2026-09-29. */
    static final Compressor DEFAULT_COMPRESSOR = new Compressor(true, -12.887324f, 1.9642187f, 24.797567f, 164.18472f,
            6f, 7.352113f, -3.8450704f, true, Latency.LOW);

    /**
     * Against Minecraft's dark, muffled tone: clear sub rumble, take a little mud out of the
     * low mids, lift presence and add air on top.
     */
    static final Eq DEFAULT_EQ = new Eq(true, List.of(
            new Band("highpass", 30f, 0f, 0.707f),
            new Band("peak", 300f, -2f, 1.0f),
            new Band("peak", 3500f, 2f, 0.9f),
            new Band("highshelf", 9000f, 3f, 0.707f)));

    /** Level of each category at 100% on its slider (options.txt names). Music is left at 1. */
    static final Map<String, Float> DEFAULT_MIX = ordered(
            "record", 0.6197183f,
            "weather", 0.33422535f,
            "block", 0.2959513f,
            "hostile", 0.16901408f,
            "neutral", 0.27f,
            "player", 0.1602113f,
            "ambient", 0.32042253f,
            "voice", 0.3732394f);

    /** Per-sound adjustments in dB on top of the category mix. */
    static final Map<String, Float> DEFAULT_SOUNDS = ordered(
            "minecraft:block.grass.place", 6.0f,
            "minecraft:entity.enderman.ambient", 7.5f,
            "minecraft:entity.tnt.primed", 2.5f);

    public static final float MUTE_DB = -40f;
    private static final int MAX_BANDS = 10;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile boolean masterBus = true;
    private static volatile boolean musicSkipsCompressor = true;
    private static volatile boolean showMeter = true;
    private static volatile Compressor compressor = DEFAULT_COMPRESSOR;
    private static volatile Eq eq = DEFAULT_EQ;
    private static volatile Map<String, Float> mix = DEFAULT_MIX;
    private static volatile Map<String, Float> sounds = DEFAULT_SOUNDS;
    private static volatile boolean soundGroups = hasGroups(DEFAULT_SOUNDS);
    private static final Map<String, Float> gainCache = new ConcurrentHashMap<>();

    /** Set while parsing when a key is absent, so the file gets the default written in. */
    private static boolean missing;
    private static FileTime lastSeen;
    private static int pollTicks;

    static {
        load();
    }

    private ClarityConfig() {}

    /** false = audio takes Minecraft's normal path: no EQ, compressor, meter or music bypass. */
    public static boolean masterBus() {
        return masterBus;
    }

    public static boolean musicSkipsCompressor() {
        return musicSkipsCompressor;
    }

    public static boolean showMeter() {
        return showMeter;
    }

    public static Compressor compressor() {
        return compressor;
    }

    /** A new object whenever the settings change, so the EQ can tell when to recompute. */
    public static Eq eq() {
        return eq;
    }

    /** A category's level at 100% on its slider. */
    public static float mix(String category) {
        return mix.getOrDefault(category, 1f);
    }

    /**
     * Linear gain for a sound ID - 1.0 when untouched. Keys are sound IDs, or a group ending in
     * ".*" for every sound whose ID starts with it; a sound gets its own adjustment plus every
     * group it belongs to. Called for every sound that plays, so results are cached.
     */
    public static float soundGain(String id) {
        Map<String, Float> rules = sounds;
        if (rules.isEmpty()) {
            return 1f;
        }
        Float cached = gainCache.get(id);
        if (cached != null) {
            return cached;
        }
        float db = rules.getOrDefault(id, 0f);
        if (soundGroups) {
            for (Map.Entry<String, Float> e : rules.entrySet()) {
                String k = e.getKey();
                if (k.endsWith("*") && id.startsWith(k.substring(0, k.length() - 1))) {
                    db += e.getValue();
                }
            }
        }
        float g = db <= MUTE_DB ? 0f : (float) Math.pow(10.0, db / 20.0);
        gainCache.put(id, g);
        return g;
    }

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("audio-clarity.json");
    }

    private static boolean engineStarted;

    /** The sound engine is (re)starting: re-read the file, except on the very first start (already read). */
    public static void onSoundEngineStart() {
        if (engineStarted) {
            load();
        }
        engineStarted = true;
    }

    /** Client tick: once a second, re-read the file if it was saved since. */
    public static void poll() {
        if (++pollTicks < 20) {
            return;
        }
        pollTicks = 0;
        try {
            Path f = file();
            FileTime t = Files.exists(f) ? Files.getLastModifiedTime(f) : null;
            if (t != null && !t.equals(lastSeen)) {
                load();
            }
        } catch (Exception e) {
            LOGGER.debug("Could not check the config file: {}", e.toString());
        }
    }

    /** Reads the file (creating it with the defaults if missing). */
    public static synchronized void load() {
        Path f = file();
        JsonObject o = new JsonObject();
        boolean exists = Files.exists(f);
        try {
            if (exists) {
                lastSeen = Files.getLastModifiedTime(f);
                o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("config/audio-clarity.json has a mistake in it, keeping the previous settings: {}", e.toString());
            return;
        }
        missing = !exists;

        masterBus = bool(o, "master_bus", true);
        musicSkipsCompressor = bool(o, "music_skips_compressor", true);
        showMeter = bool(o, "show_meter", true);

        JsonObject c = section(o, "compressor");
        Compressor d = DEFAULT_COMPRESSOR;
        Latency latency;
        try {
            latency = Latency.valueOf(str(c, "latency", d.latency().name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            latency = d.latency();
        }
        compressor = new Compressor(
                bool(c, "enabled", d.enabled()),
                clamp(num(c, "threshold_db", d.thresholdDb()), -60f, 0f),
                clamp(num(c, "ratio", d.ratio()), 1f, 30f),
                clamp(num(c, "attack_ms", d.attackMs()), 0.1f, 500f),
                clamp(num(c, "release_ms", d.releaseMs()), 5f, 3000f),
                clamp(num(c, "knee_db", d.kneeDb()), 0f, 24f),
                clamp(num(c, "makeup_db", d.makeupDb()), -24f, 24f),
                clamp(num(c, "output_db", d.outputDb()), -24f, 12f),
                bool(c, "limiter", d.limiter()),
                latency);

        eq = readEq(section(o, "eq"));

        mix = Collections.unmodifiableMap(floatMap(o, "category_mix", DEFAULT_MIX));
        Map<String, Float> s = Collections.unmodifiableMap(floatMap(o, "sound_adjustments_db", DEFAULT_SOUNDS));
        soundGroups = hasGroups(s);
        sounds = s;
        gainCache.clear();

        if (missing) {
            save();
        }
        LOGGER.info("Loaded: compressor {}, EQ {} ({} bands), {} category levels, {} sound adjustments",
                compressor.enabled() && masterBus ? "on" : "off", eq.enabled() && masterBus ? "on" : "off",
                eq.bands().size(), mix.size(), sounds.size());
    }

    private static Eq readEq(JsonObject e) {
        boolean enabled = bool(e, "enabled", DEFAULT_EQ.enabled());
        if (!e.has("bands") || !e.get("bands").isJsonArray()) {
            missing = true;
            return new Eq(enabled, DEFAULT_EQ.bands());
        }
        List<Band> bands = new ArrayList<>();
        for (JsonElement el : e.getAsJsonArray("bands")) {
            if (!el.isJsonObject() || bands.size() >= MAX_BANDS) {
                continue;
            }
            JsonObject b = el.getAsJsonObject();
            String type = str(b, "type", "peak").toLowerCase(Locale.ROOT);
            if (!BAND_TYPES.contains(type)) {
                LOGGER.warn("Ignoring EQ band of unknown type \"{}\"", type);
                continue;
            }
            boolean pass = type.endsWith("pass");
            bands.add(new Band(type,
                    clamp(num(b, "freq_hz", 1000f), 10f, 22000f),
                    pass ? 0f : clamp(num(b, "gain_db", 0f), -24f, 24f),
                    clamp(num(b, "q", 0.707f), 0.1f, 18f)));
        }
        return new Eq(enabled, List.copyOf(bands));
    }

    private static void save() {
        Compressor c = compressor;
        JsonObject o = new JsonObject();
        o.addProperty("_help", "Audio Clarity settings. Saved changes apply within a second "
                + "(master_bus and latency: press F3+T). master_bus=false turns the whole chain off - "
                + "use it if the game has no sound or crackles.");
        o.addProperty("master_bus", masterBus);
        o.addProperty("music_skips_compressor", musicSkipsCompressor);
        o.addProperty("show_meter", showMeter);

        JsonObject q = new JsonObject();
        q.addProperty("_help", "Global EQ on everything except music, after the compressor and before the limiter. Band types: "
                + "highpass / lowpass (freq_hz, q), lowshelf / highshelf / peak (freq_hz, gain_db, q). "
                + "q: 0.707 = gentle/standard, higher = narrower peak. Up to " + MAX_BANDS + " bands.");
        q.addProperty("enabled", eq.enabled());
        JsonArray arr = new JsonArray();
        for (Band b : eq.bands()) {
            JsonObject j = new JsonObject();
            j.addProperty("type", b.type());
            j.addProperty("freq_hz", b.freqHz());
            if (!b.type().endsWith("pass")) {
                j.addProperty("gain_db", b.gainDb());
            }
            j.addProperty("q", b.q());
            arr.add(j);
        }
        q.add("bands", arr);
        o.add("eq", q);

        JsonObject j = new JsonObject();
        j.addProperty("_help", "enabled=false keeps the chain but passes audio through uncompressed. "
                + "latency: LOW (10 ms), NORMAL (20 ms) or SAFE (40 ms) - raise it if you hear crackling.");
        j.addProperty("enabled", c.enabled());
        j.addProperty("threshold_db", c.thresholdDb());
        j.addProperty("ratio", c.ratio());
        j.addProperty("attack_ms", c.attackMs());
        j.addProperty("release_ms", c.releaseMs());
        j.addProperty("knee_db", c.kneeDb());
        j.addProperty("makeup_db", c.makeupDb());
        j.addProperty("output_db", c.outputDb());
        j.addProperty("limiter", c.limiter());
        j.addProperty("latency", c.latency().name());
        o.add("compressor", j);

        JsonObject m = new JsonObject();
        m.addProperty("_help", "Level of each sound category when its slider is at 100% (1.0 = vanilla). "
                + "Names: record, weather, block, hostile, neutral, player, ambient, voice, ui, music.");
        mix.forEach(m::addProperty);
        o.add("category_mix", m);

        JsonObject s = new JsonObject();
        s.addProperty("_help", "Extra volume in dB for single sounds (\"minecraft:entity.zombie.hurt\") or groups "
                + "ending in .* (\"minecraft:entity.zombie.*\"). -40 mutes. /playsound suggests the IDs.");
        sounds.forEach(s::addProperty);
        o.add("sound_adjustments_db", s);

        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
            lastSeen = Files.getLastModifiedTime(f);
        } catch (Exception e) {
            LOGGER.warn("Could not write config/audio-clarity.json: {}", e.toString());
        }
    }

    // ---- helpers

    private static Map<String, Float> ordered(Object... kv) {
        Map<String, Float> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Float) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }

    private static boolean hasGroups(Map<String, Float> rules) {
        return rules.keySet().stream().anyMatch(k -> k.endsWith("*"));
    }

    private static JsonObject section(JsonObject o, String key) {
        if (o.has(key) && o.get(key).isJsonObject()) {
            return o.getAsJsonObject(key);
        }
        missing = true;
        return new JsonObject();
    }

    private static Map<String, Float> floatMap(JsonObject o, String key, Map<String, Float> def) {
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            missing = true;
            return new LinkedHashMap<>(def);
        }
        Map<String, Float> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            if (e.getKey().startsWith("_")) {
                continue;
            }
            try {
                m.put(e.getKey(), e.getValue().getAsFloat());
            } catch (Exception ex) {
                LOGGER.warn("Ignoring {} \"{}\": not a number", key, e.getKey());
            }
        }
        return m;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        if (!o.has(key)) {
            missing = true;
            return def;
        }
        try {
            return o.get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    private static float num(JsonObject o, String key, float def) {
        if (!o.has(key)) {
            missing = true;
            return def;
        }
        try {
            return o.get(key).getAsFloat();
        } catch (Exception e) {
            return def;
        }
    }

    private static String str(JsonObject o, String key, String def) {
        if (!o.has(key)) {
            missing = true;
            return def;
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static float clamp(float v, float min, float max) {
        return Float.isNaN(v) ? min : Math.max(min, Math.min(max, v));
    }
}
