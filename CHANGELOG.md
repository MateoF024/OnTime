# OnTime Mod - Changelog

## Version 4.0.0

- Added Minecraft 1.21.11 support
- Added Minecraft 26.1 support
- Added Minecraft 26.2 support
- 1.20.1 and 1.21.1-1.21.10 stay supported and are updated to 4.0.0 (1.21.5 is not supported)
- Fixed: /timer commands now work from command blocks, the server console, datapack functions and RCON
- Fixed: NeoForge jars declared a wrong mod version
- Fixed: jars could be installed on unsupported Minecraft versions; every jar now declares its exact range
- Files are now saved atomically — a crash can no longer corrupt them
- Fewer disk writes: only the modified timer file is rewritten
- WebSocket messages are sent from a dedicated thread — a slow client can no longer slow down the server
- More command messages are translatable (en, es_ar, es_es, es_mx)
- For mod devs, 26.x only: ITimerRenderer.render receives GuiGraphicsExtractor instead of GuiGraphics
- For mod devs: custom permission providers are no longer consulted for non-player command sources

## Version 3.0.0

- Added web admin panel (/timer webpanel)
- Added dynamic time expressions (/timer expr)
- Added condition expressions (/timer condition <name> if <expr>)
- Added event triggers: player death, dimension change, advancement
- Added FTB Quests triggers: quest completion and reward claim
- Added Jade compatibility (overlays no longer overlap)
- Added cooldowns between repeats and between sequence steps
- Added per-timer storage: one JSON file per timer, migrated automatically
- Added /timer export, /timer import and /timer clone
- Fixed the on-screen counter stepping back and forth by 1 second
- Lower server overhead: fewer scoreboard updates and disk writes
- WebSocket default port moved to 25581

## Version 2.1.0

- Added timer sequences (/timer sequence)
- Added repeat mode (/timer repeat)
- Added scoreboard finish conditions (/timer condition)
- Active timer synced to the ontime_active scoreboard objective
- Added /timer command to view or update a timer's finish command
- Added history log (config/ontime/history.json)
- Added optional WebSocket server
- API: event callbacks, external finish conditions, custom HUD renderer, setTimerCommand, setTimerRepeat
- Added LuckPerms permission nodes per subcommand

## Version 2.0.0

- All display settings are now server-side and synced automatically to clients
- Commands now require op level 4
- Added /timer scale
- Added public API (OnTimeAPI) with entrypoint registration
- Added Placeholder API integration
- Rewrote client-server sync and cleaned up internals

## Version 1.2.1

- Added NeoForge support
- Fixed scale and position settings not saving
- Fixed a sync issue when pausing in singleplayer

## Version 1.2.0

- Added in-game config screen (Cloth Config, with ModMenu access)
- Added timer position and scale settings
- Added configurable display colors and thresholds
- BossBar now detects collision with the timer display

## Version 1.1.0

- Added configuration system: permissions, time limits, colors
- Added /timer hide with player selectors
- Added /timer stop and /timer reset
- Timer color changes based on remaining time
- Timer names auto-complete in commands
- Added configurable maximum timer duration
- Fixed timer desync with variable TPS
- Timers auto-resume after a server restart
- Fixed timer not being detected after server restart
