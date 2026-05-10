# MuseumWorld by Tantrum90

<a href="https://github.com/Tantrum90/MuseumWorld">
  <img alt="GitHub" src="https://img.shields.io/badge/GitHub-MuseumWorld-181717?style=for-the-badge&logo=github">
</a>

<a href="https://hangar.papermc.io/Tantrum90/MuseumWorld">
  <img alt="Hangar" src="https://img.shields.io/badge/Hangar-MuseumWorld-blue?style=for-the-badge">
</a>

<a href="https://modrinth.com/plugin/museumworld">
  <img alt="Modrinth" src="https://img.shields.io/badge/Modrinth-MuseumWorld-00AF5C?style=for-the-badge&logo=modrinth">
</a>

MuseumWorld is a lightweight Paper plugin that turns selected worlds into **read-only “museum / showcase” worlds**.

Players can explore freely, while destructive actions, inventory modification, entity damage, and common interaction exploits are blocked.

It is designed for:

- lobby / spawn / hub worlds
- showcase maps
- museum builds
- read-only adventure areas
- worlds where visitors should explore, but not edit

---

## Key Features

### ✅ Read-only worlds

You choose which worlds are protected through `config.yml`.

In protected worlds, MuseumWorld can block:

- block breaking
- block placing
- inventory modification
- entity damage
- armor stand manipulation
- item frame / painting interaction
- protected container modification
- hopper/item transfer involving protected inventories
- explosions damaging blocks
- item dropping
- item pickup
- bucket fill/empty actions
- flint and steel / fire charge use
- fire ignition
- TNT ignition
- bed use
- bone meal use
- Nether portal creation
- natural growth, spread and decay changes
- projectile use, with optional Elytra firework boost exception
- lead use
- name tag use
- vehicle placement and breaking
- hanging entity breaking/removal

---

### ✅ View-only containers

MuseumWorld allows players to **open selected containers and look inside**, while blocking item movement.

For example, players can open:

- chests
- barrels
- shulker boxes
- furnaces
- hoppers
- dispensers
- droppers
- brewing stands
- lecterns
- beacons

But they cannot:

- take items
- place items
- shift-click items
- drag items into the container
- move items using hopper transfers

---

### ✅ Strictly config-driven protection

MuseumWorld is designed to be predictable and controlled through `config.yml`.

Read-only blocks and read-only entities are protected **only if they are explicitly listed** in:

```yml
readonly-blocks:
readonly-entities:
```

There is no automatic read-only block/entity detection.

This means navigation blocks such as doors, trapdoors and fence gates are not blocked unless the server owner explicitly adds them to `readonly-blocks`.

For example, if players should be able to open doors in a museum world, simply do not add those doors to `readonly-blocks`.

If a specific door should be locked, add it manually:

```yml
readonly-blocks:
  - OAK_DOOR
  - IRON_DOOR
```

This keeps the plugin fully configurable and avoids unexpected protection behavior.

---

### ✅ Extended protection toggles

MuseumWorld includes additional protection options for common grief-prevention and museum-world use cases.

Example:

```yml
block-item-drop: true
block-item-pickup: true
block-bucket-use: true
block-fire-use: true
block-natural-growth: false
block-bone-meal-use: true
block-portal-creation: true
block-item-frame-rotation: true
block-armor-stand-manipulation: true
block-tnt-ignite: true
block-player-bed-use: true
block-hanging-break: true
block-vehicle-place-break: true
block-projectile-use: true
block-lead-use: true
block-name-tag-use: true
```

These options allow server owners to decide exactly how strict each protected world should be.

---

### ✅ Elytra support

Projectile blocking can be enabled while still allowing Elytra firework boosting.

```yml
block-projectile-use: true
allow-elytra-firework-boost: true
```

When enabled, players can still use firework rockets while gliding with Elytra.

This allows Elytra flight in protected worlds while still blocking other projectile-style interactions such as eggs, ender pearls, splash potions and similar actions.

---

### ✅ Config validation and safe cleanup

MuseumWorld can validate selected config lists on startup.

The validator checks:

```yml
readonly-blocks
view-only-containers
readonly-entities
blocked-entity-types
```

If invalid `Material` or `EntityType` names are found, MuseumWorld can automatically remove them when enabled:

```yml
auto-clean-invalid-config-values: true
```

Before cleanup, the plugin can create a backup of `config.yml`.

Validation reports are saved under:

```text
plugins/MuseumWorld/logs/
```

Config backups are saved under:

```text
plugins/MuseumWorld/backups/
```

The validator does **not** modify:

```yml
locked-worlds
language
```

---

### ✅ Automatic config updates

MuseumWorld can automatically add missing config keys when a new plugin version introduces new options.

Existing values are preserved.

Protected config keys, such as:

```yml
protected-config-keys:
  - locked-worlds
  - language
```

are not overwritten during config updates.

MuseumWorld can also update missing list values when this option is enabled:

```yml
update-lists-on-next-reload: true
```

After the list update is completed, the option automatically returns to:

```yml
update-lists-on-next-reload: false
```

Before automatic config updates, the plugin can create a backup if enabled:

```yml
backup-config-before-auto-update: true
```

Backups are stored in:

```text
plugins/MuseumWorld/backups/
```

---

### ✅ Template-based config rewrite

MuseumWorld rewrites `config.yml` using the bundled default configuration as a template.

This means automatic config updates preserve:

