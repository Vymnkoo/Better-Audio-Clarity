**Note**: This mod is experimental and uses AI assistance with the help of Claude (Anthropic) under human direction. I might not update this mod anytime soon so If you want to make a fork version, then go for it (:

# Better Audio Clarity

A mastering chain for Minecraft's sound. Client-side Fabric mod for **Minecraft 26.3**. It needs only Fabric Loader; Fabric API isn't required.

> **Status: Beta.** It works, but it's still being tuned: settings and defaults may change between versions.

## TL;DR

Makes Minecraft sound slightly better.

- 🔊 **Quiet sounds are easier to hear, and loud ones don't blast your ears.**
- ✨ **Clearer sound:** less muffled, more detail in footsteps, mobs and blocks.
- 🎵 **Music is never interrupted** by fights or explosions.
- ⚡ **No delay**, and your volume slider works as usual.

Install it and play. No setup needed.

## Technical overview

Minecraft's sound can come out dark, muffled and uneven. Better Audio Clarity puts a studio-style master chain on everything the game plays:

```
game mix → compressor → make-up → output → EQ → limiter → Master slider → sound card
```

## Features

- **Transparent master compressor.** Stereo-linked with a soft knee, tuned gently (threshold −30 dB, ratio 2:1, attack 50 ms, release 248 ms, knee 11 dB), so quiet sounds are heard and loud ones don't blast, without the game sounding squashed.
- **Master slider is the final gain.** It's applied after the compressor, so turning the game down never changes how hard the compressor works.
- **Global EQ.** Up to 10 bands (highpass, lowpass, lowshelf, highshelf, peak) after the compressor. The default curve brightens Minecraft's muffled tone:
  - highpass at 30 Hz
  - −2 dB at 300 Hz
  - +2 dB at 3.5 kHz
  - +3 dB high shelf from 9 kHz
- **Lookahead safety limiter** at −1 dB. It sees loud peaks 10 ms ahead and lowers the volume smoothly before they arrive, so nothing clips or crackles, and recordings stay clean after encoding.
- **Music and UI skip the chain.** They play straight to the sound card, so loud moments never pump the music down and menu clicks never make the compressor pump the game.
- **Server sounds that would pump skip it too.** Servers often use normal sounds as menu "ticks" or countdowns: button clicks (mcpvp), UI clicks in the Master category and note block ticks (Hypixel). They go around the compressor, and the Hypixel countdown is turned down.
- **One Output for everything.** The Output gain (default +5.5 dB) applies to music and UI as well, so raising the overall level never buries them.
- **Settings in Mod Menu.** Mods → Better Audio Clarity → config: an **Output** slider (raise it for quiet headphones, lower it for loud speakers) and on/off switches for the compressor, EQ, music/UI bypass and the meter. Heard live, saved on Done. The tuned compressor, EQ and mix values stay in the config file, so they can't be wrecked by accident.
- **Category mix.** Each sound category has a tuned level at 100% on its slider (music at 14%). Players can still turn anything down from there.
- **Per-sound adjustments** in dB, for single sounds, groups (`minecraft:entity.zombie.*`, `*.step`) or one category only (`player|*.step` = just your own footsteps, +10 dB by default).
- **First start sets the category sliders to 100%, once**, so sliders lowered in vanilla don't lower a category twice. Master and Music are left alone.
- **Meter.** IN / GR / OUT bars at the bottom of Music & Sound, above Done.
- **Low latency.** No extra buffering: the sound card pulls audio through the chain on demand. The delay is the card's own period (~10 ms) plus the limiter's 10 ms lookahead.
- **Level.** About −21 to −19 LUFS in normal play, with peaks held at −1 dB: a natural level for game audio. For YouTube-level recordings, add Gain + Limiter in OBS.
## How it works

Minecraft normally mixes straight into the sound card through OpenAL. Better Audio Clarity makes it mix into an OpenAL Soft **loopback device** instead. Every sound, every mod and every reverb effect still happens inside OpenAL.

The real sound card then plays a single stereo source that uses `AL_SOFT_callback_buffer`. Whenever the card needs audio, the callback renders exactly that many frames from the loopback mix, runs them through the chain in place and hands them back.

Sounds that skip the chain (music, UI and the skip list) are streamed, and their channels are created on the sound card's own context, so they bypass the loopback. Each call on such a channel switches to that context with `ALC_EXT_thread_local_context`. The game gets 16 streaming channels instead of 8 for this.

If the OpenAL build lacks the needed extensions, or anything fails to start, audio falls back to Minecraft's normal path.
## Configuration

The everyday settings (Output and the on/off switches) are in **Mod Menu** (optional). Everything lives in `config/better-audio-clarity.json`, which is created on first start with the defaults. **Saved changes apply live within a second.** Only `master_bus` and `latency` need F3+T. A file with a typo is ignored (the log says so), and the last good settings stay in use.

| Key | What it does |
|---|---|
| `master_bus` | `false` = whole chain off, vanilla audio path (use this if the game has no sound or crackles) |
| `music_skips_compressor` / `ui_skips_compressor` | `false` = that category goes through the chain like everything else |
| `skip_compressor_sounds` | list of single sounds that go around the chain, e.g. `"*_button.click_on"`, `"minecraft:ui.*"` |
| `log_sounds` | `true` = write every sound to the log with its ID, category and route (to find a server's loud sound) |
| `show_meter` | meter on the Music & Sound screen |
| `slider_reset_done` | set after the one-time slider reset; `false` = do it again on next start |
| `eq.enabled`, `eq.bands[]` | `type`, `freq_hz`, `gain_db` (not for pass filters), `q` (0.707 = standard) |
| `compressor.*` | `threshold_db`, `ratio`, `attack_ms`, `release_ms`, `knee_db`, `makeup_db`, `output_db`, `limiter`, `lookahead_ms` (0–20), `latency` (`LOW` 10 ms / `NORMAL` 20 ms / `SAFE` 40 ms, a request to the sound card) |
| `category_mix` | level per category at 100% on its slider; `1.0` = vanilla |
| `sound_adjustments_db` | `"pattern": dB`. Patterns: a sound ID, `"prefix.*"`, `"*suffix"`, optionally `"category|pattern"`. Matching rules add up; `-40` mutes |

**A server sound is too loud or makes the audio pump?** Set `log_sounds` to `true`, reproduce it, and look for `[sound]` lines in `logs/latest.log` at that moment. Add its ID to `skip_compressor_sounds` and/or `sound_adjustments_db`.
## Compatibility

- Works alongside Sodium, Iris, Lithium, C2ME, ViaFabricPlus and similar mods (tested in a 148-mod instance).
- If Sound Physics Remastered is installed, its reverb is skipped for sounds that bypass the chain (music, UI, the skip list).
- Voice chat mods (Simple Voice Chat, Plasmo Voice) open their own OpenAL device, so voice doesn't go through the chain.
- Tested on Hypixel and mcpvp.com (through ViaFabricPlus).
- Mod Menu is optional: with it, the mod gets a settings screen in the mod list.

## Building

No Gradle. `build.ps1` compiles with `javac` against the client jar and libraries that the Modrinth App already downloaded. Minecraft 26.x is unobfuscated, and Fabric runs it under Mojang's names.

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1              # 26.3 + Fabric Loader 0.19.5
powershell -ExecutionPolicy Bypass -File build.ps1 -McVersion 26.3 -Loader 0.19.5
```

Requirements:
- JDK 25
- A Fabric instance of the target version, launched once in the Modrinth App

Output: `better-audio-clarity-<version>.jar`.

## How this was made

Better Audio Clarity was built with AI assistance. The code was written with the help of Claude (Anthropic), under human direction.

The ideas, decisions and sound are mine:
- A compressor like the ones audio engineers use, with Master as the final gain.
- Music skipping the chain, and the EQ placed after the compressor.
- Every value was tuned by ear in-game: threshold, ratio, attack, release, make-up, output, the category mix, the per-sound tweaks and the EQ curve.
- Each version was tested in real play before it was kept.

The AI did the engineering: the OpenAL routing, the DSP code and the mixins.

## License

[GPL-3.0](LICENSE)
