# OnTime ⏱️

A powerful and flexible timer mod for Minecraft that allows you to create custom timers with on-screen display and command execution capabilities. Perfect for minigames, events, speedruns, or automated server tasks.

[![Environment](https://img.shields.io/badge/Environment-Client%20%26%20Server-blue?style=flat-square)](https://modrinth.com/mod/ontime)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/ontime?style=flat-square&logo=modrinth&label=downloads&color=00AF5C)](https://modrinth.com/mod/ontime)
[![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/ontime?style=flat-square&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/ontime)

---

## 📋 Features

### Core Features
- **Multiple Timers** - Create and manage unlimited named timers
- **Dual Modes** - Support for both countdown and count-up timers
- **On-Screen Display** - Real-time timer visualization with dynamic color coding
- **Command Execution** - Execute any server command when a timer completes
- **Persistent Storage** - Timers are automatically saved and restored across server restarts
- **Client Synchronization** - Smooth interpolation and proper sync even with variable TPS
- **Auto-Resume** - Timers automatically pause on shutdown and resume on startup

### Customization
- **Sound Feedback** - Configurable tick sounds with volume and pitch control
- **Personal Visibility** - Each player can independently show/hide the timer display
- **Position Presets** - 10 pre-configured positions plus custom positioning
- **Color Themes** - Customizable color gradients based on remaining time
- **Permission System** - Granular control over who can use each feature

### Advanced Features
- **Placeholder System** - Use dynamic placeholders in commands (e.g., `{name}`, `{time}`, `{ticks}`)
- **Silent Mode** - Toggle sound effects globally or per-player
- **Multi-Loader Support** - Works on Fabric, NeoForge, and Legacy Forge
- **Multi-Version** - Compatible with Minecraft 1.20.1 through 1.21.10
- **Localization** - Available in English, Spanish (AR/ES/MX)

---

## 🎮 Usage

### Basic Commands

All commands start with `/timer` and require operator permission level 2 by default (configurable).

#### Timer Management

```bash
# Create a new timer
/timer create <name> <hours> <minutes> <seconds> [countUp] [command]

# Examples:
/timer create speedrun 0 30 0 true
/timer create event 1 0 0 false say The event has started!
/timer create race 0 5 0 false say {name} finished in {time}!

# Start a timer
/timer start <name>

# Pause/resume the active timer
/timer pause

# Stop and reset the active timer
/timer stop

# Reset a timer to its default value
/timer reset [name]

# Delete a timer permanently
/timer remove <name>
```

#### Time Management

```bash
# Set a timer to a specific time
/timer set <name> <hours> <minutes> <seconds>

# Add time to a timer
/timer add <name> <hours> <minutes> <seconds>
```

#### Display & Sound

```bash
# Toggle timer visibility for yourself or specific players
/timer hide [targets]
# Examples:
/timer hide              # Toggle for yourself
/timer hide @a           # Toggle for all players
/timer hide PlayerName   # Toggle for specific player

# Toggle tick sounds for yourself or specific players
/timer silent [targets]

# Set timer display position
/timer position <preset> [targets]
# Available presets: bossbar, actionbar, top_left, top_center, top_right,
#                   center, bottom_left, bottom_center, bottom_right, custom

# Configure timer sound (global)
/timer sound <soundId> [volume] [pitch]
# Examples:
/timer sound block.note_block.hat
/timer sound entity.experience_orb.pickup 0.5
/timer sound ui.button.click 0.8 1.5
```

#### Information

```bash
# List all timers with their status
/timer list

# Show help menu
/timer help [page|command]
```

---

## ⚙️ Configuration

Configuration files are located in `config/ontime/`:

### Server Configuration (`config.json`)

```json
{
  "requiredPermissionLevel": 2,
  "maxTimerSeconds": 86400,
  "colorHigh": "#FFFFFF",
  "colorMid": "#FFFF00",
  "colorLow": "#FF0000",
  "thresholdMid": 30,
  "thresholdLow": 10,
  "allowPlayersUseHide": true,
  "allowPlayersUseList": true,
  "allowPlayersUseSilent": true,
  "allowPlayersChangePosition": true,
  "allowPlayersChangeSound": true,
  "timerSoundId": "minecraft:block.note_block.hat",
  "timerSoundVolume": 0.75,
  "timerSoundPitch": 2.0
}
```

#### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `requiredPermissionLevel` | int (0-4) | `2` | Permission level required for timer commands |
| `maxTimerSeconds` | long | `86400` | Maximum timer duration in seconds (24 hours) |
| `colorHigh` | hex | `#FFFFFF` | Color when timer is above mid threshold |
| `colorMid` | hex | `#FFFF00` | Color when timer is between thresholds |
| `colorLow` | hex | `#FF0000` | Color when timer is below low threshold |
| `thresholdMid` | int (0-100) | `30` | Percentage threshold for mid color |
| `thresholdLow` | int (0-100) | `10` | Percentage threshold for low color |
| `allowPlayersUseHide` | boolean | `true` | Allow all players to use `/timer hide` |
| `allowPlayersUseList` | boolean | `true` | Allow all players to use `/timer list` |
| `allowPlayersUseSilent` | boolean | `true` | Allow all players to use `/timer silent` |
| `allowPlayersChangePosition` | boolean | `true` | Allow all players to use `/timer position` |
| `allowPlayersChangeSound` | boolean | `true` | Allow all players to use `/timer sound` |
| `timerSoundId` | string | `minecraft:block.note_block.hat` | Sound ID for timer ticks |
| `timerSoundVolume` | float (0.0-1.0) | `0.75` | Volume of timer sound |
| `timerSoundPitch` | float (0.5-2.0) | `2.0` | Pitch of timer sound |

### Client Configuration (`client-config.json`)

```json
{
  "timerX": -1,
  "timerY": 4,
  "timerScale": 1.0,
  "positionPreset": "BOSSBAR"
}
```

### Color System

The timer display automatically changes color based on remaining time percentage:

- **Countdown timers**: 100% (start) → 0% (end)
- **Count-up timers**: 100% (start) → 0% (end, approaching limit)

Color transitions:
- **Above 30%**: White (default)
- **10-30%**: Yellow (warning)
- **Below 10%**: Red (critical)

---

## 🎯 Placeholder System

When creating timers with commands, you can use the following placeholders:

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `{name}` | Timer name | `speedrun` |
| `{time}` | Formatted time | `00:15:30` |
| `{ticks}` | Current ticks | `18600` |
| `{target}` | Target ticks | `36000` |
| `{mode}` | Timer mode | `count-up` or `countdown` |
| `{seconds}` | Total seconds | `930` |

### Example

```bash
/timer create speedrun 0 30 0 true tellraw @a {"text":"Speedrun {name} completed in {time}!","color":"gold"}
```

When the timer finishes, it will execute:
```
tellraw @a {"text":"Speedrun speedrun completed in 00:30:00!","color":"gold"}
```

---

## 📦 Installation

### Requirements

- **Minecraft**: 1.20.1, 1.21.1-1.21.10
- **Mod Loader**: Fabric, NeoForge, or Legacy Forge (1.20.1)
- **Dependencies**:
    - Fabric API (Fabric)
    - Cloth Config
    - Mod Menu (Optional for Fabric)

### Installation Steps

1. Download the appropriate version for your mod loader from [Modrinth](https://modrinth.com/mod/ontime)
2. Place the `.jar` file in your `mods` folder
3. Install dependencies (Fabric API and Cloth Config)
4. Launch the game

### Mod Menu Integration

On Fabric, the mod integrates with Mod Menu to provide an in-game configuration screen.

---

## 🔧 Technical Details

### Data Storage

#### Timers (`config/ontime/timers.json`)
Stores all timer configurations including:
- Timer name and settings
- Current tick count
- Target duration
- Running state
- Associated command
- Silent mode setting
- Auto-resume flag

#### Player Preferences (`config/ontime/player_preferences.json`)
Stores per-player settings:
- Timer visibility
- Silent mode preference
- Position preset

### Auto-Resume System

When a timer is running and the server shuts down:
1. Timer is automatically paused
2. Current state is saved
3. On server restart, timer auto-resumes from where it left off

This ensures no time is lost during planned maintenance or unexpected crashes.

### Client-Server Synchronization

- Timers are synced every 20 ticks (1 second)
- Client-side interpolation for smooth display
- Handles variable TPS gracefully
- Pause detection for single-player

---

## 🌐 Localization

OnTime includes full translations for:

- 🇺🇸 English (en_us)
- 🇦🇷 Spanish - Argentina (es_ar)
- 🇪🇸 Spanish - Spain (es_es)
- 🇲🇽 Spanish - Mexico (es_mx)

---

## 🏗️ Building from Source

```bash
# Clone the repository
git clone https://github.com/MateoF24/ontime.git
cd ontime

# Build all versions
./gradlew build

# Build specific loader
./gradlew :fabric:build
./gradlew :forge:build
./gradlew :legacyforge:build
```

Compiled artifacts will be in `<loader>/build/libs/`

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Setup

1. Clone the repository
2. Import as a Gradle project in your IDE
3. Run the appropriate Gradle task for your loader
4. Make your changes
5. Test on all supported versions
6. Submit a PR

---

## 📄 License

This mod is available under the [CC0 License](LICENSE).

---

## 💬 Support

- **Issues**: [GitHub Issues](https://github.com/MateoF24/ontime/issues)
- **Modrinth**: [OnTime on Modrinth](https://modrinth.com/mod/ontime)

---

## 📊 Project Stats

![Modrinth Downloads](https://img.shields.io/modrinth/dt/ontime?style=for-the-badge&logo=modrinth&color=00AF5C)
![Modrinth Followers](https://img.shields.io/modrinth/followers/ontime?style=for-the-badge&logo=modrinth&color=00AF5C)
![GitHub Stars](https://img.shields.io/github/stars/MateoF24/ontime?style=for-the-badge&logo=github)
![GitHub Issues](https://img.shields.io/github/issues/MateoF24/ontime?style=for-the-badge&logo=github)

---

## 🙏 Credits

Created by **MateoF24**

---

<div align="center">

**⭐ If you enjoy OnTime, please consider starring the repository! ⭐**

[![Modrinth](https://img.shields.io/badge/Download%20on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth)](https://modrinth.com/mod/ontime)
[![GitHub](https://img.shields.io/badge/View%20on-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/MateoF24/ontime)

</div>