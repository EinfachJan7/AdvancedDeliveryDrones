# 📦 Advanced Delivery Drones

**Version 1.0.7**

Licensed under the [Apache-2.0 License](LICENSE).

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
> - ✅ **Custom drone models (1.0.6)** — skull fallback or custom items via Nexo, Oraxen, ItemsAdder
> - ✅ **Flight polish (1.0.7)** — precomputed routes, smooth per-tick path sync, configurable glow
> - ✅ **PlaceholderAPI (1.0.7)** — optional expansion with many placeholders and UUID → player name resolution
> - 📋 **Future:** Built-in ADD pack workflow, advanced logistics, multi-target routing

---

## ✨ Features

### 🎨 Custom drone appearance
- Default: player-head skull texture (`skull-texture`)
- Optional custom item on the armor stand helmet via `custom-model.provider`:
  - `NONE` — skull texture only
  - `NEXO`, `ORAXEN`, `ITEMSADDER` — item from the respective plugin (`item-id`)
- `glowing-enabled` — toggle outline glow on the drone entity (default `true`)

### 🚁 Physical drone flight
- Armor-stand drone flies from sender to target in real time (skull or custom helmet model)
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
- **Precomputed flight path (1.0.7)**: route geometry is built once per delivery (and rebuilt when the target moves); position per tick is sampled from the path instead of recalculating every tick
- **Smooth cruise sync (1.0.7)**: the stand is teleported to the expected path position each tick (velocity cleared, stand ticking disabled) for steady client-side motion

### 🔌 PlaceholderAPI (optional)

| | |
|---|---|
| **Identifier** | `deliverydrones` |
| **Syntax** | `%deliverydrones_<placeholder>%` |
| **Test** | `/papi parse me %deliverydrones_outgoing_count%` |

**UUID → player name:** Placeholders that refer to players return **names**, not raw UUIDs. Use `*_uuid` when you need the UUID string. Any UUID can be resolved with `%deliverydrones_playername_<uuid>%` (aliases: `name_`, `uuid_to_name_`, `player_name_`).

**Indexed placeholders:** Use `_1_`, `_2_`, … or omit the index for the first entry (`%deliverydrones_outgoing_receiver%` = `%deliverydrones_outgoing_1_receiver%`).

#### Player (viewer)

| Placeholder | Description |
|-------------|-------------|
| `can_receive` / `receive_enabled` | Can receive drones (`true` / `false`) |
| `outgoing_count` | Outgoing drones (as sender) |
| `incoming_count` | Incoming drones (as receiver) |
| `incoming_flying_count` | Incoming, still flying |
| `incoming_landed_count` | Incoming, landed |
| `active_outgoing` / `outgoing_active` | Active send slots used |
| `active_slots_max` / `max_active` | Max concurrent outgoing drones |
| `can_send` / `can_launch` | May send another drone |
| `blacklist_count` | Players on personal blacklist |
| `blacklist_names` | Blacklisted player names (comma-separated) |
| `socket_count` | Owned sockets |
| `socket_max` / `max_sockets` | Max sockets per player |
| `socket_names` | Socket names (comma-separated) |
| `socket_slots_free` | Remaining socket slots |
| `cooldown_player` / `send_cooldown_player` | Player send cooldown (config, seconds) |
| `cooldown_socket` / `send_cooldown_socket` | Socket send cooldown (config, seconds) |
| `cooldown_player_remaining` | Remaining player cooldown (seconds) |
| `cooldown_socket_remaining` | Remaining socket cooldown (seconds) |
| `pending_returns` | Pending return item stacks |
| `has_incoming` / `has_outgoing` / `has_landed_incoming` | Boolean flags |
| `nearest_landed_distance` | Distance to nearest landed incoming drone (same world) |
| `nearest_landed_world` / `_x` / `_y` / `_z` | Position of nearest landed drone |
| `nearest_landed_uuid` | Drone UUID |
| `nearest_landed_sender` / `nearest_landed_sender_name` | Sender **name** of nearest landed drone |
| `players_enabled` / `sockets_enabled` / `glowing_enabled` | Feature toggles |
| `custom_model_provider` / `custom_model_item_id` | Custom model config |
| `version` / `plugin_version` | Plugin version |
| `database_type` / `language` | Config meta |

#### Server totals

| Placeholder | Description |
|-------------|-------------|
| `total_drones` / `active_drones` | All active drones |
| `total_flying` | Flying drones |
| `total_landed` | Landed drones |

#### Resolve any player UUID to a name

| Placeholder | Description |
|-------------|-------------|
| `playername_<uuid>` | Player name for UUID (falls back to UUID if unknown) |
| `name_<uuid>` | Alias |
| `uuid_to_name_<uuid>` | Alias |
| `player_name_<uuid>` | Alias |

