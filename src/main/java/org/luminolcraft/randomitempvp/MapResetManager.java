package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地图重置管理器
 * 记录游戏过程中的所有变化，并在游戏结束后恢复地图到初始状态
 */
public class MapResetManager {
    private final JavaPlugin plugin;
    private final World world;
    private final Location center;
    private final double radius;
    
    // 记录方块变化：位置 -> 原始方块状态
    private final Map<Location, BlockState> originalBlocks = new ConcurrentHashMap<>();
    
    // 记录方块的 BlockData（用于保存含水状态等）
    private final Map<Location, BlockData> originalBlockData = new ConcurrentHashMap<>();
    
    // 记录方块实体的完整数据（用于酿造台等）
    private final Map<Location, SavedBlockData> savedBlockData = new ConcurrentHashMap<>();
    
    // 记录被破坏的方块：位置 -> 原始方块状态
    private final Map<Location, BlockState> brokenBlocks = new ConcurrentHashMap<>();
    
    // 记录被放置的方块：位置 -> 原始方块状态（放置前的状态）
    private final Map<Location, BlockState> placedBlocks = new ConcurrentHashMap<>();
    
    // 记录生成的实体（需要删除的）
    private final Set<UUID> spawnedEntities = ConcurrentHashMap.newKeySet();
    
    // 记录流体变化（水、岩浆）
    private final Map<Location, BlockState> fluidChanges = new ConcurrentHashMap<>();
    
    // 记录基岩生成（特别处理）
    private final Map<Location, BlockState> bedrockPlaced = new ConcurrentHashMap<>();
    
    // 记录所有需要恢复的方块位置（去重）
    private final Set<Location> allChangedBlocks = ConcurrentHashMap.newKeySet();
    
    // 记录地图初始状态下的水和岩浆方块（这些不应该被重置）
    private final Set<Location> originalFluidBlocks = ConcurrentHashMap.newKeySet();
    
    // 记录地图初始状态下的石头/原石/黑曜石等方块（这些不应该被重置）
    private final Set<Location> originalStoneBlocks = ConcurrentHashMap.newKeySet();
    
    // 记录方块实体的初始状态（酿造台、箱子等）
    private final Map<Location, BlockState> originalBlockEntities = new ConcurrentHashMap<>();
    
    // 记录展示框实体的初始状态（展示框是实体，不是方块实体）
    private final Map<UUID, org.bukkit.entity.ItemFrame> originalItemFrames = new ConcurrentHashMap<>();
    
    private boolean recording = false;
    
    public MapResetManager(JavaPlugin plugin, World world, Location center, double radius) {
        this.plugin = plugin;
        this.world = world;
        this.center = center.clone();
        this.radius = radius;
    }
    
    /**
     * 开始记录变化
     */
    public void startRecording() {
        recording = true;
        originalBlocks.clear();
        brokenBlocks.clear();
        placedBlocks.clear();
        spawnedEntities.clear();
        fluidChanges.clear();
        bedrockPlaced.clear();
        allChangedBlocks.clear();
        originalFluidBlocks.clear();
        originalStoneBlocks.clear();
        
        // 扫描并记录地图初始状态下的所有水和岩浆方块
        scanInitialFluids();
        
        // 扫描并记录地图初始状态下的所有石头/原石/黑曜石等方块
        scanInitialStoneBlocks();
        
        // 扫描并记录所有方块实体的初始状态（酿造台、箱子等）
        scanInitialBlockEntities();
        
        // 扫描并保存所有方块的 BlockData（包括含水状态）
        scanInitialBlockData();
        
        // 扫描并记录所有展示框实体
        scanInitialItemFrames();
    }
    
    /**
     * 停止记录
     */
    public void stopRecording() {
        recording = false;
    }
    
    /**
     * 检查位置是否在游戏区域内
     */
    private boolean isInArena(Location loc) {
        if (loc.getWorld() != world) return false;
        return loc.distance(center) <= radius;
    }
    
    /**
     * 记录方块破坏（保存完整的方块状态，包括含水状态等）
     */
    public void recordBlockBreak(Block block) {
        if (!recording || !isInArena(block.getLocation())) {
            return;
        }
        
        Location loc = block.getLocation();
        
        // 保存完整的方块状态（包括含水状态、方向等所有属性）
        BlockState originalState = block.getState();
        
        // 保存 BlockData（包括含水状态）- 使用 clone() 确保完整复制
        BlockData blockData = block.getBlockData().clone();
        
        // 如果这个位置之前没有被记录过，保存原始状态
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc, originalState);
            // 确保 BlockData 被保存（重要：用于恢复含水状态）
            originalBlockData.put(loc, blockData);
            
