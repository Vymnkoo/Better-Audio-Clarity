**Note**: This mod is experimental and uses AI assistance with the help of Claude (Anthropic) under human direction. I might not update this mod anytime soon so If you want to make a fork version, then go for it (:

# Better Audio Clarity

A mastering chain for Minecraft's sound. Client-side Fabric mod for **Minecraft 26.1 – 26.4** (one jar for all of them). It needs only Fabric Loader 0.18 or newer; Fabric API isn't required.

## TL;DR

Makes Minecraft sound slightly better.

- 🔊 **Quiet sounds are easier to hear, and loud ones don't blast your ears.**
- 💥 **Big sounds stay big without towering over the game.** TNT still hits hard, it just doesn't drown everything else.
- ✨ **Clearer sound:** less muffled, more detail in footsteps, mobs and blocks, and harsh sounds (coins, glass, anvils) are smoothed out.
- 🎵 **Music is never interrupted** by fights or explosions, and it steps back a little while you play.
- 🎚️ **One Master Volume** in Music & Sound sets how loud everything is.
- ⚡ **No noticeable delay.**

Install it and play. No setup needed.

## Technical overview

Minecraft's sound can come out dark, muffled and uneven. Better Audio Clarity puts a studio-style master chain on everything the game plays:

```
sounds (category mix, per-sound adjustments, loud-sound taming)
  → leveling compressor → make-up → Output (Master Volume) → EQ → harshness tamer → limiter → sound card
```

## Features

- **Gentle leveling compressor.** Stereo-linked with a soft knee and a slow 1.5 s release, so it rides the overall level instead of pumping (threshold −23.2 dB, ratio 1.33:1, attack 15 ms, knee 12 dB, make-up +3.7 dB). Quiet sounds come forward, and the game never sounds squashed.
- **Loud-sound taming.** Every vanilla sound file's loudness was measured. Sounds that play far above the typical level (more than 6 dB) lose 35% of the excess before they reach the compressor: TNT comes down about 3.4 dB and is still about 12 dB louder than a normal sound. Normal and quiet sounds are never touched.
- **Measured sound rebalance.** All ~2,000 sound events were compared with similar ones (every block "break", every mob "hurt"…), with each mob's own in-game volume taken into account. 43 sounds that were loud by accident are turned down, e.g. anvil placing, glass breaking, ladders, shulker boxes, cow and hoglin footsteps, the horse family, and the new ice and icicle sounds of 26.4. Sounds that are meant to be big (explosions, the Warden, the Dragon, totems) keep their weight.
- **Harshness tamer.** A two-band dynamic EQ after the EQ. It only acts while a sound's harsh band sticks out from the rest of that sound:
  - **Presence** (~3.5 kHz): piercing sounds.
  - **Sizzle** (~8 kHz, wide): coins, the ringing of ice and icicles, and other bright "tss" sounds.

  Calibrated on 400 vanilla sounds and a server recording: most vanilla sounds are never touched.
- **Global EQ.** Up to 10 bands (highpass, lowpass, lowshelf, highshelf, peak) after the compressor. The default curve brightens Minecraft's muffled tone:
  - highpass at 30 Hz
  - −2 dB at 300 Hz
  - +2 dB at 3.5 kHz
  - +3 dB high shelf from 9 kHz
- **Lookahead safety limiter** at −1 dB. It sees loud peaks 10 ms ahead and lowers the volume smoothly before they arrive, so nothing clips or crackles, and recordings stay clean after encoding.
- **Master Volume = Output.** The big slider at the top of Music & Sound sets the overall level in dB (0.0 dB = as tuned, OFF to +9 dB) and moves music and UI with it. On first start, a Master Volume below 100% is folded into it once, so nothing gets louder or quieter.
- **Music and UI skip the chain.** They play straight to the sound card, so loud moments never pump the music down and menu clicks never make the compressor pump the game.
- **Music steps back in game.** In menus music plays at its level; in a world or on a server it fades down 6 dB over 2 seconds (adjustable).
- **Server sounds.** Servers often play plugin sounds in the Master category; they get their own level ("Server" in the Gains tab) so they sit with the game instead of 11 dB above it. Sounds servers use as menu "ticks" or countdowns (mcpvp button clicks, Hypixel's UI clicks and note blocks) skip the compressor.
- **Settings in four tabs** (Music & Sound → "Better Audio Clarity…", or Mod Menu):
  - **General:** Master Volume, music in game, the on/off switches and the meters.
  - **Dynamics (Comp):** every compressor setting, with IN / GR / OUT meters and a bypass for A/B listening.
  - **Gains:** the category levels, Tame loud sounds and the list of per-sound adjustments.
  - **Harshness:** both tamer bands, with BAND / CUT meters.

  The tuning tabs start greyed out and ask "Change the tuning?" first, so the tuned sound can't be wrecked by accident. "Reset to defaults" always brings it back.
- **Category mix.** Each sound category has a tuned level at 100% on its slider (music at 18.7%). Players can still turn anything down from there.
- **Per-sound adjustments** in dB, for single sounds, groups (`minecraft:entity.zombie.*`, `*.step`) or one category only (`player|*.step` = just your own footsteps, +10 dB by default).
- **First start sets the category sliders to 100%, once**, so sliders lowered in vanilla don't lower a category twice. Music is left alone.
- **Meters.** IN / GR / OUT at the bottom of Music & Sound, and an optional HUD meter (IN / GR / LIM / OUT) in the top-right corner while you play. It hides with F1 and the F3 screen.
- **Low latency.** No extra buffering: the sound card pulls audio through the chain on demand. The delay is the card's own period (about 10–20 ms) plus the limiter's 10 ms lookahead.

## How it works

Minecraft normally mixes straight into the sound card through OpenAL. Better Audio Clarity makes it mix into an OpenAL Soft **loopback device** instead. Every sound, every mod and every reverb effect still happens inside OpenAL.

The real sound card then plays a single stereo source that uses `AL_SOFT_callback_buffer`. Whenever the card needs audio, the callback renders exactly that many frames from the loopback mix, runs them through the chain in place and hands them back.

Sounds that skip the chain (music, UI and the skip list) are streamed, and their channels are created on the sound card's own context, so they bypass the loopback. Each call on such a channel switches to that context with `ALC_EXT_thread_local_context`. The game gets 16 streaming channels instead of 8 for this.

Loud-sound taming knows each sound's loudness from a table measured offline (EBU R128 max momentary of every vanilla sound file, shipped in the jar), plus the volume the game asks for when it plays the sound. Sounds that aren't in the table (resource packs, servers' own sounds) are left to the harshness tamer and the compressor.

If the OpenAL build lacks the needed extensions, or anything fails to start, audio falls back to Minecraft's normal path.

## Configuration

Everything is in the settings tabs, and everything lives in `config/better-audio-clarity.json`, which is created on first start with the defaults. **Saved changes apply live within a second.** Only `master_bus` and `latency` need F3+T. A file with a typo is ignored (the log says so), and the last good settings stay in use.

| Key | What it does |
|---|---|
| `master_bus` | `false` = whole chain off, vanilla audio path (use this if the game has no sound or crackles) |
| `music_skips_compressor` / `ui_skips_compressor` | `false` = that category goes through the chain like everything else |
| `skip_compressor_sounds` | list of single sounds that go around the chain, e.g. `"*_button.click_on"`, `"minecraft:ui.*"` |
| `log_sounds` | `true` = write every sound to the log with its ID, category, volume, file and route (to find a server's loud sound) |
| `show_meter` / `hud_meter` | meter on the Music & Sound screen / on the HUD while playing |
| `in_game_music_db` | how much music fades down in a world or on a server (0 to −40 dB; default −6) |
| `loud_sound_taming` | share of a loud sound's excess that is taken off (0 = off; default 0.35) |
| `slider_reset_done` / `master_moved_to_output` | set after the one-time slider reset / Master move; `false` = do it again on next start |
| `eq.enabled`, `eq.bands[]` | `type`, `freq_hz`, `gain_db` (not for pass filters), `q` (0.707 = standard) |
| `compressor.*` | `threshold_db`, `ratio`, `attack_ms`, `release_ms`, `knee_db`, `makeup_db`, `output_db`, `limiter`, `lookahead_ms` (0–20), `latency` (`LOW` 10 ms / `NORMAL` 20 ms / `SAFE` 40 ms, a request to the sound card) |
| `harshness_tamer.*` / `sizzle_tamer.*` | `enabled`, `freq_hz`, `q`, `threshold_db` (how far the band may stand above the rest of the sound), `ratio`, `max_cut_db`, `attack_ms`, `release_ms` |
| `category_mix` | level per category at 100% on its slider; `1.0` = vanilla. `master` = sounds servers play in the Master category |
| `sound_adjustments_db` | `"pattern": dB`. Patterns: a sound ID, `"prefix.*"`, `"*suffix"`, optionally `"category|pattern"`. Matching rules add up; `-40` mutes |

**A server sound is too loud or makes the audio pump?** Set `log_sounds` to `true`, reproduce it, and look for `[sound]` lines in `logs/latest.log` at that moment. Add its ID to `skip_compressor_sounds` and/or `sound_adjustments_db`.

## Compatibility

- Minecraft 26.1, 26.2, 26.3 and 26.4 (snapshots included), Fabric Loader 0.18 or newer. One jar.
- Works alongside Sodium, Iris, Lithium, C2ME, ViaFabricPlus and similar mods (tested in instances of up to 148 mods).
- If Sound Physics Remastered is installed, its reverb is skipped for sounds that bypass the chain (music, UI, the skip list).
- Voice chat mods (Simple Voice Chat, Plasmo Voice) open their own OpenAL device, so voice doesn't go through the chain.
- Tested on Hypixel, MCC Island and mcpvp.com (through ViaFabricPlus).
- Mod Menu is optional: with it, the settings also open from the mod list.

## Building

No Gradle. `build.ps1` compiles with `javac` against the client jar and libraries that the Modrinth App already downloaded. Minecraft 26.x is unobfuscated, and Fabric runs it under Mojang's names. One jar runs on 26.1 – 26.4, but compile against each version to catch API differences:

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1                                   # 26.3 + Fabric Loader 0.19.5
powershell -ExecutionPolicy Bypass -File build.ps1 -McVersion 26.1 -Loader 0.18.4
powershell -ExecutionPolicy Bypass -File build.ps1 -McVersion 26.4-snapshot-2
```

Requirements:
- JDK 25
- A Fabric instance of the target version, launched once in the Modrinth App

Output: `better-audio-clarity-<version>.jar`.

## For developers

A map for anyone forking or maintaining the mod. Package `com.groundzero.audioclarity`, mod id `better_audio_clarity`.

### Where things are

**Core**
- `ClarityConfig` holds every setting and default (all tuned values live here). It loads and saves `config/better-audio-clarity.json`, re-reads the file when it changes (polled once a second), matches sound rules, and migrates old files (`config_version`).
- `AudioClarity`: entry point and logger. `ModMenuIntegration`: the optional Mod Menu button.

**`audio/`: the sound processing.** Everything here runs on OpenAL's mixing thread, so the per-block code never allocates (a GC pause would be an audible dropout).
- `MasterBus`: the loopback device the game mixes into, and the callback source on the real sound card that pulls audio through the chain.
- `Compressor`: the whole chain, frame by frame, in this order: compressor → make-up × Output → `Equalizer` → `HarshnessTamer` (presence) → `HarshnessTamer` (sizzle) → `LookaheadLimiter` → clamp. It also fills the meter values.
- `Equalizer` (RBJ biquads), `HarshnessTamer` (a band-pass detector comparing the band with the rest of the sound; cut = input − amount × band), `LookaheadLimiter` (sliding-window minimum, −1 dBFS ceiling).
- `SoundLeveler`: loud-sound taming, from `loudness.tsv`.
- `MusicRoute`: plays music, UI and skip-list sounds on the sound card's context, around the chain.
- `MusicDuck` (music fades down in game), `OutputWatcher` (playing sounds pick up a new Output at once).
- `CompressorMeter` (Music & Sound), `HudMeter` (HUD).

**`gui/`: the settings screens**
- `TabbedScreen`: the tab bar, the "Change the tuning?" lock (`addTuning(...)`), saving on close.
- `SettingsScreen` (General), `CompressorScreen` (Dynamics), `GainsScreen` (Gains), `HarshnessScreen` (Harshness).
- `OutputVolumeSlider`: the Master Volume slider in Music & Sound. `Screens`: opens a screen on every version.

**`mixin/`: the hooks into Minecraft**
- `LibraryMasterBusMixin` + `LibraryInvoker`: swap the game's sound device for the loopback, 16 streaming channels.
- `SoundEngineMixerMixin`: per-sound gain (rules, taming, the gain for sounds that go around the chain), sends skipping sounds to `MusicRoute`, the sound log.
- `OptionsSoundMixMixin`: the category mix, the music fade and Output for sounds around the chain.
- `ChannelGainMixin`, `ChannelHandleMusicMixin`, `ChannelAccessAccessor`: channels that live on the sound card's context; the gain cap for boosted sounds.
- `MinecraftTickMixin`: every tick, the config poll, the one-time slider reset and Master move, the music fade and the Output watcher.
- `SoundOptionsOutputMixin` (Master → Master Volume), `SoundOptionsCompressorButtonMixin` (the settings button), `MusicSoundFooterMixin` + `ScreenMeterMixin` (the meter in Music & Sound, saving on close).
- `HudMeterMixin` (26.2+) and `GuiHudMeterMixin` (26.1): the HUD meter.
- `SoundPhysicsSkipMusicMixin`: optional Sound Physics Remastered compatibility.

### Version differences (26.1 – 26.4)

One jar runs on all four because the code bridges these:
- Opening a screen: `Minecraft.setScreen` on 26.1, `Minecraft.gui.setScreen` on 26.2+ (`gui/Screens` picks one at runtime).
- The HUD is drawn by `Gui` on 26.1 and by `Hud` on 26.2+: two mixins, `require = 0` and `@Pseudo`.
- `OptionsList.addBig(AbstractWidget)` only exists from 26.2 (`OutputVolumeSlider` falls back to `addSmall`).
- `AbstractWidget.visible` is private on 26.4.
- The streaming channel count in `Library.init` is clamped with `Mth.clamp(int, int, int)` up to 26.4-snapshot-2 and with `java.lang.Math.clamp(long, int, int)` from snapshot-3: `LibraryMasterBusMixin` redirects both, each optional.
- "Is the debug screen open?": use `debugEntries.isOverlayVisible()`. `showDebugScreen()` is also true while single debug lines such as FPS are shown.

Compile against every supported version before a release (`build.ps1 -McVersion …`). When adding a mixin, check that its target methods and the calls it hooks exist in each version (a `javap -c` diff of the target classes works well).

### The measurements

- `loudness.tsv`: the EBU R128 max momentary (400 ms) loudness of every vanilla sound effect file, from the 26.3 assets (asset index 34), measured with ffmpeg's `ebur128` filter. The reference level is their median, −25.6 LUFS.
- The sound rules in `DEFAULT_SOUNDS` came from comparing each sound event with its family (same kind, same action: every block "break", every mob "hurt"), with each mob's code volume (`getSoundVolume`) applied, then cutting about two thirds of the excess.
- The harshness thresholds came from measuring band-vs-rest on 400 random vanilla sounds through the default EQ, and on a server recording.
- The compressor, category levels and per-sound tweaks were tuned by ear in game; compressor candidates were simulated on a recording first.

The measuring scripts were one-off tools and aren't in the repo; the method above is enough to redo them.

### Adding a setting

1. In `ClarityConfig`: a field, a default, a getter and setter, a read in `load()` through `bool`/`num`/`section` (so a missing key is written back to the file) and a write in `save()`.
2. If it changes the sound for people who already have a config, bump `CONFIG_VERSION` and extend the migration in `load()`.
3. In the GUI: a control in the right tab. Tuning controls go through `addTuning(...)`, so they're locked like the others.

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
