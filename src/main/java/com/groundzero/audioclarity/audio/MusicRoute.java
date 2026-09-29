package com.groundzero.audioclarity.audio;

import org.lwjgl.openal.EXTThreadLocalContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sends music around the master compressor, straight to the sound card, so loud moments in the
 * game never pump the music down.
 *
 * <p>A music track's OpenAL channel is created on the real sound card's context (the master
 * bus's output context) instead of the loopback mix. OpenAL objects belong to one device, so
 * every call on such a channel - play, volume, streaming, pause, cleanup - runs with that context
 * current on the calling thread (the sound engine's thread), and switches back afterwards.
 * Only streamed sounds are routed: static sound buffers live on the loopback device.
 */
public final class MusicRoute {

    /** Set on the sound thread while a music channel is being created. */
    private static boolean creatingMusic;
    private static final ThreadLocal<int[]> depth = ThreadLocal.withInitial(() -> new int[1]);

    private MusicRoute() {}

    /** Implemented on Channel by the mixin. */
    public interface MusicChannel {
        boolean audioclarity$isMusic();

        void audioclarity$markMusic();
    }

    public static boolean isMusic(Object channel) {
        return channel instanceof MusicChannel m && m.audioclarity$isMusic();
    }

    /**
     * Wraps ChannelAccess.createHandle so the channel it makes (on the sound thread, in order)
     * is built on the sound card's context.
     */
    public static <T> CompletableFuture<T> createOnSoundCard(Executor soundThread, Supplier<CompletableFuture<T>> createHandle) {
        soundThread.execute(() -> creatingMusic = true);
        try {
            return createHandle.get();
        } finally {
            soundThread.execute(() -> creatingMusic = false);
        }
    }

    public static boolean creatingMusic() {
        return creatingMusic;
    }

    /** Runs a channel command with the sound card's context current if it's a music channel. */
    public static <C> Consumer<C> wrap(Consumer<C> action) {
        return channel -> {
            boolean music = isMusic(channel);
            if (music) {
                enter();
            }
            try {
                action.accept(channel);
            } finally {
                if (music) {
                    exit();
                }
            }
        };
    }

    /** True while this thread is running a music channel's calls. */
    public static boolean inMusicContext() {
        return depth.get()[0] > 0;
    }

    /** Nested calls are fine: only the outermost one switches the context. */
    public static void enter() {
        MasterBus bus = MasterBus.active();
        int[] d = depth.get();
        if (d[0]++ == 0 && bus != null) {
            EXTThreadLocalContext.alcSetThreadContext(bus.outContext());
        }
    }

    public static void exit() {
        int[] d = depth.get();
        if (d[0] > 0 && --d[0] == 0) {
            EXTThreadLocalContext.alcSetThreadContext(0L);
        }
    }
}