Example: `%deliverydrones_playername_550e8400-e29b-41d4-a716-446655440000%`

#### Outgoing / incoming drone (index)

Replace `outgoing` with `incoming` for incoming drones. Fields apply to both unless noted.

| Field | Description |
|-------|-------------|
| `sender` / `sender_name` | Sender **player name** |
| `sender_uuid` | Sender UUID |
| `receiver` / `receiver_name` | Receiver **player name** (socket owner name for socket deliveries) |
| `receiver_uuid` | Receiver UUID |
| `socket` / `socket_name` | Socket name (empty if player delivery) |
| `is_socket` | Socket delivery (`true` / `false`) |
| `uuid` / `id` | Drone UUID |
| `stand_uuid` / `entity_uuid` | Armor stand entity UUID |
| `world`, `x`, `y`, `z` | Current position |
| `target_world`, `target_x`, `target_y`, `target_z` | Target position |
| `distance` / `distance_target` | Distance to target (metres) |
| `eta` / `eta_seconds` | Estimated arrival (seconds, `0` if landed) |
| `flying` / `is_flying` | In flight |
| `landed` / `is_landed` | Landed |
| `opened` / `was_opened` | Opened by receiver |
| `animals_only` | Animals-only delivery |
| `item_count` / `items` | Filled inventory slots |
| `animal_count` / `animals` | Animals in transit |
| `despawn_seconds` / `despawn_remaining` | Seconds until despawn (landed only) |
| `distance_player` | Distance from viewer to drone (same world) |

Examples:

- `%deliverydrones_outgoing_1_sender%` — name of sender of your first outgoing drone
- `%deliverydrones_incoming_2_eta%` — ETA of second incoming drone
- `%deliverydrones_outgoing_receiver%` — receiver name (first outgoing)

#### Drone by UUID

`%deliverydrones_id_<drone-uuid>_<field>%` or `%deliverydrones_drone_<drone-uuid>_<field>%` — same fields as in the table above.

Example: `%deliverydrones_id_a1b2c3d4-e5f6-7890-abcd-ef1234567890_eta%`

#### Socket (index)

| Field | Description |
|-------|-------------|
| `name` | Socket name |
| `world`, `x`, `y`, `z` | Location |
| `coords` / `coordinates` | `x, y, z` string |
| `owner` | Owner name |
| `trusted_count` / `trusted_names` | Trust list size / names |
| `blacklist_count` / `blacklist_names` | Socket blacklist size / names |
| `created` | Creation timestamp (ms) |
| `uuid` / `id` | Socket UUID |

Example: `%deliverydrones_socket_1_trusted_names%`

#### Config mirrors (`config_` prefix)

| Placeholder | Maps to `settings.drone.*` |
|-------------|---------------------------|
| `config_speed` | `speed` |
| `config_startup_speed` | `startup-speed` |
| `config_startup_seconds` | `startup-seconds` |
| `config_approach_speed` | `approach-speed` |
| `config_approach_distance` | `approach-distance` |
| `config_delivery_radius` | `delivery-radius` |
| `config_despawn_minutes` | `despawn-time-minutes` |
| `config_despawn_mode` | `despawn-mode` |
| `config_inventory_size` | `inventory-size` |
| `config_max_active_per_sender` | `max-active-per-sender` |
| `config_max_sockets_per_player` | `max-sockets-per-player` |
| `config_max_leashed_animals` | `max-leashed-animals-per-drone` |
| `config_carry_leashed_animals` | `carry-leashed-animals` |
| `config_follow_gliding` | `follow-gliding-player` |
| `config_follow_airborne` | `follow-airborne-player-before-landing` |
| `config_airborne_follow_min_height` | `airborne-follow-min-height` |
| `config_airborne_follow_max_seconds` | `airborne-follow-max-seconds-after-start` |
| `config_hologram_enabled` | `hologram.enabled` |
| `config_bossbar_enabled` | `bossbar.enabled` |
| `config_container_integration` | `container-integration.enabled` |
| `config_container_search_radius` | `container-integration.search-radius` |
| `config_launch_animation` | `launch-animation.enabled` |
| `config_collection_animation` | `collection-animation.enabled` |
| `config_locate_particles` | `locate-particles.enabled` |

#### Scoreboard / TAB examples

```text
&aIncoming: %deliverydrones_incoming_flying_count% flying, %deliverydrones_incoming_landed_count% landed
&7From: %deliverydrones_incoming_1_sender% (&e%deliverydrones_incoming_1_eta%s&7)
&cCooldown: %deliverydrones_cooldown_player_remaining%s
```

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
