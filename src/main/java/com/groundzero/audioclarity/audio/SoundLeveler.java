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
 * Sound leveling (part of the Loud mix): pulls each sound part of the way toward a common level
 * before it reaches the compressor, so one huge sound (TNT) no longer drags the whole mix down
 * and quiet ones sit a little closer. Loud sounds stay louder - a sound 20 dB above the
 * reference at 40% leveling comes down 8 dB and is still 12 dB above it.
 *
 * <p>Each vanilla sound file's loudness was measured offline (assets/.../loudness.tsv, EBU R128
 * max momentary); the level a sound actually plays at is that plus the volume the game asks for
 * (code multipliers such as a footstep's 15%). Sounds not in the table (resource packs, servers'
 * own sounds) are left alone - the harshness tamer and compressor still handle them.
 */
public final class SoundLeveler {

    /** The median vanilla sound effect file, played at full volume. */
    private static final double REFERENCE_DB = -25.6;
    private static final double MAX_CUT_DB = 10;
    private static final double MAX_LIFT_DB = 4;   // quiet stays fairly quiet: caves must not fill up

    private static volatile Map<String, Float> table;

    private SoundLeveler() {}

    /** Linear gain for this sound; 1 when leveling is off or the sound is unknown. */
    public static float gain(SoundInstance instance) {
        float amount = ClarityConfig.soundLeveling();
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
        double plays = file + 20 * Math.log10(asked);
        double offset = Math.max(-MAX_CUT_DB, Math.min(MAX_LIFT_DB, -amount * (plays - REFERENCE_DB)));
        return (float) Math.pow(10, offset / 20);
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
                LOGGER.warn("loudness.tsv missing - sound leveling is off");
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
            LOGGER.warn("Could not read loudness.tsv - sound leveling is off: {}", e.toString());
        }
        return m;
    }
}
