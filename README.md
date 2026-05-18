# 📦 Advanced Delivery Drones

Licensed under the [MIT License](LICENSE).

Physical drone deliveries for Minecraft Paper servers: visible flight, package inventories, sockets, animal transport, blacklists, Discord webhooks, and fully configurable GUIs.

> **🗺️ Roadmap:** Dedicated translation files (locale bundles) and a finer-grained permission system are planned for a future release. Until then, all player-facing text lives in `config.yml` / `gui.yml`, and permissions are grouped as documented below.

---

## ✨ Features

### 🚁 Physical drone flight
- Armor-stand drone with custom skull texture flies from sender to target in real time
- **Launch animation** (optional): rise, spin, particles, and sound (`launch-animation.*`)
- **Startup phase**: slow acceleration for `startup-seconds` at `startup-speed`
- **Cruise**: main speed via `speed` (blocks per tick)
- **Approach**: slower final segment inside `approach-distance` at `approach-speed`
- **Smooth landing**: eased descent into the delivery zone (not an instant snap)
- Virtual progress continues when chunks are unloaded; the drone reappears at the correct position when the chunk loads again
- Admin coordinate sends can **preload** the destination chunk before arrival

### 📦 Package & inventory
- Compose GUI (9–54 slots, multiple of 9) — close to send
- Receiver opens the package after landing (right-click drone)
- Preview incoming drones read-only (`/drone preview <uuid>`) with item and animal summary
- Despawn timer starts **after** landing (`despawn-time-minutes`)
- Despawn modes: `DELETE` (remove contents) or `COLLECT` (return unopened items to sender)

### 🎨 Hologram & boss bar
- Hologram above landed drones: recipient name + despawn countdown (`hologram.*`)
- Boss bar for the receiver: distance and ETA (`bossbar.*`)

### ✨ Particles & sound
- Configurable particle trail (including `DUST:R,G,B:SIZE`)
- Flight sound while airborne (`flight-sound`)
- Receiver beacon particles after landing

### 🚫 Receive toggle, decline & cancel
- Toggle whether you accept drones (`/drone toggle`, GUI)
- Decline all incoming deliveries — items returned to senders (`/drone decline`)
- Cancel your own outgoing drones (`/drone cancel`)
- Clickable chat links for cancel and preview

### 🔢 Limits & worlds
- Max active outgoing drones per sender (`max-active-per-sender`)
- Blocked worlds list (`blocked-worlds`, Nether/End by default)

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
- Pending returns if the socket owner is unreachable (`socket-pending-returns.yml`)

### 🧱 Socket structures (builder tool)
- Select a region with the structure tool (`/drone socket select`, corner clicks with carrot on a stick)
- Save templates: `create` → `confirm <name>` / `cancel`
- Stored in `socket_structures.yml` (preview via block displays)

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
- Server restart: returns items/animals to senders, cleans orphaned entities
- Receiver offline / dimension change: outgoing drone cancelled, items returned
- Flying drone armor stand cannot be manipulated

### ⚡ Performance
- Throttled particles (distance culling, drone count)
- Chunk preload and boss bar updates are rate-limited
- Flight path math is cached per drone

### 🌐 Messages & formatting
- All strings in `config.yml` (MiniMessage)
- GUI labels in `gui.yml`
- **Planned:** separate translation/locale files instead of a single config

---

## 🎮 Commands

Root command: **`/drone`** (players only — opens the main GUI when run without arguments).

### 📤 Sending & receiving

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone send <player>` | Open compose flow and send to a player | `drone.send` |
| `/drone cancel` | Cancel all your outgoing drones | `drone.send` |
| `/drone preview <uuid>` | Preview an incoming drone (read-only) | `drone.use` |
| `/drone toggle` | Enable/disable receiving drones | `drone.use` |
| `/drone decline` | Decline all incoming drones | `drone.use` |

### ⛔ Player blacklist

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone blacklist` | Open blacklist GUI | `drone.use` |
| `/drone blacklist player add [player]` | Block a player (GUI if name omitted) | `drone.use` |
| `/drone blacklist player remove [player]` | Unblock a player | `drone.use` |
| `/drone blacklist player list` | List blocked players | `drone.use` |

### 🏗 Sockets

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone socket place <name>` | Place a socket at your location | `drone.socket` |
| `/drone socket remove <name>` | Remove your socket | `drone.socket` |
| `/drone socket list` | List your sockets | `drone.socket` |
| `/drone socket send <name>` | Send to a socket (global name) | `drone.socket` |
| `/drone socket manage` | Socket management GUI | `drone.socket` |
| `/drone socket rename <old> <new>` | Rename a socket | `drone.socket` |
| `/drone socket trust <socket> <player>` | Allow pickup on your socket | `drone.socket` |
| `/drone socket untrust <socket> <player>` | Revoke trust | `drone.socket` |
| `/drone socket blacklist add <socket> [player]` | Block sender for a socket | `drone.socket` |
| `/drone socket blacklist remove <socket> [player]` | Unblock sender | `drone.socket` |
| `/drone socket blacklist list <socket>` | List socket blacklist | `drone.socket` |
| `/drone socket select` | Get structure selection tool | `drone.socket` |
| `/drone socket create` | Capture selected region | `drone.socket` |
| `/drone socket confirm <name>` | Save structure template | `drone.socket` |
| `/drone socket cancel` | Cancel structure capture | `drone.socket` |

### 🛠 Admin

| Command | Description | Permission |
|--------|-------------|------------|
| `/drone admin send <x> <y> <z> [world]` | Send to coordinates (admin as receiver) | `drone.admin` |
| `/drone list` | List active drones + teleport links | `drone.admin` |
| `/drone reload` | Reload `config.yml` and `gui.yml` | `drone.admin` |

---

## 🔐 Permissions

Current permission nodes (more granular permissions are planned):

| Permission | Description | Default |
|------------|-------------|---------|
| `drone.send` | Send and cancel drones | `true` |
| `drone.use` | Toggle, decline, preview, player blacklist | `true` |
| `drone.socket` | All socket commands and structure tools | `true` |
| `drone.admin` | Admin send, list, reload | OP |

---

## ⚙️ Configuration

| File | Purpose |
|------|---------|
| `config.yml` | Flight, particles, sounds, hologram, boss bar, Discord, messages |
| `gui.yml` | Menu layouts, items, titles |
| `players.yml` | Per-player receive toggle (auto-generated) |
| `blacklists.yml` | Player blacklists (auto-generated) |
| `sockets.yml` | Sockets, trust, socket blacklists (auto-generated) |
| `socket-pending-returns.yml` | Pending socket returns (auto-generated) |
| `socket_structures.yml` | Saved structure templates (auto-generated) |

Key `settings.drone` options: `speed`, `startup-speed`, `startup-seconds`, `approach-speed`, `approach-distance`, `delivery-radius`, `despawn-time-minutes`, `despawn-mode`, `max-active-per-sender`, `carry-leashed-animals`, `max-leashed-animals-per-drone`, `max-sockets-per-player`, `blocked-worlds`, particles, `flight-sound`, `inventory-size`, `hologram.*`, `bossbar.*`, `collection-animation.enabled`, `launch-animation.*`.

---

## 🔨 Build

```bash
mvn package
```

Output: `target/advanced-delivery-drones-1.0.0.jar`
