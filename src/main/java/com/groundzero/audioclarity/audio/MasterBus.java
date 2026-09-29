package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTFloat32;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTCallbackBuffer;
import org.lwjgl.openal.SOFTCallbackBufferType;
import org.lwjgl.openal.SOFTDirectChannels;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

import static com.groundzero.audioclarity.AudioClarity.LOGGER;

/**
 * Puts a compressor on Minecraft's master output.
 *
 * <p>Minecraft normally mixes straight into the sound card. Here it mixes into an OpenAL Soft
 * <em>loopback</em> device instead (every sound, every mod, Sound Physics' reverb - all of it
 * still happens inside OpenAL), and the real sound card plays a single stereo source whose
 * samples come from a callback: whenever the card needs audio, the callback renders exactly that
 * many frames from the loopback mix, compresses them in place and hands them back. No queue in
 * between, so the compressor adds essentially no delay beyond the card's own mixing period.
 *
 * <p>Order matters on shutdown: the card must stop asking for audio before the loopback device
 * it renders from is closed.
 */
public final class MasterBus implements org.lwjgl.openal.SOFTCallbackBufferTypeI {

    private static final int ALC_HRTF_SOFT = 0x1992;
    private static final int ALC_OUTPUT_LIMITER_SOFT = 0x199A;
    private static final int ALC_REFRESH = 0x1008;

    private static volatile MasterBus active;
    /** The game's Master slider, copied here every client tick; applied after the compressor. */
    public static volatile float masterVolume = 1f;
    /** Why the bus isn't running (null when fine). */
    public static volatile String lastError;

    private final long realDevice;
    private final long outContext;
    private final long loopDevice;
    private final int sampleRate;
    private final Compressor compressor;
    private SOFTCallbackBufferType callback;
    private IntBuffer loopAttributes;
    private int source;
    private int buffer;
    private volatile boolean running;

    private MasterBus(long realDevice, long outContext, long loopDevice, int sampleRate) {
        this.realDevice = realDevice;
        this.outContext = outContext;
        this.loopDevice = loopDevice;
        this.sampleRate = sampleRate;
        this.compressor = new Compressor(sampleRate);
    }

    /** The bus currently playing; null when audio runs the vanilla way. */
    public static MasterBus active() {
        return active;
    }

    public Compressor compressor() {
        return compressor;
    }

    public long realDevice() {
        return realDevice;
    }

    public long loopDevice() {
        return loopDevice;
    }

    /** The real sound card's context - where music plays, around the compressor. */
    public long outContext() {
        return outContext;
    }

    public int sampleRate() {
        return sampleRate;
    }

    /** The sound card's actual mixing period in ms, as OpenAL reports it. */
    public float periodMs() {
        int refresh = ALC10.alcGetInteger(realDevice, ALC_REFRESH);
        return refresh > 0 ? 1000f / refresh : -1f;
    }

    /**
     * Opens the context on the real device and a loopback device at the same rate. Returns null
     * (and leaves the real device untouched) if anything is unsupported - audio then runs the
     * vanilla way.
     */
    public static MasterBus tryCreate(long realDevice) {
        if (realDevice == 0L || !ClarityConfig.masterBus()) {
            return null;
        }
        long outContext = 0L;
        long loop = 0L;
        try {
            if (!ALC10.alcIsExtensionPresent(0L, "ALC_SOFT_loopback") || !ALC10.alcIsExtensionPresent(0L, "ALC_EXT_thread_local_context")) {
                lastError = "this OpenAL has no loopback / thread-local context support";
                LOGGER.warn("Master compressor off: {}", lastError);
                return null;
            }
            int[] outAttrs = {
                    ALC_HRTF_SOFT, ALC10.ALC_FALSE,   // the game mix is already spatialised
                    ALC_OUTPUT_LIMITER_SOFT, ALC10.ALC_TRUE,
                    ALC_REFRESH, ClarityConfig.compressor().latency().refreshHz,
                    0};
            outContext = ALC10.alcCreateContext(realDevice, outAttrs);
            if (outContext == 0L) {
                LOGGER.warn("Master compressor off: could not create the output context");
                return null;
            }
            int rate = ALC10.alcGetInteger(realDevice, ALC10.ALC_FREQUENCY);
            if (rate <= 0) {
                rate = 48000;
            }
            loop = SOFTLoopback.alcLoopbackOpenDeviceSOFT((CharSequence) null);
            if (loop == 0L || !SOFTLoopback.alcIsRenderFormatSupportedSOFT(loop, rate, SOFTLoopback.ALC_STEREO_SOFT, SOFTLoopback.ALC_FLOAT_SOFT)) {
                LOGGER.warn("Master compressor off: loopback can't render float stereo at {} Hz", rate);
                if (loop != 0L) {
                    ALC10.alcCloseDevice(loop);
                }
                ALC10.alcDestroyContext(outContext);
                return null;
            }
            return new MasterBus(realDevice, outContext, loop, rate);
        } catch (Throwable t) {
            LOGGER.warn("Master compressor off: {}", t.toString());
            if (loop != 0L) {
                ALC10.alcCloseDevice(loop);
            }
            if (outContext != 0L) {
                ALC10.alcDestroyContext(outContext);
            }
            return null;
        }
    }

