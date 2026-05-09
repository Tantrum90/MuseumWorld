package mk.tantrum90.museumworld;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MuseumWorld extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 31236;
    private static final String UPDATE_CHECKER_URL =
            "https://api.github.com/repos/Tantrum90/MuseumWorld/releases/latest";

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
    private boolean blockItemDrop;
    private boolean blockItemPickup;
    private boolean blockBucketUse;
    private boolean blockFireUse;
    private boolean blockNaturalGrowth;
    private boolean blockBoneMealUse;
    private boolean blockPortalCreation;
    private boolean blockItemFrameRotation;
    private boolean blockArmorStandManipulation;
    private boolean blockTntIgnite;
    private boolean blockPlayerBedUse;
    private boolean blockHangingBreak;
    private boolean blockVehiclePlaceBreak;
    private boolean blockVehicleEnter;
    private boolean blockProjectileUse;
    private boolean allowElytraFireworkBoost;
    private boolean blockLeadUse;
    private boolean blockNameTagUse;
    private final Set<EntityType> blockedEntityTypes = EnumSet.noneOf(EntityType.class);

    private boolean blockReadonlyInteractions;
    private final Set<Material> readonlyBlocks = EnumSet.noneOf(Material.class);

    private final Set<Material> viewOnlyContainers = EnumSet.noneOf(Material.class);

    private final Set<EntityType> readonlyEntities = EnumSet.noneOf(EntityType.class);

    private String msgBlocked;
    private final Map<String, String> actionMessages = new HashMap<>();

    private boolean updateCheckerEnabled;
    private boolean notifyAdminsAboutUpdates;
    private volatile boolean updateAvailable;
    private volatile String latestVersion = "";
    private volatile String latestVersionUrl = "";

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

        startMetrics();
        checkForUpdatesAsync();

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
        getLogger().info("Block item drop: " + blockItemDrop);
        getLogger().info("Block item pickup: " + blockItemPickup);
        getLogger().info("Block bucket use: " + blockBucketUse);
        getLogger().info("Block fire use: " + blockFireUse);
        getLogger().info("Block natural growth: " + blockNaturalGrowth);
        getLogger().info("Block bone meal use: " + blockBoneMealUse);
        getLogger().info("Block portal creation: " + blockPortalCreation);
        getLogger().info("Block item frame rotation: " + blockItemFrameRotation);
        getLogger().info("Block armor stand manipulation: " + blockArmorStandManipulation);
        getLogger().info("Block TNT ignite: " + blockTntIgnite);
        getLogger().info("Block player bed use: " + blockPlayerBedUse);
        getLogger().info("Block hanging break: " + blockHangingBreak);
        getLogger().info("Block vehicle place/break: " + blockVehiclePlaceBreak);
        getLogger().info("Block vehicle enter: " + blockVehicleEnter);
        getLogger().info("Block projectile use: " + blockProjectileUse);
        getLogger().info("Allow Elytra firework boost: " + allowElytraFireworkBoost);
        getLogger().info("Block lead use: " + blockLeadUse);
        getLogger().info("Block name tag use: " + blockNameTagUse);
        getLogger().info("Read-only interactions: " + blockReadonlyInteractions);
        getLogger().info("Update checker enabled: " + updateCheckerEnabled);
        getLogger().info("Notify admins about updates: " + notifyAdminsAboutUpdates);
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
            getLogger().warning("Could not load default config.yml from plugin jar. Config template update skipped.");
            return;
        }

        String defaultTemplate = loadDefaultConfigTextFromJar();

        if (defaultTemplate == null || defaultTemplate.isBlank()) {
            getLogger().warning("Could not load default config.yml text from plugin jar. Config template update skipped.");
            return;
        }

        YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);
        Set<String> protectedConfigKeys = loadProtectedConfigKeys(existingConfig, defaultConfig);

        boolean updateListsOnNextReload = existingConfig.getBoolean(
                "update-lists-on-next-reload",
                defaultConfig.getBoolean("update-lists-on-next-reload", false)
        );

        if (updateListsOnNextReload) {
            boolean listsChanged = copyMissingListValues(defaultConfig, existingConfig, protectedConfigKeys);

            existingConfig.set("update-lists-on-next-reload", false);

            if (listsChanged) {
                getLogger().info("Config lists updated with missing default values.");
            } else {
                getLogger().info("Config list update requested, but no missing list values were found.");
            }
        }

        /*
         * During DEV builds we keep config-version at the value from the default
         * config.yml bundled in the plugin jar.
         *
         * For the current 1.0.x DEV cycle this remains config-version: 7.
         */
        int defaultVersion = defaultConfig.getInt("config-version", 7);
        existingConfig.set("config-version", defaultVersion);

        String currentConfigText = readTextFile(configFile);
        String rewrittenConfig = rewriteConfigUsingDefaultTemplate(defaultTemplate, existingConfig, defaultConfig);

        if (normalizeLineEndings(currentConfigText).equals(normalizeLineEndings(rewrittenConfig))) {
            return;
        }

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
            Files.writeString(configFile.toPath(), rewrittenConfig, StandardCharsets.UTF_8);
            getLogger().info("config.yml updated from reference template. Config version: " + defaultVersion);
        } catch (Exception ex) {
            getLogger().severe("Could not save template-updated config.yml: " + ex.getMessage());
        }
    }

    private String loadDefaultConfigTextFromJar() {
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                return null;
            }

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            getLogger().warning("Could not read default config.yml text from plugin jar: " + ex.getMessage());
            return null;
        }
    }

    private String readTextFile(File file) {
        if (file == null || !file.exists()) {
            return "";
        }

        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            getLogger().warning("Could not read existing config.yml text: " + ex.getMessage());
            return "";
        }
    }

    private String normalizeLineEndings(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private String rewriteConfigUsingDefaultTemplate(
            String defaultTemplate,
            YamlConfiguration existingConfig,
            YamlConfiguration defaultConfig
    ) {
        List<String> templateLines = new ArrayList<>(List.of(defaultTemplate.split("\\R", -1)));
        List<String> outputLines = new ArrayList<>();

        String currentTopLevelSection = null;

        for (int i = 0; i < templateLines.size(); i++) {
            String line = templateLines.get(i);

            if (isConfigVersionLine(line)) {
                continue;
            }

            String topLevelKey = getTopLevelKey(line);

            if (topLevelKey != null) {
                currentTopLevelSection = null;

                if ("locked-worlds".equals(topLevelKey)) {
                    List<String> listBlock = collectIndentedBlock(templateLines, i);
                    outputLines.addAll(renderLockedWorldsBlock(existingConfig));
                    i += listBlock.size();
                    continue;
                }

                if (isListPath(topLevelKey, existingConfig, defaultConfig)) {
                    List<String> listBlock = collectIndentedBlock(templateLines, i);
                    outputLines.addAll(renderListBlock(topLevelKey, listBlock, existingConfig, defaultConfig));
                    i += listBlock.size();
                    continue;
                }

                if (isSectionPath(topLevelKey, existingConfig, defaultConfig)) {
                    currentTopLevelSection = topLevelKey;
                    outputLines.add(line);
                    continue;
                }

                if (isScalarPath(topLevelKey, existingConfig, defaultConfig)) {
                    outputLines.add(renderScalarLine(line, topLevelKey, existingConfig, defaultConfig));
                    continue;
                }
            }

            String nestedKey = getNestedKey(line);

            if (nestedKey != null && currentTopLevelSection != null) {
                String fullPath = currentTopLevelSection + "." + nestedKey;

                if (isScalarPath(fullPath, existingConfig, defaultConfig)) {
                    outputLines.add(renderScalarLine(line, fullPath, existingConfig, defaultConfig));
                    continue;
                }
            }

            if (isNewTopLevelCommentOrBlank(line)) {
                currentTopLevelSection = null;
            }

            outputLines.add(line);
        }

        removeTrailingBlankLines(outputLines);

        int configVersion = existingConfig.getInt(
                "config-version",
                defaultConfig.getInt("config-version", 7)
        );

        outputLines.add("");
        outputLines.add("config-version: " + configVersion);

        return String.join(System.lineSeparator(), outputLines) + System.lineSeparator();
    }

    private List<String> renderLockedWorldsBlock(YamlConfiguration existingConfig) {
        List<String> result = new ArrayList<>();
        List<String> worlds = existingConfig.getStringList("locked-worlds");

        if (worlds.isEmpty()) {
            result.add("locked-worlds: []");
            return result;
        }

        result.add("locked-worlds:");

        for (String world : worlds) {
            if (world == null || world.isBlank()) {
                continue;
            }

            result.add("  - " + world.trim());
        }

        if (result.size() == 1) {
            result.clear();
            result.add("locked-worlds: []");
        }

        return result;
    }

    private boolean isConfigVersionLine(String line) {
        if (line == null) {
            return false;
        }

        String trimmed = line.trim();

        if (trimmed.startsWith("#")) {
            return false;
        }

        return !line.startsWith(" ")
                && !line.startsWith("\t")
                && trimmed.matches("^config-version\\s*:.*$");
    }

    private String getTopLevelKey(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        if (line.startsWith(" ") || line.startsWith("\t")) {
            return null;
        }

        String trimmed = line.trim();

        if (trimmed.startsWith("#")) {
            return null;
        }

        int colonIndex = trimmed.indexOf(':');

        if (colonIndex <= 0) {
            return null;
        }

        return trimmed.substring(0, colonIndex).trim();
    }

    private String getNestedKey(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        if (!line.startsWith("  ") || line.startsWith("    ")) {
            return null;
        }

        String trimmed = line.trim();

        if (trimmed.startsWith("#") || trimmed.startsWith("-")) {
            return null;
        }

        int colonIndex = trimmed.indexOf(':');

        if (colonIndex <= 0) {
            return null;
        }

        return trimmed.substring(0, colonIndex).trim();
    }

    private boolean isNewTopLevelCommentOrBlank(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }

        return !line.startsWith(" ") && !line.startsWith("\t") && line.trim().startsWith("#");
    }

    private boolean isListPath(String path, YamlConfiguration existingConfig, YamlConfiguration defaultConfig) {
        return existingConfig.isList(path) || defaultConfig.isList(path);
    }

    private boolean isSectionPath(String path, YamlConfiguration existingConfig, YamlConfiguration defaultConfig) {
        return existingConfig.isConfigurationSection(path) || defaultConfig.isConfigurationSection(path);
    }

    private boolean isScalarPath(String path, YamlConfiguration existingConfig, YamlConfiguration defaultConfig) {
        if (isListPath(path, existingConfig, defaultConfig)) {
            return false;
        }

        if (isSectionPath(path, existingConfig, defaultConfig)) {
            return false;
        }

        return existingConfig.contains(path) || defaultConfig.contains(path);
    }

    private List<String> collectIndentedBlock(List<String> lines, int keyLineIndex) {
        List<String> block = new ArrayList<>();

        for (int i = keyLineIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);

            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                break;
            }

            block.add(line);
        }

        return block;
    }

    private List<String> renderListBlock(
            String path,
            List<String> templateBlock,
            YamlConfiguration existingConfig,
            YamlConfiguration defaultConfig
    ) {
        List<String> result = new ArrayList<>();

        List<String> existingValues = getStringListPreservingValues(existingConfig, defaultConfig, path);
        List<String> defaultValues = defaultConfig.getStringList(path);

        if (existingValues.isEmpty()) {
            result.add(path + ": []");
            return result;
        }

        result.add(path + ":");

        Set<String> existingUpper = new LinkedHashSet<>();
        for (String value : existingValues) {
            existingUpper.add(value.toUpperCase(Locale.ROOT));
        }

        Set<String> renderedUpper = new LinkedHashSet<>();
        Set<String> defaultUpper = new LinkedHashSet<>();

        for (String value : defaultValues) {
            defaultUpper.add(value.toUpperCase(Locale.ROOT));
        }

        boolean renderedAnyDefaultItem = false;

        for (String blockLine : templateBlock) {
            String trimmed = blockLine.trim();

            if (trimmed.startsWith("- ")) {
                String itemValue = trimmed.substring(2).trim();
                String itemUpper = itemValue.toUpperCase(Locale.ROOT);

                if (existingUpper.contains(itemUpper)) {
                    result.add(blockLine);
                    renderedUpper.add(itemUpper);
                    renderedAnyDefaultItem = true;
                }

                continue;
            }

            /*
             * Keep comments and blank lines from the reference config.
             * This preserves the explanation/category structure.
             */
            result.add(blockLine);
        }

        List<String> customValues = new ArrayList<>();

        for (String value : existingValues) {
            String valueUpper = value.toUpperCase(Locale.ROOT);

            if (!renderedUpper.contains(valueUpper) && !defaultUpper.contains(valueUpper)) {
                customValues.add(value);
            }
        }

        if (!customValues.isEmpty()) {
            if (renderedAnyDefaultItem) {
                result.add("");
            }

            result.add("  # Custom values preserved from existing config.yml");
            for (String customValue : customValues) {
                result.add("  - " + customValue);
            }
        }

        removeTrailingBlankLines(result);

        return result;
    }

    private List<String> getStringListPreservingValues(
            YamlConfiguration existingConfig,
            YamlConfiguration defaultConfig,
            String path
    ) {
        List<String> values = new ArrayList<>();

        if (existingConfig.isList(path)) {
            List<?> existingList = existingConfig.getList(path);

            if (existingList == null) {
                return values;
            }

            for (Object rawValue : existingList) {
                if (rawValue == null) {
                    continue;
                }

                String value = String.valueOf(rawValue).trim();

                if (!value.isBlank()) {
                    values.add(value);
                }
            }

            return values;
        }

        if (defaultConfig.isList(path)) {
            values.addAll(defaultConfig.getStringList(path));
        }

        return values;
    }

    private String renderScalarLine(
            String originalLine,
            String path,
            YamlConfiguration existingConfig,
            YamlConfiguration defaultConfig
    ) {
        Object value;

        if (existingConfig.contains(path)) {
            value = existingConfig.get(path);
        } else {
            value = defaultConfig.get(path);
        }

        String keyName = path.contains(".")
                ? path.substring(path.lastIndexOf('.') + 1)
                : path;

        String indent = getLeadingWhitespace(originalLine);

        return indent + keyName + ": " + formatYamlScalar(value);
    }

    private String getLeadingWhitespace(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }

        int index = 0;

        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }

        return line.substring(0, index);
    }

    private String formatYamlScalar(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }

        String stringValue = String.valueOf(value);

        if (stringValue.isBlank()) {
            return "\"\"";
        }

        if (stringValue.matches("^[A-Za-z0-9_./:+\\-]+$")) {
            return stringValue;
        }

        return "\"" + stringValue
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private void removeTrailingBlankLines(List<String> lines) {
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
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

        String defaultTemplate = loadDefaultTextFromJar(fileName);

        if (defaultTemplate == null || defaultTemplate.isBlank()) {
            getLogger().warning("Could not load default " + fileName + " text from plugin jar. Missing key update skipped.");
            return;
        }

        List<String> addedKeys = new ArrayList<>();
        List<String> preservedKeys = new ArrayList<>();
        List<String> customKeys = new ArrayList<>();

        for (String key : defaultMessages.getKeys(false)) {
            if (existingMessages.contains(key)) {
                preservedKeys.add(key);
                continue;
            }

            existingMessages.set(key, defaultMessages.get(key));
            addedKeys.add(key);
        }

        int defaultFormatVersion = defaultMessages.getInt("messages-format-version", 1);
        int existingFormatVersion = existingMessages.getInt("messages-format-version", 1);

        if (existingFormatVersion < defaultFormatVersion) {
            existingMessages.set("messages-format-version", defaultFormatVersion);

            if (!addedKeys.contains("messages-format-version") && !preservedKeys.contains("messages-format-version")) {
                addedKeys.add("messages-format-version");
            }
        }

        for (String key : existingMessages.getKeys(false)) {
            if (!defaultMessages.contains(key)) {
                customKeys.add(key);
            }
        }

        String rewrittenMessages = rewriteMessagesUsingDefaultTemplate(defaultTemplate, existingMessages, defaultMessages, customKeys);
        String currentMessagesText = readTextFile(messageFile);

        if (normalizeLineEndings(currentMessagesText).equals(normalizeLineEndings(rewrittenMessages))) {
            return;
        }

        int maxBackups = getConfig().getInt("max-config-backups", 10);
        backupConfigFile(messageFile, maxBackups, fileName.replace(".yml", "") + "-before-auto-update-");

        try {
            Files.writeString(messageFile.toPath(), rewrittenMessages, StandardCharsets.UTF_8);
            getLogger().info(fileName + " updated with missing message keys while preserving existing values.");
            writeMessagesUpdateReport(fileName, addedKeys, preservedKeys, customKeys);
        } catch (Exception ex) {
            getLogger().severe("Could not save updated " + fileName + ": " + ex.getMessage());
        }
    }

    private String loadDefaultTextFromJar(String resourceName) {
        try (InputStream stream = getResource(resourceName)) {
            if (stream == null) {
                return null;
            }

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            getLogger().warning("Could not read default text resource " + resourceName + ": " + ex.getMessage());
            return null;
        }
    }

    private String rewriteMessagesUsingDefaultTemplate(
            String defaultTemplate,
            YamlConfiguration existingMessages,
            YamlConfiguration defaultMessages,
            List<String> customKeys
    ) {
        List<String> templateLines = new ArrayList<>(List.of(defaultTemplate.split("\\R", -1)));
        List<String> outputLines = new ArrayList<>();
        Set<String> renderedKeys = new LinkedHashSet<>();

        for (String line : templateLines) {
            String key = getTopLevelKey(line);

            if (key == null) {
                outputLines.add(line);
                continue;
            }

            if (existingMessages.contains(key) || defaultMessages.contains(key)) {
                outputLines.add(renderScalarLine(line, key, existingMessages, defaultMessages));
                renderedKeys.add(key);
            } else {
                outputLines.add(line);
            }
        }

        List<String> missingRenderedDefaults = new ArrayList<>();

        for (String key : defaultMessages.getKeys(false)) {
            if (!renderedKeys.contains(key)) {
                missingRenderedDefaults.add(key);
            }
        }

        if (!missingRenderedDefaults.isEmpty()) {
            removeTrailingBlankLines(outputLines);
            outputLines.add("");
            outputLines.add("# Missing default message keys added automatically");

            for (String key : missingRenderedDefaults) {
                outputLines.add(key + ": " + formatYamlScalar(existingMessages.get(key, defaultMessages.get(key))));
                renderedKeys.add(key);
            }
        }

        List<String> preservedCustomKeys = new ArrayList<>();

        for (String key : customKeys) {
            if (!renderedKeys.contains(key) && existingMessages.contains(key)) {
                preservedCustomKeys.add(key);
            }
        }

        if (!preservedCustomKeys.isEmpty()) {
            removeTrailingBlankLines(outputLines);
            outputLines.add("");
            outputLines.add("# Custom message keys preserved from existing file");

            for (String key : preservedCustomKeys) {
                outputLines.add(key + ": " + formatYamlScalar(existingMessages.get(key)));
            }
        }

        removeTrailingBlankLines(outputLines);

        return String.join(System.lineSeparator(), outputLines) + System.lineSeparator();
    }

    private void writeMessagesUpdateReport(
            String fileName,
            List<String> addedKeys,
            List<String> preservedKeys,
            List<String> customKeys
    ) {
        File logFolder = new File(getDataFolder(), "logs");

        if (!logFolder.exists() && !logFolder.mkdirs()) {
            getLogger().warning("Could not create logs folder for messages update report.");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File reportFile = new File(logFolder, "messages-update-" + fileName.replace(".yml", "") + "-" + timestamp + ".log");

        StringBuilder report = new StringBuilder();
        report.append("MuseumWorld Messages Update Report").append(System.lineSeparator());
        report.append("Generated: ").append(timestamp).append(System.lineSeparator());
        report.append("File: ").append(fileName).append(System.lineSeparator());
        report.append(System.lineSeparator());

        report.append("Added missing keys:").append(System.lineSeparator());
        appendReportList(report, addedKeys);

        report.append(System.lineSeparator());
        report.append("Preserved existing keys:").append(System.lineSeparator());
        appendReportList(report, preservedKeys);

        report.append(System.lineSeparator());
        report.append("Custom keys kept:").append(System.lineSeparator());
        appendReportList(report, customKeys);

        report.append(System.lineSeparator());
        report.append("Overwritten text values:").append(System.lineSeparator());
        report.append("- none").append(System.lineSeparator());

        try {
            Files.writeString(reportFile.toPath(), report.toString(), StandardCharsets.UTF_8);
            getLogger().info("Messages update report saved: logs/" + reportFile.getName());
        } catch (Exception ex) {
            getLogger().warning("Could not write messages update report: " + ex.getMessage());
        }
    }

    private void appendReportList(StringBuilder report, List<String> values) {
        if (values == null || values.isEmpty()) {
            report.append("- none").append(System.lineSeparator());
            return;
        }

        for (String value : values) {
            report.append("- ").append(value).append(System.lineSeparator());
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

    private void startMetrics() {
        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
            getLogger().info("bStats metrics enabled.");
        } catch (Exception ex) {
            getLogger().warning("Could not start bStats metrics: " + ex.getMessage());
        }
    }

    private void checkForUpdatesAsync() {
        if (!updateCheckerEnabled) {
            getLogger().info("Update checker is disabled in config.yml.");
            return;
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(UPDATE_CHECKER_URL))
                    .header("Accept", "application/json")
                    .header("User-Agent", "MuseumWorld/" + getPluginMeta().getVersion())
                    .GET()
                    .build();
        } catch (Exception ex) {
            getLogger().warning("Could not create update checker request: " + ex.getMessage());
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                handleUpdateResponse(response.statusCode(), response.body());
            } catch (Exception ex) {
                getLogger().warning("Could not check for MuseumWorld updates: " + ex.getMessage());
            }
        });
    }

    private void handleUpdateResponse(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            getLogger().warning("Update checker returned HTTP " + statusCode + ".");
            return;
        }

        Optional<UpdateInfo> updateInfo = parseUpdateInfo(body);

        if (updateInfo.isEmpty()) {
            getLogger().warning("Update checker could not find a version in the response.");
            return;
        }

        UpdateInfo info = updateInfo.get();
        String currentVersion = getPluginMeta().getVersion();
        int comparison = compareVersions(info.version(), currentVersion);

        if (comparison > 0) {
            updateAvailable = true;
            latestVersion = info.version();
            latestVersionUrl = info.url();

            getLogger().warning("A new MuseumWorld version is available: " + latestVersion + " (current: " + currentVersion + ")");
            if (!latestVersionUrl.isBlank()) {
                getLogger().warning("Download: " + latestVersionUrl);
            }
            return;
        }

        updateAvailable = false;
        latestVersion = info.version();
        latestVersionUrl = info.url();
        getLogger().info("MuseumWorld is up to date. Current: " + currentVersion + ", latest: " + info.version());
    }

    private Optional<UpdateInfo> parseUpdateInfo(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }

        String version = findJsonStringValue(body, "tag_name")
                .orElseGet(() -> findJsonStringValue(body, "version_number")
                        .orElseGet(() -> findJsonStringValue(body, "name").orElse("")));

        if (version.isBlank()) {
            return Optional.empty();
        }

        String url = findJsonStringValue(body, "html_url")
                .orElseGet(() -> findJsonStringValue(body, "url").orElse(""));

        return Optional.of(new UpdateInfo(version, url));
    }

    private Optional<String> findJsonStringValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\x22" + Pattern.quote(key) + "\\x22\\s*:\\s*\\x22([^\\x22]*)\\x22");
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return Optional.empty();
        }

        return Optional.of(unescapeJsonString(matcher.group(1)));
    }

    private String unescapeJsonString(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private int compareVersions(String latest, String current) {
        List<Integer> latestParts = semanticVersionParts(latest);
        List<Integer> currentParts = semanticVersionParts(current);
        int max = Math.max(latestParts.size(), currentParts.size());

        for (int i = 0; i < max; i++) {
            int latestPart = i < latestParts.size() ? latestParts.get(i) : 0;
            int currentPart = i < currentParts.size() ? currentParts.get(i) : 0;

            if (latestPart != currentPart) {
                return Integer.compare(latestPart, currentPart);
            }
        }

        return 0;
    }

    private List<Integer> semanticVersionParts(String version) {
        String normalized = normalizeVersion(version);
        List<Integer> parts = new ArrayList<>();

        for (String part : normalized.split("\\.")) {
            if (part.isBlank()) {
                continue;
            }

            try {
                parts.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }

        return parts;
    }

    private String normalizeVersion(String version) {
        if (version == null) {
            return "0";
        }

        String normalized = version.trim();

        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        int dashIndex = normalized.indexOf('-');
        if (dashIndex >= 0) {
            normalized = normalized.substring(0, dashIndex);
        }

        return normalized.replaceAll("[^0-9.]", "");
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
        blockItemDrop = getConfig().getBoolean("block-item-drop", true);
        blockItemPickup = getConfig().getBoolean("block-item-pickup", true);
        blockBucketUse = getConfig().getBoolean("block-bucket-use", true);
        blockFireUse = getConfig().getBoolean("block-fire-use", true);
        blockNaturalGrowth = getConfig().getBoolean("block-natural-growth", false);
        blockBoneMealUse = getConfig().getBoolean("block-bone-meal-use", true);
        blockPortalCreation = getConfig().getBoolean("block-portal-creation", true);
        blockItemFrameRotation = getConfig().getBoolean("block-item-frame-rotation", true);
        blockArmorStandManipulation = getConfig().getBoolean("block-armor-stand-manipulation", true);
        blockTntIgnite = getConfig().getBoolean("block-tnt-ignite", true);
        blockPlayerBedUse = getConfig().getBoolean("block-player-bed-use", true);
        blockHangingBreak = getConfig().getBoolean("block-hanging-break", true);
        blockVehiclePlaceBreak = getConfig().getBoolean("block-vehicle-place-break", true);
        blockVehicleEnter = getConfig().getBoolean("block-vehicle-enter", true);
        blockProjectileUse = getConfig().getBoolean("block-projectile-use", true);
        allowElytraFireworkBoost = getConfig().getBoolean("allow-elytra-firework-boost", true);
        blockLeadUse = getConfig().getBoolean("block-lead-use", true);
        blockNameTagUse = getConfig().getBoolean("block-name-tag-use", true);

        updateCheckerEnabled = getConfig().getBoolean("update-checker-enabled", true);
        notifyAdminsAboutUpdates = getConfig().getBoolean("notify-admins-about-updates", true);

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

        actionMessages.clear();

        msgBlocked = color(loadedMessages.getString(
                "blocked-message",
                "§6Museum world: §eThis action is not allowed here."
        ));
        String msgEntityDamage = color(loadedMessages.getString(
                "entity-damage-message",
                msgBlocked
        ));

        loadActionMessage(loadedMessages, "block-break-message", msgBlocked);
        loadActionMessage(loadedMessages, "block-place-message", msgBlocked);
        loadActionMessage(loadedMessages, "inventory-message", msgBlocked);
        loadActionMessage(loadedMessages, "readonly-block-message", msgBlocked);
        loadActionMessage(loadedMessages, "item-drop-message", msgBlocked);
        loadActionMessage(loadedMessages, "item-pickup-message", msgBlocked);
        loadActionMessage(loadedMessages, "entity-damage-message", msgEntityDamage);
        loadActionMessage(loadedMessages, "readonly-entity-message", msgBlocked);
        loadActionMessage(loadedMessages, "item-frame-message", msgBlocked);
        loadActionMessage(loadedMessages, "armor-stand-message", msgBlocked);
        loadActionMessage(loadedMessages, "hanging-entity-message", msgBlocked);
        loadActionMessage(loadedMessages, "vehicle-place-break-message", msgBlocked);
        loadActionMessage(loadedMessages, "vehicle-enter-message", msgBlocked);
        loadActionMessage(loadedMessages, "bucket-use-message", msgBlocked);
        loadActionMessage(loadedMessages, "fire-use-message", msgBlocked);
        loadActionMessage(loadedMessages, "tnt-ignite-message", msgBlocked);
        loadActionMessage(loadedMessages, "portal-create-message", msgBlocked);
        loadActionMessage(loadedMessages, "bone-meal-message", msgBlocked);
        loadActionMessage(loadedMessages, "bed-use-message", msgBlocked);
        loadActionMessage(loadedMessages, "projectile-use-message", msgBlocked);
        loadActionMessage(loadedMessages, "lead-use-message", msgBlocked);
        loadActionMessage(loadedMessages, "name-tag-use-message", msgBlocked);
    }

    private void loadActionMessage(YamlConfiguration messagesConfig, String key, String fallback) {
        actionMessages.put(key, color(messagesConfig.getString(key, fallback)));
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


    public boolean blockItemDrop() {
        return blockItemDrop;
    }

    public boolean blockItemPickup() {
        return blockItemPickup;
    }

    public boolean blockBucketUse() {
        return blockBucketUse;
    }

    public boolean blockFireUse() {
        return blockFireUse;
    }

    public boolean blockNaturalGrowth() {
        return blockNaturalGrowth;
    }

    public boolean blockBoneMealUse() {
        return blockBoneMealUse;
    }

    public boolean blockPortalCreation() {
        return blockPortalCreation;
    }

    public boolean blockItemFrameRotation() {
        return blockItemFrameRotation;
    }

    public boolean blockArmorStandManipulation() {
        return blockArmorStandManipulation;
    }

    public boolean blockTntIgnite() {
        return blockTntIgnite;
    }

    public boolean blockPlayerBedUse() {
        return blockPlayerBedUse;
    }

    public boolean blockHangingBreak() {
        return blockHangingBreak;
    }

    public boolean blockVehiclePlaceBreak() {
        return blockVehiclePlaceBreak;
    }

    public boolean blockVehicleEnter() {
        return blockVehicleEnter;
    }

    public boolean blockProjectileUse() {
        return blockProjectileUse;
    }

    public boolean allowElytraFireworkBoost() {
        return allowElytraFireworkBoost;
    }

    public boolean blockLeadUse() {
        return blockLeadUse;
    }

    public boolean blockNameTagUse() {
        return blockNameTagUse;
    }

    public Set<EntityType> blockedEntityTypes() {
        return blockedEntityTypes;
    }

    public boolean blockReadonlyInteractions() {
        return blockReadonlyInteractions;
    }

    public Set<Material> readonlyBlocks() {
        return readonlyBlocks;
    }

    public Set<Material> viewOnlyContainers() {
        return viewOnlyContainers;
    }

    public Set<EntityType> readonlyEntities() {
        return readonlyEntities;
    }


    public String message(String key) {
        if (key == null || key.isBlank()) {
            return msgBlocked;
        }

        return actionMessages.getOrDefault(key, msgBlocked);
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

    public boolean updateCheckerEnabled() {
        return updateCheckerEnabled;
    }

    public boolean notifyAdminsAboutUpdates() {
        return notifyAdminsAboutUpdates;
    }

    public boolean updateAvailable() {
        return updateAvailable;
    }

    public String latestVersion() {
        return latestVersion == null ? "" : latestVersion;
    }

    public String latestVersionUrl() {
        return latestVersionUrl == null ? "" : latestVersionUrl;
    }

    private record UpdateInfo(String version, String url) {
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