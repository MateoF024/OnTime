# OnTime Mod - Changelog

## Version 5.0.0

### Changed

- **Breaking:** a command is a command and a pause, not a string. `TimerDefinition`
  reports `finishCommands` and `scheduledCommands` as `TimedCommand` rather than
  `String`. The server-wide `commandDelayTicks` setting and the per-timer copy of
  it are gone with it: one figure for a whole timer could only ever be right for
  one pair of its commands.

- **Breaking:** "all of these hold" means at the same moment. Conditions that
  have a state behind them are asked rather than remembered: being in a
  dimension, holding an advancement, having completed a quest or claimed a
  reward, being online. Each is true while it is true and stops when it stops,
  so an "and" of two of them asks for both at once — one player can no longer
  satisfy "in the Nether and in the End" by visiting them in turn. Leaving,
  dying and respawning leave nothing behind to ask about, so they are true for
  the one pass that reads them: two of those in one alternative have to land in
  the same tick.
- **Breaking:** a condition no longer carries `latched` and a group no longer
  carries `windowMillis`. The latch is what made an "and" mean "both happened
  at some point"; the window was its bound and nothing ever set it. Both are
  dropped from the stored shape and from `Condition`.
- **Breaking:** the FTB quest poller, the trigger registry and the trigger
  evaluator are gone. They were the pre-5.0.0 engine, and nothing read what
  they wrote: FTB quests and rewards are polled per player by the condition
  engine, which respects who the condition is watching. Nothing in the API
  referred to them.

- The event feed is a real WebSocket. Browsers and the `ws` libraries of Node
  and Python can connect to it now; the plain TCP line protocol still works on
  the same port.
- **Breaking:** the event feed requires a token. A TCP consumer must send it as
  its first line, before anything is delivered:

  ```
  printf '%s
' "$TOKEN" | nc localhost 25581
  ```

  A browser passes it as `?t=<token>` or as the `ontime.token.<token>`
  subprotocol. The token is printed to the server console on every start.
- The event feed can be bound to one interface with `webSocketBindAddress`,
  the same way the web panel already could.
- The web panel no longer serves `/api/history`. The history is still available
  through `/timer history`.

### Fixed

- Escape steps back one thing rather than closing the panel: a warning answers
  itself with Cancel, the advanced editor goes back to the list, and a tab with
  unapplied changes asks the same question the Exit button asks. It used to
  take the whole panel with it, unapplied changes and all.
- Applying no longer makes the value flicker. What was typed stayed on screen
  until the server's answer arrived; it was being dropped the instant the
  request was sent, so the old figure came back for the moment in between.
- The trigger state file only ever grew. Anything remembered about a timer that
  no longer exists is dropped when the file is written and again when it is
  read, so a file left behind by an earlier version is cleaned on first load.
- Every tooltip follows one convention: no colour at the front, grey for the
  qualifier, yellow for a value you type or pick, red only where something is
  destroyed. A button that appears in two places says the same thing from one
  key -- fourteen keys held a sentence that was already written elsewhere.

- A warning is the dimming and its words, with no panel drawn on top. The
  screen behind it is already dimmed to near black, so the box was a second
  frame around something already separate — and its edges were what the text
  kept running into.
- Stopping a timer and stopping one of its executions are one warning, worded
  once, naming what it ends and how many. They were two dialogs with different
  words for the same act.
- The copy of a timer is named the way a file system names one: the original
  keeps its name and each copy takes the lowest free number, so with "Timer"
  and "Timer (2)" about, the next is "Timer (1)".
- A dialog forgets what was typed into it when it closes as well as when it
  opens.
- Copying the same timer twice offers a name both times. The buttons on a
  list row went through the same call that picks and unpicks a timer, so the
  second press unpicked it and the dialog had no timer to name a copy after.
- The placement screen's warning lost its box too. It was the one left.
- While a timer is being filled in, the third button is Cancel and it leaves
  the creation. It said Discard, and pressing it cleared the complaint about
  the missing name and nothing else, as many times as you liked.

- Starting a timer "one each" for everyone did nothing and said "already
  running for those players, or its slot is taken". The API refuses a global
  audience in that mode because it has no player list to expand; the panel
  expands it to whoever is online first.
- Stop, on an execution, asks before ending it. Stop all always has.
- The runs page rebuilt itself on every snapshot, once a second, whether
  anything about the list had changed or not. That threw away every widget --
  and every tooltip counting down to its own appearance, which is why they
  blinked in step with the timer. It rebuilds when an execution appears,
  disappears or changes state.