    /**
     * Minecraft's context attributes plus the loopback render format. Its output limiter is
     * switched off here: the compressor needs to see the real peaks, and has its own limiter.
     */
    public IntBuffer withLoopbackFormat(IntBuffer attrs) {
        IntBuffer in = attrs.duplicate();
        int pairs = 0;
        int[] copy = new int[in.remaining() + 8];
        int n = 0;
        while (in.remaining() >= 2) {
            int key = in.get();
            if (key == 0) {
                break;
            }
            int value = in.get();
            if (key == ALC_OUTPUT_LIMITER_SOFT) {
                value = ALC10.ALC_FALSE;
            }
            if (key == SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT || key == SOFTLoopback.ALC_FORMAT_TYPE_SOFT || key == ALC10.ALC_FREQUENCY) {
                continue;
            }
            copy[n++] = key;
            copy[n++] = value;
            pairs++;
        }
        copy[n++] = SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT;
        copy[n++] = SOFTLoopback.ALC_STEREO_SOFT;
        copy[n++] = SOFTLoopback.ALC_FORMAT_TYPE_SOFT;
        copy[n++] = SOFTLoopback.ALC_FLOAT_SOFT;
        copy[n++] = ALC10.ALC_FREQUENCY;
        copy[n++] = sampleRate;
        copy[n++] = 0;
        if (loopAttributes != null) {
            MemoryUtil.memFree(loopAttributes);
        }
        loopAttributes = MemoryUtil.memAllocInt(n);
        loopAttributes.put(copy, 0, n).flip();
        return loopAttributes;
    }

    /**
     * Starts the real device pulling audio. Called once Minecraft's own context is current, so
     * our output context is only made current on this thread for the few calls we need.
     */
    public boolean start() {
        try {
            enterOutputContext();
            callback = SOFTCallbackBufferType.create(this);
            if (callback.address() == 0L) {
                throw new IllegalStateException("LWJGL could not create the audio callback");
            }
            buffer = AL10.alGenBuffers();
            source = AL10.alGenSources();
            // The user pointer is unused, but LWJGL insists it isn't null.
            SOFTCallbackBuffer.alBufferCallbackSOFT(buffer, EXTFloat32.AL_FORMAT_STEREO_FLOAT32, sampleRate, callback, callback.address());
            AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL10.AL_TRUE); // L->L, R->R, no panning
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            running = true;
            AL10.alSourcePlay(source);
            int err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR) {
                throw new IllegalStateException("OpenAL error " + err + " starting the output source");
            }
            active = this;
            LOGGER.info("Master compressor on: {} Hz, sound card period {} ms", sampleRate, String.format("%.1f", periodMs()));
            return true;
        } catch (Throwable t) {
            running = false;
            StackTraceElement[] st = t.getStackTrace();
            lastError = t + (st.length > 0 ? " at " + st[0] + (st.length > 1 ? " <- " + st[1] : "") : "");
            LOGGER.error("Master compressor failed to start - falling back to normal audio", t);
            return false;
        } finally {
            exitOutputContext();
        }
    }

    /**
     * Makes our output context current on this thread only; Minecraft's context stays current for
     * everything else. (Same OpenAL library, so Minecraft's LWJGL function table works for both.)
     */
    private void enterOutputContext() {
        EXTThreadLocalContext.alcSetThreadContext(outContext);
    }

    private void exitOutputContext() {
        EXTThreadLocalContext.alcSetThreadContext(0L);
    }

    /** Stops the real device and frees everything except the loopback device, which Minecraft closes. */
    public void close() {
        running = false;
        if (active == this) {
            active = null;
        }
        try {
            if (source != 0) {
                enterOutputContext();
                try {
                    AL10.alSourceStop(source);
                    AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                    AL10.alDeleteSources(source);
                    AL10.alDeleteBuffers(buffer);
                } finally {
                    exitOutputContext();
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Problem stopping the master compressor output: {}", t.toString());
        }
        // Destroying the context waits for the card's mixer, so no callback is still running after this.
        ALC10.alcDestroyContext(outContext);
        ALC10.alcCloseDevice(realDevice);
        if (callback != null) {
            callback.free();
            callback = null;
        }
        if (loopAttributes != null) {
            MemoryUtil.memFree(loopAttributes);
            loopAttributes = null;
        }
    }

    /** Called by OpenAL on the sound card's mixing thread whenever it needs more audio. */
    @Override
    public int invoke(long userptr, long sampledata, int numbytes) {
        int frames = numbytes >> 3; // 2 channels x 4-byte float
        try {
            if (!running) {
                MemoryUtil.memSet(sampledata, 0, numbytes);
                return numbytes;
            }
            SOFTLoopback.nalcRenderSamplesSOFT(loopDevice, sampledata, frames);
            compressor.process(sampledata, frames, masterVolume);
        } catch (Throwable t) {
            // An exception must never escape into native code.
            MemoryUtil.memSet(sampledata, 0, numbytes);
        }
        return numbytes;
    }
}
