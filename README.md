# 📦 Advanced Delivery Drones

**Version 1.0.4**

Licensed under the [MIT License](LICENSE).

Physical drone deliveries for Minecraft Paper servers: visible flight, package inventories, sockets, animal transport, blacklists, Discord webhooks, and fully configurable GUIs.

> **🗺️ Roadmap**
> - ✅ **Hierarchical permission system** — fine-grained permissions for all commands
> - ✅ **Feature toggles** — enable/disable sockets and player-to-player deliveries
> - ✅ **Translation files** — `languages/*.yml` (de_DE, en_EN, es_ES, fr_FR, ru_RU, zh_CN)
> - ✅ **Cross-dimension delivery** — drones between Overworld, Nether, and End
> - ✅ **Landing improvements (1.0.1)** — elytra/airborne follow, pre-landing safety check, distance-based landing notification
> - ✅ **Performance (1.0.2)** — cached landing spots, optimized ground scan, velocity-based cruise flight
> - ✅ **Cooldown system (1.0.3)** — configurable send cooldowns for players and sockets
> - ✅ **Self-send blocking (1.0.4)** — prevent sending to yourself and own sockets
> - 📋 **Future:** Advanced logistics, multi-target routing, further performance tuning

---

## ✨ Features

### 🚁 Physical drone flight
- Armor-stand drone with custom skull texture flies from sender to target in real time
- **Launch animation** (optional): rise, spin, particles, and sound (`launch-animation.*`)
- **Startup phase**: slow acceleration for `startup-seconds` at `startup-speed`
- **Cruise**: main speed via `speed` (blocks per tick)
- **Approach**: slower final segment inside `approach-distance` at `approach-speed`
- **Smooth landing**: eased descent into the delivery zone (not an instant snap)
- **Pre-landing safety check**: landing spot is recomputed once before touchdown so the drone does not hover in unsafe air
- Virtual progress continues when chunks are unloaded; the drone reappears at the correct position when the chunk loads again
- Admin coordinate sends can **preload** the destination chunk before arrival
- **Cross-dimension flight**: arc path between worlds (Overworld ↔ Nether ↔ End)

### 🪂 Elytra & airborne follow (player deliveries)
- **Elytra follow** (`follow-gliding-player`): while the receiver glides, the drone tracks them in the air; when they land, the target is **relocated once** and the drone lands there
- **Airborne follow** (`follow-airborne-player-before-landing`): before landing, if the receiver is **significantly** in the air (e.g. long fall, not a normal jump), the drone follows until they touch the ground, **relocates once**, then lands — controlled by `airborne-follow-min-height` (default `5` blocks above solid ground)

### 📦 Package & inventory
- Compose GUI (9–54 slots, multiple of 9) — close to send
- Receiver opens the package after landing (right-click drone)
- Preview incoming drones read-only (`/drone preview <uuid>`) with item and animal summary
- Despawn timer starts **after** landing (`despawn-time-minutes`)
- Despawn modes: `DELETE` (remove contents) or `COLLECT` (return unopened items to sender)
- **Collection animation** when the receiver picks up the drone (`collection-animation.enabled`)

### 🎨 Hologram & boss bar
- Hologram above landed drones: recipient name + despawn countdown (`hologram.*`)
- Boss bar for the receiver: distance and ETA (`bossbar.*`)

### ✨ Particles & sound
- Configurable particle trail (including `DUST:R,G,B:SIZE`)
- Flight sound while airborne (`flight-sound`)
- Receiver beacon particles after landing

### 📍 Locate landed drones
- `/drone locate` — particle trail toward your nearest **landed** drone in the current world (`locate-particles.*`, permission `drone.locate`)

### 🚫 Receive toggle, decline & cancel
- Toggle whether you accept drones (`/drone toggle`, GUI)
- Decline all incoming deliveries — items returned to senders (`/drone decline`)
- Cancel your own outgoing drones (`/drone cancel`)
- Clickable chat links for cancel and preview