- Clicking away from the command box and back again left it dead. Clearing the
  flag on a widget does not tell the screen to stop pointing at it, so the
  second click was landing on the box the screen already believed was focused.
- A command box opened and left alone no longer keeps whatever the completion
  list put in it on the way past. It keeps what was typed, and Tab counts as
  typing, because taking a suggestion is a decision.

### Added

- The trigger page is two fixed headings, "Starts it when..." and "Ends it
  when...", always both. Under a heading sit alternatives, any one of which is
  enough; inside an alternative everything has to hold at once. Adding a
  condition asks four questions in game, one at a time, and each answer
  explains itself; in the web panel it writes a sentence, in place, where the
  condition will land. Answering the last one adds it -- there is no summary
  page to press through.
- The command box is the command block's. Completions come from the dispatcher
  the server sent this client, so they are that server's actual commands --
  another mod's and a datapack's functions included. The text is coloured
  argument by argument the way the chat line colours it, whatever brigadier
  could not make sense of goes red from the point it gave up, and Add stays
  dead until the whole command parses.
- The execution panel lists what starts or ends a timer, as the same tree the
  editor draws and with nothing to press. A heading with nothing under it is
  left out, the way a timer with no commands has no command section.
- Everything under a run's actions scrolls: who is watching, what it runs and
  what starts or ends it, in one list with a bar that appears only while
  something is out of view. Long lists used to stop at the bottom of the pane
  and say "+3 more", which is a way of saying there is more without offering
  it.
- Suggestions appear the moment a field is focused, in alphabetical order,
  rather than waiting for a first letter. Selector fields offer the five
  selectors, and the arguments one takes once a bracket is open.
- "Stop all" sits at the foot of the list it acts on, and the list gives up the
  room for it.
- The trigger page draws its tree: a spine down each heading and a stub into
  every row under it, redrawn as conditions are added and removed. A spine
  never crosses between the two headings.
- A line too long for its row is cut short and carries the whole of it in
  vanilla's own tooltip.
- The boxes that add a command sit above the list they add to: the command
  across the whole line, the four numbers under it, each with its whole name
  written above rather than a letter inside. A box with its own name in it
  is empty and looks filled.
- Commands are drawn as a tree wherever they are listed: a time reading, or
  "At the end", with the commands that fire at it hanging off it. Every row
  used to repeat the reading beside the command, so five commands at the end
  said "At the end" five times and nothing said they were one batch.
- The advanced editor is the two things that are lists: commands and
  triggers. Titles, repeating and handing over are values like any other and
  sit with the rest of them in the column beside the list, which is also the
  only place with an Apply. Inside the advanced editor there is no Exit at
  all -- Back stands where it stood.
- Typed values are checked as they are typed, on both surfaces and by the same
  rules: an id has to be an id, a selector a selector, and a list of names a
  list of names. Advancements, dimensions and the players online complete as
  you type, from what the server actually has.
- Every command carries its own pause: how long to wait after it before the
  next one in the same batch. Set it as the command is added, on either
  surface, or afterwards with `/timer commands <name> delay <index> <ticks>`.
  Zero, which is what a command starts with, runs the batch together in one
  tick.
- The counter can hide itself while it waits out a cooldown, so a timer between
  repeats no longer reads as a timer that has broken. On by default.
- Advancement fields complete as you type, from the list the server holds, the
  same way the commands complete.
- The web panel is rebuilt: cards, light and dark themes, a language selector,
  and progress bars in the same colour the counter has in game.
- The web panel can do everything the in-game screen and the commands can do.
- The address `/timer webpanel` gives you is clickable in chat.
- Clicking a timer in the web panel opens its editor; clicking a running one
  opens a read-only summary of everything about it.
- Web panel clocks are predicted between updates, so they no longer read a
  second behind the counters in game.
- Consumers receive a `HELLO` message on connecting, listing everything already
  running, so one that starts halfway through a countdown knows it is there.
- Events carry `runId`, `scope` and `audienceSize` alongside the fields they
  always had.
- A limit on how many consumers may be connected at once, and a lockout for
  addresses that keep guessing the token.

## Version 4.0.0

### Added

- Minecraft 1.21.5 support is back (Omitted in 3.0.0)
- Minecraft 1.21.11 support
- Minecraft 26.1.X support
- Minecraft 26.2 support
- Timer titles: decorative text above, below, left or right of the counter, as plain text or tellraw-style JSON (/timer title)
- Scheduled commands: run commands at intermediate times and several commands per point, on top of the classic finish command (/timer commands)
- Config option to add a delay between commands that run as a sequence (Command Delay)

### Changed
- License changed from CC0 to MIT
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