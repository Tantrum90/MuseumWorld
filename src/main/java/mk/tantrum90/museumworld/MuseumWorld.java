package mk.tantrum90.museumworld;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MuseumWorld extends JavaPlugin {

    private static final Set<String> DEFAULT_PROTECTED_CONFIG_KEYS = Set.of(
            "locked-worlds",
            "language"
    );

    private final Set<String> lockedWorlds = new HashSet<>();
    private boolean notifyPlayer;
    private String language;
    private boolean debugMode;
    private long cooldownMs;

    private boolean blockEntityDamage;
    private boolean blockFriendlyDamage;
    private final Set<EntityType> blockedEntityTypes = EnumSet.noneOf(EntityType.class);

    private boolean blockReadonlyInteractions;
    private boolean readonlyBlocksAuto;
    private final Set<Material> readonlyBlocks = EnumSet.noneOf(Material.class);

    private final Set<Material> viewOnlyContainers = EnumSet.noneOf(Material.class);

    private boolean readonlyEntitiesAuto;
    private final Set<EntityType> readonlyEntities = EnumSet.noneOf(EntityType.class);

    private YamlConfiguration messages;
    private String msgBlocked;
    private String msgEntityDamage;
    private String msgFriendlyDamage;

    private final Map<UUID, Map<String, Long>> lastMessageByKey = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> event.registrar().register("museum", new MuseumCommand(this))
        );
    }

    @Override
    public void onEnable() {
        reloadAll();

        logBuildChannelWarning();

        Bukkit.getPluginManager().registerEvents(new MuseumWorldListener(this), this);

        getLogger().info("MuseumWorld enabled. Locked worlds: " + lockedWorlds);
    }

    @Override
    public void onDisable() {
        getLogger().info("MuseumWorld disabled.");
    }

    private void logBuildChannelWarning() {
        String pluginVersion = getPluginMeta().getVersion();

        if (pluginVersion.isBlank()) {
            return;
        }

        String normalizedVersion = pluginVersion.trim().toUpperCase(Locale.ROOT);

        final String reset = "\u001B[0m";
        final String red = "\u001B[31m";
        final String yellow = "\u001B[33m";
        final String cyan = "\u001B[36m";

        if (normalizedVersion.endsWith("-DEV")) {
            getLogger().warning(yellow + "==================================================" + reset);
            getLogger().warning(cyan + "MuseumWorld is running as a " + red + "DEVELOPMENT" + cyan + " build." + reset);
            getLogger().warning(yellow + "This version is intended for testing and active development." + reset);
            getLogger().warning(yellow + "Some features may be incomplete, unstable, or may not work as expected." + reset);
            getLogger().warning(red + "Do not use this build as a stable production version." + reset);
            getLogger().warning(yellow + "==================================================" + reset);
            return;
        }

        if (normalizedVersion.endsWith("-BETA")) {
            getLogger().warning(yellow + "==================================================" + reset);
            getLogger().warning(cyan + "MuseumWorld is running as a " + red + "BETA" + cyan + " build." + reset);
            getLogger().warning(yellow + "This version is intended for testing before a stable release." + reset);
            getLogger().warning(yellow + "Most features should work, but bugs or unexpected behavior may still occur." + reset);
            getLogger().warning(red + "Please test carefully if you are planning to use this plugin in production." + reset);
            getLogger().warning(yellow + "==================================================" + reset);
        }
    }

    public void reloadAll() {
        ensureDefaultConfigExists();
        ensureDefaultMessageFilesExist();

        updateConfigMissingKeys();
        updateMessageMissingKeys("messages_en.yml");
        updateMessageMissingKeys("messages_mk.yml");

        reloadConfig();
        loadConfigState();
        loadMessages();
    }

    private void ensureDefaultConfigExists() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveDefaultConfig();
        }
    }

    private void ensureDefaultMessageFilesExist() {
        saveResourceIfMissing("messages_en.yml");
        saveResourceIfMissing("messages_mk.yml");
    }

    private void saveResourceIfMissing(String resourceName) {
        File file = new File(getDataFolder(), resourceName);

        if (!file.exists()) {
            saveResource(resourceName, false);
        }
    }

    private void updateConfigMissingKeys() {
        File configFile = new File(getDataFolder(), "config.yml");

        YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration defaultConfig = loadDefaultYamlFromJar("config.yml");

        if (defaultConfig == null) {
            getLogger().warning("Could not load default config.yml from plugin jar. Missing key update skipped.");
            return;
        }

        Set<String> protectedConfigKeys = loadProtectedConfigKeys(existingConfig, defaultConfig);

        boolean changed = copyMissingKeys(defaultConfig, existingConfig, protectedConfigKeys);

        boolean updateListsOnNextReload = existingConfig.getBoolean(
                "update-lists-on-next-reload",
                defaultConfig.getBoolean("update-lists-on-next-reload", false)
        );

        if (updateListsOnNextReload) {
            boolean listsChanged = copyMissingListValues(defaultConfig, existingConfig, protectedConfigKeys);

            existingConfig.set("update-lists-on-next-reload", false);
            changed = true;

            if (listsChanged) {
                getLogger().info("Config lists updated with missing default values.");
            } else {
                getLogger().info("Config list update requested, but no missing list values were found.");
            }
        }

        int existingVersion = existingConfig.getInt("config-version", 1);
        int defaultVersion = defaultConfig.getInt("config-version", existingVersion);

        if (existingVersion < defaultVersion) {
            existingConfig.set("config-version", defaultVersion);
            changed = true;
        }

        if (changed) {
            if (existingConfig.getBoolean(
                    "backup-config-before-auto-update",
                    defaultConfig.getBoolean("backup-config-before-auto-update", true)
            )) {
                backupConfigFile(configFile);
            }

            try {
                existingConfig.save(configFile);
                getLogger().info("config.yml updated. Config version: " + defaultVersion);
            } catch (Exception ex) {
                getLogger().severe("Could not save updated config.yml: " + ex.getMessage());
            }
        }
    }

    private Set<String> loadProtectedConfigKeys(YamlConfiguration existingConfig, YamlConfiguration defaultConfig) {
        Set<String> protectedKeys = new HashSet<>(DEFAULT_PROTECTED_CONFIG_KEYS);

        List<String> defaultProtectedKeys = defaultConfig.getStringList("protected-config-keys");
        for (String key : defaultProtectedKeys) {
            if (key != null && !key.isBlank()) {
                protectedKeys.add(key.trim());
            }
        }

        List<String> existingProtectedKeys = existingConfig.getStringList("protected-config-keys");
        for (String key : existingProtectedKeys) {
            if (key != null && !key.isBlank()) {
                protectedKeys.add(key.trim());
            }
        }

        return protectedKeys;
    }

    private void backupConfigFile(File configFile) {
        if (configFile == null || !configFile.exists()) {
            return;
        }

        File backupFolder = new File(getDataFolder(), "backups");

        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            getLogger().warning("Could not create config backup folder.");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File backupFile = new File(backupFolder, "config-before-auto-update-" + timestamp + ".yml");

        try {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("Created config backup: backups/" + backupFile.getName());
        } catch (Exception ex) {
            getLogger().warning("Could not create config backup: " + ex.getMessage());
        }
    }

    private void updateMessageMissingKeys(String fileName) {
        File messageFile = new File(getDataFolder(), fileName);

        if (!messageFile.exists()) {
            saveResourceIfMissing(fileName);
            return;
        }

        YamlConfiguration existingMessages = YamlConfiguration.loadConfiguration(messageFile);
        YamlConfiguration defaultMessages = loadDefaultYamlFromJar(fileName);

        if (defaultMessages == null) {
            getLogger().warning("Could not load default " + fileName + " from plugin jar. Missing key update skipped.");
            return;
        }

        boolean changed = copyMissingKeys(defaultMessages, existingMessages, Set.of());

        if (changed) {
            try {
                existingMessages.save(messageFile);
                getLogger().info(fileName + " updated with missing keys.");
            } catch (Exception ex) {
                getLogger().severe("Could not save updated " + fileName + ": " + ex.getMessage());
            }
        }
    }

    private YamlConfiguration loadDefaultYamlFromJar(String resourceName) {
        InputStream stream = getResource(resourceName);

        if (stream == null) {
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception ex) {
            getLogger().warning("Could not read default resource " + resourceName + ": " + ex.getMessage());
            return null;
        }
    }

    private boolean copyMissingKeys(ConfigurationSection source, ConfigurationSection target, Set<String> protectedKeys) {
        boolean changed = false;

        for (String key : source.getKeys(false)) {
            String fullPath = buildPath(source, key);

            if (isProtectedConfigKey(key, fullPath, protectedKeys)) {
                getLogger().info("Skipped protected config key during missing-key update: " + fullPath);
                continue;
            }

            Object sourceValue = source.get(key);

            if (sourceValue instanceof ConfigurationSection sourceSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);

                if (targetSection == null) {
                    target.createSection(key);
                    targetSection = target.getConfigurationSection(key);
                    changed = true;
                }

                if (targetSection != null) {
                    if (copyMissingKeys(sourceSection, targetSection, protectedKeys)) {
                        changed = true;
                    }
                }

                continue;
            }

            if (!target.contains(key)) {
                target.set(key, sourceValue);
                getLogger().info("Added missing key: " + fullPath);
                changed = true;
            }
        }

        return changed;
    }

    private boolean copyMissingListValues(ConfigurationSection source, ConfigurationSection target, Set<String> protectedKeys) {
        boolean changed = false;

        for (String key : source.getKeys(false)) {
            String fullPath = buildPath(source, key);

            Object sourceValue = source.get(key);

            if (sourceValue instanceof ConfigurationSection sourceSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);

                if (targetSection != null) {
                    if (copyMissingListValues(sourceSection, targetSection, protectedKeys)) {
                        changed = true;
                    }
                }

                continue;
            }

            if (!source.isList(key) || !target.isList(key)) {
                continue;
            }

            if (isProtectedConfigKey(key, fullPath, protectedKeys)) {
                getLogger().info("Skipped protected list auto-update for: " + fullPath);
                continue;
            }

            List<?> defaultList = source.getList(key);
            List<?> existingList = target.getList(key);

            if (defaultList == null || existingList == null) {
                continue;
            }

            List<Object> updatedList = new ArrayList<>(existingList);
            boolean listChanged = false;

            for (Object defaultItem : defaultList) {
                if (!containsListItem(updatedList, defaultItem)) {
                    updatedList.add(defaultItem);
                    listChanged = true;
                    getLogger().info("Added missing list value under " + fullPath + ": " + defaultItem);
                }
            }

            if (listChanged) {
                target.set(key, updatedList);
                changed = true;
            }
        }

        return changed;
    }

    private boolean isProtectedConfigKey(String key, String fullPath, Set<String> protectedKeys) {
        if (protectedKeys == null || protectedKeys.isEmpty()) {
            return false;
        }

        return protectedKeys.contains(key) || protectedKeys.contains(fullPath);
    }

    private boolean containsListItem(List<Object> list, Object itemToFind) {
        for (Object existingItem : list) {
            if (Objects.equals(existingItem, itemToFind)) {
                return true;
            }

            if (existingItem instanceof String existingString && itemToFind instanceof String itemString) {
                if (existingString.equalsIgnoreCase(itemString)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String buildPath(ConfigurationSection section, String key) {
        String currentPath = section.getCurrentPath();

        if (currentPath == null || currentPath.isEmpty()) {
            return key;
        }

        return currentPath + "." + key;
    }

    private void loadConfigState() {
        lockedWorlds.clear();
        for (String w : getConfig().getStringList("locked-worlds")) {
            if (w != null && !w.isBlank()) {
                lockedWorlds.add(w.trim());
            }
        }

        notifyPlayer = getConfig().getBoolean("notify-player", true);
        language = getConfig().getString("language", "en");
        debugMode = getConfig().getBoolean("debug-mode", false);
        cooldownMs = getConfig().getLong("messages.cooldown-ms", 2500);

        blockEntityDamage = getConfig().getBoolean("block-entity-damage", true);
        blockFriendlyDamage = getConfig().getBoolean("block-friendly-damage", true);

        blockedEntityTypes.clear();
        for (String s : getConfig().getStringList("blocked-entity-types")) {
            if (s == null || s.isBlank()) {
                continue;
            }

            try {
                blockedEntityTypes.add(EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ex) {
                getLogger().warning("Invalid entity type in config.yml under blocked-entity-types: " + s);
            }
        }

        blockReadonlyInteractions = getConfig().getBoolean("block-readonly-interactions", true);

        readonlyBlocksAuto = getConfig().getBoolean("readonly-blocks-auto", true);
        readonlyBlocks.clear();
        for (String s : getConfig().getStringList("readonly-blocks")) {
            if (s == null || s.isBlank()) {
                continue;
            }

            try {
                readonlyBlocks.add(Material.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ex) {
                getLogger().warning("Invalid material in config.yml under readonly-blocks: " + s);
            }
        }

        viewOnlyContainers.clear();
        for (String s : getConfig().getStringList("view-only-containers")) {
            if (s == null || s.isBlank()) {
                continue;
            }

            try {
                viewOnlyContainers.add(Material.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ex) {
                getLogger().warning("Invalid material in config.yml under view-only-containers: " + s);
            }
        }

        readonlyEntitiesAuto = getConfig().getBoolean("readonly-entities-auto", true);
        readonlyEntities.clear();
        for (String s : getConfig().getStringList("readonly-entities")) {
            if (s == null || s.isBlank()) {
                continue;
            }

            try {
                readonlyEntities.add(EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ex) {
                getLogger().warning("Invalid entity type in config.yml under readonly-entities: " + s);
            }
        }
    }

    private void loadMessages() {
        String lang = (language == null || language.isBlank()) ? "en" : language.trim().toLowerCase(Locale.ROOT);
        String file = "messages_" + lang + ".yml";

        File f = new File(getDataFolder(), file);
        if (!f.exists()) {
            getLogger().warning("Missing " + file + ", falling back to messages_en.yml");
            f = new File(getDataFolder(), "messages_en.yml");
        }

        messages = YamlConfiguration.loadConfiguration(f);

        msgBlocked = color(messages.getString("blocked-message", "§6Museum world: §eEnjoy looking around!"));
        msgEntityDamage = color(messages.getString("entity-damage-message", "§6Museum world: §cYou cannot damage entities here."));
        msgFriendlyDamage = color(messages.getString("friendly-damage-message", "§6Museum world: §cYou cannot damage friendly mobs here."));
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    public boolean isLocked(World world) {
        return world != null && lockedWorlds.contains(world.getName());
    }

    public boolean notifyPlayer() {
        return notifyPlayer;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public boolean debugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean enabled) {
        debugMode = enabled;
        getConfig().set("debug-mode", enabled);
        saveConfig();
    }

    public void debugDecision(String decision, String action, PlayerDebugContext context, String reason) {
        if (!debugMode || context == null) {
            return;
        }

        getLogger().info("[DEBUG] "
                + decision
                + " " + action
                + " | player=" + context.playerName()
                + " | world=" + context.worldName()
                + " | locked=" + context.locked()
                + " | bypass=" + context.bypass()
                + " | reason=" + reason);
    }

    public PlayerDebugContext debugContext(String playerName, String worldName, boolean locked, boolean bypass) {
        return new PlayerDebugContext(playerName, worldName, locked, bypass);
    }

    public boolean blockEntityDamage() {
        return blockEntityDamage;
    }

    public boolean blockFriendlyDamage() {
        return blockFriendlyDamage;
    }

    public Set<EntityType> blockedEntityTypes() {
        return blockedEntityTypes;
    }

    public boolean blockReadonlyInteractions() {
        return blockReadonlyInteractions;
    }

    public boolean readonlyBlocksAuto() {
        return readonlyBlocksAuto;
    }

    public Set<Material> readonlyBlocks() {
        return readonlyBlocks;
    }

    public Set<Material> viewOnlyContainers() {
        return viewOnlyContainers;
    }

    public boolean readonlyEntitiesAuto() {
        return readonlyEntitiesAuto;
    }

    public Set<EntityType> readonlyEntities() {
        return readonlyEntities;
    }

    public String msgBlocked() {
        return msgBlocked;
    }

    public String msgEntityDamage() {
        return msgEntityDamage;
    }

    public String msgFriendlyDamage() {
        return msgFriendlyDamage;
    }

    public boolean shouldSend(UUID playerId, String key) {
        if (cooldownMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        Map<String, Long> perKey = lastMessageByKey.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        Long last = perKey.get(key);

        if (last == null || (now - last) >= cooldownMs) {
            perKey.put(key, now);
            return true;
        }

        return false;
    }

    public List<String> getLockedWorlds() {
        return new ArrayList<>(lockedWorlds);
    }

    public void addLockedWorld(String name) {
        lockedWorlds.add(name);
        getConfig().set("locked-worlds", new ArrayList<>(lockedWorlds));
        saveConfig();
    }

    public void removeLockedWorld(String name) {
        lockedWorlds.remove(name);
        getConfig().set("locked-worlds", new ArrayList<>(lockedWorlds));
        saveConfig();
    }

    public record PlayerDebugContext(String playerName, String worldName, boolean locked, boolean bypass) {
    }
}