### 🔢 Limits & worlds
- Max active outgoing drones per sender (`max-active-per-sender`)
- Blocked worlds list (`blocked-worlds`)
- **Send cooldown** (`send-cooldown-seconds-player`, `send-cooldown-seconds-socket`) — configurable cooldown in seconds between drone sends
- **Self-send blocking** (`allow-send-to-self-player`, `allow-send-to-self-socket`) — prevent sending drones to yourself and your own sockets

### ⚙️ Feature toggles
- `players-enabled` — allow/disable player-to-player deliveries (GUI + commands)
- `sockets-enabled` — allow/disable socket system

### ⛔ Player blacklist
- Block specific players from sending **direct** deliveries to you
- Commands and GUI (`/drone blacklist …`)

### 🏗 Delivery sockets
- Place personal delivery points (`/drone socket place <name>`)
- Send to any socket by global name (`/drone socket send <name>`)
- Trust list: trusted players may pick up socket deliveries
- Per-socket blacklist for blocked senders
- Rename, relocate, remove; management GUI (`/drone socket manage`)
- Exact landing on socket coordinates
- **Container integration**: auto-unload into chests/hoppers near the socket (`container-integration.search-radius`, blacklist skips permanently)
- Pending returns if the socket owner is unreachable (`socket-pending-returns.yml`)

### 🐾 Animal transport
- Optional leashed-animal delivery (`carry-leashed-animals`)
- Send-mode GUI: animals only vs items
- Animals removed on send, respawned at delivery; invulnerable in transit
- Limits via `max-leashed-animals-per-drone`

### 🔔 Discord webhooks
- Optional notifications: sent, delivered, declined/cancelled, expired
- Embeds with items/animals (`discord.*` in `config.yml`)

### 🖥 GUIs (`gui.yml`)
- Main menu: send, preview, socket manage, toggle, decline, blacklist
- Player & socket target selection
- Socket edit: rename (sign UI), relocate, trust, blacklist, delete
- Blacklist add/remove player pickers
- Send-mode inventory (animals vs items)
- Live reload via `/drone reload` (no restart)

### 💾 Persistence & safety
- `players.yml` — receive toggle
- `blacklists.yml` — player blacklists
- `sockets.yml` — sockets, trust, socket blacklists
- `socket-pending-returns.yml` — stranded socket deliveries
- `drones.yml` — active in-flight/landed drones (survives restarts where applicable)
- Server restart: returns items/animals to senders, cleans orphaned entities
- Receiver offline / dimension change: outgoing drone cancelled, items returned
- Flying drone armor stand cannot be manipulated

### ⚡ Performance
- `PerformanceOptimizer`: throttling when many drones are active
- Distance-culled particles and rate-limited boss bar / hologram updates
- Chunk preload and flight path math cached per drone
- **Landing spot cache (1.0.2)**: `computeLandingFrom()` runs once per target, not every tick during smooth landing
- Faster ground scan: highest-block shortcut, center fast-path, coarse-to-fine radius search, skip unloaded chunks
- **Velocity movement (1.0.2)**: cruise flight uses `setVelocity()` (armor stand ticks only while moving); teleport only for snap/dimension/land

### 🌐 Languages
- Messages in `plugins/AdvancedDeliveryDrones/languages/` (not in `config.yml`)
- Select locale via `language:` in `config.yml` (`de_DE`, `en_EN`, `es_ES`, `fr_FR`, `ru_RU`, `zh_CN`)
- MiniMessage formatting; reload with `/drone reload`
- Landing notification shows **distance to the drone** in metres (`<distance>`), not delivery radius

---

## 🎮 Commands

Root command: **`/drone`** (players only — opens the main GUI when run without arguments).

### 📤 Sending & receiving

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone send <player>` | Open compose flow and send to a player | `drone.send.players` |
| `/drone cancel` | Cancel all your outgoing drones | `drone.cancel` |
| `/drone preview <uuid>` | Preview an incoming drone (read-only) | `drone.preview` |
| `/drone toggle` | Enable/disable receiving drones | `drone.toggle` |
| `/drone decline` | Decline all incoming drones | `drone.decline` |
| `/drone locate` | Particle trail to nearest landed drone (this world) | `drone.locate` |

### ⛔ Player blacklist

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone blacklist` | Open blacklist GUI | `drone.blacklist` |
| `/drone blacklist player add [player]` | Block a player (GUI if name omitted) | `drone.blacklist.player.add` |
| `/drone blacklist player remove [player]` | Unblock a player | `drone.blacklist.player.remove` |
| `/drone blacklist player list` | List blocked players | `drone.blacklist.player.list` |

