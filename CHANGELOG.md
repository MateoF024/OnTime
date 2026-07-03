# OnTime Mod - Changelog

## Version 4.0.0

Support for three new Minecraft versions, plus internal robustness work. No timer/config file formats changed and the public API is source-compatible on 1.21.x — worlds and integrations coming from 3.0.x just work.

### 🆕 New Minecraft versions

| Family | Loaders | Java |
|---|---|---|
| 1.21.11 | Fabric + NeoForge | 21 |
| 26.1.x | Fabric + NeoForge | 25 |
| 26.2.x | Fabric + NeoForge | 25 |

- 1.21.1–1.21.4 and 1.21.6–1.21.10 remain supported (Java 21). 1.21.5 remains unsupported.
- 1.20.1 (Fabric + Forge) stays on the 3.0.x line, maintained separately.
- Fixes the crash when 3.0.0 jars were forced onto 1.21.11+ (Mojang reworked the command permission system; OnTime now uses the new `PermissionSet` API there).

### ⚠️ Breaking changes

- **For mod developers, 26.x only:** `ITimerRenderer.render` receives `GuiGraphicsExtractor` instead of `GuiGraphics` — Mojang removed `GuiGraphics` in 26.1, so custom timer renderers must be compiled per family. On 1.21.x the signature is unchanged.
- **Strict Minecraft ranges:** every jar declares the exact range it supports and refuses to load elsewhere (intentional — previously it crashed at world load instead).
- Custom `IPermissionProvider`s are no longer consulted for non-player command sources (console, command blocks, functions, RCON always pass — see 3.0.2 below, included in this release).

### 🛠️ Improvements

- **Crash-safe saves:** all JSON files (timers, config, preferences, history) are written to a temp file and atomically moved, so a crash mid-write can no longer corrupt them. All file I/O is now explicitly UTF-8 on every platform.
- **Less disk churn:** single-timer operations now rewrite only that timer's file instead of every timer file.
- **Smoother ticks:** the WebSocket feed now sends from its own thread — a slow TCP client can no longer stall the server tick — and several per-tick polls stopped copying the timer map.
- More command feedback is localizable (en/es_ar/es_es/es_mx).

---

## Version 3.0.2 (unreleased)

### 🐛 Fixed

- **`/timer` commands failed when run from command blocks.** Command blocks execute at vanilla permission level 2, but every OnTime command required level 4, so command blocks (and, with some permission mods, the server console) were rejected. Non-player sources — server console, command blocks, datapack functions, RCON — now always pass the permission check. Players are unaffected: they still need OP (or the corresponding permission node when a permission provider such as LuckPerms is installed).
  - Note for mod developers: a custom `IPermissionProvider` registered through the OnTime API is no longer consulted for non-player sources.

---

## Version 3.0.1

Metadata hotfix — no gameplay changes.

### 🐛 Fixed

- **NeoForge builds reported the wrong mod version.** OnTime 3.0.0 for NeoForge identified itself as `2.1.0` because the version in `neoforge.mods.toml` was maintained by hand. All loader metadata (mod version and supported Minecraft range) is now generated from the build configuration, so this class of error can't happen again.
  - Note for mod developers: if your mod depends on OnTime with `versionRange="[3.0.0,)"` on NeoForge, that check failed against the mislabeled 3.0.0 jar. It works correctly from 3.0.1 onward.
- **The mod could be installed on unsupported Minecraft versions.** The Fabric jar declared `minecraft >= 1.20.1` with no upper bound, so it could be forced onto 1.21.11+ where it crashes on world load (Mojang reworked the command permission system in 1.21.11). Every jar now declares the exact range it supports and the loader will refuse to run it elsewhere:
  - Fabric/Forge 1.20.1 → `1.20.1` only
  - Fabric/NeoForge 1.21.1 → `1.21.1 – 1.21.4`
  - Fabric/NeoForge 1.21.6 → `1.21.6 – 1.21.10`
  - 1.21.5 remains unsupported.
- Removed a stray `\n` escape from the NeoForge mod description.

> Support for Minecraft 1.21.11, 26.1 and 26.2 is in development for OnTime 4.0.0.

---

