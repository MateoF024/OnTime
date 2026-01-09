# OnTime

A Fabric mod for Minecraft 1.21.1 that allows you to create custom timers with on-screen display and execute commands when they complete.

## Features

- **Multiple Timers**: Create and manage multiple named timers
- **Count Up & Countdown**: Support for both counting modes
- **On-Screen Display**: Real-time timer display at the top of the screen with color coding
- **Command Execution**: Execute any command when a timer completes
- **Persistent Storage**: Timers are saved and restored across server restarts
- **Client Synchronization**: Smooth interpolation and proper sync even with variable TPS
- **Sound Feedback**: Optional tick sounds (can be toggled per timer)
- **Personal Visibility**: Each player can hide/show the timer display
- **Server Management**: Auto-pause on shutdown, auto-resume on startup
- **Configurable**: Extensive configuration options for permissions, limits, and colors

## Commands

All commands require operator permission level 2 by default (configurable).

### Basic Commands

- `/timer create <name> <hours> <minutes> <seconds> [countUp]`
    - Create a new timer (default is countdown)
    - Example: `/timer create speedrun 0 30 0 true` (30-minute count-up timer)

- `/timer start <name>`
    - Start or resume a timer

- `/timer pause`
    - Pause the currently running timer

- `/timer stop`
    - Stop and reset the current timer to its default value

- `/timer reset [name]`
    - Reset a timer to its default value without stopping it
    - Without name: resets the active timer
    - With name: resets the specified timer

- `/timer remove <name>`
    - Delete a timer permanently

### Time Management

- `/timer set <name> <hours> <minutes> <seconds>`
    - Set a timer to a specific time

- `/timer add <name> <hours> <minutes> <seconds>`
    - Add time to an existing timer

### Display & Sound

- `/timer hide [targets]`
    - Toggle timer visibility for yourself or specified players
    - Examples:
        - `/timer hide` - Toggle for yourself
        - `/timer hide @a` - Toggle for all players
        - `/timer hide @p` - Toggle for nearest player
        - `/timer hide PlayerName` - Toggle for specific player

- `/timer silent`
    - Toggle tick sounds for the active timer

### Information

- `/timer list`
    - Display all timers with their status

## Configuration

Configuration file is located at `config/ontime/config.json` and is automatically created on first run.

### Default Configuration

```json
{
  "requiredPermissionLevel": 2,
  "maxTimerSeconds": 86400,
  "colorHigh": "#FFFFFF",
  "colorMid": "#FFFF00",
  "colorLow": "#FF0000",
  "thresholdMid": 30,
  "thresholdLow": 10
}
```

### Configuration Options

- **requiredPermissionLevel** (0-4): Permission level required to use `/timer` commands
    - 0: All players
    - 1: Moderators (bypass spawn protection)
    - 2: Game masters (default, can use cheats)
    - 3: Administrators (can manage server)
    - 4: Owners (can use `/stop`)

- **maxTimerSeconds** (integer): Maximum allowed timer duration in seconds
    - Default: 86400 (24 hours)

- **colorHigh** (hex color): Color for timer when above mid threshold
    - Default: `#FFFFFF` (white)

- **colorMid** (hex color): Color for timer between low and mid thresholds
    - Default: `#FFFF00` (yellow)

- **colorLow** (hex color): Color for timer below low threshold
    - Default: `#FF0000` (red)

- **thresholdMid** (0-100): Percentage threshold for mid color
    - Default: 30

- **thresholdLow** (0-100): Percentage threshold for low color
    - Default: 10

### Color System

The timer display changes color based on remaining time percentage:
- **Countdown timers**: 100% (start) → 0% (end)
- **Count-up timers**: 100% (start) → 0% (end, approaching limit)

Color transitions:
- Above 30%: White
- 10-30%: Yellow (warning)
- Below 10%: Red (critical)

## Timer Data Storage

Timers are stored in `config/ontime/timers.json` with the following information:
- Timer name and configuration
- Current tick count
- Target duration
- Running state
- Command to execute (if configured)
- Silent mode setting
- Auto-resume flag for server restarts

Player preferences (timer visibility) are stored in `config/ontime/player_preferences.json`.

## Advanced Features

### Command Execution

You can manually edit `config/ontime/timers.json` to add a command that executes when the timer completes:

```json
{
  "timers": [
    {
      "name": "event",
      "command": "say The event has started!",
      ...
    }
  ]
}
```

The command is executed with permission level 4 (server console level).

### Auto-Resume on Restart

When a timer is running and the server shuts down:
1. The timer is automatically paused
2. Current state is saved
3. On server restart, the timer auto-resumes from where it left off

This ensures no time is lost during planned maintenance or crashes.

## Localization

OnTime includes translations for:
- English (en_us)
- Spanish - Argentina (es_ar)
- Spanish - Spain (es_es)
- Spanish - Mexico (es_mx)

## Technical Details

- **Minecraft Version**: 1.21.1
- **Mod Loader**: Fabric
- **Fabric API**: Required
- **Java Version**: 21+
- **Environment**: Universal (client + server)

## Building from Source

```bash
./gradlew build
```

The compiled mod will be in `build/libs/`.

## License

This mod is available under the CC0 license.

## Credits

Created by MateoF24