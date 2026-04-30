# 📦 DeliverMe — Advanced Delivery Drones

> **Ein physisches Drohnen-Liefersystem für Minecraft Paper-Server.**  
> Schicke Pakete quer durch die Welt — sichtbar, animiert, absagbar.

---

## ✨ Features

- 🚁 **Physische Drohnen** fliegen in Echtzeit von Sender zu Empfänger
- 🎨 **Hologramm** über der Drohne zeigt Empfänger & Countdown an
- 📊 **Bossbar** zeigt dem Empfänger Distanz & ETA in Echtzeit
- ✨ **Partikeleffekte** mit Trail — auch RGB `DUST`-Partikel werden unterstützt
- 🔔 **Fluggeräusch** (konfigurierbar, Standard: Elytra)
- 🏠 **Despawn-Logik** — Drohnen landen automatisch oder verschwinden nach Timeout
- 🚫 **Toggle** — Spieler können Drohnen-Empfang deaktivieren
- ↩️ **Ablehnen** — Empfänger können eingehende Drohnen zurückschicken
- ⚙️ **Vollständig konfigurierbar** über `config.yml`
- 🌍 **Blockierte Welten** (Nether & End standardmäßig gesperrt)
- 🔒 **Sender-Limit** — max. aktive Drohnen pro Spieler einstellbar

---

## 🛠 Installation

1. **Java 21** und **Paper 1.21.4+** werden vorausgesetzt
2. JAR aus dem `target/`-Ordner in den `plugins/`-Ordner des Servers kopieren:
   ```
   advanced-delivery-drones-1.0.0.jar → /plugins/
   ```
3. Server (neu)starten — `config.yml` wird automatisch generiert
4. Fertig 🎉

### Build from Source

```bash
git clone <repo-url>
cd Deliverme
mvn package
# → target/advanced-delivery-drones-1.0.0.jar
```

---

## 🎮 Commands

| Command | Beschreibung | Permission |
|---|---|---|
| `/drone send <Spieler>` | Öffnet das Paket-Inventar & schickt eine Drohne | `drone.send` |
| `/drone toggle` | Drohnen-Empfang an/ausschalten | `drone.use` |
| `/drone decline` | Alle eingehenden Drohnen ablehnen & zurückschicken | `drone.use` |
| `/drone list` | Alle aktiven Drohnen auflisten (Admin) | `drone.admin` |
| `/drone reload` | Config live neu laden (Admin) | `drone.admin` |

---

## 🔐 Permissions

| Permission | Beschreibung | Standard |
|---|---|---|
| `drone.send` | Drohnen senden | `true` |
| `drone.use` | Toggle & Decline nutzen | `true` |
| `drone.admin` | List & Reload | OP |

---

## ⚙️ Konfiguration

```yaml
settings:
  drone:
    speed: 1.0                      # Fluggeschwindigkeit (Blöcke/Tick)
    startup-speed: 0.2              # Anlaufgeschwindigkeit
    startup-seconds: 3              # Anlaufzeit in Sekunden
    delivery-radius: 10.0           # Landeradius in Blöcken
    despawn-time-minutes: 10        # Timeout bis zum Despawn
    despawn-mode: "DELETE"          # DELETE oder COLLECT (Items zurückgeben)
    max-active-per-sender: 3        # Max. gleichzeitige Drohnen pro Spieler
    inventory-size: 54              # Paket-Größe (9–54, Vielfaches von 9)

    blocked-worlds:
      - "world_nether"
      - "world_the_end"

    # Partikel: Standard-Partikel oder RGB-Dust
    particle-types:
      - "ELECTRIC_SPARK"
      # - "DUST:255,0,0:1.0"       # Rotes Dust-Partikel, Größe 1.0
    particle-count: 4
    particle-trail-length: 10
    particle-y-offset: 1.0

    flight-sound: "entity.elytra.flying"

    hologram:
      enabled: true
      offset-y: 1.0
      format: "<yellow>Paket fuer <white><receiver></white> <gray>(Despawn: <minutes>m <seconds>s)</gray>"

    bossbar:
      enabled: true
      format: "<gold>Distanz zur Drone: <white><distance>m</white> <gray>| ETA: <white><eta>s</white></gray>"
```

### Despawn-Modi

| Modus | Verhalten |
|---|---|
| `DELETE` | Drohne & Inhalt werden gelöscht |
| `COLLECT` | Items werden an den Sender zurückgegeben |

### Partikel-Format

- Normaler Partikel: `"ELECTRIC_SPARK"`
- RGB Dust-Partikel: `"DUST:R,G,B:SIZE"` — z.B. `"DUST:0,180,255:1.5"`

---

## 📋 Ablauf einer Lieferung

```
Sender: /drone send <Spieler>
  └─▶ Inventar öffnet sich (Paket befüllen)
  └─▶ Inventar schließen → Drohne startet
        │
        ├─▶ Drohne fliegt physisch durch die Welt
        ├─▶ Empfänger sieht Bossbar mit Distanz & ETA
        ├─▶ Hologramm zeigt Empfänger & Timer
        │
        ├─▶ Empfänger kann mit /drone decline ablehnen
        │         └─▶ Items werden zurückgeschickt
        │
        └─▶ Drohne landet → Empfänger kann Paket öffnen
```

---

## 🧩 Kompatibilität

| Anforderung | Version |
|---|---|
| Minecraft | 1.21.4 |
| Server-Software | Paper (oder Fork) |
| Java | 21+ |
| API | Paper API 1.21.4-R0.1-SNAPSHOT |

---

## 📁 Projektstruktur

```
src/main/java/de/cb/drones/
├── AdvancedDeliveryDronesPlugin.java   # Plugin-Einstiegspunkt
├── command/
│   └── DroneCommand.java               # /drone Befehlshandler
├── config/
│   └── PlayerSettingsRepository.java   # Spieler-Einstellungen (Toggle)
└── drone/
    ├── DeliveryDrone.java              # Drohnen-Logik & Animation
    ├── DroneInteractionListener.java   # Klick- & Interaktionsereignisse
    ├── DroneManager.java               # Verwaltung aller aktiven Drohnen
    └── DroneSettings.java              # Konfigurationsmodell
```

---

## 👤 Autor

**CB** — Plugin entwickelt für CityBuild-Server.

---

*Made with ☕ and too many `/drone send` tests.*
