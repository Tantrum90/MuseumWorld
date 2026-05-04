package mk.tantrum90.museumworld;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NullMarked;

import java.util.*;

@NullMarked
public final class MuseumCommand implements BasicCommand {

    private final MuseumWorld plugin;

    public MuseumCommand(MuseumWorld plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (!sender.hasPermission("museumworld.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> {
                List<String> worlds = plugin.getLockedWorlds();
                worlds.sort(String.CASE_INSENSITIVE_ORDER);
                sender.sendMessage("§6Locked worlds (" + worlds.size() + "): §e" + String.join(", ", worlds));
            }
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /museum add <world>");
                    return;
                }

                String worldName = args[1];
                plugin.addLockedWorld(worldName);
                sender.sendMessage("§aAdded locked world: §e" + worldName);
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /museum remove <world>");
                    return;
                }

                String worldName = args[1];
                plugin.removeLockedWorld(worldName);
                sender.sendMessage("§aRemoved locked world: §e" + worldName);
            }
            case "lockcurrentworld" -> lockCurrentWorld(sender);
            case "unlockcurrentworld" -> unlockCurrentWorld(sender);
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("§aMuseumWorld reloaded.");
            }
            case "status" -> sendStatus(sender);
            case "debug" -> handleDebug(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§eUsage: /museum <list|add|remove|reload|status|debug|lockcurrentworld|unlockcurrentworld>");
    }

    private void lockCurrentWorld(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by a player.");
            return;
        }

        String worldName = player.getWorld().getName();

        if (plugin.isLocked(player.getWorld())) {
            sender.sendMessage("§eCurrent world is already locked: §f" + worldName);
            return;
        }

        plugin.addLockedWorld(worldName);
        sender.sendMessage("§aCurrent world locked: §e" + worldName);
    }

    private void unlockCurrentWorld(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by a player.");
            return;
        }

        String worldName = player.getWorld().getName();

        if (!plugin.isLocked(player.getWorld())) {
            sender.sendMessage("§eCurrent world is not locked: §f" + worldName);
            return;
        }

        plugin.removeLockedWorld(worldName);
        sender.sendMessage("§aCurrent world unlocked: §e" + worldName);
    }

    private void sendStatus(CommandSender sender) {
        List<String> lockedWorlds = plugin.getLockedWorlds();
        lockedWorlds.sort(String.CASE_INSENSITIVE_ORDER);

        sender.sendMessage("§6§m----------------------------------------");
        sender.sendMessage("§6MuseumWorld status");

        if (sender instanceof Player player) {
            World world = player.getWorld();
            GameMode gameMode = player.getGameMode();

            sender.sendMessage("§eCurrent world: §f" + world.getName());
            sender.sendMessage("§eCurrent world protected: " + formatBoolean(plugin.isLocked(world)));
            sender.sendMessage("§ePlayer: §f" + player.getName());
            sender.sendMessage("§eOP: " + formatBoolean(player.isOp()));
            sender.sendMessage("§eGamemode: §f" + gameMode.name());
            sender.sendMessage("§eCan normally break blocks by gamemode: " + formatBoolean(canNormallyBreakBlocks(gameMode)));
            sender.sendMessage("§ePermission museumworld.admin: " + formatBoolean(player.hasPermission("museumworld.admin")));
            sender.sendMessage("§ePermission museumworld.bypass: " + formatBoolean(player.hasPermission("museumworld.bypass")));
            sender.sendMessage("§eExpected block-break event: §f" + expectedBlockBreakEvent(gameMode));
        } else {
            sender.sendMessage("§eCurrent world: §7Console / not applicable");
            sender.sendMessage("§ePlayer diagnostics: §7Console / not applicable");
        }

        sender.sendMessage("§ePlugin version: §f" + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§eConfig version: §f" + plugin.getConfig().getInt("config-version", 1));
        sender.sendMessage("§eLanguage: §f" + plugin.getConfig().getString("language", "en"));
        sender.sendMessage("§eMessages: " + formatBoolean(plugin.notifyPlayer()));
        sender.sendMessage("§eDebug mode: " + formatBoolean(plugin.debugMode()));
        sender.sendMessage("§eMessage cooldown: §f" + plugin.cooldownMs() + " ms");

        sender.sendMessage("§eBlock entity damage: " + formatBoolean(plugin.blockEntityDamage()));
        sender.sendMessage("§eBlock friendly damage: " + formatBoolean(plugin.blockFriendlyDamage()));
        sender.sendMessage("§eBlock item drop: " + formatBoolean(plugin.blockItemDrop()));
        sender.sendMessage("§eBlock item pickup: " + formatBoolean(plugin.blockItemPickup()));
        sender.sendMessage("§eBlock bucket use: " + formatBoolean(plugin.blockBucketUse()));
        sender.sendMessage("§eBlock fire use: " + formatBoolean(plugin.blockFireUse()));
        sender.sendMessage("§eBlock natural growth: " + formatBoolean(plugin.blockNaturalGrowth()));
        sender.sendMessage("§eBlock bone meal use: " + formatBoolean(plugin.blockBoneMealUse()));
        sender.sendMessage("§eBlock portal creation: " + formatBoolean(plugin.blockPortalCreation()));
        sender.sendMessage("§eBlock item frame rotation: " + formatBoolean(plugin.blockItemFrameRotation()));
        sender.sendMessage("§eBlock armor stand manipulation: " + formatBoolean(plugin.blockArmorStandManipulation()));
        sender.sendMessage("§eBlock TNT ignite: " + formatBoolean(plugin.blockTntIgnite()));
        sender.sendMessage("§eBlock player bed use: " + formatBoolean(plugin.blockPlayerBedUse()));
        sender.sendMessage("§eBlock hanging break: " + formatBoolean(plugin.blockHangingBreak()));
        sender.sendMessage("§eBlock vehicle place/break: " + formatBoolean(plugin.blockVehiclePlaceBreak()));
        sender.sendMessage("§eBlock vehicle enter: " + formatBoolean(plugin.blockVehicleEnter()));
        sender.sendMessage("§eBlock projectile use: " + formatBoolean(plugin.blockProjectileUse()));
        sender.sendMessage("§eAllow Elytra firework boost: " + formatBoolean(plugin.allowElytraFireworkBoost()));
        sender.sendMessage("§eBlock lead use: " + formatBoolean(plugin.blockLeadUse()));
        sender.sendMessage("§eBlock name tag use: " + formatBoolean(plugin.blockNameTagUse()));
        sender.sendMessage("§eRead-only interactions: " + formatBoolean(plugin.blockReadonlyInteractions()));
        sender.sendMessage("§eUpdate lists on next reload: " + formatBoolean(plugin.getConfig().getBoolean("update-lists-on-next-reload", false)));

        sender.sendMessage("§eLocked worlds: §f" + lockedWorlds.size());
        sender.sendMessage("§eReadonly blocks: §f" + plugin.readonlyBlocks().size());
        sender.sendMessage("§eView-only containers: §f" + plugin.viewOnlyContainers().size());
        sender.sendMessage("§eReadonly entities: §f" + plugin.readonlyEntities().size());
        sender.sendMessage("§eBlocked entity types: §f" + plugin.blockedEntityTypes().size());

        if (lockedWorlds.isEmpty()) {
            sender.sendMessage("§eLocked world list: §7empty");
        } else {
            sender.sendMessage("§eLocked world list: §f" + String.join(", ", lockedWorlds));
        }

        sender.sendMessage("§6§m----------------------------------------");
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§6MuseumWorld debug mode: " + formatBoolean(plugin.debugMode()));
            sender.sendMessage("§eUsage: /museum debug <on|off|status>");
            return;
        }

        String value = args[1].toLowerCase(Locale.ROOT);

        switch (value) {
            case "on", "true", "enable", "enabled" -> {
                plugin.setDebugMode(true);
                sender.sendMessage("§aMuseumWorld debug mode enabled.");
            }
            case "off", "false", "disable", "disabled" -> {
                plugin.setDebugMode(false);
                sender.sendMessage("§cMuseumWorld debug mode disabled.");
            }
            case "status" -> sender.sendMessage("§6MuseumWorld debug mode: " + formatBoolean(plugin.debugMode()));
            default -> sender.sendMessage("§eUsage: /museum debug <on|off|status>");
        }
    }

    private String formatBoolean(boolean value) {
        return value ? "§aenabled" : "§cdisabled";
    }

    private boolean canNormallyBreakBlocks(GameMode gameMode) {
        return gameMode == GameMode.SURVIVAL || gameMode == GameMode.CREATIVE;
    }

    private String expectedBlockBreakEvent(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL, CREATIVE -> "yes";
            case ADVENTURE -> "unlikely - Adventure mode prevents normal block breaking";
            case SPECTATOR -> "no - Spectator mode prevents normal block breaking";
        };
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (!sender.hasPermission("museumworld.admin")) {
            return List.of();
        }

        List<String> subcommands = Arrays.asList(
                "list",
                "add",
                "remove",
                "reload",
                "status",
                "debug",
                "lockcurrentworld",
                "unlockcurrentworld"
        );

        if (args.length == 0) {
            return subcommands;
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], subcommands, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            List<String> names = Bukkit.getWorlds().stream().map(World::getName).toList();
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return StringUtil.copyPartialMatches(args[1], plugin.getLockedWorlds(), new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return StringUtil.copyPartialMatches(
                    args[1],
                    Arrays.asList("on", "off", "status"),
                    new ArrayList<>()
            );
        }

        return List.of();
    }

    @Override
    public String permission() {
        return "museumworld.admin";
    }
}