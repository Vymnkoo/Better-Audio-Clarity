package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.groundzero.audioclarity.AudioClarity.LOGGER;

/**
 * Loud-sound taming: turns down only the sounds that play far above the typical vanilla sound -
 * TNT, level-ups, totems - before they reach the compressor, so they stay the big hits without
 * towering over the game or slamming the compressor and limiter. A sound more than
 * {@link ClarityConfig#LOUD_SOUND_THRESHOLD_DB} above the reference loses this share of the
 * excess: at 50%, TNT (about 15.6 dB above) comes down about 4.8 dB and is still about 10.8 dB
 * louder than a normal sound. Normal and quiet sounds are never touched.
 *
 * <p>Each vanilla sound file's loudness was measured offline (assets/.../loudness.tsv, EBU R128
 * max momentary); the level a sound actually plays at is that plus the volume the game asks for
 * (code multipliers such as a footstep's 15%). Sounds not in the table (resource packs, servers'
 * own sounds) are left alone - the harshness tamer and compressor still handle them.
 */
public final class SoundLeveler {

    /** The median vanilla sound effect file, played at full volume. */
    private static final double REFERENCE_DB = -25.6;
    private static final double MAX_CUT_DB = 12;

    private static volatile Map<String, Float> table;

    private SoundLeveler() {}

    /** Linear gain for this sound; 1 when taming is off, the sound isn't loud or is unknown. */
    public static float gain(SoundInstance instance) {
        float amount = ClarityConfig.loudTaming();
        if (amount <= 0f) {
            return 1f;
        }
        Sound sound = instance.getSound();
        if (sound == null || !"minecraft".equals(sound.getLocation().getNamespace())) {
            return 1f;
        }
        Float file = table().get(sound.getLocation().getPath());
        if (file == null) {
            return 1f;
        }
        double asked = Math.max(1e-3, Math.min(1.0, instance.getVolume()));   // OpenAL caps gain at 1
        double excess = file + 20 * Math.log10(asked) - (REFERENCE_DB + ClarityConfig.LOUD_SOUND_THRESHOLD_DB);
        if (excess <= 0) {
            return 1f;
        }
        return (float) Math.pow(10, -Math.min(MAX_CUT_DB, amount * excess) / 20);
    }

    private static Map<String, Float> table() {
        Map<String, Float> t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return t;
    }

    private static Map<String, Float> load() {
        Map<String, Float> m = new HashMap<>(8192);
        try (InputStream in = SoundLeveler.class.getResourceAsStream("/assets/better_audio_clarity/loudness.tsv")) {
            if (in == null) {
                LOGGER.warn("loudness.tsv missing - loud-sound taming is off");
                return m;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                int tab = line.indexOf('\t');
                if (line.startsWith("#") || tab < 0) {
                    continue;
                }
                m.put(line.substring(0, tab), Float.parseFloat(line.substring(tab + 1)));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read loudness.tsv - loud-sound taming is off: {}", e.toString());
        }
        return m;
    }
}
