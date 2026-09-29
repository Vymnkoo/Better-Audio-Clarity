# Audio Clarity

A mastering chain for Minecraft's sound. Client-side Fabric mod for **Minecraft 26.3**. It needs only Fabric Loader; Fabric API isn't required.

## In plain words

Think of how a song sounds on a phone recording compared with the finished song on Spotify. The finished one sounds clear, even and "polished", because a sound engineer ran it through a few tools before release. Audio Clarity does the same thing for Minecraft, live, while you play.

- **Nothing gets lost or too loud.** Normally a creeper hiss or a footstep can be hard to hear, and then an explosion or a door slam is suddenly way too loud. Audio Clarity gently turns quiet sounds up and loud sounds down, so everything sits at a comfortable level. You hear more of the world without reaching for the volume.
- **Clearer, brighter sound.** Minecraft's sounds can feel dull, like listening through a blanket. Audio Clarity removes some of that "muddiness" and brings out the crisp detail: footsteps, mobs, digging and placing blocks.
- **No crackle or distortion.** A safety net at the very end stops the sound from ever breaking up, even when lots happens at once.
- **Music stays smooth.** Background music isn't affected by any of this, so a big fight never makes the music suddenly dip.
- **A better balance out of the box.** Rain, mobs, blocks and so on are pre-mixed to sit well together. You can still turn any of them down.
- **Your volume slider still works as usual.** Master Volume only changes how loud the finished sound is. It doesn't change the sound itself.
- **No delay.** The sound isn't slowed down. What you hear still matches what you see.

There's nothing to set up: install it and play. If you like to tinker, every setting is in one file, and changes apply instantly while the game runs.

## Technical overview

Minecraft's sound can come out dark, muffled and uneven. Audio Clarity puts a studio-style master chain on everything the game plays:

```
game mix → compressor → make-up → output → EQ → limiter → Master slider → sound card
```

## Features

- **Live master compressor.** Stereo-linked with a soft knee, it evens out the mix so quiet sounds are heard and loud ones don't blast.
- **Master slider is the final gain.** It's applied after the compressor, so turning the game down never changes how hard the compressor works.
- **Global EQ.** Up to 10 bands (highpass, lowpass, lowshelf, highshelf, peak) after the compressor. The default curve brightens Minecraft's muffled tone:
  - highpass at 30 Hz
  - −2 dB at 300 Hz
  - +2 dB at 3.5 kHz
  - +3 dB high shelf from 9 kHz
- **Safety limiter** at −0.3 dBFS, so nothing clips.
- **Music skips the chain.** Music plays straight to the sound card, so loud moments never pump it down.
- **Category mix.** Each sound category has a tuned level at 100% on its slider. Players can still turn anything down from there.
- **Per-sound adjustments** in dB, for single sounds or whole groups (`minecraft:entity.zombie.*`).
- **Meter.** IN / GR / OUT bars in the top-right corner of Music & Sound.
- **Low latency.** There's no extra buffering: the sound card pulls audio through the chain on demand, so the only delay is the card's own ~10 ms period.

## How it works

Minecraft normally mixes straight into the sound card through OpenAL. Audio Clarity makes it mix into an OpenAL Soft **loopback device** instead. Every sound, every mod and every reverb effect still happens inside OpenAL.

The real sound card then plays a single stereo source that uses `AL_SOFT_callback_buffer`. Whenever the card needs audio, the callback renders exactly that many frames from the loopback mix, runs them through the chain in place and hands them back.

Music channels are created on the sound card's own context, so music bypasses the loopback. Each call on a music channel switches to that context with `ALC_EXT_thread_local_context`.

If the OpenAL build lacks the needed extensions, or anything fails to start, audio falls back to Minecraft's normal path.

## Configuration

Settings live in `config/audio-clarity.json`, which is created on first start with the defaults. **Saved changes apply live within a second.** Only `master_bus` and `latency` need F3+T. A file with a typo is ignored (the log says so), and the last good settings stay in use.

| Key | What it does |
|---|---|
| `master_bus` | `false` = whole chain off, vanilla audio path (use this if the game has no sound or crackles) |
| `music_skips_compressor` | `false` = music goes through the chain like everything else |
| `show_meter` | meter on the Music & Sound screen |
| `eq.enabled`, `eq.bands[]` | `type`, `freq_hz`, `gain_db` (not for pass filters), `q` (0.707 = standard) |
| `compressor.*` | `threshold_db`, `ratio`, `attack_ms`, `release_ms`, `knee_db`, `makeup_db`, `output_db`, `limiter`, `latency` (`LOW` 10 ms / `NORMAL` 20 ms / `SAFE` 40 ms) |
| `category_mix` | level per category at 100% on its slider; `1.0` = vanilla |
| `sound_adjustments_db` | `"sound.id": dB` or `"prefix.*": dB`; `-40` mutes |

## Compatibility

- Works alongside Sodium, Iris, Lithium, C2ME, ViaFabricPlus and similar mods (tested in a 148-mod instance).
- If Sound Physics Remastered is installed, its reverb is skipped for music, which bypasses the chain.
- Voice chat mods (Simple Voice Chat, Plasmo Voice) open their own OpenAL device, so voice doesn't go through the chain.
- Not compatible with Emberwild AutoTune, which hooks the same code. `fabric.mod.json` declares this.

## Building

No Gradle. `build.ps1` compiles with `javac` against the client jar and libraries that the Modrinth App already downloaded. Minecraft 26.x is unobfuscated, and Fabric runs it under Mojang's names.

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1              # 26.3 + Fabric Loader 0.19.5
powershell -ExecutionPolicy Bypass -File build.ps1 -McVersion 26.3 -Loader 0.19.5
```

Requirements:
- JDK 25
- A Fabric instance of the target version, launched once in the Modrinth App

Output: `audio-clarity-<version>.jar`.

## How this was made

Audio Clarity was built with AI assistance. The code was written with the help of Claude (Anthropic), under human direction.

The ideas, decisions and sound are mine:
- A compressor like the ones audio engineers use, with Master as the final gain.
- Music skipping the chain, and the EQ placed after the compressor.
- Every value was tuned by ear in-game: threshold, ratio, attack, release, make-up, output, the category mix, the per-sound tweaks and the EQ curve.
- Each version was tested in real play before it was kept.

The AI did the engineering: the OpenAL routing, the DSP code and the mixins.

## License

[GPL-3.0](LICENSE)