## Version 3.0.0

The biggest update to OnTime so far. Major new feature areas plus a full rework of how the on-screen counter syncs with the server, finally fixing the visual ±1 second glitch.

### ✨ New

- **Web admin panel** — a built-in dashboard served by the mod itself. View timers in real time, start/pause/stop/reset, edit them, browse the history. Manage with `/timer webpanel start | stop | info`.
- **Dynamic time expressions** — set timer durations with math: `players_online * 30`, `score(my_obj, my_team) + 60`, etc. Use `/timer expr create | set | add`.
- **Condition expressions** — full boolean DSL for timer conditions: `&&`, `||`, `!`, comparators, and live values like `time_remaining`, `time_elapsed`, `players_online`, `score(...)`. Use `/timer condition <name> if <expr>` (or `if_start`).
- **Event triggers** — fire timers on vanilla events: player death, dimension change (any or specific), advancement earned. Each trigger can either start or finish the timer.
- **FTB Quests integration** — fire timers on quest completion or reward claim by ID. Auto-detected, no extra setup.
- **Jade compatibility** — the timer overlay no longer overlaps Jade; both move out of each other's way and restore cleanly.
- **Cooldowns** — add a delay between repetitions (`/timer repeat`) and between sequence steps (`/timer sequence`).
- **Per-timer storage** — every timer now lives in its own JSON file under `config/ontime/timers/`. Better for backups, version control and avoiding merge conflicts. Existing `timers.json` is migrated automatically on first load.
- **Export / Import / Clone** — share timers between worlds or instances with `/timer export`, `/timer import`, `/timer clone`.

### 🔧 Improved

- **Smooth counter rendering** — reworked the client-side prediction so the displayed second behaves like a real wall clock. The "the counter goes back and forth by 1 second" glitch is gone.
- **Lower server overhead** — scoreboard updates now happen once per second instead of every tick, and the timer-save path no longer rewrites every JSON file on every change.

### ♻️ Changed

- **Default ports** Websocket moved to **25581**, away from the heavily contested 8765.

### 🐛 Fixed

- The on-screen counter occasionally appearing to step backwards on networked play.
- Multiple unnecessary disk writes during the tick loop, which could cause micro-stutters on slow disks.
- Some redundant data in the timer sync packet that was sent every second to every player.

---

## Version 2.1.0

### New Features

**Timer Sequences**
Chain multiple timers so the next one starts automatically when the current finishes.
Use `/timer sequence <name> <nextName>` to set it up.

**Repeat Mode**
Loop a timer a fixed number of times or indefinitely.
`/timer repeat <name>` toggles infinite repeat. `/timer repeat <name> <count>` sets a limit.

**Scoreboard Finish Conditions**
Stop a timer early when a scoreboard objective reaches a value.
`/timer condition <name> <objective> <score> [target]` — supports wildcard `*` to match any online player.

**Active Timer Scoreboard Sync**
The active timer's remaining seconds are automatically written to the `ontime_active` scoreboard objective every tick, enabling vanilla `/execute if score` integrations.

**`/timer command` subcommand**
View or update the finish command of an existing timer without recreating it.

**Timer History Log**
Every timer completion is now recorded in `config/ontime/history.json` with timestamp, name, duration, mode and command.

**WebSocket Server (optional)**
An optional TCP server that broadcasts timer events (start, finish, pause, resume, tick) as JSON. Enable via `config/ontime/config.json`. Useful for Discord bots or admin panels.

### API Additions
- Event callbacks: `registerOnStart`, `registerOnFinish`, `registerOnPause`, `registerOnResume`, `registerOnTick`
- External finish conditions: `registerFinishCondition(timerName, Supplier<Boolean>)`
- FTB Quests helpers: `isTimerActive(name)`, `isAnyTimerActive()`
- Custom HUD renderer: register your own `ITimerRenderer` to replace the built-in overlay
- `setTimerCommand`, `setTimerRepeat` added to the public API

### LuckPerms Integration
Each `/timer` subcommand now has its own permission node (`ontime.command.<subcommand>`), with OP level 4 as fallback when LuckPerms is not present.

### Internal
- Reduced `ClientTimerState` duplication across loaders

