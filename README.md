# MuseumWorld (Tantrum90)

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
- friendly mob damage
- armor stand manipulation
- item frame / painting interaction
- protected container modification
- hopper/item transfer involving protected inventories
- explosions damaging blocks

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

### ✅ Config-driven

Most important behavior is controlled in `config.yml`, including:

- locked worlds
- read-only blocks
- read-only entities
- entity damage protection
- message language
- message cooldown
- debug mode
- config auto-update behavior

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

## Commands

Main command:

```text
/museum
```

Available subcommands:

```text
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

Shows plugin status, current world protection status, player permissions, gamemode diagnostics, and config counts.

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

Allows use of `/museum` commands.

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

This allows players to explore without breaking blocks or losing hunger. You can still kill un-friendly mobs.

---

## License

This project is licensed under the GNU General Public License v3.0.

See the `LICENSE` file for details.