### 🏗 Sockets

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone socket place <name>` | Place a socket at your location | `drone.socket.place` |
| `/drone socket remove <name>` | Remove your socket | `drone.socket.remove` |
| `/drone socket list` | List your sockets | `drone.socket.list` |
| `/drone socket send <name>` | Send to a socket (global name) | `drone.socket.send` |
| `/drone socket manage` | Socket management GUI | `drone.socket.manage` |
| `/drone socket rename <old> <new>` | Rename a socket | `drone.socket.rename` |
| `/drone socket trust <socket> <player>` | Allow pickup on your socket | `drone.socket.trust` |
| `/drone socket untrust <socket> <player>` | Revoke trust | `drone.socket.untrust` |
| `/drone socket blacklist add <socket> [player]` | Block sender for a socket | `drone.socket.blacklist` |
| `/drone socket blacklist remove <socket> [player]` | Unblock sender | `drone.socket.blacklist` |
| `/drone socket blacklist list <socket>` | List socket blacklist | `drone.socket.blacklist` |

### 🛠 Admin

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone admin send <x> <y> <z> [world]` | Send to coordinates (admin as receiver) | `drone.admin.send` |
| `/drone list` | List active drones + teleport links | `drone.admin.list` |
| `/drone reload` | Reload `config.yml`, `gui.yml`, and language files | `drone.admin.reload` |

Parent permissions (`drone.send`, `drone.use`, `drone.socket`, `drone.admin`, `drone.blacklist`) grant their children — see `plugin.yml`.

---

## 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `drone.send` | Send and cancel drones (parent) | `true` |
| `drone.send.players` | Send to players | `true` |
| `drone.cancel` | Cancel outgoing drones | `true` |
| `drone.use` | Preview, toggle, decline, blacklist (parent) | `true` |
| `drone.preview` | Preview incoming drones | `true` |
| `drone.toggle` | Toggle receiving drones | `true` |
| `drone.decline` | Decline incoming drones | `true` |
| `drone.locate` | Locate landed drones | `true` |
| `drone.blacklist` | Blacklist management (parent) | `true` |
| `drone.blacklist.player.*` | Add / remove / list player blacklist | `true` |
| `drone.socket` | All socket commands (parent) | `true` |
| `drone.socket.*` | Individual socket subcommands | `true` |
| `drone.admin` | Admin commands (parent) | OP |
| `drone.admin.send` | Admin coordinate send | OP |
| `drone.admin.list` | List active drones | OP |
| `drone.admin.reload` | Reload configs | OP |

---

## ⚙️ Configuration

| File | Purpose |
|------|---------|
| `config.yml` | Flight, particles, sounds, hologram, boss bar, Discord, feature toggles, follow settings |
| `gui.yml` | Menu layouts, items, titles |
| `languages/*.yml` | All player-facing messages (MiniMessage) |
| `players.yml` | Per-player receive toggle (auto-generated) |
| `blacklists.yml` | Player blacklists (auto-generated) |
| `sockets.yml` | Sockets, trust, socket blacklists (auto-generated) |
| `socket-pending-returns.yml` | Pending socket returns (auto-generated) |
| `drones.yml` | Active drones (auto-generated) |

Key `settings.drone` options: `speed`, `startup-speed`, `startup-seconds`, `approach-speed`, `approach-distance`, `delivery-radius`, `despawn-time-minutes`, `despawn-mode`, `max-active-per-sender`, `carry-leashed-animals`, `max-leashed-animals-per-drone`, `max-sockets-per-player`, `players-enabled`, `sockets-enabled`, `follow-gliding-player`, `follow-airborne-player-before-landing`, `airborne-follow-min-height`, `blocked-worlds`, particles, `flight-sound`, `inventory-size`, `hologram.*`, `bossbar.*`, `locate-particles.*`, `container-integration.*`, `collection-animation.enabled`, `launch-animation.*`.

---