---

# Version 2.0.0

This release is a major overhaul. The internal architecture has been rewritten to centralize all display logic server-side, introduce a public API, and clean up code.

---

## ⚠️ Breaking Changes

- **All display settings are now server-side.** Position, colors, scale, and sound are configured once on the server and automatically pushed to every connected client. Individual players can no longer change these settings themselves.
- Permission level for all `/timer` commands has been raised to **op level 4**.

---

## ✨ New

### `/timer scale <value>`
Sets the timer display scale globally for all players. Accepts values from `0.1` to `5.0`.

### Public API
Other mods can now interact with OnTime programmatically via `OnTimeAPI`:
- Create, remove, start, stop, pause and query timers
- Register custom command placeholders
- Receive the API instance at initialization via the entrypoint system:
    - **Fabric**: `ontime` entrypoint in `fabric.mod.json`
    - **NeoForge / Forge**: `InterModComms` with method `"register"`

### PlaceholderAPI Integration (Fabric Only)
When [Placeholder API](https://modrinth.com/mod/placeholder-api) is present, OnTime automatically registers placeholders for active and named timers: `%ontime:active_time%`, `%ontime:active_name%`, `%ontime:timer_time:<name>%`, and more. See the [wiki](https://github.com/MateoF024/ontime/wiki/Fabric-PlaceholderAPI) for the full list.

---

## 🔧 Changes

- **Client–server sync rewritten.** Display config (position, preset, colors, scale, sound) is now sent to clients as a dedicated packet on join and on every server-side config save. Clients are purely passive receivers.
- **Permission system unified.** The `hide`, `silent`, `position`, `sound`, and `scale` commands are now admin-only. Operators can still target individual players with `hide` and `silent`.
- **Codebase cleanup.** Removed redundant abstractions, dead code paths, and inconsistencies between loader implementations. Internal packet and platform layers have been simplified.

---

## Version 1.2.1

### Bug Fixes
- Added Neoforge Support
- Fixed the issue where scale and position settings were not saved after closing the game
- Fixed a synchronization issue when pausing the game in singleplayer mode

---

## Version 1.2.0

### New Features

#### In-Game Configuration Menu
- Added complete in-game configuration screen using Cloth Config API
- All settings now editable without manually editing JSON files

#### Timer Display Customization
- **Position Control**: Configure X and Y coordinates of the timer display
    - X Position: Set specific coordinate or use -1 for centered (default)
    - Y Position: Customize vertical position from top of screen (default: 4)
- **Scale Adjustment**: Change timer size from 0.1x to 5.0x (default: 1.0x)
- Real-time preview of changes in configuration screen

#### Smart BossBar Collision Detection
- BossBar now intelligently detects collision with timer display

### Configuration Categories

The new config screen is organized into three categories:

**Display**
- Timer X Position
- Timer Y Position
- Timer Scale

**Colors**
- High Color (when above mid threshold)
- Mid Color (between thresholds)
- Low Color (below low threshold)
- Mid Threshold % (default: 30%)
- Low Threshold % (default: 10%)

**Server**
- Permission Level (default: 2)
- Max Timer Seconds (default: 86400)

### Technical Changes

- Added Cloth Config API as dependency
- Added ModMenu integration for easy config access
- New mixin system for BossBar collision detection
- Enhanced ModConfig with new properties and validation
- Updated TimerRenderer to support position and scale transformations

---

## Version 1.1.0

### New Features
- **Configuration System**: Fully configurable permissions, time limits, and display colors
- **Timer Visibility Control**: `/timer hide` command with player selector support
- **Stop & Reset Commands**: Better timer lifecycle management
- **Color-Coded Display**: Timer color changes based on remaining time percentage
- **Command Suggestions**: Auto-complete timer names in commands
- **Max Time Validation**: Configurable maximum timer duration with enforcement

### Improvements
- **Enhanced Sync System**: Fixed timer desync issues with variable TPS (including `/tick rate` adjustments)
- **Robust Persistence**: Improved save/load system with active timer restoration after restarts
- **Auto-Resume**: Timers automatically resume after server restarts if they were running

### Bug Fixes
- Fixed timer not being detected after server restart