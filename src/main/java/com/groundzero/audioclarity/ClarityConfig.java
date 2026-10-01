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
 * config/better-audio-clarity.json. Ships with the Emberwild tuning; every value can be edited in the
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

        public Latency next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public record Compressor(boolean enabled, float thresholdDb, float ratio, float attackMs, float releaseMs,
                             float kneeDb, float makeupDb, float outputDb, boolean limiter, Latency latency,
                             float lookaheadMs) {}

    /** One EQ band. gainDb is ignored by highpass / lowpass. */
    public record Band(String type, float freqHz, float gainDb, float q) {}

    public record Eq(boolean enabled, List<Band> bands) {}

    public static final Set<String> BAND_TYPES = Set.of("highpass", "lowpass", "lowshelf", "highshelf", "peak");

    /**
     * Tuned by ear by Vymnkoo, 2026-09-30 (1.2.0): a high threshold, so the compressor only
     * catches the loud moments and everyday sound keeps its dynamics.
     */
    public static final Compressor DEFAULT_COMPRESSOR = new Compressor(true, -15.669014f, 2.0925527f, 49.729725f, 247.50362f,
            12.0f, 3.0422535f, 5.5f, true, Latency.LOW, 10f);

    /**
     * Against Minecraft's dark, muffled tone: clear sub rumble, take a little mud out of the
     * low mids, lift presence and add air on top.
     */
    static final Eq DEFAULT_EQ = new Eq(true, List.of(
            new Band("highpass", 30f, 0f, 0.707f),
            new Band("peak", 300f, -2f, 1.0f),
            new Band("peak", 3500f, 2f, 0.9f),
            new Band("highshelf", 9000f, 3f, 0.707f)));

    /**
     * Level of each category at 100% on its slider (options.txt names). Raised 2.5 dB together
     * with the compressor threshold on 2026-10-01, so the louder level the tuning was heard at
     * sits at Output +5.5 dB (Master Volume 0.0 dB) and the compressor works exactly as tuned.
     */
    static final Map<String, Float> DEFAULT_MIX = ordered(
            "record", 0.5092301f,
            "weather", 0.3119877f,
            "block", 0.3626957f,
            "hostile", 0.2253839f,
            "neutral", 0.3600508f,
            "player", 0.1512067f,
            "ambient", 0.4272903f,
            "voice", 0.4977227f,
            "ui", 0.6745154f,
            "music", 0.186693f,     // soft background music; it skips the compressor, so it stays at this level
            "master", 0.3600508f);  // sounds servers play in the Master category (plugins): with the game, not 11 dB above it

    /** Per-sound adjustments in dB on top of the category mix. */
    static final Map<String, Float> DEFAULT_SOUNDS = ordered(
            "minecraft:block.grass.place", 6.0f,
            "minecraft:entity.enderman.ambient", 7.5f,
            "minecraft:entity.tnt.primed", 2.5f,
            "minecraft:entity.firework_rocket.*", -8.0f,
            "player|*.step", 10.0f,
            "minecraft:entity.wither.*", -6.0f,    // the Wither only - wither skeletons are "wither_skeleton"
            "master|minecraft:ui.*", -2.5f,           // UI sounds servers play in Master (Hypixel countdown): -14 dB with the master level
            "minecraft:block.note_block.*", -6.0f,
            "minecraft:entity.splash_potion.break", -8.0f,    // splash and lingering potions shattering
            // Measured against similar sounds (every block "break", every "hit"...): these stood
            // 9-15 dB above the rest by accident. Cut by about two thirds of that, keeping their character.
            "minecraft:block.anvil.place", -10.0f,
            "minecraft:block.glass.break", -8.0f,
            "minecraft:entity.fishing_bobber.splash", -8.0f,
            "minecraft:item.firecharge.use", -8.0f,
            "minecraft:block.ladder.step", -3.0f,
            "minecraft:block.ladder.hit", -3.0f,
            "minecraft:block.ladder.fall", -3.0f,
            "minecraft:block.shulker_box.*", -7.0f,
            "minecraft:block.tripwire.click_*", -6.0f,
            "minecraft:block.nether_sprouts.hit", -5.0f,
            "minecraft:block.nether_sprouts.fall", -5.0f,
            "minecraft:block.suspicious_gravel.*", -5.0f,
            "minecraft:entity.zombified_piglin.ambient", -4.0f,    // the Nether's constant grunting
            // Mobs, measured the same way with each mob's own code volume applied (cows, wolves...
            // are already turned down in the game). Big and boss mobs keep their weight on purpose.
            "minecraft:entity.cow.step", -9.0f,
            "minecraft:entity.cow_moody.step", -9.0f,
            "minecraft:entity.hoglin.step", -7.0f,
            "minecraft:entity.donkey.death", -7.0f,
            "minecraft:entity.mule.death", -7.0f,
            "minecraft:entity.horse.hurt", -6.0f,
            "minecraft:entity.horse.death", -6.0f,
            "minecraft:entity.horse.eat", -6.0f,
            "minecraft:entity.donkey.eat", -6.0f,
            "minecraft:entity.mule.eat", -6.0f,
            "minecraft:entity.polar_bear.ambient", -6.0f,
            "minecraft:entity.polar_bear.hurt", -6.0f,
            "minecraft:entity.polar_bear.step", -6.0f,
            "minecraft:entity.blaze.ambient", -5.0f,
            "minecraft:entity.ghast.hurt", -5.0f,
            "minecraft:entity.guardian.ambient", -5.0f,
            "minecraft:entity.shulker.ambient", -5.0f,
            "minecraft:entity.wolf_big.pant", -5.0f,
            "minecraft:entity.wolf_grumpy.pant", -5.0f,
            "minecraft:entity.zoglin.step", -5.0f,
            "minecraft:entity.donkey.angry", -5.0f,
            "minecraft:entity.mule.angry", -5.0f,
            "minecraft:entity.pig.step", -4.0f);

    public static final float MUTE_DB = -40f;
    private static final int MAX_BANDS = 10;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile boolean masterBus = true;
    private static volatile boolean musicSkipsCompressor = true;
    private static volatile boolean uiSkipsCompressor = true;
    /** Single sounds that skip the chain whatever their category (same patterns as sound_adjustments_db). */
    static final List<String> DEFAULT_SKIP_SOUNDS = List.of(
            "*_button.click_on", "*_button.click_off",   // menu "ticks" (mcpvp: cherry button in Blocks)
            "minecraft:ui.*",                           // UI sounds servers play in other categories (Hypixel countdown: Master)
            "minecraft:block.note_block.*");            // note block ticks (Hypixel countdown hat / start pling)
    private static volatile List<String> skipSounds = DEFAULT_SKIP_SOUNDS;
    private static volatile boolean logSounds;
    private static volatile boolean showMeter = true;
    /** Music level while in a world or on a server, relative to the menus (dB, <= 0). */
    static final float DEFAULT_IN_GAME_MUSIC_DB = -6f;
    private static volatile float inGameMusicDb = DEFAULT_IN_GAME_MUSIC_DB;
    private static volatile boolean sliderResetDone;
    /** True once the Master slider's level has been moved into Output (Music & Sound shows Output instead). */
    private static volatile boolean masterMovedToOutput;
    /** The bottom of the Output slider in Music & Sound: effectively silent. */
    public static final float OUTPUT_OFF_DB = -60f;
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

    /** False until the one-time reset of the category sliders has run (see MinecraftTickMixin). */
    public static boolean sliderResetDone() {
        return sliderResetDone;
    }

    public static synchronized void markSliderResetDone() {
        sliderResetDone = true;
        save();
    }

    public static boolean masterMovedToOutput() {
        return masterMovedToOutput;
    }

    public static synchronized void markMasterMovedToOutput() {
        masterMovedToOutput = true;
        save();
    }

    /**
     * Music and UI sounds can go straight to the sound card, around the whole chain: music so a
     * loud moment never pumps it down, UI (menu clicks) so it never makes the compressor pump the
     * game.
     */
    public static boolean skipsChain(net.minecraft.sounds.SoundSource source) {
        return switch (source) {
            case MUSIC -> musicSkipsCompressor;
            case UI -> uiSkipsCompressor;
            default -> false;
        };
    }

    /**
     * Single sounds that skip the chain although their category doesn't - e.g. servers that use a
     * button click as a menu "tick" (mcpvp plays block.cherry_wood_button.click_on in Blocks),
     * which would otherwise make the compressor pump.
     */
    public static boolean skipsChainAsSound(String id, String category) {
        for (String rule : skipSounds) {
            int bar = rule.indexOf('|');
            if (bar >= 0) {
                if (!rule.substring(0, bar).equals(category)) {
                    continue;
                }
                rule = rule.substring(bar + 1);
            }
            if (matches(rule, id)) {
                return true;
            }
        }
        return false;
    }

    /** Dev aid: log every sound that plays (ID, category, whether it skipped the chain). */
    public static boolean logSounds() {
        return logSounds;
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

    public static boolean musicSkipsCompressorSetting() {
        return musicSkipsCompressor;
    }

    public static boolean uiSkipsCompressorSetting() {
        return uiSkipsCompressor;
    }

    // ---- changes from the settings screen (Mod Menu): heard at once, written by saveNow()

    public static synchronized void setOutputDb(float db) {
        Compressor c = compressor;
        compressor = new Compressor(c.enabled(), c.thresholdDb(), c.ratio(), c.attackMs(), c.releaseMs(), c.kneeDb(),
                c.makeupDb(), clamp(db, OUTPUT_OFF_DB, 12f), c.limiter(), c.latency(), c.lookaheadMs());
    }

    public static synchronized void setCompressorEnabled(boolean on) {
        Compressor c = compressor;
        compressor = new Compressor(on, c.thresholdDb(), c.ratio(), c.attackMs(), c.releaseMs(), c.kneeDb(),
                c.makeupDb(), c.outputDb(), c.limiter(), c.latency(), c.lookaheadMs());
    }

    public static synchronized void setEqEnabled(boolean on) {
        eq = new Eq(on, eq.bands());
    }

    public static void setMusicSkipsCompressor(boolean on) {
        musicSkipsCompressor = on;
    }

    public static void setUiSkipsCompressor(boolean on) {
        uiSkipsCompressor = on;
    }

    public static void setShowMeter(boolean on) {
        showMeter = on;
    }

    /** How much quieter music plays once in a world or on a server (dB, 0 = same as the menus). */
    public static float inGameMusicDb() {
        return inGameMusicDb;
    }

    public static void setInGameMusicDb(float db) {
        inGameMusicDb = clamp(db, -40f, 0f);
    }

    /** Every compressor setting at once (the Compressor tab). */
    public static void setCompressor(Compressor c) {
        compressor = c;
    }

    /** A category's level at 100% on its slider (the Gains tab). */
    public static synchronized void setMix(String category, float level) {
        Map<String, Float> m = new LinkedHashMap<>(mix);
        m.put(category, clamp(level, 0f, 1f));
        mix = Collections.unmodifiableMap(m);
    }

    public static synchronized void resetMix() {
        mix = DEFAULT_MIX;
    }

    /** The whole category mix, in file order. */
    public static Map<String, Float> mixLevels() {
        return mix;
    }

    /** The per-sound adjustments in dB, in file order. */
    public static Map<String, Float> soundAdjustments() {
        return sounds;
    }

    /** Writes the current settings to the file. */
    public static synchronized void saveNow() {
        save();
    }

    /** A category's level at 100% on its slider. */
    public static float mix(String category) {
        return mix.getOrDefault(category, 1f);
    }

    /**
     * Linear gain for a sound - 1.0 when untouched. Keys are:
     * <ul>
     *   <li>a sound ID: {@code "minecraft:entity.zombie.hurt"}</li>
     *   <li>a group by start: {@code "minecraft:entity.zombie.*"}</li>
     *   <li>a group by end: {@code "*.step"} (every footstep sound)</li>
     *   <li>any of those limited to one category: {@code "player|*.step"} (only the player's
     *       footsteps - mobs walking on the same blocks use the same sounds)</li>
     * </ul>
     * A sound gets the sum of every rule that matches it. Called for every sound that plays, so
     * results are cached per ID and category.
     */
    public static float soundGain(String id, String category) {
        Map<String, Float> rules = sounds;
        if (rules.isEmpty()) {
            return 1f;
        }
        String cacheKey = category + '|' + id;
        Float cached = gainCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        float db = rules.getOrDefault(id, 0f);
        if (soundGroups) {
            for (Map.Entry<String, Float> e : rules.entrySet()) {
                String k = e.getKey();
                int bar = k.indexOf('|');
                if (bar >= 0) {
                    if (!k.substring(0, bar).equals(category)) {
                        continue;
                    }
                    k = k.substring(bar + 1);
                } else if (k.equals(id)) {
                    continue; // already counted above
                }
                if (matches(k, id)) {
                    db += e.getValue();
                }
            }
        }
        float g = db <= MUTE_DB ? 0f : (float) Math.pow(10.0, db / 20.0);
        gainCache.put(cacheKey, g);
        return g;
    }

    private static List<String> stringList(JsonObject o, String key, List<String> def) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            missing = true;
            return def;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(key)) {
            try {
                out.add(e.getAsString());
            } catch (Exception ex) {
                LOGGER.warn("Ignoring a non-text entry in {}", key);
            }
        }
        return List.copyOf(out);
    }

    private static boolean matches(String pattern, String id) {
        if (pattern.endsWith("*")) {
            return id.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return id.endsWith(pattern.substring(1));
        }
        return pattern.equals(id);
    }

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("better-audio-clarity.json");
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
        // Pre-release builds were called "Audio Clarity": keep that config.
        Path old = f.resolveSibling("audio-clarity.json");
        if (!Files.exists(f) && Files.exists(old)) {
            try {
                Files.move(old, f);
                LOGGER.info("Moved config/audio-clarity.json to config/better-audio-clarity.json");
            } catch (Exception e) {
                LOGGER.warn("Could not move the old config/audio-clarity.json: {}", e.toString());
            }
        }
        JsonObject o = new JsonObject();
        boolean exists = Files.exists(f);
        try {
            if (exists) {
                lastSeen = Files.getLastModifiedTime(f);
                o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("config/better-audio-clarity.json has a mistake in it, keeping the previous settings: {}", e.toString());
            return;
        }
        missing = !exists;

        masterBus = bool(o, "master_bus", true);
        musicSkipsCompressor = bool(o, "music_skips_compressor", true);
        uiSkipsCompressor = bool(o, "ui_skips_compressor", true);
        skipSounds = stringList(o, "skip_compressor_sounds", DEFAULT_SKIP_SOUNDS);
        logSounds = bool(o, "log_sounds", false);
        showMeter = bool(o, "show_meter", true);
        inGameMusicDb = clamp(num(o, "in_game_music_db", DEFAULT_IN_GAME_MUSIC_DB), -40f, 0f);
        sliderResetDone = bool(o, "slider_reset_done", false);
        masterMovedToOutput = bool(o, "master_moved_to_output", false);

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
                clamp(num(c, "output_db", d.outputDb()), OUTPUT_OFF_DB, 12f),
                bool(c, "limiter", d.limiter()),
                latency,
                clamp(num(c, "lookahead_ms", d.lookaheadMs()), 0f, 20f));

        eq = readEq(section(o, "eq"));

        Map<String, Float> mixLevels = floatMap(o, "category_mix", DEFAULT_MIX);
        for (Map.Entry<String, Float> def : DEFAULT_MIX.entrySet()) {
            if (mixLevels.putIfAbsent(def.getKey(), def.getValue()) == null) {
                missing = true;   // a category with no level would play at 100%: add new ones to older files
            }
        }
        mix = Collections.unmodifiableMap(mixLevels);
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
        o.addProperty("_help", "Better Audio Clarity settings. Saved changes apply within a second "
                + "(master_bus and latency: press F3+T). master_bus=false turns the whole chain off - "
                + "use it if the game has no sound or crackles.");
        o.addProperty("master_bus", masterBus);
        o.addProperty("music_skips_compressor", musicSkipsCompressor);
        o.addProperty("ui_skips_compressor", uiSkipsCompressor);
        JsonArray skip = new JsonArray();
        skipSounds.forEach(skip::add);
        o.add("skip_compressor_sounds", skip);
        o.addProperty("log_sounds", logSounds);
        o.addProperty("show_meter", showMeter);
        o.addProperty("in_game_music_db", inGameMusicDb);
        o.addProperty("slider_reset_done", sliderResetDone);
        o.addProperty("master_moved_to_output", masterMovedToOutput);

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
                + "latency: LOW (10 ms), NORMAL (20 ms) or SAFE (40 ms) asks the sound card for that mixing period - raise it if you hear dropouts. "
                + "lookahead_ms (0-20): the safety limiter sees peaks this far ahead and lowers the gain smoothly instead of clipping; adds that much delay.");
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
        j.addProperty("lookahead_ms", c.lookaheadMs());
        o.add("compressor", j);

        JsonObject m = new JsonObject();
        m.addProperty("_help", "Level of each sound category when its slider is at 100% (1.0 = vanilla). "
                + "Names: record, weather, block, hostile, neutral, player, ambient, voice, ui, music, master (sounds servers play in the Master category).");
        mix.forEach(m::addProperty);
        o.add("category_mix", m);

        JsonObject s = new JsonObject();
        s.addProperty("_help", "Extra volume in dB for single sounds (\"minecraft:entity.zombie.hurt\") or groups "
                + "starting or ending with * (\"minecraft:entity.zombie.*\", \"*.step\"), optionally limited to one category with \"category|\" "
                + "(\"player|*.step\" = only your own footsteps). Matching rules add up. -40 mutes. /playsound suggests the IDs.");
        sounds.forEach(s::addProperty);
        o.add("sound_adjustments_db", s);

        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
            lastSeen = Files.getLastModifiedTime(f);
        } catch (Exception e) {
            LOGGER.warn("Could not write config/better-audio-clarity.json: {}", e.toString());
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
        return rules.keySet().stream().anyMatch(k -> k.contains("*") || k.contains("|"));
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