- official comments
- key order
- newly added explanations
- existing user-defined values
- custom list entries
- a clean and predictable config layout

The bundled default `config.yml` acts as the reference layout.

The active server `config.yml` keeps user values, while missing keys and comments are restored from the default template.

`config-version` is always kept as the final key at the bottom of the file.

---

### ✅ bStats metrics

MuseumWorld uses anonymous bStats metrics.

bStats helps track how many servers use the plugin and which server/software versions are common. It does not collect private player data, chat, world names or config values.

### ✅ Update checker

MuseumWorld can check GitHub Releases for new plugin versions on startup.

```yml
update-checker-enabled: true
notify-admins-about-updates: true
```

The update checker only notifies server owners/admins. It never downloads or installs updates automatically.

## Commands

Main command:

```text
/museum
```

Available subcommands:

```text
/museum version
/museum list
/museum add <world>
/museum remove <world>
/museum reload
/museum status
/museum debug <on|off|status>
/museum lockcurrentworld
/museum unlockcurrentworld
```

### Command overview

```text
/museum version
```

Shows public plugin information. This command is available to all players and does not require `museumworld.admin`.

Example output:

```text
MuseumWorld
Version: 1.1.0
Channel: STABLE
Target API: Paper 26.1.2
Author: Tantrum90MK
```

```text
/museum list
```

Shows all locked/protected worlds.

```text
/museum add <world>
```

Adds a world to the protected worlds list.

```text
/museum remove <world>
```

Removes a world from the protected worlds list.

```text
/museum reload
```

Reloads config and message files.

```text
/museum status
```

Shows plugin status, current world protection status, player permissions, gamemode diagnostics, config counts and protection toggle states.

```text
/museum debug on
```

Enables debug mode.

```text
/museum debug off
```

Disables debug mode.

```text
/museum debug status
```

Shows whether debug mode is enabled.

```text
/museum lockcurrentworld
```

Adds the world where the player is currently standing to the protected worlds list.

```text
/museum unlockcurrentworld
```

Removes the world where the player is currently standing from the protected worlds list.

---

## Permissions

```text
museumworld.admin
```

Allows use of administrative `/museum` commands. The `/museum version` subcommand is public and does not require this permission.

```text
museumworld.bypass
```

Allows bypassing MuseumWorld protection checks.

By default, `museumworld.admin` is assigned to OP users through `paper-plugin.yml`.

---

## Debug mode

Debug mode helps identify why actions are blocked or allowed.

Enable it with:

```text
/museum debug on
```

Example debug output:

```text
[MuseumWorld] [DEBUG] DENIED block-break | player=PlayerName | world=WorldName | locked=true | bypass=false | reason=locked world protection
```

Example allowed output:

```text
[MuseumWorld] [DEBUG] ALLOWED block-break | player=PlayerName | world=WorldName | locked=true | bypass=true | reason=player has bypass/admin permission
```

Debug mode is useful when testing:

- protected worlds
- bypass permissions
- read-only blocks
- read-only entities
- inventory protection
- projectile blocking
- Elytra firework boost behavior
- bucket/fire/TNT protections
- vehicle and hanging entity protections

---

## Gamemode-aware diagnostics

MuseumWorld status and debug output include gamemode-related information.

This is useful because Minecraft gamemodes affect whether block breaking is possible.

For example:

- `SURVIVAL` and `CREATIVE` normally allow block breaking.
- `ADVENTURE` prevents normal block breaking.
- `SPECTATOR` prevents normal block interaction.

If a player is in `ADVENTURE` mode, `BlockBreakEvent` may not fire because Minecraft itself prevents normal block breaking.

Use:

```text
/museum status
```

to check:

- current world
- current world protection status
- player OP status
- player gamemode
- `museumworld.admin` permission
- `museumworld.bypass` permission
- whether block breaking is expected to work in the current gamemode
- important protection toggle states

---

## Language

MuseumWorld supports English and Macedonian message files.

Included files:

- `messages_en.yml`
- `messages_mk.yml`

Select language in `config.yml`:

```yml
language: en    # for English

# language: mk  # for Macedonian
```

Only one `language` key should be active.

---

## Recommended museum-world setup

For museum/showcase worlds, it is recommended to use multiple protection layers:

- Multiverse world gamemode: `ADVENTURE`
- MuseumWorld protection: enabled
- WorldGuard global region protection if needed

Recommended Multiverse-style settings for museum worlds:

```text
gamemode: ADVENTURE
hunger: false
```

This allows players to explore without breaking blocks or losing hunger.

MuseumWorld can then provide additional protection against interactions that Adventure mode does not fully cover, such as:

- inventory movement
- container modification
- item dropping and pickup
- bucket use
- fire use
- TNT ignition
- entity manipulation
- projectile use
- item frame and armor stand interaction
- vehicle placement/removal

---

## Notes about doors, trapdoors and gates

MuseumWorld does not automatically block doors, trapdoors or fence gates.

They are normal navigation blocks in many museum/showcase worlds.

If you want to lock a specific door, trapdoor or fence gate type, add it manually to:

```yml
readonly-blocks:
```

Example:

```yml
readonly-blocks:
  - OAK_DOOR
  - IRON_DOOR
  - OAK_TRAPDOOR
  - OAK_FENCE_GATE
```

If they are not listed, players can use them normally.

---

## License

This project is licensed under the GNU General Public License v3.0.

See the `LICENSE` file for details.
