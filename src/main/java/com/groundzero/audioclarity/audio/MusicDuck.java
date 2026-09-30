package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * Lowers the music once the player is in a world or on a server, so it sits behind the game;
 * the menus keep it at full level. Changes fade over about two seconds instead of jumping.
 */
public final class MusicDuck {

    private static final int FADE_TICKS = 40;   // 2 seconds

    private static float currentDb;
    private static float fadingTo;
    private static float stepDb;

    private MusicDuck() {}

    /** Extra music gain (linear) on top of the category mix. */
    public static float gain() {
        return currentDb == 0f ? 1f : (float) Math.pow(10.0, currentDb / 20.0);
    }

    /** Client tick: move toward the target and let playing music take the new level. */
    public static void tick(Minecraft mc) {
        float target = mc.level != null ? ClarityConfig.inGameMusicDb() : 0f;
        if (currentDb == target) {
            return;
        }
        if (target != fadingTo) {
            fadingTo = target;
            stepDb = Math.max(0.01f, Math.abs(target - currentDb) / FADE_TICKS);
        }
        currentDb = currentDb < target ? Math.min(target, currentDb + stepDb) : Math.max(target, currentDb - stepDb);
        mc.getSoundManager().refreshCategoryVolume(SoundSource.MUSIC);
    }
}
