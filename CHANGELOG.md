# OnTime Mod - Changelog

## Version 4.0.0

### Added

- Minecraft 1.21.11 support
- Minecraft 26.1 support
- Minecraft 26.2 support

### Changed

- 1.20.1 and 1.21.1-1.21.10 remain supported and move to 4.0.0 (1.21.5 remains unsupported)
- For mod developers, 26.x only: `ITimerRenderer.render` receives `GuiGraphicsExtractor` instead of `GuiGraphics`
- For mod developers: custom permission providers are no longer consulted for non-player command sources

### Fixed

- `/timer` commands can now be used from command blocks, the server console, datapack functions and RCON; players still require OP or a permission node
- NeoForge jars declared a wrong mod version
- The mod could be installed on unsupported Minecraft versions; every jar now declares the exact range it supports
- Stray `\n` escape in the NeoForge mod description

### Improved

- All files are saved atomically; a crash mid-save can no longer corrupt them
- Single-timer operations rewrite only that timer's file instead of every file
- WebSocket messages are sent from a dedicated thread; a slow client can no longer stall the server
- More command messages are localizable (en, es_ar, es_es, es_mx)

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