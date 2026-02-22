 package org.luminolcraft.randomitempvp;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.util.BlockVector;

public class MapResetter implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final World world;
    private final Location center;
    private final double radius;
    private final File dataFile;
    private final String mapId;
    
    // 数据存储 - 只在需要时加载，大部分时间为null
    private transient Map<BlockPos, BlockData> originalBlockData;
    private transient Map<BlockPos, BlockState> originalBlockStates;
    
    private boolean isProtected = false;
    
    /**
     * 轻量级的方块位置类，只包含整数坐标，用于作为Map的key
     */
    public static class BlockPos {
        private final int x;
        private final int y;
        private final int z;
        
        public BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public BlockPos(Location loc) {
            this.x = loc.getBlockX();
            this.y = loc.getBlockY();
            this.z = loc.getBlockZ();
        }
        
        public int getX() {
            return x;
        }
        
        public int getY() {
            return y;
        }
        
        public int getZ() {
            return z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockPos blockPos = (BlockPos) o;
            return x == blockPos.x && y == blockPos.y && z == blockPos.z;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
        
        @Override
        public String toString() {
            return x + "," + y + "," + z;
        }
        
        public Location toLocation(World world) {
            return new Location(world, x, y, z);
        }
    }
    
    public MapResetter(JavaPlugin plugin, World world, Location center, double radius, String mapId) {
        this.plugin = plugin;
        this.config = RandomItemPVP.getInstance().getConfigManager();
        this.world = world;
        this.center = center.clone();
        this.radius = radius;
        this.mapId = mapId != null ? mapId : "default";
        // 生成唯一的文件名，与地图ID关联
        this.dataFile = new File(plugin.getDataFolder(), "map_data_" + this.mapId + ".json");
        
        // 初始化数据存储 - 大部分时间为null，只在需要时加载
        this.originalBlockData = null;
        this.originalBlockStates = null;
        
        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // 检查是否存在对应的JSON文件
        if (dataFile.exists()) {
            // 存在，只记录日志，不立即加载
            plugin.getLogger().info("[地图重置] 发现地图 " + mapId + " 的JSON数据文件，将在需要时加载...");
        } else {
            // 不存在，需要保存初始状态
            plugin.getLogger().info("[地图重置] 未发现地图 " + mapId + " 的JSON数据文件，准备保存初始状态...");
        }
    }
    
    /**
     * 重载构造函数，保持向后兼容
     */
    public MapResetter(JavaPlugin plugin, World world, Location center, double radius) {
        this(plugin, world, center, radius, "default");
    }
    
    /**
     * 检查位置是否在重置区域内
     */
    public boolean isInRegion(Location loc) {
        if (loc.getWorld() != world) return false;
        // 只考虑 XZ 平面上的距离，忽略 Y 坐标
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= radius;
    }
    
    /**
     * 保存初始地图状态
     */
    public void saveInitialMapState() {
        saveInitialMapState(false);
    }
    
    /**
     * 保存初始地图状态
     * @param force 强制保存，即使文件已存在
     */
    public void saveInitialMapState(boolean force) {
        if (!config.isMapResetEnabled()) {
            plugin.getLogger().info("[地图重置] 地图重置已禁用，跳过保存初始状态");
            return;
        }
        
        // 检查JSON文件是否已存在，如果存在且不强制保存则跳过
        if (dataFile.exists() && !force) {
            plugin.getLogger().info("[地图重置] 地图 " + mapId + " 的JSON数据文件已存在，跳过保存初始状态");
            // 直接加载已存在的数据（内部会异步处理）
            loadDataFromFile();
            return;
        }
        
        // 强制使用异步执行，避免主线程阻塞
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("[地图重置] 开始保存初始地图状态...");
                
                // 确保数据存储已初始化
                if (originalBlockData == null) {
                    originalBlockData = new ConcurrentHashMap<>();
                    plugin.getLogger().info("[地图重置] 初始化 originalBlockData 为新的 ConcurrentHashMap");
                } else {
                    originalBlockData.clear();
                    plugin.getLogger().info("[地图重置] 清空现有的 originalBlockData");
                }
                
                if (originalBlockStates == null) {
                    originalBlockStates = new ConcurrentHashMap<>();
                    plugin.getLogger().info("[地图重置] 初始化 originalBlockStates 为新的 ConcurrentHashMap");
                } else {
                    originalBlockStates.clear();
                    plugin.getLogger().info("[地图重置] 清空现有的 originalBlockStates");
                }
                
                final int minX = (int) (center.getX() - radius);
                final int maxX = (int) (center.getX() + radius);
                final int minZ = (int) (center.getZ() - radius);
                final int maxZ = (int) (center.getZ() + radius);
                final int minY = world.getMinHeight();
                final int maxY = world.getMaxHeight();
                
                final AtomicInteger processedBlocks = new AtomicInteger(0);
                final AtomicInteger waterloggedBlocks = new AtomicInteger(0);
                final AtomicInteger waterBlocks = new AtomicInteger(0);
                final CountDownLatch latch = new CountDownLatch((maxX - minX + 1) * (maxZ - minZ + 1));
                
                // 遍历区域内的所有方块
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        // 检查该 XZ 坐标是否在圆形区域内
                        final Location tempLoc = new Location(world, x, 0, z);
                        if (!isInRegion(tempLoc)) {
                            latch.countDown();
                            continue;
                        }
                        
                        // 使用区域调度器加载区块和保存方块状态
                        final int finalX = x;
                        final int finalZ = z;
                        Bukkit.getRegionScheduler().run(plugin, tempLoc, regionTask -> {
                            try {
                                // 加载区块
                                Chunk chunk = tempLoc.getChunk();
                                if (!chunk.isLoaded()) {
                                    chunk.load();
                                }
                                
                                for (int y = minY; y < maxY; y++) {
                                    Location loc = new Location(world, finalX, y, finalZ);
                                    Block block = loc.getBlock();
                                    BlockPos blockPos = new BlockPos(loc);
                                    
                                    // 保存方块数据（克隆，包括 Waterlogged 和水流状态）
                                    BlockData blockData = block.getBlockData().clone();
                                    originalBlockData.put(blockPos, blockData);
                                    
                                    // 统计水相关方块
                                    if (blockData.getMaterial() == Material.WATER) {
                                        waterBlocks.incrementAndGet();
                                    } else if (blockData instanceof org.bukkit.block.data.Waterlogged) {
                                        org.bukkit.block.data.Waterlogged waterlogged = (org.bukkit.block.data.Waterlogged) blockData;
                                        if (waterlogged.isWaterlogged()) {
                                            waterloggedBlocks.incrementAndGet();
                                        }
                                    }
                                    
                                    // 保存方块实体数据（如容器、告示牌等）
                                    BlockState state = block.getState();
                                    if (state instanceof org.bukkit.block.Container || 
                                        state instanceof org.bukkit.block.Sign ||
                                        state instanceof org.bukkit.block.Bed ||
                                        state instanceof org.bukkit.block.BrewingStand ||
                                        state instanceof org.bukkit.block.Furnace ||
                                        state instanceof org.bukkit.block.EnchantingTable ||
                                        state instanceof org.bukkit.block.EndGateway ||
                                        state instanceof org.bukkit.block.Jukebox ||
                                        state instanceof org.bukkit.block.ShulkerBox ||
                                        state instanceof org.bukkit.block.Skull ||
                                        state instanceof org.bukkit.block.Beehive ||
                                        state instanceof org.bukkit.block.Bell ||
                                        state instanceof org.bukkit.block.Campfire ||
                                        state instanceof org.bukkit.block.Chest ||
                                        state instanceof org.bukkit.block.EnderChest ||
                                        state instanceof org.bukkit.block.Hopper ||
                                        state instanceof org.bukkit.block.Smoker ||
                                        state instanceof org.bukkit.block.BlastFurnace ||
                                        state instanceof org.bukkit.block.Lectern) {
                                        originalBlockStates.put(blockPos, block.getState());
                                    }
                                    
                                    int currentProcessed = processedBlocks.incrementAndGet();
                                    // if (currentProcessed % 1000 == 0) {
                                    //     plugin.getLogger().info("[地图重置] 已保存 " + currentProcessed + " 个方块状态");
                                    // }
                                }
                            } finally {
                                latch.countDown();
                            }
                        });
                    }
                }
                
                // 等待所有区域任务完成
                try {
                    latch.await(60, TimeUnit.SECONDS); // 最多等待60秒
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                // plugin.getLogger().info("[地图重置] 初始地图状态保存完成！总计 " + processedBlocks.get() + " 个方块");
                // plugin.getLogger().info("[地图重置] 其中包含 " + originalBlockStates.size() + " 个实体方块");
                // plugin.getLogger().info("[地图重置] 水相关统计：水源/水流 " + waterBlocks.get() + " 个，含水方块 " + waterloggedBlocks.get() + " 个");
                
                // 保存数据到文件
                if (config.isMapResetPersistenceEnabled()) {
                    saveDataToFile();
                    plugin.getLogger().info("[地图重置] 地图 " + mapId + " 的数据已保存为JSON文件：" + dataFile.getName());
                }
                
                // 启用保护机制
                if (config.isMapResetProtectionEnabled()) {
                    isProtected = true;
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * 提前保存地图数据为JSON文件
     */
    public void saveMapDataToJson() {
        // plugin.getLogger().info("[地图重置] 开始保存地图数据到JSON文件...");
        saveInitialMapState();
        // plugin.getLogger().info("[地图重置] 地图数据已保存到JSON文件: " + dataFile.getPath());
    }
    
    /**
     * 从JSON文件恢复地图
     */
    public void restoreMapFromJson() {
        plugin.getLogger().info("[地图重置] 开始从JSON文件恢复地图...");
        
        // 检查JSON文件是否存在
        if (!dataFile.exists()) {
            plugin.getLogger().warning("[地图重置] 没有初始JSON文件，无法恢复！");
            return;
        }
        
        // 1. 传送所有玩家出区域
        teleportPlayersOut();
        
        // 2. 清理区域内的非玩家实体
        clearEntities();
        
        // 3. 加载数据（异步加载，主线程恢复）
        loadDataFromFile();
        
        // 4. 延迟执行恢复操作，确保数据加载完成
        // 使用Folia区域调度器，更好地支持Folia线程模型
        Bukkit.getRegionScheduler().runDelayed(plugin, center, task -> {
            if (originalBlockData != null && !originalBlockData.isEmpty()) {
                restoreBlocks();
            } else {
                plugin.getLogger().warning("[地图重置] 从JSON文件加载数据失败，没有方块数据可恢复！");
                // 确保清理资源
                clearMaps();
            }
        }, 40); // 延迟2秒执行，确保数据加载完成
    }
    
    // 数据加载完成标志
    private final AtomicBoolean dataLoaded = new AtomicBoolean(false);
    
    /**
     * 重置地图到初始状态
     */
    public void resetMap() {
        resetMap(true);
    }
    
    /**
     * 重置地图到初始状态
     * @param log 是否输出日志
     */
    public void resetMap(boolean log) {
        if (log) {
            plugin.getLogger().info("[地图重置] 开始重置地图...");
        }
        
        // 检查JSON文件是否存在
        if (!dataFile.exists()) {
            plugin.getLogger().warning("[地图重置] 没有初始JSON文件，无法重置！");
            return;
        }
        
        // 重置数据加载标志
        dataLoaded.set(false);
        
        // 1. 传送所有玩家出区域
        teleportPlayersOut();
        
        // 2. 清理区域内的非玩家实体
        clearEntities();
        
        // 3. 加载数据（异步加载，主线程恢复）
        loadDataFromFile();
        
        // 4. 轮询检查数据是否加载完成，确保数据加载完成后再恢复方块
        // 使用Folia区域调度器，更好地支持Folia线程模型
        Bukkit.getRegionScheduler().runDelayed(plugin, center, task -> {
            checkDataLoadedAndRestore();
        }, 10); // 先延迟0.5秒检查
    }
    
    /**
     * 检查数据是否加载完成并恢复方块
     */
    private void checkDataLoadedAndRestore() {
        if (dataLoaded.get()) {
            // 数据已加载完成，开始恢复方块
            if (originalBlockData != null && !originalBlockData.isEmpty()) {
                restoreBlocks();
            } else {
                plugin.getLogger().warning("[地图重置] 从JSON文件加载数据失败，没有方块数据可恢复！");
                // 确保清理资源
                clearMaps();
            }
        } else {
            // 数据未加载完成，继续检查
            plugin.getLogger().info("[地图重置] 等待数据加载完成...");
            Bukkit.getRegionScheduler().runDelayed(plugin, center, task -> {
                checkDataLoadedAndRestore();
            }, 10); // 每0.5秒检查一次
        }
    }
    
    /**
     * 传送所有玩家出区域
     */
    private void teleportPlayersOut() {
        for (Player player : world.getPlayers()) {
            if (isInRegion(player.getLocation())) {
                // 传送玩家到世界出生点
                Location spawnLoc = world.getSpawnLocation();
                player.teleport(spawnLoc);
                player.sendMessage("§c[地图重置] 你已被传送出区域，地图正在重置...");
            }
        }
    }
    
    /**
     * 清理区域内的非玩家实体
     */
    private void clearEntities() {
        plugin.getLogger().info("[地图重置] 开始清理实体...");
        
        int minX = (int) (center.getX() - radius);
        int maxX = (int) (center.getX() + radius);
        int minZ = (int) (center.getZ() - radius);
        int maxZ = (int) (center.getZ() + radius);
        
        int clearedEntities = 0;
        int clearedLivingEntities = 0;
        
        // 遍历区域内的所有区块
        for (int x = minX; x <= maxX; x += 16) {
            for (int z = minZ; z <= maxZ; z += 16) {
                Location chunkLoc = new Location(world, x, 0, z);
                Chunk chunk = chunkLoc.getChunk();
                if (!chunk.isLoaded()) continue;
                
                // 获取区块内的所有实体
                for (Entity entity : chunk.getEntities()) {
                    // 跳过玩家
                    if (entity instanceof Player) continue;
                    
                    // 检查实体是否在区域内
                    if (isInRegion(entity.getLocation())) {
                        // 统计生物实体
                        if (entity instanceof org.bukkit.entity.LivingEntity) {
                            clearedLivingEntities++;
                        }
                        entity.remove();
                        clearedEntities++;
                    }
                }
            }
        }
        
        // plugin.getLogger().info("[地图重置] 已清理 " + clearedEntities + " 个实体，其中生物实体 " + clearedLivingEntities + " 个");
    }
    
    /**
     * 分批恢复方块，避免主线程阻塞
     */
    private void restoreBlocks() {
        plugin.getLogger().info("[地图重置] 开始恢复方块...");
        
        // 只缓存key列表，value按需从map获取
        List<BlockPos> blockDataKeys = new ArrayList<>(originalBlockData.keySet());
        List<BlockPos> blockStateKeys = new ArrayList<>(originalBlockStates.keySet());
        
        plugin.getLogger().info("[地图重置] 总计需要恢复 " + blockDataKeys.size() + " 个基本方块和 " + blockStateKeys.size() + " 个实体方块");
        
        final int batchSize = Math.min(1000, Math.max(500, config.getMapResetBatchSize())); // 每批500~1000个方块
        final int delay = Math.min(5, Math.max(1, config.getMapResetDelay())); // 延迟1~5 ticks
        final int totalBatches = (blockDataKeys.size() + batchSize - 1) / batchSize;
        
        plugin.getLogger().info("[地图重置] 基本方块分为 " + totalBatches + " 批恢复");
        
        // 创建任务队列
        final Queue<List<BlockPos>> taskQueue = new LinkedList<>();
        for (int i = 0; i < totalBatches; i++) {
            int start = i * batchSize;
            int end = Math.min(start + batchSize, blockDataKeys.size());
            taskQueue.offer(blockDataKeys.subList(start, end));
        }
        
        // 线程池大小（根据服务器性能调整）
        final int poolSize = Math.min(4, Runtime.getRuntime().availableProcessors());
        final AtomicInteger activeTasks = new AtomicInteger(0);
        final AtomicInteger completedBatches = new AtomicInteger(0);
        final AtomicInteger cleanupCounter = new AtomicInteger(0);
        final AtomicInteger skippedBlocks = new AtomicInteger(0);
        
        plugin.getLogger().info("[地图重置] 使用 " + poolSize + " 个线程处理方块恢复");
        
        // 启动线程池处理任务
        for (int i = 0; i < poolSize; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    while (!taskQueue.isEmpty()) {
                        final List<BlockPos> batchKeys = taskQueue.poll();
                        if (batchKeys == null) break;
                        
                        activeTasks.incrementAndGet();
                        
                        // 使用区域调度器处理每个方块，确保在正确的线程中执行
                        for (final BlockPos blockPos : batchKeys) {
                            // 安全获取blockData，避免空指针
                            final BlockData blockData;
                            if (originalBlockData != null) {
                                blockData = originalBlockData.get(blockPos);
                            } else {
                                blockData = null;
                            }
                            if (blockData == null) {
                                skippedBlocks.incrementAndGet();
                                continue;
                            }
                            
                            final Location loc = blockPos.toLocation(world);
                            Bukkit.getRegionScheduler().run(plugin, loc, regionTask -> {
                                Block block = loc.getBlock();
                                if (block != null) {
                                    try {
                                        // 使用 false 参数防止物理连锁反应
                                        block.setBlockData(blockData, false);
                                        // 特别处理含水方块，确保水状态正确
                                        if (blockData instanceof org.bukkit.block.data.Waterlogged) {
                                            org.bukkit.block.data.Waterlogged waterlogged = (org.bukkit.block.data.Waterlogged) blockData;
                                            if (waterlogged.isWaterlogged()) {
                                                // 确保含水状态正确应用
                                                BlockData updatedData = block.getBlockData();
                                                if (updatedData instanceof org.bukkit.block.data.Waterlogged) {
                                                    ((org.bukkit.block.data.Waterlogged) updatedData).setWaterlogged(true);
                                                    block.setBlockData(updatedData, false);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[地图重置] 恢复方块失败 (" + blockPos + "): " + e.getMessage());
                                    }
                                } else {
                                    skippedBlocks.incrementAndGet();
                                }
                            });
                        }
                        
                        int batchNum = completedBatches.incrementAndGet();
                        plugin.getLogger().info("[地图重置] 已完成第 " + batchNum + "/" + totalBatches + " 批方块恢复");
                        activeTasks.decrementAndGet();
                    }
                    
                    // 所有任务完成后恢复实体方块
                    if (activeTasks.get() == 0 && completedBatches.get() == totalBatches) {
                        // 确保只有一个线程执行清理操作
                        if (cleanupCounter.incrementAndGet() == 1) {
                            if (skippedBlocks.get() > 0) {
                                plugin.getLogger().warning("[地图重置] 跳过了 " + skippedBlocks.get() + " 个无效方块");
                            }
                            // 恢复实体方块
                            plugin.getLogger().info("[地图重置] 基本方块恢复完成，开始恢复实体方块...");
                            restoreBlockStates(blockStateKeys);
                        }
                    }
                }
            }.runTaskLater(plugin, i * delay);
        }
    }
    
    /**
     * 恢复实体方块（如容器、告示牌等）
     */
    private void restoreBlockStates(List<BlockPos> blockStateKeys) {
        plugin.getLogger().info("[地图重置] 开始恢复实体方块...");
        
        final int batchSize = Math.min(1000, Math.max(500, config.getMapResetBatchSize())); // 每批500~1000个方块
        final int delay = Math.min(5, Math.max(1, config.getMapResetDelay())); // 延迟1~5 ticks
        final int totalBatches = (blockStateKeys.size() + batchSize - 1) / batchSize;
        
        plugin.getLogger().info("[地图重置] 实体方块分为 " + totalBatches + " 批恢复");
        
        // 创建任务队列
        final Queue<List<BlockPos>> taskQueue = new LinkedList<>();
        for (int i = 0; i < totalBatches; i++) {
            int start = i * batchSize;
            int end = Math.min(start + batchSize, blockStateKeys.size());
            taskQueue.offer(blockStateKeys.subList(start, end));
        }
        
        // 线程池大小（根据服务器性能调整）
        final int poolSize = Math.min(4, Runtime.getRuntime().availableProcessors());
        final AtomicInteger activeTasks = new AtomicInteger(0);
        final AtomicInteger completedBatches = new AtomicInteger(0);
        final AtomicInteger skippedStates = new AtomicInteger(0);
        final AtomicInteger restoredContainers = new AtomicInteger(0);
        
        // 启动线程池处理任务
        for (int i = 0; i < poolSize; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    while (!taskQueue.isEmpty()) {
                        final List<BlockPos> batchKeys = taskQueue.poll();
                        if (batchKeys == null) break;
                        
                        activeTasks.incrementAndGet();
                        
                        // 使用区域调度器处理每个方块，确保在正确的线程中执行
                        for (final BlockPos blockPos : batchKeys) {
                            final BlockState state = originalBlockStates.get(blockPos);
                            if (state == null) {
                                skippedStates.incrementAndGet();
                                continue;
                            }
                            
                            final Location loc = blockPos.toLocation(world);
                            Bukkit.getRegionScheduler().run(plugin, loc, regionTask -> {
                                Block block = loc.getBlock();
                                if (block != null) {
                                    try {
                                        // 先设置方块类型
                                        block.setType(state.getType());
                                        // 然后更新状态，保留物理效果但不触发事件
                                        state.update(true, false);
                                        // 特别处理容器，确保内容正确恢复
                                        if (state instanceof org.bukkit.block.Container) {
                                            restoredContainers.incrementAndGet();
                                            // 重新获取容器状态并验证
                                            BlockState updatedState = block.getState();
                                            if (updatedState instanceof org.bukkit.block.Container) {
                                                org.bukkit.block.Container container = (org.bukkit.block.Container) updatedState;
                                                // 确保容器 inventory 不为 null
                                                if (container.getInventory() != null) {
                                                    // 这里不需要重新设置内容，因为 state.update() 应该已经处理了
                                                    plugin.getLogger().fine("[地图重置] 容器内容已恢复: " + blockPos);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[地图重置] 恢复实体方块失败 (" + blockPos + "): " + e.getMessage());
                                    }
                                } else {
                                    skippedStates.incrementAndGet();
                                }
                            });
                        }
                        
                        int batchNum = completedBatches.incrementAndGet();
                        plugin.getLogger().info("[地图重置] 已完成第 " + batchNum + "/" + totalBatches + " 批实体方块恢复");
                        activeTasks.decrementAndGet();
                    }
                    
                    // 所有任务完成后清理流动水
                    if (activeTasks.get() == 0 && completedBatches.get() == totalBatches) {
                        plugin.getLogger().info("[地图重置] 所有方块恢复完成！");
                        if (skippedStates.get() > 0) {
                            plugin.getLogger().warning("[地图重置] 跳过了 " + skippedStates.get() + " 个无效实体方块");
                        }
                        if (restoredContainers.get() > 0) {
                            plugin.getLogger().info("[地图重置] 成功恢复了 " + restoredContainers.get() + " 个容器");
                        }
                        
                        // 检查是否需要清理流动水
                        if (config.isMapResetPostClearFlowing()) {
                            plugin.getLogger().info("[地图重置] 开始清理流动水...");
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    clearFlowingWater();
                                    // 清理流动水后立即释放内存并销毁实例
                                    clearMaps();
                                    // 重置完成后自动销毁实例，避免外部过早调用destroy()导致数据被提前清理
                                }
                            }.runTaskLater(plugin, 20); // 延迟 1 秒执行，确保方块完全恢复
                        } else {
                            // 不需要清理流动水，直接释放内存并销毁实例
                            plugin.getLogger().info("[地图重置] 不需要清理流动水，直接释放内存");
                            clearMaps();
                            // 重置完成后自动销毁实例，避免外部过早调用destroy()导致数据被提前清理
                        }
                    }
                }
            }.runTaskLater(plugin, i * delay);
        }
    }
    
    /**
     * 清理区域内的流动水（level > 0 的 WATER 方块）
     */
    private void clearFlowingWater() {
        // plugin.getLogger().info("[地图重置] 开始清理流动水...");
        
        int minX = (int) (center.getX() - radius);
        int maxX = (int) (center.getX() + radius);
        int minZ = (int) (center.getZ() - radius);
        int maxZ = (int) (center.getZ() + radius);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        
        int clearedWaterBlocks = 0;
        
        // 遍历区域内的所有方块
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // 检查该 XZ 坐标是否在圆形区域内
                Location tempLoc = new Location(world, x, 0, z);
                if (!isInRegion(tempLoc)) continue;
                
                // 加载区块
                Chunk chunk = tempLoc.getChunk();
                if (!chunk.isLoaded()) {
                    chunk.load();
                }
                
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, x, y, z);
                    Block block = loc.getBlock();
                    BlockData blockData = block.getBlockData();
                    
                    // 检查是否为流动水（level > 0 的 WATER）
                    if (blockData.getMaterial() == Material.WATER) {
                        if (blockData instanceof org.bukkit.block.data.Levelled) {
                            org.bukkit.block.data.Levelled levelled = (org.bukkit.block.data.Levelled) blockData;
                            if (levelled.getLevel() > 0) {
                                // 清理流动水
                                block.setType(Material.AIR, false);
                                clearedWaterBlocks++;
                            }
                        }
                    }
                }
            }
        }
        
        // plugin.getLogger().info("[地图重置] 已清理 " + clearedWaterBlocks + " 个流动水方块");
    }
    
    /**
     * 保存数据到 JSON 文件
     */
    private void saveDataToFile() {
        // plugin.getLogger().info("[地图重置] 开始保存数据到文件...");
        
        try {
            // 确保数据文件夹存在
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            // 创建 ObjectMapper 实例
            ObjectMapper mapper = new ObjectMapper();
            
            // 创建根对象节点
            ObjectNode rootNode = mapper.createObjectNode();
            
            // 创建基本方块数据节点
            ObjectNode blockDataNode = mapper.createObjectNode();
            // 保存基本方块数据
            if (originalBlockData != null) {
                for (Map.Entry<BlockPos, BlockData> entry : originalBlockData.entrySet()) {
                    BlockPos blockPos = entry.getKey();
                    BlockData blockData = entry.getValue();
                    String key = blockPos.toString();
                    blockDataNode.put(key, blockData.getAsString());
                }
            }
            rootNode.set("blockData", blockDataNode);
            
            // 创建实体方块数据节点
            ObjectNode blockStatesNode = mapper.createObjectNode();
            if (originalBlockStates != null) {
                for (Map.Entry<BlockPos, BlockState> entry : originalBlockStates.entrySet()) {
                    BlockPos blockPos = entry.getKey();
                    BlockState state = entry.getValue();
                    String key = blockPos.toString();
                    
                    ObjectNode stateNode = mapper.createObjectNode();
                    // 保存方块类型
                    stateNode.put("type", state.getType().name());
                    
                    // 保存方块数据
                    stateNode.put("blockData", state.getBlockData().getAsString());
                    
                    // 保存方块实体数据（如果是容器）
                    if (state instanceof org.bukkit.block.Container) {
                        org.bukkit.block.Container container = (org.bukkit.block.Container) state;
                        org.bukkit.inventory.ItemStack[] contents = container.getInventory().getContents();
                        ObjectNode contentsNode = mapper.createObjectNode();
                        for (int i = 0; i < contents.length; i++) {
                            if (contents[i] != null) {
                                // 保存物品数据为Map
                                Map<String, Object> itemData = contents[i].serialize();
                                ObjectNode itemNode = mapper.valueToTree(itemData);
                                contentsNode.set(String.valueOf(i), itemNode);
                            }
                        }
                        stateNode.set("contents", contentsNode);
                    }
                    
                    blockStatesNode.set(key, stateNode);
                }
            }
            rootNode.set("blockStates", blockStatesNode);
            
            // 保存配置到文件
            try (OutputStream os = new FileOutputStream(dataFile)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(os, rootNode);
            }
            // plugin.getLogger().info("[地图重置] 数据保存到文件成功！");
        } catch (IOException e) {
            // plugin.getLogger().severe("[地图重置] 保存数据到文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从 JSON 文件加载数据（异步读取，主线程应用）
     */
    public void loadDataFromFile() {
        plugin.getLogger().info("[地图重置] 开始从文件加载数据...");
        
        if (!dataFile.exists()) {
            plugin.getLogger().warning("[地图重置] 数据文件不存在，跳过加载");
            dataLoaded.set(false);
            return;
        }
        
        // 异步读取和解析 JSON 文件
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            plugin.getLogger().info("[地图重置] 开始异步读取和解析 JSON 文件...");
            
            try {
                // 创建 ObjectMapper 实例
                ObjectMapper mapper = new ObjectMapper();
                
                // 读取 JSON 文件
                plugin.getLogger().info("[地图重置] 正在读取文件: " + dataFile.getAbsolutePath());
                ObjectNode rootNode = mapper.readValue(dataFile, ObjectNode.class);
                plugin.getLogger().info("[地图重置] JSON 文件读取成功");
                
                // 临时存储解析的数据
                final Map<BlockPos, BlockData> tempBlockData = new ConcurrentHashMap<>();
                final Map<BlockPos, Map<String, Object>> tempBlockStates = new ConcurrentHashMap<>();
                
                // 加载基本方块数据
                if (rootNode.has("blockData")) {
                    plugin.getLogger().info("[地图重置] 开始加载基本方块数据...");
                    ObjectNode blockDataNode = (ObjectNode) rootNode.get("blockData");
                    int blockDataCount = 0;
                    
                    for (Iterator<String> it = blockDataNode.fieldNames(); it.hasNext(); ) {
                        String key = it.next();
                        String[] parts = key.split(",");
                        if (parts.length == 3) {
                            try {
                                int x = Integer.parseInt(parts[0]);
                                int y = Integer.parseInt(parts[1]);
                                int z = Integer.parseInt(parts[2]);
                                BlockPos blockPos = new BlockPos(x, y, z);
                                
                                String blockDataString = blockDataNode.get(key).asText();
                                BlockData blockData = Bukkit.createBlockData(blockDataString);
                                tempBlockData.put(blockPos, blockData);
                                blockDataCount++;
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning("[地图重置] 解析位置失败: " + key);
                            } catch (Exception e) {
                                plugin.getLogger().warning("[地图重置] 创建方块数据失败: " + e.getMessage());
                            }
                        }
                    }
                    plugin.getLogger().info("[地图重置] 基本方块数据加载完成，共 " + blockDataCount + " 个方块");
                }
                
                // 加载实体方块数据（只解析数据，不操作方块）
                if (rootNode.has("blockStates")) {
                    plugin.getLogger().info("[地图重置] 开始加载实体方块数据...");
                    ObjectNode blockStatesNode = (ObjectNode) rootNode.get("blockStates");
                    int blockStateCount = 0;
                    
                    for (Iterator<String> it = blockStatesNode.fieldNames(); it.hasNext(); ) {
                        String key = it.next();
                        String[] parts = key.split(",");
                        if (parts.length == 3) {
                            try {
                                int x = Integer.parseInt(parts[0]);
                                int y = Integer.parseInt(parts[1]);
                                int z = Integer.parseInt(parts[2]);
                                BlockPos blockPos = new BlockPos(x, y, z);
                                
                                ObjectNode stateNode = (ObjectNode) blockStatesNode.get(key);
                                String typeName = stateNode.get("type").asText();
                                if (typeName != null) {
                                    Material type = Material.getMaterial(typeName);
                                    if (type != null) {
                                        // 存储解析的数据
                                        Map<String, Object> stateData = new HashMap<>();
                                        stateData.put("type", typeName);
                                        
                                        // 加载方块数据
                                        if (stateNode.has("blockData")) {
                                            String blockDataString = stateNode.get("blockData").asText();
                                            if (blockDataString != null) {
                                                stateData.put("blockData", blockDataString);
                                            }
                                        }
                                        
                                        // 加载容器内容
                                        if (stateNode.has("contents")) {
                                            ObjectNode contentsNode = (ObjectNode) stateNode.get("contents");
                                            Map<String, Map<String, Object>> contentsData = new HashMap<>();
                                            for (Iterator<String> slotIt = contentsNode.fieldNames(); slotIt.hasNext(); ) {
                                                String slotKey = slotIt.next();
                                                try {
                                                    ObjectNode itemNode = (ObjectNode) contentsNode.get(slotKey);
                                                    Map<String, Object> itemData = mapper.treeToValue(itemNode, Map.class);
                                                    contentsData.put(slotKey, itemData);
                                                } catch (Exception e) {
                                                    plugin.getLogger().warning("[地图重置] 解析物品数据失败: " + e.getMessage());
                                                }
                                            }
                                            stateData.put("contents", contentsData);
                                        }
                                        
                                        tempBlockStates.put(blockPos, stateData);
                                        blockStateCount++;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning("[地图重置] 解析位置失败: " + key);
                            } catch (Exception e) {
                                plugin.getLogger().warning("[地图重置] 加载实体方块数据失败: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                    plugin.getLogger().info("[地图重置] 实体方块数据加载完成，共 " + blockStateCount + " 个实体方块");
                }
                
                long parseTime = System.currentTimeMillis() - startTime;
                plugin.getLogger().info("[地图重置] JSON 文件解析完成，耗时 " + parseTime + "ms");
                
                // 在主线程中应用数据
                Bukkit.getScheduler().runTask(plugin, () -> {
                    long applyStartTime = System.currentTimeMillis();
                    plugin.getLogger().info("[地图重置] 开始在主线程中应用数据...");
                    
                    try {
                        // 清空现有数据或初始化
                        if (originalBlockData == null) {
                            originalBlockData = new ConcurrentHashMap<>();
                            plugin.getLogger().info("[地图重置] 初始化 originalBlockData 为新的 ConcurrentHashMap");
                        } else {
                            try {
                                originalBlockData.clear();
                                plugin.getLogger().info("[地图重置] 清空现有的 originalBlockData");
                            } catch (NullPointerException e) {
                                // 防止其他线程在检查后修改了originalBlockData
                                originalBlockData = new ConcurrentHashMap<>();
                                plugin.getLogger().info("[地图重置] 捕获到 NullPointerException，重新初始化 originalBlockData");
                            }
                        }
                        if (originalBlockStates == null) {
                            originalBlockStates = new ConcurrentHashMap<>();
                            plugin.getLogger().info("[地图重置] 初始化 originalBlockStates 为新的 ConcurrentHashMap");
                        } else {
                            try {
                                originalBlockStates.clear();
                                plugin.getLogger().info("[地图重置] 清空现有的 originalBlockStates");
                            } catch (NullPointerException e) {
                                // 防止其他线程在检查后修改了originalBlockStates
                                originalBlockStates = new ConcurrentHashMap<>();
                                plugin.getLogger().info("[地图重置] 捕获到 NullPointerException，重新初始化 originalBlockStates");
                            }
                        }
                        
                        // 应用基本方块数据
                        originalBlockData.putAll(tempBlockData);
                        plugin.getLogger().info("[地图重置] 已应用 " + originalBlockData.size() + " 个基本方块数据");
                        
                        // 应用实体方块数据
                        int appliedStateCount = 0;
                        for (Map.Entry<BlockPos, Map<String, Object>> entry : tempBlockStates.entrySet()) {
                            BlockPos blockPos = entry.getKey();
                            Map<String, Object> stateData = entry.getValue();
                            
                            try {
                                String typeName = (String) stateData.get("type");
                                Material type = Material.getMaterial(typeName);
                                if (type != null) {
                                    Location loc = blockPos.toLocation(world);
                                    Block block = loc.getBlock();
                                    BlockState state = block.getState();
                                    state.setType(type);
                                    
                                    // 加载方块数据
                                    if (stateData.containsKey("blockData")) {
                                        String blockDataString = (String) stateData.get("blockData");
                                        if (blockDataString != null) {
                                            BlockData blockData = Bukkit.createBlockData(blockDataString);
                                            state.setBlockData(blockData);
                                        }
                                    }
                                    
                                    // 加载容器内容
                                    if (state instanceof org.bukkit.block.Container && stateData.containsKey("contents")) {
                                        org.bukkit.block.Container container = (org.bukkit.block.Container) state;
                                        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[container.getInventory().getSize()];
                                        
                                        Map<String, Map<String, Object>> contentsData = (Map<String, Map<String, Object>>) stateData.get("contents");
                                        for (Map.Entry<String, Map<String, Object>> slotEntry : contentsData.entrySet()) {
                                            try {
                                                int slot = Integer.parseInt(slotEntry.getKey());
                                                if (slot >= 0 && slot < contents.length) {
                                                    Map<String, Object> itemData = slotEntry.getValue();
                                                    org.bukkit.inventory.ItemStack item = org.bukkit.inventory.ItemStack.deserialize(itemData);
                                                    contents[slot] = item;
                                                }
                                            } catch (NumberFormatException e) {
                                                plugin.getLogger().warning("[地图重置] 解析槽位失败: " + slotEntry.getKey());
                                            } catch (Exception e) {
                                                plugin.getLogger().warning("[地图重置] 解析物品数据失败: " + e.getMessage());
                                            }
                                        }
                                        
                                        container.getInventory().setContents(contents);
                                    }
                                    
                                    originalBlockStates.put(blockPos, state);
                                    appliedStateCount++;
                                }
                            } catch (Exception e) {
                                plugin.getLogger().warning("[地图重置] 应用实体方块数据失败: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        
                        long applyTime = System.currentTimeMillis() - applyStartTime;
                        plugin.getLogger().info("[地图重置] 数据应用完成，耗时 " + applyTime + "ms");
                        plugin.getLogger().info("[地图重置] 数据加载完成！");
                        plugin.getLogger().info("[地图重置] 加载了 " + originalBlockData.size() + " 个基本方块和 " + originalBlockStates.size() + " 个实体方块");
                        
                        // 启用保护机制
                        if (this.config.isMapResetProtectionEnabled()) {
                            isProtected = true;
                            plugin.getLogger().info("[地图重置] 启用了地图保护机制");
                        }
                        
                        // 设置数据加载完成标志
                        dataLoaded.set(true);
                        plugin.getLogger().info("[地图重置] 数据加载完成标志已设置为 true");
                    } catch (Exception e) {
                        plugin.getLogger().severe("[地图重置] 应用数据失败: " + e.getMessage());
                        e.printStackTrace();
                        dataLoaded.set(false);
                        plugin.getLogger().info("[地图重置] 数据加载完成标志已设置为 false（应用失败）");
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[地图重置] 从文件加载数据失败: " + e.getMessage());
                e.printStackTrace();
                dataLoaded.set(false);
                plugin.getLogger().info("[地图重置] 数据加载完成标志已设置为 false（加载失败）");
            }
        }, "MapResetter-JSON-Loader-" + mapId).start();
    }
    
    /**
     * 处理方块破坏事件
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // 每次都直接从配置中读取保护状态，确保使用最新配置
        if (!config.isMapResetProtectionEnabled()) return;
        
        Block block = event.getBlock();
        if (isInRegion(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[地图重置] 此区域已被保护，无法破坏方块！");
        }
    }
    
    /**
     * 处理方块放置事件
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // 每次都直接从配置中读取保护状态，确保使用最新配置
        if (!config.isMapResetProtectionEnabled()) return;
        
        Block block = event.getBlock();
        if (isInRegion(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[地图重置] 此区域已被保护，无法放置方块！");
        }
    }
    
    /**
     * 禁用保护机制
     */
    public void disableProtection() {
        isProtected = false;
    }
    
    /**
     * 启用保护机制
     */
    public void enableProtection() {
        isProtected = true;
    }
    
    /**
     * 清理所有地图数据，释放内存
     * 应用完地图数据后立即调用，确保内存及时释放
     */
    private void clearMaps() {
        // 清理原始方块数据
        if (originalBlockData != null) {
            originalBlockData.clear();
            originalBlockData = null;
        }
        
        // 清理实体方块状态，特别注意清理容器内容
        if (originalBlockStates != null) {
            for (BlockState state : originalBlockStates.values()) {
                if (state instanceof org.bukkit.block.Container) {
                    try {
                        org.bukkit.block.Container container = (org.bukkit.block.Container) state;
                        container.getInventory().clear();
                    } catch (Exception e) {
                        // 忽略清理容器时的异常
                    }
                }
            }
            originalBlockStates.clear();
            originalBlockStates = null;
        }
        
        // 提示GC，帮助及时回收内存
        System.gc();
        plugin.getLogger().info("[地图重置] 已清理所有地图数据，释放内存");
    }
    
    /**
     * 清理数据
     */
    public void clearData() {
        if (originalBlockData != null) {
            originalBlockData.clear();
        }
        if (originalBlockStates != null) {
            originalBlockStates.clear();
        }
        isProtected = false;
    }
    
    /**
     * 销毁实例，清理所有资源
     * 包括注销事件监听器和清理数据
     */
    public void destroy() {
        // 清理数据
        clearMaps();
        
        // 注意：事件监听器会在插件被禁用时由 Bukkit 自动注销
        // 这里不再手动注销，避免 API 版本兼容性问题
    }
}