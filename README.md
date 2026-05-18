# 📦 Advanced Delivery Drones
# MADE BY AI
A physical drone delivery system for Minecraft Paper servers.

---

## ✨ Features

### 🚁 Physical Drone Flight
- Drones fly in real time from sender to recipient through the world
- Smooth launch animation with configurable startup speed and duration
- Configurable flight speed (blocks per tick)

### 🎨 Holograms
- Hologram above each drone displays the recipient's name and a live despawn countdown
- Fully customizable format and Y-offset

### 📊 Boss Bar
- Recipient sees a real-time boss bar showing distance to the drone and ETA
- Fully customizable format

### ✨ Particle Effects
- Configurable particle trail follows the drone during flight
- Supports standard particles (e.g. `ELECTRIC_SPARK`) and custom RGB DUST particles (`DUST:R,G,B:SIZE`)
- Configurable trail length, count, and Y-offset

### 🔔 Flight Sound
- Audible flight sound while the drone is airborne
- Configurable sound (default: Elytra flying)

### 🏠 Despawn Logic
- Drones automatically land at the destination or despawn after a configurable timeout
- Two despawn modes:
  - `DELETE` — drone and contents are deleted
  - `COLLECT` — items are returned to the sender

### 🚫 Receive Toggle
- Players can enable or disable receiving drones at any time

### ↩️ Decline & Cancel
- Recipients can decline all incoming drones; items are automatically returned to the sender
- Senders can cancel their own active outgoing drones

### 🔢 Sender Limit
- Configurable maximum number of simultaneously active drones per player

### 📦 Configurable Inventory Size
- Package inventory size can be set between 9 and 54 slots (multiples of 9)

### 🌍 World Restrictions
- Specific worlds can be blocked from drone usage (Nether and End blocked by default)

### 🏗 Delivery Sockets
- Dedicated landing socket structures can be placed in the world as fixed delivery points
- Sockets support trust lists, blacklists, and renaming
- Pending returns are persisted across server restarts

### 🐾 Entity Transport
- Drones can carry animals and entities during flight
- Transported entities are invulnerable while in transit and restored to their previous state on delivery

### 📡 Chunk Handling
- Flight progress and despawn countdown continue even when the drone's chunk is unloaded
- Drone re-appears at its current progress point once the chunk reloads
- On admin-sent deliveries, the destination chunk is pre-loaded just before arrival for reliable landing

### 🔔 Discord Integration
- Optional Discord webhook notifications for deliveries

### 🌐 Translations
- All player-facing messages are fully translatable via the config
- MiniMessage formatting is supported throughout

### 🖥 GUI
- Full custom GUI for composing, previewing, and managing drone deliveries
- GUI layout and items are configurable via `gui.yml`

### ⚙️ Full Configuration
- All behavior configurable via `config.yml` and `gui.yml`
- Live config reload — no server restart required

---

## 🎮 Commands

`/drone` — Opens the main drone GUI menu.

### Player Commands

| Command | Description | Permission |
|---|---|---|
| `/drone send <player>` | Open the package inventory and send a drone to a player | `drone.send` |
| `/drone cancel` | Cancel your active outgoing drones | `drone.send` |
| `/drone preview <id>` | Preview an incoming drone's contents (read-only) | `drone.use` |
| `/drone toggle` | Enable or disable receiving drones | `drone.use` |
| `/drone decline` | Decline all incoming drones and return them to senders | `drone.use` |
| `/drone blacklist add <player>` | Block a player from sending you drones | `drone.use` |
| `/drone blacklist remove <player>` | Unblock a player | `drone.use` |
| `/drone blacklist list` | View your current drone blacklist | `drone.use` |

### Socket Commands

| Command | Description | Permission |
|---|---|---|
| `/drone socket place <name>` | Place a delivery socket structure | `drone.socket` |
| `/drone socket remove <name>` | Remove one of your sockets | `drone.socket` |
| `/drone socket list` | List all your sockets | `drone.socket` |
| `/drone socket send <socket> <player>` | Send a drone to a specific socket | `drone.socket` |
| `/drone socket manage` | Open the socket management GUI | `drone.socket` |
| `/drone socket rename <old> <new>` | Rename one of your sockets | `drone.socket` |
| `/drone socket trust <socket> <player>` | Allow a player to send to your socket | `drone.socket` |
| `/drone socket untrust <socket> <player>` | Revoke a player's trust for your socket | `drone.socket` |
| `/drone socket blacklist add <socket> <player>` | Block a player from sending to your socket | `drone.socket` |
| `/drone socket blacklist remove <socket> <player>` | Unblock a player for your socket | `drone.socket` |
| `/drone socket blacklist list <socket>` | View a socket's blacklist | `drone.socket` |

### Admin Commands

| Command | Description | Permission |
|---|---|---|
| `/drone admin send <player\|x y z world>` | Send a drone to a player or coordinates (bypasses limits) | `drone.admin` |
| `/drone list` | List all currently active drones | `drone.admin` |
| `/drone reload` | Reload config and GUI config live | `drone.admin` |

---

## 🔐 Permissions

| Permission | Description | Default |
|---|---|---|
| `drone.send` | Send drones (`/drone send`, `/drone cancel`) | `true` |
| `drone.use` | Receive management (`/drone toggle`, `/drone decline`, `/drone preview`, `/drone blacklist`) | `true` |
| `drone.socket` | All `/drone socket` subcommands | `true` |
| `drone.admin` | Admin commands (`/drone admin`, `/drone list`, `/drone reload`) | OP |

---

## 🧩 Compatibility

| Requirement | Version |
|---|---|
| Minecraft | 1.21.4 |
| Server Software | Paper (or fork) |
| Java | 21+ |
| API | Paper API 1.21.4-R0.1-SNAPSHOT |
