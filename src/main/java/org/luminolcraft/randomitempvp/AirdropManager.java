package org.luminolcraft.randomitempvp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.Map;
import java.util.HashMap;

/**
 * 空投系统 - 定时掉落稀有装备箱
 */
public class AirdropManager implements Listener {
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<String, ScheduledTask> arenaAirdropTasks = new HashMap<>(); // 多房间系统的空投任务 <房间名, 任务>
    private final Set<Location> airdropChests = new HashSet<>();
    private final Map<Location, Material> originalBlocks = new HashMap<>(); // 保存被信标替换的原始方块
    private final Set<Location> openedChests = new HashSet<>(); // 记录已被打开过的空投箱
    private AllyMobManager allyMobManager = null;
    
    // 空投配置（快节奏）
    private static final long AIRDROP_INTERVAL_TICKS = 800L; // 40秒一次空投（加快）
    private static final long FIRST_AIRDROP_DELAY = 400L; // 首次空投延迟20秒（提前）
    
    public AirdropManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 设置盟友生物管理器
     */
    public void setAllyMobManager(AllyMobManager allyMobManager) {
        this.allyMobManager = allyMobManager;
    }
    
    /**
     * 为指定房间启动空投系统（多房间系统）
     */
    public void startAirdropForArena(String arenaName, Location centerLocation) {
        // 如果已有任务，先取消
        if (arenaAirdropTasks.containsKey(arenaName)) {
            ScheduledTask oldTask = arenaAirdropTasks.get(arenaName);
            if (oldTask != null) {
                oldTask.cancel();
            }
        }
        
        // 创建新的空投任务
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            // 检查房间是否仍在运行
            boolean isRunning = false;
            if (plugin instanceof RandomItemPVP) {
                ArenaManager arenaManager = ((RandomItemPVP) plugin).getArenaManager();
                if (arenaManager != null) {
                    GameArena arena = arenaManager.getArena(arenaName);
                    if (arena != null && arena.getGameInstance() != null && arena.getGameInstance().isRunning()) {
                        isRunning = true;
                    }
                }
            }
            
            if (!isRunning) {
                scheduledTask.cancel();
                arenaAirdropTasks.remove(arenaName);
                return;
            }
            
            if (centerLocation != null && centerLocation.getWorld() != null) {
                dropAirdropForArena(arenaName, centerLocation);
            }
        }, FIRST_AIRDROP_DELAY, AIRDROP_INTERVAL_TICKS);
        
        arenaAirdropTasks.put(arenaName, task);
    }
    
    /**
     * 停止指定房间的空投系统
     */
    public void stopAirdropForArena(String arenaName) {
        ScheduledTask task = arenaAirdropTasks.remove(arenaName);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * 停止所有房间的空投系统
     */
    public void stopAllAirdrops() {
        // 停止所有多房间系统的空投
        for (Map.Entry<String, ScheduledTask> entry : new HashMap<>(arenaAirdropTasks).entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().cancel();
            }
        }
        arenaAirdropTasks.clear();
        
        // 清理所有空投箱
        clearAirdropChests();
        openedChests.clear();
    }
    
    /**
     * 生成空投箱
     */
    private void spawnAirdropChest(Location location) {
        World world = location.getWorld();
        
        // 在空中显示信标光束效果
        Location beaconBase = location.clone().subtract(0, 1, 0);
        
        // 保存信标底座位置的原始方块
        Material originalBlock = beaconBase.getBlock().getType();
        
        // 如果原始方块是空气或非固体方块，保存为草方块（避免留下洞）
        if (!originalBlock.isSolid() || originalBlock == Material.AIR) {
            originalBlock = Material.GRASS_BLOCK;
        }
        
        originalBlocks.put(beaconBase, originalBlock);
        

        
        beaconBase.getBlock().setType(Material.BEACON);
        location.getBlock().setType(Material.CHEST);
        
        // 记录箱子位置
        airdropChests.add(location);
        
        // 填充宝箱
        Chest chest = (Chest) location.getBlock().getState();
        fillAirdropChest(chest.getInventory(), location.getWorld());
        
        // 粒子效果
        world.spawnParticle(Particle.FIREWORK, location.clone().add(0.5, 1, 0.5), 100, 0.5, 2, 0.5, 0.1);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0.5, 1, 0.5), 50, 0.5, 2, 0.5, 0.05);
        
        // 音效
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0f, 1.0f);
        world.playSound(location, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
        
        // 只向游戏中的玩家播报
        if (plugin instanceof RandomItemPVP) {
            ArenaManager arenaManager = ((RandomItemPVP) plugin).getArenaManager();
            if (arenaManager != null) {
                for (String arenaName : arenaManager.getArenaNames()) {
                    GameArena arena = arenaManager.getArena(arenaName);
                    if (arena != null && arena.getGameInstance().isRunning()) {
                        Set<Player> participants = arena.getGameInstance().getParticipants();
                        for (Player p : participants) {
                            if (p.isOnline()) {
                                p.sendMessage("§a§l【空投已送达】§e快去抢夺稀有装备！");
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 填充空投箱内容
     */
    private void fillAirdropChest(Inventory inv, World world) {
        if (inv == null) {
            return;
        }
        
        // 稀有物品池
        List<ItemStack> rareItems = Arrays.asList(
            new ItemStack(Material.DIAMOND_SWORD, 1),
            new ItemStack(Material.DIAMOND_AXE, 1),
            new ItemStack(Material.BOW, 1),
            new ItemStack(Material.CROSSBOW, 1),
            new ItemStack(Material.DIAMOND_HELMET, 1),
            new ItemStack(Material.DIAMOND_CHESTPLATE, 1),
            new ItemStack(Material.DIAMOND_LEGGINGS, 1),
            new ItemStack(Material.DIAMOND_BOOTS, 1),
            new ItemStack(Material.SHIELD, 1),
            new ItemStack(Material.TOTEM_OF_UNDYING, 1),
            new ItemStack(Material.GOLDEN_APPLE, 3),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
            new ItemStack(Material.ENDER_PEARL, 3),
            new ItemStack(Material.TNT, 5),
            new ItemStack(Material.END_CRYSTAL, 2),
            new ItemStack(Material.FIRE_CHARGE, 8),
            new ItemStack(Material.ARROW, 32),
            new ItemStack(Material.SPECTRAL_ARROW, 16),
            new ItemStack(Material.NETHERITE_INGOT, 1)
        );
        
        // 随机选择5-8个物品
        int itemCount = 5 + random.nextInt(4);
        for (int i = 0; i < itemCount; i++) {
            ItemStack item = rareItems.get(random.nextInt(rareItems.size())).clone();
            inv.addItem(item);
        }
        
        // 必定包含一个特殊物品
        ItemStack guaranteed = Arrays.asList(
            new ItemStack(Material.TOTEM_OF_UNDYING, 1),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2),
            new ItemStack(Material.NETHERITE_INGOT, 1)
        ).get(random.nextInt(3));
        inv.addItem(guaranteed);
        
        // 检查是否应该添加盟友刷怪蛋（游戏后期）
        if (allyMobManager != null && allyMobManager.isEnabled() && plugin instanceof RandomItemPVP && world != null) {
            RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
            ArenaManager arenaManager = pluginInstance.getArenaManager();
            if (arenaManager != null) {
                ConfigManager config = arenaManager.getConfig();
                if (config != null) {
                    // 获取当前边界大小和初始边界大小
                    WorldBorder border = world.getWorldBorder();
                    if (border != null) {
                        double currentSize = border.getSize();
                        double initialSize = config.getInitialBorderSize();
                        
                        // 计算游戏进度（边界缩小比例）
                        double progress = 1.0 - (currentSize / initialSize); // 0.0 = 游戏开始, 1.0 = 游戏结束
                        double threshold = config.getAllyGameProgressThreshold();
                        
                        // 如果达到阈值，添加盟友刷怪蛋
                        if (progress >= threshold) {
                            int eggsPerAirdrop = config.getAllySpawnEggsPerAirdrop();
                            Map<EntityType, Integer> allyWeights = config.getAllyWeights();
                            
                            if (!allyWeights.isEmpty()) {
                                for (int i = 0; i < eggsPerAirdrop; i++) {
                                    EntityType selectedType = selectWeightedAllyType(allyWeights);
                                    if (selectedType != null) {
                                        ItemStack allyEgg = allyMobManager.createAllySpawnEgg(selectedType);
                                        inv.addItem(allyEgg);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 根据权重选择盟友生物类型
     */
    private EntityType selectWeightedAllyType(Map<EntityType, Integer> weights) {
        if (weights.isEmpty()) {
            return null;
        }
        
        // 计算总权重
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return null;
        }
        
        // 随机选择
        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (Map.Entry<EntityType, Integer> entry : weights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                return entry.getKey();
            }
        }
        
        // 如果没找到（不应该发生），返回第一个
        return weights.keySet().iterator().next();
    }
    
    /**
     * 清除所有空投箱
     */
    private void clearAirdropChests() {
        // 方块操作必须在区域调度器中执行（Folia 要求）
        // 但如果插件已禁用（服务器关闭），则跳过区块操作，只清理集合
        for (Location loc : airdropChests) {
            if (plugin.isEnabled()) {
                // 插件运行中，使用区域调度器（线程安全）
                Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                    try {
                        // 检查世界是否有效
                        if (loc.getWorld() == null) {
                            return;
                        }
                        if (loc.getBlock().getType() == Material.CHEST) {
                            loc.getBlock().setType(Material.AIR);
                        }
                        // 恢复信标底座的原始方块
                        Location below = loc.clone().subtract(0, 1, 0);
                        if (below.getWorld() != null && below.getBlock().getType() == Material.BEACON) {
                            Material original = originalBlocks.getOrDefault(below, Material.AIR);
                            below.getBlock().setType(original);
                        }
                    } catch (Exception e) {
                        // 捕获所有异常，确保清理过程不会中断
                    }
                });
            }
        }
        airdropChests.clear();
        originalBlocks.clear(); // 清除原始方块记录
    }
    

    
    /**
     * 为指定房间投放空投
     */
    private void dropAirdropForArena(String arenaName, Location center) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        
        World world = center.getWorld();
        
        // 获取当前边界大小，确保空投掉落在边界内
        WorldBorder border = world.getWorldBorder();
        if (border == null) {
            return;
        }
        
        double currentSize = border.getSize();
        
        // 检查边界是否已缩小到最小范围
        if (plugin instanceof RandomItemPVP) {
            ArenaManager arenaManager = ((RandomItemPVP) plugin).getArenaManager();
            if (arenaManager != null) {
                ConfigManager config = arenaManager.getConfig();
                if (config != null) {
                    double minSize = config.getMinBorderSize();
                    // 如果边界已达到最小大小，不再刷新空投
                    if (currentSize <= minSize) {
                        return;
                    }
                }
            }
        }
        
        // 空投范围为当前边界的 70%（留出安全边距）
        double currentRadius = currentSize / 2.0;
        int maxOffset = (int) (currentRadius * 0.7);
        if (maxOffset < 10) maxOffset = 10; // 最小范围 10 格
        
        // 随机位置（在边界安全范围内）
        int range = maxOffset * 2;
        int offsetX = random.nextInt(range) - maxOffset;
        int offsetZ = random.nextInt(range) - maxOffset;
        
        // 方块操作必须在区域调度器中执行（Folia 要求）
        Location checkLoc = new Location(world, center.getBlockX() + offsetX, 64, center.getBlockZ() + offsetZ);
        Bukkit.getRegionScheduler().run(plugin, checkLoc, task -> {
            int y = world.getHighestBlockYAt(center.getBlockX() + offsetX, center.getBlockZ() + offsetZ) + 1;
            Location dropLoc = new Location(world, center.getBlockX() + offsetX, y, center.getBlockZ() + offsetZ);
            
            // 只向房间内的玩家播报
            if (plugin instanceof RandomItemPVP) {
                ArenaManager arenaManager = ((RandomItemPVP) plugin).getArenaManager();
                if (arenaManager != null) {
                    GameArena arena = arenaManager.getArena(arenaName);
                    if (arena != null) {
                        GameInstance instance = arena.getGameInstance();
                        if (instance != null) {
                            Set<Player> participants = instance.getParticipants();
                            
                            // 向房间内的玩家播报
                            for (Player p : participants) {
                                if (p.isOnline()) {
                                    p.sendMessage("§6§l【空投来袭】§e稀有装备箱即将降落！坐标：§b" + 
                                        dropLoc.getBlockX() + ", " + dropLoc.getBlockY() + ", " + dropLoc.getBlockZ());
                                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.8f);
                                }
                            }
                        }
                    }
                }
            }
            
            // 延迟3秒后投放（给玩家反应时间）
            Bukkit.getRegionScheduler().runDelayed(plugin, dropLoc, spawnTask -> {
                spawnAirdropChest(dropLoc);
            }, 60L);
        });
    }
    
    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        // 检查玩家是否在运行中的游戏中
        boolean inGame = false;
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (plugin instanceof RandomItemPVP) {
                ArenaManager arenaManager = ((RandomItemPVP) plugin).getArenaManager();
                if (arenaManager != null && arenaManager.isPlayerInArena(player)) {
                    String arenaName = arenaManager.getPlayerArena(player);
                    if (arenaName != null) {
                        GameArena playerArena = arenaManager.getArena(arenaName);
                        if (playerArena != null && playerArena.getGameInstance().isRunning()) {
                            inGame = true;
                        }
                    }
                }
            }
        }
        
        if (!inGame) {
            return;
        }
        
        if (event.getInventory().getHolder() instanceof Chest) {
            Chest chest = (Chest) event.getInventory().getHolder();
            Location chestLoc = chest.getLocation();
            
            // 检查是否是空投箱
            if (airdropChests.contains(chestLoc)) {
                // 检查是否已经被打开过（防止刷屏）
                if (openedChests.contains(chestLoc)) {
                    // 已经打开过，不再广播
                    return;
                }
                
                // 标记为已打开
                openedChests.add(chestLoc);
                
                Player player = (Player) event.getPlayer();
                
                // 只在第一次打开时广播（不使用标题，避免过于干扰）
                // 聊天消息播报
                if (plugin instanceof RandomItemPVP) {
                    ArenaManager arenaManager = ((RandomItemPVP) plugin).getArenaManager();
                    if (arenaManager != null && arenaManager.isPlayerInArena(player)) {
                        String arenaName = arenaManager.getPlayerArena(player);
                        if (arenaName != null) {
                            GameArena arena = arenaManager.getArena(arenaName);
                            if (arena != null) {
                                GameInstance instance = arena.getGameInstance();
                                Set<Player> participants = instance.getParticipants();
                                for (Player p : participants) {
                                    if (p.isOnline()) {
                                        p.sendMessage("§6§l【空投】§e" + player.getName() + " §7打开了空投箱并获得稀有装备！");
                                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 特效（只在箱子位置）
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, chestLoc.clone().add(0.5, 1, 0.5), 30, 0.5, 0.5, 0.5, 0.1);
                player.getWorld().spawnParticle(Particle.FIREWORK, chestLoc.clone().add(0.5, 1, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                
                // 给打开玩家特别的音效和动作栏提示
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.sendActionBar(Component.text("§6§l✦ §e你获得了空投装备！ §6§l✦"));
            }
        }
    }
}