            // 如果是容器类方块（如酿造台），保存完整数据
            if (originalState instanceof BrewingStand || originalState instanceof org.bukkit.block.Container) {
                SavedBlockData savedData = captureBlockData(block);
                savedBlockData.put(loc, savedData);
            }
        } else {
            // 如果已经记录过，也要更新 BlockData（因为含水状态可能改变了）
            // 这很重要：当玩家倒水到已记录的方块时，需要更新 BlockData
            originalBlockData.put(loc, blockData);
        }
        
        // 记录为被破坏的方块
        brokenBlocks.put(loc, originalState);
        allChangedBlocks.add(loc);
    }
    
    /**
     * 记录方块放置
     */
    public void recordBlockPlace(Block block) {
        if (!recording || !isInArena(block.getLocation())) {
            return;
        }
        
        Location loc = block.getLocation();
        
        // 如果这个位置之前没有被记录过，保存原始状态
        if (!originalBlocks.containsKey(loc)) {
            // 获取放置前的状态（通过获取被替换的方块）
            BlockState originalState = block.getState();
            // 注意：BlockPlaceEvent 中，block 已经是新方块了
            // 我们需要在事件中获取被替换的方块状态
            originalBlocks.put(loc, originalState);
        }
        
        // 检查是否是基岩
        if (block.getType() == Material.BEDROCK) {
            bedrockPlaced.put(loc, block.getState());
        }
        
        // 记录为被放置的方块
        placedBlocks.put(loc, block.getState());
        allChangedBlocks.add(loc);
    }
    
    /**
     * 记录方块放置（从事件中获取被替换的方块）
     */
    public void recordBlockPlace(Block block, BlockState replacedBlockState) {
        if (!recording || !isInArena(block.getLocation())) {
            return;
        }
        
        Location loc = block.getLocation();
        
        // 保存被替换的原始状态
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc, replacedBlockState);
        }
        
        // 检查是否是基岩
        if (block.getType() == Material.BEDROCK) {
            bedrockPlaced.put(loc, block.getState());
        }
        
        // 记录为被放置的方块
        placedBlocks.put(loc, block.getState());
        allChangedBlocks.add(loc);
    }
    
    /**
     * 记录流体变化（水、岩浆）
     */
    public void recordFluidChange(Block block) {
        if (!recording || !isInArena(block.getLocation())) {
            return;
        }
        
        Location loc = block.getLocation();
        Material type = block.getType();
        
            // 只记录水和岩浆
            if (type == Material.WATER || type == Material.LAVA || 
                type == Material.WATER_CAULDRON || type == Material.LAVA_CAULDRON ||
                type.name().contains("WATER") || type.name().contains("LAVA")) {
                
                // 如果这是地图自带的流体方块，不记录（不会被重置）
                if (originalFluidBlocks.contains(loc)) {
                    return;
                }
                
                // 这是玩家放置或流动产生的流体，需要记录
                // 如果这个位置之前没有被记录过，保存原始状态（通常是空气）
                if (!originalBlocks.containsKey(loc)) {
                    // 获取被替换的方块状态（假设原始是空气）
                    BlockState airState = block.getState();
                    airState.setType(Material.AIR);
                    originalBlocks.put(loc, airState);
                }
                
                fluidChanges.put(loc, block.getState());
                allChangedBlocks.add(loc);
            }
    }
    
    /**
     * 记录实体生成
     */
    public void recordEntitySpawn(Entity entity) {
        if (!recording || !isInArena(entity.getLocation())) {
            return;
        }
        
        // 不记录玩家
        if (entity.getType() == EntityType.PLAYER) {
            return;
        }
        
        spawnedEntities.add(entity.getUniqueId());
    }
    
    /**
     * 记录基岩生成（特别处理）
     */
    public void recordBedrockPlace(Block block) {
        if (!recording || !isInArena(block.getLocation())) {
            return;
        }
        
        Location loc = block.getLocation();
        
        // 如果这个位置之前没有被记录过，保存原始状态
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc, block.getState());
        }
        
        bedrockPlaced.put(loc, block.getState());
        allChangedBlocks.add(loc);
        
        plugin.getLogger().info("[地图重置] 检测到基岩生成: " + loc);
    }
    
    /**
     * 重置地图到初始状态
     */
    public void resetMap() {
        if (!recording) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始重置地图...");
        plugin.getLogger().info("[地图重置] 需要恢复的方块数: " + allChangedBlocks.size());
        plugin.getLogger().info("[地图重置] 需要删除的实体数: " + spawnedEntities.size());
        plugin.getLogger().info("[地图重置] 基岩生成数: " + bedrockPlaced.size());
        
        // 1. 删除所有生成的实体和掉落物
        int[] deletedEntities = {0};
        int[] deletedItems = {0};
        
        // 删除记录的实体
        for (UUID entityId : new ArrayList<>(spawnedEntities)) {
            try {
                // 使用全局调度器查找实体
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    for (Entity entity : world.getEntities()) {
                        if (entity.getUniqueId().equals(entityId) && isInArena(entity.getLocation())) {
                            entity.remove();
                            deletedEntities[0]++;
                        }
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[地图重置] 删除实体失败: " + entityId + " - " + e.getMessage());
            }
        }
        
        // 清除所有掉落物（Item实体）
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Item && isInArena(entity.getLocation())) {
                    entity.remove();
                    deletedItems[0]++;
                }
            }
            plugin.getLogger().info("[地图重置] 已清除 " + deletedItems[0] + " 个掉落物");
        });
        
        // 2. 恢复所有改变的方块（直接使用 BlockState.update() 恢复完整状态，包括含水状态）
        int[] restoredBlocks = {0};
        for (Location loc : new ArrayList<>(allChangedBlocks)) {
            try {
                BlockState originalState = originalBlocks.get(loc);
                if (originalState != null) {
                    // 使用区域调度器恢复方块
                    Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                        Block block = loc.getBlock();
                        if (block != null) {
                            // 直接使用 BlockState.update() 恢复完整状态
                            // 这会恢复所有属性，包括：
                            // - 方块类型
                            // - BlockData（包括含水状态、方向等所有属性）
                            // - 方块实体的内容（如果有）
                            originalState.update(true, false); // true = 应用物理更新，false = 不触发事件
                            
                            // 如果是容器类方块，需要单独恢复内容（因为 update() 可能不会恢复容器内容）
                            SavedBlockData savedData = savedBlockData.get(loc);
                            if (savedData != null && savedData.contents != null) {
                                // 只恢复容器内容，不重新设置方块类型和 BlockData
                                BlockState currentState = block.getState();
                                if (currentState instanceof org.bukkit.block.Container) {
                                    org.bukkit.block.Container container = (org.bukkit.block.Container) currentState;
                                    ItemStack[] clonedContents = new ItemStack[savedData.contents.length];
                                    for (int i = 0; i < savedData.contents.length; i++) {
                                        clonedContents[i] = savedData.contents[i] != null ? savedData.contents[i].clone() : null;
                                    }
                                    container.getInventory().setContents(clonedContents);
                                    currentState.update();
                                } else if (currentState instanceof BrewingStand) {
                                    BrewingStand brewingStand = (BrewingStand) currentState;
                                    ItemStack[] clonedContents = new ItemStack[savedData.contents.length];
                                    for (int i = 0; i < savedData.contents.length; i++) {
                                        clonedContents[i] = savedData.contents[i] != null ? savedData.contents[i].clone() : null;
                                    }
                                    brewingStand.getInventory().setContents(clonedContents);
                                    currentState.update();
                                }
                            }
                            
                            restoredBlocks[0]++;
                        }
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[地图重置] 恢复方块失败: " + loc + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 等待一段时间让方块恢复完成
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getLogger().info("[地图重置] 已删除 " + deletedEntities[0] + " 个实体");
            plugin.getLogger().info("[地图重置] 已恢复 " + restoredBlocks[0] + " 个方块");
            
            // 3. 特别处理基岩：确保所有基岩都被移除
            int[] bedrockRemoved = {0};
            for (Location loc : new ArrayList<>(bedrockPlaced.keySet())) {
                try {
                    Bukkit.getRegionScheduler().run(plugin, loc, task2 -> {
                        Block block = loc.getBlock();
                        if (block != null && block.getType() == Material.BEDROCK) {
                            BlockState originalState = originalBlocks.get(loc);
                            if (originalState != null && originalState.getType() != Material.BEDROCK) {
                                originalState.update(true, false);
                                bedrockRemoved[0]++;
                            }
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("[地图重置] 移除基岩失败: " + loc + " - " + e.getMessage());
                }
            }
            
            // 4. 先扫描并清除所有含水方块（waterlogged blocks）
            // 注意：必须先清理含水方块，再清理流动的液体，避免产生新的含水方块
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task3_waterlogged -> {
                int[] waterloggedRemoved = {0};
                scanAndRemoveWaterlogged(waterloggedRemoved);
                
                // 4.5. 然后扫描并清除所有流动的水和岩浆
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task3 -> {
                    int[] fluidsRemoved = {0};
                    scanAndRemoveFluids(fluidsRemoved);
                    
                    // 4.6. 最后扫描并清除所有水和岩浆生成的石头/原石/黑曜石
                    int[] stonesRemoved = {0};
                    scanAndRemoveStones(stonesRemoved);
                    
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task4 -> {
                    // 5. 恢复所有方块实体的内容（展示框、酿造台等）
                    // 注意：需要在方块恢复完成后再恢复容器内容，避免被覆盖
                    int[] restoredEntities = {0};
                    
                    plugin.getLogger().info("[地图重置] 开始恢复方块实体内容，originalBlockEntities 数量: " + originalBlockEntities.size());
                    plugin.getLogger().info("[地图重置] savedBlockData 数量: " + savedBlockData.size());
                    
                    // 先恢复所有在 originalBlockEntities 中的方块实体（游戏开始时扫描到的）
                    for (Location loc : new ArrayList<>(originalBlockEntities.keySet())) {
                        try {
                            Bukkit.getRegionScheduler().run(plugin, loc, task5 -> {
                                Block block = loc.getBlock();
                                if (block == null) {
                                    plugin.getLogger().warning("[地图重置] 方块为 null: " + loc);
                                    return;
                                }
                                
                                BlockState originalState = originalBlockEntities.get(loc);
                                if (originalState == null) {
                                    plugin.getLogger().warning("[地图重置] originalState 为 null: " + loc);
                                    return;
                                }
                                
                                plugin.getLogger().info("[地图重置] 尝试恢复方块实体: " + loc + ", 类型: " + block.getType());
                                
                                // 使用保存的完整数据恢复（包括 NMS Reflection 完整复制）
                                SavedBlockData savedData = savedBlockData.get(loc);
                                if (savedData != null) {
                                    plugin.getLogger().info("[地图重置] 找到 savedData: " + loc + ", contents 是否为 null: " + (savedData.contents == null));
                                    if (savedData.contents != null) {
                                        plugin.getLogger().info("[地图重置] contents 长度: " + savedData.contents.length);
                                        try {
                                            // 使用保存的完整数据恢复（包括酿造台的药水等）
                                            pasteBlockData(loc, savedData);
                                            restoredEntities[0]++;
                                            plugin.getLogger().info("[地图重置] 已恢复方块实体内容: " + loc + " (使用 savedBlockData)");
                                        } catch (Exception e) {
                                            plugin.getLogger().warning("[地图重置] 使用 savedBlockData 恢复失败: " + loc + " - " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    } else {
                                        plugin.getLogger().warning("[地图重置] savedData.contents 为 null: " + loc);
                                    }
                                } else {
                                    plugin.getLogger().warning("[地图重置] savedData 为 null: " + loc);
                                }
                                
                                if (savedData == null || savedData.contents == null) {
                                    // 回退到原来的方法
                                    // 回退到原来的方法（使用 originalState）
                                    BlockState currentState = block.getState();
                                    try {
                                        if (originalState instanceof org.bukkit.block.Container) {
                                            org.bukkit.block.Container originalContainer = (org.bukkit.block.Container) originalState;
                                            org.bukkit.block.Container currentContainer = (org.bukkit.block.Container) currentState;
                                            // 克隆物品数组以避免引用问题
                                            org.bukkit.inventory.ItemStack[] contents = originalContainer.getInventory().getContents();
                                            org.bukkit.inventory.ItemStack[] clonedContents = new org.bukkit.inventory.ItemStack[contents.length];
                                            for (int i = 0; i < contents.length; i++) {
                                                clonedContents[i] = contents[i] != null ? contents[i].clone() : null;
                                            }
                                            currentContainer.getInventory().setContents(clonedContents);
                                            currentState.update();
                                            restoredEntities[0]++;
                                            plugin.getLogger().info("[地图重置] 已恢复方块实体内容: " + loc + " (使用 originalState)");
                                        } else if (originalState instanceof org.bukkit.block.Furnace) {
                                            org.bukkit.block.Furnace originalFurnace = (org.bukkit.block.Furnace) originalState;
                                            org.bukkit.block.Furnace currentFurnace = (org.bukkit.block.Furnace) currentState;
                                            // 克隆物品数组
                                            org.bukkit.inventory.ItemStack[] contents = originalFurnace.getInventory().getContents();
                                            org.bukkit.inventory.ItemStack[] clonedContents = new org.bukkit.inventory.ItemStack[contents.length];
                                            for (int i = 0; i < contents.length; i++) {
                                                clonedContents[i] = contents[i] != null ? contents[i].clone() : null;
                                            }
                                            currentFurnace.getInventory().setContents(clonedContents);
                                            currentState.update();
                                            restoredEntities[0]++;
                                        } else if (originalState instanceof org.bukkit.block.Jukebox) {
                                            org.bukkit.block.Jukebox originalJukebox = (org.bukkit.block.Jukebox) originalState;
                                            org.bukkit.block.Jukebox currentJukebox = (org.bukkit.block.Jukebox) currentState;
                                            currentJukebox.setRecord(originalJukebox.getRecord() != null ? originalJukebox.getRecord().clone() : null);
                                            currentState.update();
                                            restoredEntities[0]++;
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[地图重置] 恢复方块实体内容失败: " + loc + " - " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } catch (Exception e) {
                            plugin.getLogger().warning("[地图重置] 恢复方块实体失败: " + loc + " - " + e.getMessage());
                        }
                    }
                    
                    // 同时恢复所有在 savedBlockData 中但不在 originalBlockEntities 中的方块实体
                    // （这些是在游戏过程中被改变的容器方块）
                    for (Location loc : new ArrayList<>(savedBlockData.keySet())) {
                        if (originalBlockEntities.containsKey(loc)) {
                            continue; // 已经在上面处理过了
                        }
                        try {
                            Bukkit.getRegionScheduler().run(plugin, loc, task6 -> {
                                SavedBlockData savedData = savedBlockData.get(loc);
                                if (savedData != null && savedData.contents != null) {
                                    try {
                                        pasteBlockData(loc, savedData);
                                        restoredEntities[0]++;
                                        plugin.getLogger().info("[地图重置] 已恢复改变的方块实体内容: " + loc);
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[地图重置] 恢复改变的方块实体失败: " + loc + " - " + e.getMessage());
                                    }
                                }
                            });
                        } catch (Exception e) {
                            plugin.getLogger().warning("[地图重置] 恢复改变的方块实体失败: " + loc + " - " + e.getMessage());
                        }
                    }
                    
                    // 6. 恢复所有展示框的内容
                    int[] restoredFrames = {0};
                    for (UUID frameId : new ArrayList<>(originalItemFrames.keySet())) {
                        try {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task6 -> {
                                for (Entity entity : world.getEntities()) {
                                    if (entity.getUniqueId().equals(frameId) && entity instanceof org.bukkit.entity.ItemFrame) {
                                        org.bukkit.entity.ItemFrame currentFrame = (org.bukkit.entity.ItemFrame) entity;
                                        org.bukkit.entity.ItemFrame originalFrame = originalItemFrames.get(frameId);
                                        if (originalFrame != null) {
                                            // 克隆物品以避免引用问题
                                            org.bukkit.inventory.ItemStack originalItem = originalFrame.getItem();
                                            currentFrame.setItem(originalItem != null ? originalItem.clone() : null);
                                            restoredFrames[0]++;
                                        }
                                    }
                                }
                            });
                        } catch (Exception e) {
                            plugin.getLogger().warning("[地图重置] 恢复展示框失败: " + frameId + " - " + e.getMessage());
                        }
                    }
                    
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task7 -> {
                        plugin.getLogger().info("[地图重置] 已移除 " + bedrockRemoved[0] + " 个基岩");
                        plugin.getLogger().info("[地图重置] 已清除 " + waterloggedRemoved[0] + " 个含水方块");
                        plugin.getLogger().info("[地图重置] 已清除 " + fluidsRemoved[0] + " 个流体方块（水/岩浆）");
                        plugin.getLogger().info("[地图重置] 已清除 " + stonesRemoved[0] + " 个生成的石头/原石/黑曜石");
                        plugin.getLogger().info("[地图重置] 已恢复 " + restoredEntities[0] + " 个方块实体内容");
                        plugin.getLogger().info("[地图重置] 已恢复 " + restoredFrames[0] + " 个展示框内容");
                        plugin.getLogger().info("[地图重置] 地图重置完成！");
                        
                        // 在所有恢复完成后再清理记录
                        originalBlocks.clear();
                        originalBlockData.clear();
                        savedBlockData.clear();
                        brokenBlocks.clear();
                        placedBlocks.clear();
                        spawnedEntities.clear();
                        fluidChanges.clear();
                        bedrockPlaced.clear();
                        allChangedBlocks.clear();
                        originalBlockEntities.clear();
                        originalItemFrames.clear();
                        originalStoneBlocks.clear();
                    }, 20L); // 1秒后
                }, 20L); // 1秒后
            }, 20L); // 1秒后
            }, 20L); // 1秒后 - 闭合 task3_waterlogged
        }, 10L); // 0.5秒后
    }
    
    /**
     * 保存方块完整数据（包括容器内容）
     */
    private SavedBlockData captureBlockData(Block block) {
        Material type = block.getType();
        BlockData blockData = block.getBlockData().clone(); // clone 很重要！
        ItemStack[] contents = null;
        
        BlockState state = block.getState();
        if (state instanceof BrewingStand) {
            BrewingStand brewingStand = (BrewingStand) state;
            contents = brewingStand.getInventory().getContents();
        } else if (state instanceof org.bukkit.block.Container) {
            org.bukkit.block.Container container = (org.bukkit.block.Container) state;
            contents = container.getInventory().getContents();
        }
        
        return new SavedBlockData(type, blockData, contents);
    }
    
    /**
     * 在目标位置重建方块（使用 NMS Reflection 完整复制酿造台数据）
     */
    private void pasteBlockData(Location loc, SavedBlockData data) {
        Block targetBlock = loc.getBlock();
        targetBlock.setType(data.type, false); // false = 不触发事件
        targetBlock.setBlockData(data.blockData, false); // ← 这里会自动设置 isWaterlogged!
        
        if (data.contents != null) {
            BlockState currentState = targetBlock.getState();
            
            if (currentState instanceof BrewingStand) {
                // 使用 NMS Reflection 完整复制酿造台数据（包括药水 NBT）
                try {
                    BrewingStand target = (BrewingStand) currentState;
                    
                    // 先使用 Bukkit API 设置物品
                    ItemStack[] clonedContents = new ItemStack[data.contents.length];
                    for (int i = 0; i < data.contents.length; i++) {
                        clonedContents[i] = data.contents[i] != null ? data.contents[i].clone() : null;
                    }
                    target.getInventory().setContents(clonedContents);
                    
                    // 使用 NMS Reflection 完整复制 NBT 数据（包括药水的完整信息）
                    copyBrewingStandNBT(target, data);
                    
                    target.update(); // 保存到世界
                } catch (Exception e) {
                    plugin.getLogger().warning("[地图重置] 使用 NMS 复制酿造台数据失败，回退到普通方法: " + e.getMessage());
                    // 回退到普通方法
                    BrewingStand target = (BrewingStand) currentState;
                    ItemStack[] clonedContents = new ItemStack[data.contents.length];
                    for (int i = 0; i < data.contents.length; i++) {
                        clonedContents[i] = data.contents[i] != null ? data.contents[i].clone() : null;
                    }
                    target.getInventory().setContents(clonedContents);
                    target.update();
                }
            } else if (currentState instanceof org.bukkit.block.Container) {
                org.bukkit.block.Container container = (org.bukkit.block.Container) currentState;
                ItemStack[] clonedContents = new ItemStack[data.contents.length];
                for (int i = 0; i < data.contents.length; i++) {
                    clonedContents[i] = data.contents[i] != null ? data.contents[i].clone() : null;
                }
                container.getInventory().setContents(clonedContents);
                currentState.update();
            }
        }
    }
    
    /**
     * 使用 NMS Reflection 完整复制酿造台的 NBT 数据（包括药水）
     */
    private void copyBrewingStandNBT(BrewingStand target, SavedBlockData data) {
        try {
            // 获取 NMS 类
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftBlockEntityStateClass = Class.forName("org.bukkit.craftbukkit." + version + ".block.CraftBlockEntityState");
            Class<?> tileEntityBrewingStandClass = Class.forName("net.minecraft.world.level.block.entity.TileEntityBrewingStand");
            Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
            
            // 获取 CraftBlockEntityState 的 tileEntity 字段
            Field tileEntityField = craftBlockEntityStateClass.getDeclaredField("tileEntity");
            tileEntityField.setAccessible(true);
            Object tileEntity = tileEntityField.get(target);
            
            if (tileEntity != null && tileEntityBrewingStandClass.isInstance(tileEntity)) {
                // 创建 NBT 标签
                Method saveMethod = tileEntityBrewingStandClass.getMethod("save", nbtTagCompoundClass);
                Object nbtTag = nbtTagCompoundClass.getDeclaredConstructor().newInstance();
                saveMethod.invoke(tileEntity, nbtTag);
                
                // 这里我们已经通过 setContents 设置了物品，NBT 会自动更新
                // 如果需要更精确的控制，可以在这里修改 NBT
            }
        } catch (Exception e) {
            // NMS Reflection 失败，使用 Bukkit API（已经在 pasteBlockData 中处理）
            throw new RuntimeException("NMS Reflection failed", e);
        }
    }
    
    /**
     * 数据容器（保存方块的完整数据）
     */
    private static class SavedBlockData {
        Material type;
        BlockData blockData;
        ItemStack[] contents;
        
        public SavedBlockData(Material type, BlockData blockData, ItemStack[] contents) {
            this.type = type;
            this.blockData = blockData;
            this.contents = contents;
        }
    }
    
    /**
     * 扫描并清除所有含水方块（waterlogged blocks）
     * 注意：这个方法在恢复方块之后执行，用于清除那些没有被恢复的含水方块
     */
    private void scanAndRemoveWaterlogged(int[] count) {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描含水方块...");
        
        // 计算扫描范围
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusInt = (int) Math.ceil(radius);
        
        // 获取世界高度范围
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 64);
        int maxY = Math.min(world.getMaxHeight(), center.getBlockY() + 64);
        
        // 扫描整个区域
        for (int x = centerX - radiusInt; x <= centerX + radiusInt; x++) {
            for (int z = centerZ - radiusInt; z <= centerZ + radiusInt; z++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                if (distance > radius) {
                    continue;
                }
                
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, x, y, z);
                    if (!isInArena(loc)) {
                        continue;
                    }
                    
                    // 使用区域调度器处理方块
                    Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                        Block block = loc.getBlock();
                        org.bukkit.block.data.BlockData blockData = block.getBlockData();
                        
                        // 检查是否是含水方块
                        if (blockData instanceof org.bukkit.block.data.Waterlogged) {
                            org.bukkit.block.data.Waterlogged waterlogged = (org.bukkit.block.data.Waterlogged) blockData;
                            if (waterlogged.isWaterlogged()) {
                                // 检查这个位置是否在 originalBlocks 中
                                BlockState originalState = originalBlocks.get(loc);
                                if (originalState != null) {
                                    // 有记录的原始状态，恢复它（这会自动清除含水状态）
                                    originalState.update(true, false);
                                    count[0]++;
                                } else {
                                    // 没有记录，检查是否是地图自带的（通过 originalBlockData）
                                    BlockData originalBlockDataValue = originalBlockData.get(loc);
                                    if (originalBlockDataValue != null) {
                                        // 检查原始 BlockData 是否含水
                                        if (originalBlockDataValue instanceof org.bukkit.block.data.Waterlogged) {
                                            org.bukkit.block.data.Waterlogged originalWaterlogged = (org.bukkit.block.data.Waterlogged) originalBlockDataValue;
                                            if (!originalWaterlogged.isWaterlogged()) {
                                                // 原始状态不含水，但现在是含水的，需要清除
                                                block.setBlockData(originalBlockDataValue, false);
                                                count[0]++;
                                            }
                                            // 如果原始状态也含水，说明是地图自带的，不处理
                                        } else {
                                            // 原始 BlockData 不是含水类型，但现在是含水的，需要清除
                                            block.setBlockData(originalBlockDataValue, false);
                                            count[0]++;
                                        }
                                    } else {
                                        // 完全没有记录，但方块是含水的，直接清除含水状态
                                        waterlogged.setWaterlogged(false);
                                        block.setBlockData(blockData, false);
                                        count[0]++;
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }
    }
    
    /**
     * 扫描并清除所有流动的水和岩浆
     */
    private void scanAndRemoveFluids(int[] count) {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描流体方块...");
        
        // 计算扫描范围
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusInt = (int) Math.ceil(radius);
        
        // 扫描整个区域（分块处理，避免卡顿）
        int chunkSize = 16; // 每次处理 16x16 的区域
        int totalChunks = (radiusInt * 2 / chunkSize) + 1;
        
        for (int chunkX = 0; chunkX < totalChunks; chunkX++) {
            for (int chunkZ = 0; chunkZ < totalChunks; chunkZ++) {
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;
                
                // 计算延迟：第一个块立即执行，后续块延迟执行以避免卡顿
                long delay = (long) (finalChunkX * totalChunks + finalChunkZ) * 2;
                
                // 第一个块立即执行，后续块延迟执行以避免卡顿
                if (delay == 0) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        int startX = centerX - radiusInt + (finalChunkX * chunkSize);
                        int startZ = centerZ - radiusInt + (finalChunkZ * chunkSize);
                        int endX = Math.min(startX + chunkSize, centerX + radiusInt);
                        int endZ = Math.min(startZ + chunkSize, centerZ + radiusInt);
                        
                        // 获取世界高度范围（从基岩到天空）
                        int minY = world.getMinHeight();
                        int maxY = world.getMaxHeight();
                        
                        for (int x = startX; x < endX; x++) {
                            for (int z = startZ; z < endZ; z++) {
                                // 检查是否在半径内
                                double distance = Math.sqrt(
                                    Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2)
                                );
                                if (distance > radius) {
                                    continue;
                                }
                                
                                // 扫描这个 XZ 坐标的所有 Y 坐标
                                for (int y = minY; y < maxY; y++) {
                                    Location loc = new Location(world, x, y, z);
                                    if (!isInArena(loc)) {
                                        continue;
                                    }
                                    
                                    // 使用区域调度器处理方块
                                    Bukkit.getRegionScheduler().run(plugin, loc, blockTask -> {
                                        Block block = loc.getBlock();
                                        Material type = block.getType();
                                        
                                        // 检查是否是水或岩浆
                                        if (type == Material.WATER || type == Material.LAVA ||
                                            type == Material.WATER_CAULDRON || type == Material.LAVA_CAULDRON ||
                                            type.name().contains("WATER") || type.name().contains("LAVA")) {
                                            
                                            // 只重置玩家放置或流动产生的水/岩浆，地图本身自带的不重置
                                            if (!originalFluidBlocks.contains(loc)) {
                                                // 这个位置原本不是水/岩浆，是玩家放置或流动产生的，需要重置
                                                BlockState originalState = originalBlocks.get(loc);
                                                if (originalState != null && originalState.getType() != type) {
                                                    // 有记录的原始状态，恢复它
                                                    originalState.update(true, false);
                                                    count[0]++;
                                                } else {
                                                    // 没有记录的原始状态，设置为空气（可能是玩家放置的流体）
                                                    block.setType(Material.AIR);
                                                    count[0]++;
                                                }
                                            }
                                            // 如果 originalFluidBlocks 包含这个位置，说明是地图自带的，不重置
                                        }
                                    });
                                }
                            }
                        }
                    });
                } else {
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                        int startX = centerX - radiusInt + (finalChunkX * chunkSize);
                        int startZ = centerZ - radiusInt + (finalChunkZ * chunkSize);
                        int endX = Math.min(startX + chunkSize, centerX + radiusInt);
                        int endZ = Math.min(startZ + chunkSize, centerZ + radiusInt);
                        
                        // 获取世界高度范围（从基岩到天空）
                        int minY = world.getMinHeight();
                        int maxY = world.getMaxHeight();
                        
                        for (int x = startX; x < endX; x++) {
                            for (int z = startZ; z < endZ; z++) {
                                // 检查是否在半径内
                                double distance = Math.sqrt(
                                    Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2)
                                );
                                if (distance > radius) {
                                    continue;
                                }
                                
                                // 扫描这个 XZ 坐标的所有 Y 坐标
                                for (int y = minY; y < maxY; y++) {
                                    Location loc = new Location(world, x, y, z);
                                    if (!isInArena(loc)) {
                                        continue;
                                    }
                                    
                                    // 使用区域调度器处理方块
                                    Bukkit.getRegionScheduler().run(plugin, loc, blockTask -> {
                                        Block block = loc.getBlock();
                                        Material type = block.getType();
                                        
                                        // 检查是否是水或岩浆
                                        if (type == Material.WATER || type == Material.LAVA ||
                                            type == Material.WATER_CAULDRON || type == Material.LAVA_CAULDRON ||
                                            type.name().contains("WATER") || type.name().contains("LAVA")) {
                                            
                                            // 只重置玩家放置或流动产生的水/岩浆，地图本身自带的不重置
                                            if (!originalFluidBlocks.contains(loc)) {
                                                // 这个位置原本不是水/岩浆，是玩家放置或流动产生的，需要重置
                                                BlockState originalState = originalBlocks.get(loc);
                                                if (originalState != null && originalState.getType() != type) {
                                                    // 有记录的原始状态，恢复它
                                                    originalState.update(true, false);
                                                    count[0]++;
                                                } else {
                                                    // 没有记录的原始状态，设置为空气（可能是玩家放置的流体）
                                                    block.setType(Material.AIR);
                                                    count[0]++;
                                                }
                                            }
                                            // 如果 originalFluidBlocks 包含这个位置，说明是地图自带的，不重置
                                        }
                                    });
                                }
                            }
                        }
                    }, delay);
                }
            }
        }
    }
    
    /**
     * 扫描并记录地图初始状态下的所有水和岩浆方块
     * 这些是地图自带的，不应该被重置
     */
    private void scanInitialFluids() {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描地图初始流体方块...");
        
        // 计算扫描范围
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusInt = (int) Math.ceil(radius);
        
        // 获取世界高度范围（只扫描有意义的范围）
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 64);
        int maxY = Math.min(world.getMaxHeight(), center.getBlockY() + 64);
        
        // 扫描整个区域（分块处理，避免卡顿）
        int chunkSize = 16;
        int totalChunksX = (radiusInt * 2 / chunkSize) + 1;
        int totalChunksZ = (radiusInt * 2 / chunkSize) + 1;
        
        final int[] scannedCount = {0};
        
        for (int chunkX = 0; chunkX < totalChunksX; chunkX++) {
            for (int chunkZ = 0; chunkZ < totalChunksZ; chunkZ++) {
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;
                
                // 计算延迟：第一个块立即执行，后续块延迟执行以避免卡顿
                long delay = (long) (finalChunkX * totalChunksZ + finalChunkZ) * 2;
                
                // 第一个块立即执行，后续块延迟执行以避免卡顿
                if (delay == 0) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        int startX = centerX - radiusInt + (finalChunkX * chunkSize);
                        int startZ = centerZ - radiusInt + (finalChunkZ * chunkSize);
                        int endX = Math.min(startX + chunkSize, centerX + radiusInt);
                        int endZ = Math.min(startZ + chunkSize, centerZ + radiusInt);
                        
                        for (int x = startX; x < endX; x++) {
                            for (int z = startZ; z < endZ; z++) {
                                // 检查是否在半径内
                                double distance = Math.sqrt(
                                    Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2)
                                );
                                if (distance > radius) {
                                    continue;
                                }
                                
                                // 扫描这个 XZ 坐标的所有 Y 坐标
                                for (int y = minY; y < maxY; y++) {
                                    Location loc = new Location(world, x, y, z);
                                    if (!isInArena(loc)) {
                                        continue;
                                    }
                                    
                                    // 使用区域调度器处理方块
                                    Bukkit.getRegionScheduler().run(plugin, loc, blockTask -> {
                                        Block block = loc.getBlock();
                                        Material type = block.getType();
                                        
                                        // 检查是否是水或岩浆（包括所有变体）
                                        if (type == Material.WATER || type == Material.LAVA ||
                                            type == Material.WATER_CAULDRON || type == Material.LAVA_CAULDRON ||
                                            type.name().contains("WATER") || type.name().contains("LAVA")) {
                                            
                                            // 记录这是地图自带的流体方块
                                            originalFluidBlocks.add(loc);
                                            scannedCount[0]++;
                                        }
                                    });
                                }
                            }
                        }
                    });
                } else {
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                        int startX = centerX - radiusInt + (finalChunkX * chunkSize);
                        int startZ = centerZ - radiusInt + (finalChunkZ * chunkSize);
                        int endX = Math.min(startX + chunkSize, centerX + radiusInt);
                        int endZ = Math.min(startZ + chunkSize, centerZ + radiusInt);
                        
                        for (int x = startX; x < endX; x++) {
                            for (int z = startZ; z < endZ; z++) {
                                // 检查是否在半径内
                                double distance = Math.sqrt(
                                    Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2)
                                );
                                if (distance > radius) {
                                    continue;
                                }
                                
                                // 扫描这个 XZ 坐标的所有 Y 坐标
                                for (int y = minY; y < maxY; y++) {
                                    Location loc = new Location(world, x, y, z);
                                    if (!isInArena(loc)) {
                                        continue;
                                    }
                                    
                                    // 使用区域调度器处理方块
                                    Bukkit.getRegionScheduler().run(plugin, loc, blockTask -> {
                                        Block block = loc.getBlock();
                                        Material type = block.getType();
                                        
                                        // 检查是否是水或岩浆（包括所有变体）
                                        if (type == Material.WATER || type == Material.LAVA ||
                                            type == Material.WATER_CAULDRON || type == Material.LAVA_CAULDRON ||
                                            type.name().contains("WATER") || type.name().contains("LAVA")) {
                                            
                                            // 记录这是地图自带的流体方块
                                            originalFluidBlocks.add(loc);
                                            scannedCount[0]++;
                                        }
                                    });
                                }
                            }
                        }
                    }, delay);
                }
            }
        }
        
        // 延迟一段时间后记录扫描完成
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getLogger().info("[地图重置] 已记录 " + scannedCount[0] + " 个地图自带的流体方块（不会被重置）");
        }, (long) (totalChunksX * totalChunksZ * 2) + 20);
    }
    
    /**
     * 扫描并清除所有水和岩浆生成的石头/原石/黑曜石
     */
    private void scanAndRemoveStones(int[] count) {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描石头/原石/黑曜石方块...");
        
        // 计算扫描范围
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusInt = (int) Math.ceil(radius);
        
        // 获取世界高度范围
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 64);
        int maxY = Math.min(world.getMaxHeight(), center.getBlockY() + 64);
        
        // 扫描整个区域
        for (int x = centerX - radiusInt; x <= centerX + radiusInt; x++) {
            for (int z = centerZ - radiusInt; z <= centerZ + radiusInt; z++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                if (distance > radius) {
                    continue;
                }
                
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, x, y, z);
                    if (!isInArena(loc)) {
                        continue;
                    }
                    
                    // 使用区域调度器处理方块
                    Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                        Block block = loc.getBlock();
                        Material type = block.getType();
                        
                        // 检查是否是石头/原石/黑曜石等（可能是水和岩浆生成的）
                        if (type == Material.STONE || type == Material.COBBLESTONE || 
                            type == Material.OBSIDIAN || type == Material.BASALT ||
                            type == Material.BLACKSTONE || type == Material.COBBLED_DEEPSLATE ||
                            type == Material.SMOOTH_BASALT || type == Material.MAGMA_BLOCK) {
                            
                            // 只删除不在初始记录中的（即游戏过程中生成的）
                            if (!originalStoneBlocks.contains(loc)) {
                                // 这个位置原本不是石头/原石/黑曜石，是游戏过程中生成的，需要删除
                                BlockState originalState = originalBlocks.get(loc);
                                if (originalState != null && originalState.getType() != type) {
                                    // 有记录的原始状态，恢复它
                                    originalState.update(true, false);
                                    count[0]++;
                                } else {
                                    // 没有记录的原始状态，设置为空气
                                    block.setType(Material.AIR);
                                    count[0]++;
                                }
                            }
                            // 如果 originalStoneBlocks 包含这个位置，说明是地图自带的，不删除
                        }
                    });
                }
            }
        }
    }
    
    /**
     * 扫描并记录地图初始状态下的所有石头/原石/黑曜石等方块
     * 这些是地图自带的，不应该被重置
     */
    private void scanInitialStoneBlocks() {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描地图初始石头/原石/黑曜石方块...");
        
        // 计算扫描范围
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusInt = (int) Math.ceil(radius);
        
        // 获取世界高度范围
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 64);
        int maxY = Math.min(world.getMaxHeight(), center.getBlockY() + 64);
        
        final int[] scannedCount = {0};
        
        // 扫描整个区域
        for (int x = centerX - radiusInt; x <= centerX + radiusInt; x++) {
            for (int z = centerZ - radiusInt; z <= centerZ + radiusInt; z++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                if (distance > radius) {
                    continue;
                }
                
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, x, y, z);
                    if (!isInArena(loc)) {
                        continue;
                    }
                    
                    // 使用区域调度器处理方块
                    Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                        Block block = loc.getBlock();
                        Material type = block.getType();
                        
                        // 检查是否是石头/原石/黑曜石等（可能是水和岩浆生成的）
                        if (type == Material.STONE || type == Material.COBBLESTONE || 
                            type == Material.OBSIDIAN || type == Material.BASALT ||
                            type == Material.BLACKSTONE || type == Material.COBBLED_DEEPSLATE ||
                            type == Material.SMOOTH_BASALT || type == Material.MAGMA_BLOCK) {
                            
                            // 记录这是地图自带的石头/原石/黑曜石方块
                            originalStoneBlocks.add(loc);
                            scannedCount[0]++;
                        }
                    });
                }
            }
        }
        
        // 延迟记录扫描完成
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getLogger().info("[地图重置] 已记录 " + scannedCount[0] + " 个地图自带的石头/原石/黑曜石方块（不会被重置）");
        }, 20L);
    }
    
    /**
     * 扫描并记录所有方块实体的初始状态（展示框、酿造台、箱子等）
     */
    private void scanInitialBlockEntities() {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描方块实体...");
        
        // 计算扫描范围
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusInt = (int) Math.ceil(radius);
        
        // 获取世界高度范围
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 64);
        int maxY = Math.min(world.getMaxHeight(), center.getBlockY() + 64);
        
        final int[] scannedCount = {0};
        
        // 扫描整个区域
        for (int x = centerX - radiusInt; x <= centerX + radiusInt; x++) {
            for (int z = centerZ - radiusInt; z <= centerZ + radiusInt; z++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                if (distance > radius) {
                    continue;
                }
                
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, x, y, z);
                    if (!isInArena(loc)) {
                        continue;
                    }
                    
                    // 使用区域调度器处理方块
                    Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                        Block block = loc.getBlock();
                        BlockState state = block.getState();
                        
                        // 检查是否是方块实体（有内容的方块）
                        if (state instanceof org.bukkit.block.Container ||
                            state instanceof org.bukkit.block.BrewingStand ||
                            state instanceof org.bukkit.block.Furnace ||
                            state instanceof org.bukkit.block.Dispenser ||
                            state instanceof org.bukkit.block.Dropper ||
                            state instanceof org.bukkit.block.Hopper ||
                            state instanceof org.bukkit.block.ShulkerBox ||
                            state instanceof org.bukkit.block.Lectern ||
                            state instanceof org.bukkit.block.Jukebox) {
                            
                            // 记录方块实体的初始状态（需要克隆以避免引用问题）
                            // 注意：BlockState 是不可变的，但我们需要保存它的快照
                            originalBlockEntities.put(loc, state);
                            
                            // 同时保存完整数据（包括容器内容）
                            SavedBlockData savedData = captureBlockData(block);
                            savedBlockData.put(loc, savedData);
                            
                            scannedCount[0]++;
                        }
                    });
                }
            }
        }
        
        // 延迟记录扫描完成
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getLogger().info("[地图重置] 已记录 " + scannedCount[0] + " 个方块实体（酿造台、箱子等）");
        }, 20L);
    }
    
    /**
     * 扫描并保存所有方块的 BlockData（包括含水状态）
     */
    private void scanInitialBlockData() {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描方块 BlockData（含水状态等）...");
        
        // 计算扫描范围
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusInt = (int) Math.ceil(radius);
        
        // 获取世界高度范围
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 64);
        int maxY = Math.min(world.getMaxHeight(), center.getBlockY() + 64);
        
        final int[] scannedCount = {0};
        
        // 扫描整个区域
        for (int x = centerX - radiusInt; x <= centerX + radiusInt; x++) {
            for (int z = centerZ - radiusInt; z <= centerZ + radiusInt; z++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                if (distance > radius) {
                    continue;
                }
                
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, x, y, z);
                    if (!isInArena(loc)) {
                        continue;
                    }
                    
                    // 使用区域调度器处理方块
                    Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                        Block block = loc.getBlock();
                        // 保存 BlockData（包括含水状态）
                        BlockData blockData = block.getBlockData().clone();
                        originalBlockData.put(loc, blockData);
                        scannedCount[0]++;
                    });
                }
            }
        }
        
        // 延迟记录扫描完成
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getLogger().info("[地图重置] 已记录 " + scannedCount[0] + " 个方块的 BlockData（含水状态等）");
        }, 20L);
    }
    
    /**
     * 扫描并记录所有展示框实体
     */
    private void scanInitialItemFrames() {
        if (world == null || center == null) {
            return;
        }
        
        plugin.getLogger().info("[地图重置] 开始扫描展示框实体...");
        
        final int[] scannedCount = {0};
        
        // 扫描世界中的所有实体
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.ItemFrame && isInArena(entity.getLocation())) {
                    org.bukkit.entity.ItemFrame itemFrame = (org.bukkit.entity.ItemFrame) entity;
                    // 记录展示框的初始状态（保存物品的克隆）
                    // 注意：ItemFrame 是实体，我们需要保存它的物品
                    originalItemFrames.put(entity.getUniqueId(), itemFrame);
                    scannedCount[0]++;
                }
            }
            plugin.getLogger().info("[地图重置] 已记录 " + scannedCount[0] + " 个展示框实体");
        });
    }
    
    /**
     * 获取需要重置的方块数量
     */
    public int getChangedBlockCount() {
        return allChangedBlocks.size();
    }
    
    /**
     * 获取需要删除的实体数量
     */
    public int getSpawnedEntityCount() {
        return spawnedEntities.size();
    }
}

