package com.groundzero.audioclarity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Better Audio Clarity: a mastering chain for Minecraft's sound.
 *
 * <ul>
 *   <li>A live master compressor + limiter on everything the game plays (see
 *       {@link com.groundzero.audioclarity.audio.MasterBus}); the Master slider is the final gain,
 *       after the compressor.</li>
 *   <li>Music goes around the compressor, straight to the sound card, so loud moments never pump
 *       it down ({@link com.groundzero.audioclarity.audio.MusicRoute}).</li>
 *   <li>A built-in category mix and per-sound adjustments, under the sliders.</li>
 *   <li>A small IN / GR / OUT meter in the corner of Music &amp; Sound.</li>
 * </ul>
 *
 * Everything is set in config/better-audio-clarity.json ({@link ClarityConfig}). No entrypoint: the mod
 * is only mixins, and the config loads when the sound engine starts.
 */
public final class AudioClarity {

    public static final String MOD_ID = "better_audio_clarity";
    public static final Logger LOGGER = LoggerFactory.getLogger("Better Audio Clarity");

    private AudioClarity() {}
}
