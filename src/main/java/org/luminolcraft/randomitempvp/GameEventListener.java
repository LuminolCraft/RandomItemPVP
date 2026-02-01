package org.luminolcraft.randomitempvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * 将 Bukkit 事件路由到对应房间的 GameInstance，保证多房间逻辑生效。
 * 同时记录所有地图变化用于重置。
 */
public class GameEventListener implements Listener {

    private final ArenaManager arenaManager;

    public GameEventListener(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    private GameInstance resolveInstance(Player player) {
        if (player == null || arenaManager == null) {
            return null;
        }
        String arenaName = arenaManager.getPlayerArena(player);
        if (arenaName == null) {
            return null;
        }
        GameArena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            return null;
        }
        return arena.getGameInstance();
    }
    
    private GameInstance resolveInstanceByLocation(Location location) {
        if (location == null || arenaManager == null) {
            return null;
        }
        // 通过位置查找对应的房间
        for (GameArena arena : arenaManager.getAllArenas()) {
            if (arena.getWorld() != null && arena.getWorld().equals(location.getWorld())) {
                Location spawnLoc = arena.getSpawnLocation();
                if (spawnLoc != null) {
                    // 检查位置是否在房间范围内（使用配置的半径）
                    ConfigManager config = arenaManager.getConfig();
                    if (config != null) {
                        double radius = config.getArenaRadius();
                        if (location.distance(spawnLoc) <= radius) {
                            return arena.getGameInstance();
                        }
                    }
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        GameInstance instance = resolveInstance(event.getEntity());
        if (instance != null) {
            instance.onPlayerDeath(event);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onPlayerRespawn(event);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onPlayerQuit(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onBlockPlace(event);
            // 记录方块放置（使用被替换的方块状态）
            if (!event.isCancelled()) {
                instance.recordBlockPlace(event.getBlock(), event.getBlockReplacedState());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onBlockBreak(event);
            // 记录方块破坏
            if (!event.isCancelled()) {
                instance.recordBlockBreak(event.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmptyPre(PlayerBucketEmptyEvent event) {
        // 在事件执行前记录原始状态（包括含水状态）
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null && !event.isCancelled() && event.getBlockClicked() != null) {
            Block clickedBlock = event.getBlockClicked();
            // 记录被点击方块的完整状态（倒水前，可能不含水），重置时会恢复
            instance.recordBlockBreak(clickedBlock);
            
            // 如果水会被放置到新位置（不是点击的方块），也记录那个位置的原始状态
            Block targetBlock = clickedBlock.getRelative(event.getBlockFace());
            if (!targetBlock.equals(clickedBlock)) {
                instance.recordBlockBreak(targetBlock);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketEmptyAfter(PlayerBucketEmptyEvent event) {
        // 在事件执行后，如果方块变成了含水状态，需要再次记录
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null && !event.isCancelled() && event.getBlockClicked() != null) {
            Block clickedBlock = event.getBlockClicked();
            // 检查方块是否变成了含水状态
            try {
                org.bukkit.block.data.BlockData blockData = clickedBlock.getBlockData();
                if (blockData instanceof org.bukkit.block.data.Waterlogged) {
                    org.bukkit.block.data.Waterlogged waterlogged = (org.bukkit.block.data.Waterlogged) blockData;
                    if (waterlogged.isWaterlogged()) {
                        // 方块变成了含水状态，确保已记录原始状态（不含水）
                        // 原始状态已经在 LOWEST 优先级记录了，这里不需要再记录
                        // 但我们需要确保恢复时能正确恢复
                    }
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onBucketEmpty(event);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFillPre(PlayerBucketFillEvent event) {
        // 在事件执行前记录原始状态
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null && !event.isCancelled() && event.getBlockClicked() != null) {
            Block clickedBlock = event.getBlockClicked();
            // 记录被点击方块的原始状态（填充桶前，可能是含水方块）
            instance.recordBlockBreak(clickedBlock);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketFill(PlayerBucketFillEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            // 这里可以添加其他逻辑，记录已经在 LOWEST 优先级完成
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Entity entity = event.getEntity();
        GameInstance instance = resolveInstanceByLocation(entity.getLocation());
        if (instance != null) {
            instance.recordEntitySpawn(entity);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Entity entity = event.getEntity();
        GameInstance instance = resolveInstanceByLocation(entity.getLocation());
        if (instance != null) {
            instance.recordEntitySpawn(entity);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockFormPre(BlockFormEvent event) {
        // 在方块形成前记录原始状态
        if (event.isCancelled()) {
            return;
        }
        
        Block block = event.getBlock();
        Material type = block.getType();
        GameInstance instance = resolveInstanceByLocation(block.getLocation());
        if (instance != null) {
            // 检查是否是水和岩浆接触生成的石头/原石等
            // 在形成前记录原始状态（通常是空气或水/岩浆）
            if (type == Material.STONE || type == Material.COBBLESTONE || 
                type == Material.OBSIDIAN || type == Material.BASALT ||
                type == Material.BLACKSTONE || type == Material.COBBLED_DEEPSLATE ||
                type == Material.SMOOTH_BASALT || type == Material.MAGMA_BLOCK) {
                // 记录形成前的状态（通常是空气）
                instance.recordBlockBreak(block);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockForm(BlockFormEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Block block = event.getBlock();
        Material type = block.getType();
        GameInstance instance = resolveInstanceByLocation(block.getLocation());
        if (instance != null) {
            // 检查是否是基岩生成（例如末地传送门框架）
            if (type == Material.BEDROCK) {
                instance.recordBedrockPlace(block);
            } 
            // 检查是否是流动的水或岩浆（通过 BlockFormEvent 形成）
            else if (type == Material.WATER || type == Material.LAVA ||
                     type.name().contains("WATER") || type.name().contains("LAVA")) {
                // 记录流动的水/岩浆
                instance.recordFluidChange(block);
            } 
            // 检查是否是水和岩浆接触生成的石头/原石等（已经在 LOWEST 优先级记录了）
            else if (type == Material.STONE || type == Material.COBBLESTONE || 
                     type == Material.OBSIDIAN || type == Material.BASALT ||
                     type == Material.BLACKSTONE || type == Material.COBBLED_DEEPSLATE ||
                     type == Material.SMOOTH_BASALT || type == Material.MAGMA_BLOCK) {
                // 已经在 LOWEST 优先级记录了，这里不需要再记录
            }
            else {
                // 记录其他方块形成（如冰、雪等）
                instance.recordBlockPlace(block);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Block block = event.getBlock();
        Material type = block.getType();
        
        // BlockSpreadEvent 主要用于蘑菇和火的扩散
        // 但我们也检查是否是水或岩浆的扩散
        if (type == Material.WATER || type == Material.LAVA ||
            type.name().contains("WATER") || type.name().contains("LAVA")) {
            
            GameInstance instance = resolveInstanceByLocation(block.getLocation());
            if (instance != null) {
                // 记录流体扩散（流动的水/岩浆）
                instance.recordFluidChange(block);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStructureGrow(StructureGrowEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Location loc = event.getLocation();
        GameInstance instance = resolveInstanceByLocation(loc);
        if (instance != null) {
            // 记录结构生长（如树、蘑菇等）
            for (org.bukkit.block.BlockState blockState : event.getBlocks()) {
                instance.recordBlockPlace(blockState.getBlock());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        // 记录爆炸破坏的所有方块
        for (Block block : event.blockList()) {
            GameInstance instance = resolveInstanceByLocation(block.getLocation());
            if (instance != null) {
                // 记录被爆炸破坏的方块
                instance.recordBlockBreak(block);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Block toBlock = event.getToBlock();
        Material type = toBlock.getType();
        
        // 检查是否是脚手架自动放置（脚手架会在玩家脚下自动放置）
        // 注意：脚手架的自动放置实际上会触发 BlockPlaceEvent，但为了保险起见，我们也在这里检查
        if (type == Material.SCAFFOLDING) {
            GameInstance instance = resolveInstanceByLocation(toBlock.getLocation());
            if (instance != null) {
                // 获取原始方块（被替换的方块）
                Block fromBlock = event.getBlock();
                if (fromBlock.getType() != Material.SCAFFOLDING) {
                    // 如果原始方块不是脚手架，说明这是新放置的脚手架
                    // 记录脚手架的自动放置，使用原始方块的状态作为被替换的状态
                    instance.recordBlockPlace(toBlock, fromBlock.getState());
                }
            }
        }
    }
}

