# OnTime ⏱️

A powerful and flexible timer mod for Minecraft. Create custom timers with on-screen display and command execution — ideal for minigames, events and automated server tasks.

<div align="center">

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/ontime?style=for-the-badge&logo=modrinth&label=Modrinth&color=00AF5C&logoColor=white)](https://modrinth.com/mod/ontime)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1478348?style=for-the-badge&logo=curseforge&label=CurseForge&color=f16a20&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/ontime)
[![Modrinth Version](https://img.shields.io/modrinth/v/ontime?style=for-the-badge&logo=modrinth&label=Latest&color=00AF5C&logoColor=white)](https://modrinth.com/mod/ontime)
[![License](https://img.shields.io/badge/License-CC0-lightgrey?style=for-the-badge)](LICENSE)
[![Fabric](https://img.shields.io/badge/Fabric-1.20.1%20%7C%201.21.1--1.21.10-dbd0b4?style=for-the-badge)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1--1.21.10-f16a20?style=for-the-badge)](https://neoforged.net)
[![Forge](https://img.shields.io/badge/Forge-1.20.1-e04e14?style=for-the-badge)](https://minecraftforge.net)
[![Environment](https://img.shields.io/badge/Env-Client%20%26%20Server-4a90d9?style=for-the-badge)](https://modrinth.com/mod/ontime)

</div>

---

## ✨ Features

### 🕐 Timer System
- Create and manage multiple named timers
- **Countdown** and **count-up** modes
- Start, pause, resume, stop, reset and remove timers at any time
- Add or override time on active timers
- Timers persist across server restarts and auto-resume where they left off
- **Repeat mode** — loop a timer a set number of times or infinitely
- **Timer sequences** — chain timers so the next starts automatically when the current finishes
- **Scoreboard conditions** — finish a timer early when a scoreboard objective reaches a target value
- **Finish command** — view or update a timer's command at any time with `/timer command`

### 🖥️ Server-Controlled Display
- Real-time on-screen overlay synced to all connected players
- **10 position presets**: Boss Bar, Action Bar, Top/Center/Bottom variants, and Custom
- Per-player visibility control — admins can show or hide the timer for specific players
- Adjustable scale (0.1× to 5.0×)
- Dynamic color gradient based on remaining time (configurable thresholds and colors)
- All display settings are managed server-side and pushed to clients automatically
- **Active timer synced to scoreboard** — `ontime_active` objective updated every tick, usable with `/execute if score`

### 🔊 Sound
- Configurable tick sound, volume and pitch via `/timer sound`
- Per-player sound toggle managed by admins via `/timer silent`

### ⚡ Command Execution
- Attach any server command to a timer, executed automatically on completion
- **Placeholder system**: use `{name}`, `{time}`, `{seconds}`, `{ticks}`, `{target}`, `{mode}` in commands

### 📋 History Log
- Every timer completion is recorded in `config/ontime/history.json` with name, duration, mode, command and timestamp

### ⚙️ Configuration
- In-game config screen via **Cloth Config** (and **ModMenu** on Fabric)
- JSON config files in `config/ontime/`
- Optional **WebSocket server** for external integrations (panels, Discord bots) — enable in `config.json`

### 🔌 API & Integrations
- **OnTime API** — create, query, control timers and register event callbacks (`onStart`, `onFinish`, `onPause`, `onResume`, `onTick`)
- **External finish conditions** — register a custom `Supplier<Boolean>` that can stop a timer from another mod
- **Custom renderer API** — replace the timer HUD renderer entirely from a client-side mod
- **LuckPerms support** — per-node permission control for every subcommand, with OP-level fallback
- **Entrypoint system** — Fabric entrypoints and NeoForge/Forge IMC
- **Placeholder API** integration (Fabric)

---

## 🎮 Quick Command Reference
```
/timer create <name> <h> <m> <s> [countUp] [command]
/timer start <name>
/timer pause
/timer stop
/timer reset [name]
/timer remove <name>
/timer set <name> <h> <m> <s>
/timer add <name> <h> <m> <s>
/timer command <name> [command]
/timer repeat <name> [count]
/timer sequence <name> [nextName|clear]
/timer condition <name> <objective> <score> [target|clear]
/timer list
/timer hide [targets]
/timer silent [targets]
/timer position <preset>
/timer sound <soundId> [volume] [pitch]
/timer scale <value>
/timer help [page|command]
```

> All commands require operator permission level 4 (or a LuckPerms node if installed).


---

## 🌐 Localization

Available in **English**, **Spanish (AR / ES / MX)**.

---

## 💬 Links

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/ontime)
[![Wiki](https://img.shields.io/badge/Docs-Wiki-0969da?style=for-the-badge&logo=github&logoColor=white)](https://github.com/MateoF024/OnTime/wiki)
[![Issues](https://img.shields.io/badge/Report-Issues-red?style=for-the-badge&logo=github&logoColor=white)](https://github.com/MateoF024/OnTime/issues)

---

<div align="center">
Created by <b>MateoF24</b> — Licensed under CC0
</div>
