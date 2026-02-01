package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    private File arenaModuleFile;
    private FileConfiguration arenaModule;
    private File borderModuleFile;
    private FileConfiguration borderModule;
    private File eventsModuleFile;
    private FileConfiguration eventsModule;
    private File itemsModuleFile;
    private FileConfiguration itemsModule;
    private File databaseModuleFile;
    private FileConfiguration databaseModule;
    private File mapsModuleFile;
    private FileConfiguration mapsModule;
    private File alliesModuleFile;
    private FileConfiguration alliesModule;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadModularConfigs(false);
    }

    public List<String> reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        return loadModularConfigs(true);
    }

    private List<String> loadModularConfigs(boolean verbose) {
        List<String> statuses = new ArrayList<>();
        statuses.add("config.yml");

        arenaModuleFile = new File(plugin.getDataFolder(), "config-modules/arena.yml");
        arenaModule = loadModule(arenaModuleFile, "config-modules/arena.yml", "arena.yml", statuses);

        borderModuleFile = new File(plugin.getDataFolder(), "config-modules/border.yml");
        borderModule = loadModule(borderModuleFile, "config-modules/border.yml", "border.yml", statuses);

        eventsModuleFile = new File(plugin.getDataFolder(), "config-modules/events.yml");
        eventsModule = loadModule(eventsModuleFile, "config-modules/events.yml", "events.yml", statuses);

        itemsModuleFile = new File(plugin.getDataFolder(), "config-modules/items.yml");
        itemsModule = loadModule(itemsModuleFile, "config-modules/items.yml", "items.yml", statuses);

        databaseModuleFile = new File(plugin.getDataFolder(), "config-modules/database.yml");
        databaseModule = loadModule(databaseModuleFile, "config-modules/database.yml", "database.yml", statuses);

        mapsModuleFile = new File(plugin.getDataFolder(), "config-modules/maps.yml");
        mapsModule = loadModule(mapsModuleFile, "config-modules/maps.yml", "maps.yml", statuses);

        alliesModuleFile = new File(plugin.getDataFolder(), "config-modules/allies.yml");
        alliesModule = loadModule(alliesModuleFile, "config-modules/allies.yml", "allies.yml", statuses);

        if (verbose) {
            plugin.getLogger().info("模块化配置热加载完成 -> " + String.join(", ", statuses));
        }
        return statuses;
    }

    private FileConfiguration loadModule(File file, String resourcePath, String label, List<String> statuses) {
        FileConfiguration loaded = loadYaml(file, resourcePath);
        if (statuses != null) {
            statuses.add(label + (loaded != null ? "" : " (缺失，使用config.yml)"));
        }
        return loaded;
    }

    private FileConfiguration loadYaml(File target, String resourcePath) {
        try {
            if (!target.exists()) {
                plugin.saveResource(resourcePath, false);
            }
        } catch (IllegalArgumentException ignored) {}

        if (!target.exists()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(target);
    }

    private void saveModule(FileConfiguration module, File file, String label) {
        if (module == null || file == null) return;
        try {
            module.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("无法写入模块配置 " + label + " ：" + e.getMessage());
        }
    }

    private int readInt(FileConfiguration module, String modulePath, String legacyPath, int def) {
        if (module != null && module.contains(modulePath)) {
            return module.getInt(modulePath, def);
        }
        return config.getInt(legacyPath, def);
    }

    private long readLong(FileConfiguration module, String modulePath, String legacyPath, long def) {
        if (module != null && module.contains(modulePath)) {
            return module.getLong(modulePath, def);
        }
        return config.getLong(legacyPath, def);
    }

    private double readDouble(FileConfiguration module, String modulePath, String legacyPath, double def) {
        if (module != null && module.contains(modulePath)) {
            return module.getDouble(modulePath, def);
        }
        return config.getDouble(legacyPath, def);
    }

    private boolean readBoolean(FileConfiguration module, String modulePath, String legacyPath, boolean def) {
        if (module != null && module.contains(modulePath)) {
            return module.getBoolean(modulePath, def);
        }
        return config.getBoolean(legacyPath, def);
    }

    private String readString(FileConfiguration module, String modulePath, String legacyPath, String def) {
        if (module != null && module.contains(modulePath)) {
            return module.getString(modulePath, def);
        }
        return config.getString(legacyPath, def);
    }

    private List<String> readStringList(FileConfiguration module, String modulePath, String legacyPath) {
        List<String> list = null;
        if (module != null && module.contains(modulePath)) {
            list = module.getStringList(modulePath);
        } else if (config.isList(legacyPath)) {
            list = config.getStringList(legacyPath);
        }
        return list != null ? list : Collections.emptyList();
    }

    private ConfigurationSection readSection(FileConfiguration module, String modulePath, String legacyPath) {
        if (module != null && module.isConfigurationSection(modulePath)) {
            return module.getConfigurationSection(modulePath);
        }
        return config.getConfigurationSection(legacyPath);
    }

    public int getArenaRadius() { return readInt(arenaModule, "radius", "arena.radius", 48); }
    public int getMinPlayers() { return readInt(arenaModule, "min-players", "arena.min-players", 2); }
    public int getStartCountdown() { return readInt(arenaModule, "start-countdown", "arena.start-countdown", 10); }
    public int getAutoStartDelay() { return readInt(arenaModule, "auto-start-delay", "arena.auto-start-delay", 5); }
    public int getVoteDuration() { return readInt(arenaModule, "vote-duration", "arena.vote-duration", 15); }
    public double getBorderDamageAmount() { return readDouble(borderModule, "damage", "border.damage", 3.0); }
    public List<String> getItemBlacklist() { return new ArrayList<>(readStringList(itemsModule, "blacklist", "items.blacklist")); }
    public long getItemInterval() { return readLong(itemsModule, "interval_ticks", "items.interval_ticks", 100L); }
    public long getEventDelayMin() { return readLong(eventsModule, "delay_min_ticks", "events.delay_min_ticks", 600L); }
    public long getEventDelayMax() { return readLong(eventsModule, "delay_max_ticks", "events.delay_max_ticks", 1200L); }
    public long getEventDelayMinFinal() { return readLong(eventsModule, "delay_min_ticks_final_circle", "events.delay_min_ticks_final_circle", 200L); }
    public long getEventDelayMaxFinal() { return readLong(eventsModule, "delay_max_ticks_final_circle", "events.delay_max_ticks_final_circle", 600L); }
    public double getShrinkAmount() { return readDouble(borderModule, "shrink_amount_per_interval", "border.shrink_amount_per_interval", 4.0); }
    public long getShrinkInterval() { return readLong(borderModule, "shrink_interval_ticks", "border.shrink_interval_ticks", 100L); }
    public long getShrinkDelay() { return readLong(borderModule, "first_shrink_delay_ticks", "border.first_shrink_delay_ticks", 200L); }
    public double getMinBorderSize() { return readDouble(borderModule, "min_diameter", "border.min_diameter", 10.0); }
    
    /**
     * 获取初始边界大小（从 arena 配置中读取）
     */
    public double getInitialBorderSize() {
        // 从 arena 配置中读取初始半径，然后转换为直径
        int radius = getArenaRadius();
        return radius * 2.0;
    }

    public Map<Material, Integer> getItemWeights() {
        Map<Material, Integer> weights = new HashMap<>();
        ConfigurationSection weightsSection = readSection(itemsModule, "weights", "items.weights");

        if (weightsSection != null) {
            for (String key : weightsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    int weight = weightsSection.getInt(key, 1);
                    if (weight > 0) {
                        weights.put(material, weight);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的物品类型配置: " + key);
                }
            }
        }

        if (weights.isEmpty()) {
            plugin.getLogger().warning("配置文件中未找到任何物品权重配置！将使用默认物品。");
            weights.put(Material.IRON_SWORD, 10);
            weights.put(Material.BOW, 10);
            weights.put(Material.ARROW, 20);
            weights.put(Material.GOLDEN_APPLE, 5);
            weights.put(Material.COBBLESTONE, 15);
        }

        return weights;
    }

    public List<Material> getDroppableItems() {
        return new ArrayList<>(getItemWeights().keySet());
    }

    public int getItemWeight(Material material) {
        return getItemWeights().getOrDefault(material, 1);
    }

    public void saveSpawnLocation(Location location) {
        if (location == null) return;

        config.set("arena.spawn.world", location.getWorld().getName());
        config.set("arena.spawn.x", location.getX());
        config.set("arena.spawn.y", location.getY());
        config.set("arena.spawn.z", location.getZ());
        config.set("arena.spawn.yaw", location.getYaw());
        config.set("arena.spawn.pitch", location.getPitch());
        plugin.saveConfig();

        if (arenaModule != null) {
            if (!arenaModule.isConfigurationSection("spawn")) {
                arenaModule.createSection("spawn");
            }
            ConfigurationSection spawnSection = arenaModule.getConfigurationSection("spawn");
            if (spawnSection != null) {
                spawnSection.set("world", location.getWorld().getName());
                spawnSection.set("x", location.getX());
                spawnSection.set("y", location.getY());
                spawnSection.set("z", location.getZ());
                spawnSection.set("yaw", location.getYaw());
                spawnSection.set("pitch", location.getPitch());
                saveModule(arenaModule, arenaModuleFile, "arena.yml");
            }
        }

        plugin.getLogger().info("游戏出生点已保存：" +
            String.format("世界=%s, 坐标=(%.1f, %.1f, %.1f)",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()));
    }

    public Location loadSpawnLocation() {
        return loadSpawnLocation(false);
    }

    public Location loadSpawnLocation(boolean showLog) {
        ConfigurationSection spawnSection = readSection(arenaModule, "spawn", "arena.spawn");
        if (spawnSection == null || !spawnSection.contains("world")) {
            return null;
        }

        String worldName = spawnSection.getString("world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("配置文件中的世界 '" + worldName + "' 不存在！");
            return null;
        }

        double x = spawnSection.getDouble("x", 0.0);
        double y = spawnSection.getDouble("y", 64.0);
        double z = spawnSection.getDouble("z", 0.0);
        float yaw = (float) spawnSection.getDouble("yaw", 0.0);
        float pitch = (float) spawnSection.getDouble("pitch", 0.0);

        if (showLog) {
            plugin.getLogger().info("从配置文件加载游戏出生点：" +
                String.format("世界=%s, 坐标=(%.1f, %.1f, %.1f)",
                    worldName, x, y, z));
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    public List<String> getAvailableMaps() {
        List<String> maps = new ArrayList<>();

        if (mapsModule != null) {
            for (String key : mapsModule.getKeys(false)) {
                if (mapsModule.isConfigurationSection(key)) {
                    maps.add(key);
                }
            }
            if (!maps.isEmpty()) {
                return maps;
            }
        }

        ConfigurationSection mapsSection = config.getConfigurationSection("arena.maps");
        if (mapsSection != null) {
            maps.addAll(mapsSection.getKeys(false));
        }
        return maps;
    }

    private ConfigurationSection getMapSection(String mapId) {
        if (mapId == null) return null;
        if (mapsModule != null && mapsModule.isConfigurationSection(mapId)) {
            return mapsModule.getConfigurationSection(mapId);
        }
        return config.getConfigurationSection("arena.maps." + mapId);
    }

    public String getMapName(String mapId) {
        ConfigurationSection section = getMapSection(mapId);
        if (section != null) {
            return section.getString("name", mapId);
        }
        return mapId;
    }

    public Location loadMapSpawnLocation(String mapId) {
        ConfigurationSection section = getMapSection(mapId);
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("地图 '" + mapId + "' 的世界 '" + worldName + "' 不存在！");
            return null;
        }

        double x = section.getDouble("x", 0.0);
        double y = section.getDouble("y", 64.0);
        double z = section.getDouble("z", 0.0);
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean mapExists(String mapId) {
        return getMapSection(mapId) != null;
    }

    public String getMapWorldKey(String mapId) {
        ConfigurationSection section = getMapSection(mapId);
        if (section != null) {
            return section.getString("world");
        }
        return null;
    }

    public boolean isWorldInstancingEnabled() {
        return readBoolean(arenaModule, "world-instancing.enabled", "arena.world-instancing.enabled", true);
    }

    public boolean isWorldInstancingAutoCleanup() {
        return readBoolean(arenaModule, "world-instancing.auto-cleanup", "arena.world-instancing.auto-cleanup", true);
    }

    public Location loadLobbyLocation() {
        ConfigurationSection lobbySection = readSection(arenaModule, "lobby", "arena.lobby");
        if (lobbySection == null || !lobbySection.contains("world")) {
            return null;
        }

        if (!lobbySection.getBoolean("enabled", true)) {
            return null;
        }

        String worldName = lobbySection.getString("world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("配置文件中的大厅世界 '" + worldName + "' 不存在！");
            return null;
        }

        double x = lobbySection.getDouble("x", 0.0);
        double y = lobbySection.getDouble("y", 64.0);
        double z = lobbySection.getDouble("z", 0.0);
        float yaw = (float) lobbySection.getDouble("yaw", 0.0);
        float pitch = (float) lobbySection.getDouble("pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isLobbyEnabled() {
        ConfigurationSection lobbySection = readSection(arenaModule, "lobby", "arena.lobby");
        if (lobbySection != null && lobbySection.contains("enabled")) {
            return lobbySection.getBoolean("enabled");
        }
        return config.getBoolean("arena.lobby.enabled", true);
    }

    public int getMapStartCountdown(String mapId) {
        ConfigurationSection section = getMapSection(mapId);
        if (section != null && section.contains("start-countdown")) {
            return section.getInt("start-countdown");
        }
        return getStartCountdown();
    }

    public int getMapMinPlayers(String mapId) {
        ConfigurationSection section = getMapSection(mapId);
        if (section != null && section.contains("min-players")) {
            return section.getInt("min-players");
        }
        return getMinPlayers();
    }

    public int getMapRadius(String mapId) {
        ConfigurationSection section = getMapSection(mapId);
        if (section != null && section.contains("radius")) {
            return section.getInt("radius");
        }
        return getArenaRadius();
    }

    public int getMapVoteDuration(String mapId) {
        ConfigurationSection section = getMapSection(mapId);
        if (section != null && section.contains("vote-duration")) {
            return section.getInt("vote-duration");
        }
        return getVoteDuration();
    }

    public boolean hasItemsConfig() {
        ConfigurationSection section = readSection(itemsModule, "weights", "items.weights");
        return section != null && !section.getKeys(false).isEmpty();
    }

    private ConfigurationSection getDatabaseSectionInternal() {
        if (databaseModule != null) {
            return databaseModule;
        }
        return config.getConfigurationSection("database");
    }

    public String getDatabaseType() {
        return readString(databaseModule, "type", "database.type", "SQLITE");
    }

    public String getSqliteFilePath() {
        ConfigurationSection dbSection = getDatabaseSectionInternal();
        if (dbSection != null) {
            ConfigurationSection sqlite = dbSection.getConfigurationSection("sqlite");
            if (sqlite != null) {
                return sqlite.getString("file", "plugins/RandomItemPVP/data.db");
            }
        }
        return config.getString("database.sqlite.file", "plugins/RandomItemPVP/data.db");
    }

    public ConfigurationSection getMysqlSection() {
        ConfigurationSection dbSection = getDatabaseSectionInternal();
        if (dbSection != null) {
            return dbSection.getConfigurationSection("mysql");
        }
        return null;
    }
    
    // ==========================================
    // 盟友配置读取方法
    // ==========================================
    
    /**
     * 检查是否启用了盟友功能
     */
    public boolean isAlliesEnabled() {
        if (alliesModule != null) {
            return alliesModule.getBoolean("enabled", true);
        }
        return config.getBoolean("allies.enabled", true);
    }
    
    /**
     * 获取游戏进度阈值（边界缩小到多少时开始出现盟友刷怪蛋）
     */
    public double getAllyGameProgressThreshold() {
        if (alliesModule != null) {
            return alliesModule.getDouble("game_progress_threshold", 0.3);
        }
        return config.getDouble("allies.game_progress_threshold", 0.3);
    }
    
    /**
     * 获取每个空投箱中盟友刷怪蛋的数量
     */
    public int getAllySpawnEggsPerAirdrop() {
        if (alliesModule != null) {
            return alliesModule.getInt("spawn_eggs_per_airdrop", 1);
        }
        return config.getInt("allies.spawn_eggs_per_airdrop", 1);
    }
    
    /**
     * 获取盟友生物类型和权重配置
     */
    public Map<EntityType, Integer> getAllyWeights() {
        Map<EntityType, Integer> weights = new HashMap<>();
        ConfigurationSection alliesSection = null;
        
        if (alliesModule != null) {
            alliesSection = alliesModule.getConfigurationSection("allies");
        }
        if (alliesSection == null) {
            alliesSection = config.getConfigurationSection("allies.allies");
        }
        
        if (alliesSection != null) {
            for (String key : alliesSection.getKeys(false)) {
                try {
                    EntityType type = EntityType.valueOf(key);
                    int weight = alliesSection.getInt(key, 1);
                    if (weight > 0) {
                        weights.put(type, weight);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的盟友生物类型: " + key);
                }
            }
        }
        
        return weights;
    }
    
    /**
     * 获取盟友生命值倍数
     */
    public double getAllyHealthMultiplier() {
        ConfigurationSection behaviorSection = null;
        if (alliesModule != null) {
            behaviorSection = alliesModule.getConfigurationSection("behavior");
        }
        if (behaviorSection == null) {
            behaviorSection = config.getConfigurationSection("allies.behavior");
        }
        
        if (behaviorSection != null) {
            return behaviorSection.getDouble("health_multiplier", 1.5);
        }
        return config.getDouble("allies.behavior.health_multiplier", 1.5);
    }
    
    /**
     * 获取盟友攻击力倍数
     */
    public double getAllyDamageMultiplier() {
        ConfigurationSection behaviorSection = null;
        if (alliesModule != null) {
            behaviorSection = alliesModule.getConfigurationSection("behavior");
        }
        if (behaviorSection == null) {
            behaviorSection = config.getConfigurationSection("allies.behavior");
        }
        
        if (behaviorSection != null) {
            return behaviorSection.getDouble("damage_multiplier", 1.2);
        }
        return config.getDouble("allies.behavior.damage_multiplier", 1.2);
    }
}
