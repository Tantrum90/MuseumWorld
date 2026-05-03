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

        if (getConfig().getBoolean("startup-summary-enabled", false)) {
            logStartupSummary();
        }

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

    private void logStartupSummary() {
        getLogger().info("==================================================");
        getLogger().info("MuseumWorld startup summary");
        getLogger().info("Plugin version: " + getPluginMeta().getVersion());
        getLogger().info("Config version: " + getConfig().getInt("config-version", 1));
        getLogger().info("Locked worlds: " + lockedWorlds.size());
        getLogger().info("Readonly blocks: " + readonlyBlocks.size());
        getLogger().info("View-only containers: " + viewOnlyContainers.size());
        getLogger().info("Readonly entities: " + readonlyEntities.size());
        getLogger().info("Blocked entity types: " + blockedEntityTypes.size());
        getLogger().info("Language: " + language);
        getLogger().info("Debug mode: " + debugMode);
        getLogger().info("Notify players: " + notifyPlayer);
        getLogger().info("Message cooldown: " + cooldownMs + " ms");
        getLogger().info("Block entity damage: " + blockEntityDamage);
        getLogger().info("Block friendly damage: " + blockFriendlyDamage);
        getLogger().info("Read-only interactions: " + blockReadonlyInteractions);
        getLogger().info("Readonly blocks auto: " + readonlyBlocksAuto);
        getLogger().info("Readonly entities auto: " + readonlyEntitiesAuto);
        getLogger().info("==================================================");
    }

    public void reloadAll() {
        ensureDefaultConfigExists();
        ensureDefaultMessageFilesExist();
        ensureConfigReferenceFile();

        updateConfigMissingKeys();
        updateMessageMissingKeys("messages_en.yml");
        updateMessageMissingKeys("messages_mk.yml");

        validateActiveConfigFile();

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

    private void ensureConfigReferenceFile() {
        File configFile = new File(getDataFolder(), "config.yml");

        YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);
        boolean createReference = existingConfig.getBoolean("create-config-reference", true);

        if (!createReference) {
            return;
        }

        File referenceFile = new File(getDataFolder(), "config-reference.yml");

        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                getLogger().warning("Could not create config-reference.yml because default config.yml was not found in the plugin jar.");
                return;
            }

            Files.copy(stream, referenceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("config-reference.yml updated.");
        } catch (Exception ex) {
            getLogger().warning("Could not update config-reference.yml: " + ex.getMessage());
        }
    }

    private void updateConfigMissingKeys() {
        File configFile = new File(getDataFolder(), "config.yml");

        YamlConfiguration defaultConfig = loadDefaultYamlFromJar("config.yml");

        if (defaultConfig == null) {
            getLogger().warning("Could not load default config.yml from plugin jar. Missing key update skipped.");
            return;
        }

        int defaultVersion = defaultConfig.getInt("config-version", 1);

        normalizeDuplicateTopLevelConfigVersionEntries(configFile, defaultVersion);

        YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);

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

        if (existingVersion < defaultVersion) {
            existingConfig.set("config-version", defaultVersion);
            changed = true;
        }

        if (changed) {
            if (existingConfig.getBoolean(
                    "backup-config-before-auto-update",
                    defaultConfig.getBoolean("backup-config-before-auto-update", true)
            )) {
                int maxBackups = existingConfig.getInt(
                        "max-config-backups",
                        defaultConfig.getInt("max-config-backups", 10)
                );

                backupConfigFile(configFile, maxBackups);
            }

            try {
                existingConfig.save(configFile);
                getLogger().info("config.yml updated. Config version: " + Math.max(existingVersion, defaultVersion));
            } catch (Exception ex) {
                getLogger().severe("Could not save updated config.yml: " + ex.getMessage());
            }
        }
    }

    private void normalizeDuplicateTopLevelConfigVersionEntries(File configFile, int fallbackVersion) {
        if (configFile == null || !configFile.exists()) {
            return;
        }

        List<String> lines;

        try {
            lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            getLogger().warning("Could not scan config.yml for duplicate config-version entries: " + ex.getMessage());
            return;
        }

        List<Integer> configVersionLineIndexes = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.startsWith("#")) {
                continue;
            }

            boolean isTopLevel = !line.startsWith(" ") && !line.startsWith("\t");

            if (isTopLevel && trimmed.matches("^config-version\\s*:.*$")) {
                configVersionLineIndexes.add(i);
            }
        }

        if (configVersionLineIndexes.size() <= 1) {
            return;
        }

        int lastIndex = configVersionLineIndexes.getLast();
        String lastLine = lines.get(lastIndex);
        String lastValue = lastLine.substring(lastLine.indexOf(':') + 1).trim();

        if (lastValue.isBlank()) {
            lastValue = String.valueOf(fallbackVersion);
        }

        List<String> cleanedLines = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            if (configVersionLineIndexes.contains(i) && i != lastIndex) {
                continue;
            }

            if (i == lastIndex) {
                cleanedLines.add("config-version: " + lastValue);
            } else {
                cleanedLines.add(lines.get(i));
            }
        }

        try {
            Files.write(configFile.toPath(), cleanedLines, StandardCharsets.UTF_8);
            getLogger().warning("Duplicate config-version entries found in config.yml. Kept only the last value: " + lastValue);
        } catch (Exception ex) {
            getLogger().warning("Could not clean duplicate config-version entries: " + ex.getMessage());
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

    private void backupConfigFile(File configFile, int maxBackups) {
        backupConfigFile(configFile, maxBackups, "config-before-auto-update-");
    }

    private void backupConfigFile(File configFile, int maxBackups, String filePrefix) {
        if (configFile == null || !configFile.exists()) {
            return;
        }

        File backupFolder = new File(getDataFolder(), "backups");

        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            getLogger().warning("Could not create config backup folder.");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File backupFile = new File(backupFolder, filePrefix + timestamp + ".yml");

        try {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("Created config backup: backups/" + backupFile.getName());
            cleanupOldConfigBackups(backupFolder, maxBackups, filePrefix);
        } catch (Exception ex) {
            getLogger().warning("Could not create config backup: " + ex.getMessage());
        }
    }

    private void cleanupOldConfigBackups(File backupFolder, int maxBackups, String filePrefix) {
        if (maxBackups <= 0) {
            return;
        }

        File[] files = backupFolder.listFiles();

        if (files == null || files.length == 0) {
            return;
        }

        List<File> backups = new ArrayList<>();

        for (File file : files) {
            if (file.isFile()
                    && file.getName().startsWith(filePrefix)
                    && file.getName().endsWith(".yml")) {
                backups.add(file);
            }
        }

        if (backups.size() <= maxBackups) {
            return;
        }

        backups.sort(Comparator.comparingLong(File::lastModified).reversed());

        for (int i = maxBackups; i < backups.size(); i++) {
            File oldBackup = backups.get(i);

            if (oldBackup.delete()) {
                getLogger().info("Deleted old config backup: backups/" + oldBackup.getName());
            } else {
                getLogger().warning("Could not delete old config backup: backups/" + oldBackup.getName());
            }
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

    private void validateActiveConfigFile() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration activeConfig = YamlConfiguration.loadConfiguration(configFile);

        boolean autoClean = activeConfig.getBoolean("auto-clean-invalid-config-values", false);

        ConfigValidationResult result = new ConfigValidationResult(
                new ArrayList<>(),
                new ArrayList<>()
        );

        boolean changed = false;

        changed |= validateMaterialList(activeConfig, "readonly-blocks", autoClean, result);
        changed |= validateMaterialList(activeConfig, "view-only-containers", autoClean, result);
        changed |= validateEntityTypeList(activeConfig, "readonly-entities", autoClean, result);
        changed |= validateEntityTypeList(activeConfig, "blocked-entity-types", autoClean, result);

        if (!result.hasIssues()) {
            getLogger().info("Config validation: OK");
            return;
        }

        for (String invalidMaterial : result.invalidMaterials()) {
            if (autoClean) {
                getLogger().warning("Invalid material removed from " + invalidMaterial);
            } else {
                getLogger().warning("Invalid material found in " + invalidMaterial);
            }
        }

        for (String invalidEntity : result.invalidEntities()) {
            if (autoClean) {
                getLogger().warning("Invalid entity removed from " + invalidEntity);
            } else {
                getLogger().warning("Invalid entity found in " + invalidEntity);
            }
        }

        if (changed) {
            if (activeConfig.getBoolean("backup-config-before-auto-update", true)) {
                int maxBackups = activeConfig.getInt("max-config-backups", 10);
                backupConfigFile(configFile, maxBackups, "config-before-validation-cleanup-");
            }

            try {
                activeConfig.save(configFile);
                getLogger().info("Invalid config values cleaned from active config.yml.");
            } catch (Exception ex) {
                getLogger().severe("Could not save cleaned config.yml: " + ex.getMessage());
            }
        }

        File reportFile = writeConfigValidationReport(result, activeConfig, autoClean, changed);

        if (reportFile != null) {
            getLogger().warning("Validation report saved: logs/" + reportFile.getName());
        }
    }

    private boolean validateMaterialList(
            YamlConfiguration config,
            String path,
            boolean autoClean,
            ConfigValidationResult result
    ) {
        List<?> rawList = config.getList(path);

        if (rawList == null) {
            return false;
        }

        List<String> cleanedList = new ArrayList<>();
        boolean foundInvalidValue = false;

        for (Object rawItem : rawList) {
            if (!(rawItem instanceof String value)) {
                result.invalidMaterials().add(path + ": " + rawItem);
                foundInvalidValue = true;

                if (!autoClean) {
                    cleanedList.add(String.valueOf(rawItem));
                }

                continue;
            }

            String trimmed = value.trim();

            if (trimmed.isEmpty()) {
                foundInvalidValue = true;

                if (!autoClean) {
                    cleanedList.add(value);
                }

                continue;
            }

            String normalized = trimmed.toUpperCase(Locale.ROOT);

            try {
                Material.valueOf(normalized);
                cleanedList.add(trimmed);
            } catch (Exception ex) {
                result.invalidMaterials().add(path + ": " + trimmed);
                foundInvalidValue = true;

                if (!autoClean) {
                    cleanedList.add(trimmed);
                }
            }
        }

        if (autoClean && foundInvalidValue) {
            config.set(path, cleanedList);
            return true;
        }

        return false;
    }

    private boolean validateEntityTypeList(
            YamlConfiguration config,
            String path,
            boolean autoClean,
            ConfigValidationResult result
    ) {
        List<?> rawList = config.getList(path);

        if (rawList == null) {
            return false;
        }

        List<String> cleanedList = new ArrayList<>();
        boolean foundInvalidValue = false;

        for (Object rawItem : rawList) {
            if (!(rawItem instanceof String value)) {
                result.invalidEntities().add(path + ": " + rawItem);
                foundInvalidValue = true;

                if (!autoClean) {
                    cleanedList.add(String.valueOf(rawItem));
                }

                continue;
            }

            String trimmed = value.trim();

            if (trimmed.isEmpty()) {
                foundInvalidValue = true;

                if (!autoClean) {
                    cleanedList.add(value);
                }

                continue;
            }

            String normalized = trimmed.toUpperCase(Locale.ROOT);

            try {
                EntityType.valueOf(normalized);
                cleanedList.add(trimmed);
            } catch (Exception ex) {
                result.invalidEntities().add(path + ": " + trimmed);
                foundInvalidValue = true;

                if (!autoClean) {
                    cleanedList.add(trimmed);
                }
            }
        }

        if (autoClean && foundInvalidValue) {
            config.set(path, cleanedList);
            return true;
        }

        return false;
    }

    private File writeConfigValidationReport(
            ConfigValidationResult result,
            YamlConfiguration activeConfig,
            boolean autoClean,
            boolean configChanged
    ) {
        File logFolder = new File(getDataFolder(), "logs");

        if (!logFolder.exists() && !logFolder.mkdirs()) {
            getLogger().warning("Could not create logs folder for config validation report.");
            return null;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File reportFile = new File(logFolder, "config-validation-" + timestamp + ".log");

        StringBuilder report = new StringBuilder();

        report.append("MuseumWorld Config Validation Report").append(System.lineSeparator());
        report.append("Generated: ").append(timestamp).append(System.lineSeparator());
        report.append("Plugin version: ").append(getPluginMeta().getVersion()).append(System.lineSeparator());
        report.append("Config version: ").append(activeConfig.getInt("config-version", 1)).append(System.lineSeparator());
        report.append(System.lineSeparator());

        report.append("Scope:").append(System.lineSeparator());
        report.append("- readonly-blocks").append(System.lineSeparator());
        report.append("- view-only-containers").append(System.lineSeparator());
        report.append("- readonly-entities").append(System.lineSeparator());
        report.append("- blocked-entity-types").append(System.lineSeparator());
        report.append(System.lineSeparator());

        report.append("Not checked or modified:").append(System.lineSeparator());
        report.append("- locked-worlds").append(System.lineSeparator());
        report.append("- language").append(System.lineSeparator());
        report.append(System.lineSeparator());

        report.append("Invalid materials:").append(System.lineSeparator());

        if (result.invalidMaterials().isEmpty()) {
            report.append("- none").append(System.lineSeparator());
        } else {
            for (String invalidMaterial : result.invalidMaterials()) {
                report.append("- ").append(invalidMaterial).append(System.lineSeparator());
            }
        }

        report.append(System.lineSeparator());
        report.append("Invalid entities:").append(System.lineSeparator());

        if (result.invalidEntities().isEmpty()) {
            report.append("- none").append(System.lineSeparator());
        } else {
            for (String invalidEntity : result.invalidEntities()) {
                report.append("- ").append(invalidEntity).append(System.lineSeparator());
            }
        }

        report.append(System.lineSeparator());
        report.append("Automatic cleanup:").append(System.lineSeparator());
        report.append("- Enabled: ").append(autoClean).append(System.lineSeparator());
        report.append("- Config changed: ").append(configChanged).append(System.lineSeparator());

        if (autoClean && configChanged) {
            report.append("- Invalid Material/EntityType values were removed from active config.yml.").append(System.lineSeparator());
        } else if (autoClean) {
            report.append("- Cleanup was enabled, but no active config changes were required.").append(System.lineSeparator());
        } else {
            report.append("- Cleanup was disabled, so active config.yml was not changed.").append(System.lineSeparator());
        }

        try {
            Files.writeString(reportFile.toPath(), report.toString(), StandardCharsets.UTF_8);
            return reportFile;
        } catch (Exception ex) {
            getLogger().warning("Could not write config validation report: " + ex.getMessage());
            return null;
        }
    }

    private record ConfigValidationResult(
            List<String> invalidMaterials,
            List<String> invalidEntities
    ) {
        private boolean hasIssues() {
            return !invalidMaterials.isEmpty() || !invalidEntities.isEmpty();
        }
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

        YamlConfiguration loadedMessages = YamlConfiguration.loadConfiguration(f);

        msgBlocked = color(loadedMessages.getString("blocked-message", "§6Museum world: §eEnjoy looking around!"));
        msgEntityDamage = color(loadedMessages.getString("entity-damage-message", "§6Museum world: §cYou cannot damage entities here."));
        msgFriendlyDamage = color(loadedMessages.getString("friendly-damage-message", "§6Museum world: §cYou cannot damage friendly mobs here."));
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