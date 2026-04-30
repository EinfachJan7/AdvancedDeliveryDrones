# Advanced Delivery Drones

Paper plugin for CityBuild servers that replaces instant item transfer with physical delivery drones.

## Features

- `/drone send <player>` opens a delivery inventory and spawns a visible drone
- Drones fly with configurable speed, startup speed phase, particles and sounds
- Landing in configurable radius, secure receiver-only access, protection from damage
- Receiver bossbar with distance and ETA
- `/drone decline` returns incoming drone items to sender
- World blocking, receiver toggle, reload and admin list command

## Commands

- `/drone send <player>`
- `/drone toggle`
- `/drone decline`
- `/drone reload`
- `/drone list`

## Permissions

- `drone.send`
- `drone.use`
- `drone.admin`

## Build

```bash
mvn clean package
```

Output jar:

- `target/advanced-delivery-drones-1.0.0.jar`
# AdvancedDeliveryDrones
AdvancedDeliveryDrones
