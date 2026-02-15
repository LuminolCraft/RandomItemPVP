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
import org.bukkit.event.player.PlayerJoinEvent;
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
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ConfigManager config = arenaManager.getConfig();
        Location lobbyLocation = config != null ? config.loadLobbyLocation() : null;
        
        // 检查玩家是否在某个房间中
        String arenaName = arenaManager.getPlayerArena(player);
        if (arenaName != null) {
            // 玩家在某个房间中，将其传送到大厅
            if (lobbyLocation != null && config != null && config.isLobbyEnabled()) {
                player.teleport(lobbyLocation);
                player.sendMessage("§c[随机物品PVP] 你已重连，被传送到大厅！");
            }
            // 从房间中移除玩家
            arenaManager.removePlayerFromArena(player);
        } else {
            // 新玩家首次加入服务器，将其传送到大厅
            if (lobbyLocation != null && config != null && config.isLobbyEnabled()) {
                player.teleport(lobbyLocation);
                // 设置重生点为大厅位置
                player.setBedSpawnLocation(lobbyLocation, true);
                player.sendMessage("§a[随机物品PVP] 欢迎加入！已传送到大厅。");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onBlockPlace(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onBlockBreak(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            instance.onBucketEmpty(event);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketFill(PlayerBucketFillEvent event) {
        GameInstance instance = resolveInstance(event.getPlayer());
        if (instance != null) {
            // 这里可以添加其他逻辑
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        // 移除边界检查，允许所有实体在任何位置生成
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        // 移除边界检查，允许所有生物在任何位置生成
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
            // 方块形成由地图重置系统处理
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
                // 流体扩散由地图重置系统处理
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
            // 结构生长由地图重置系统处理
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        // 爆炸破坏由地图重置系统处理
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakByEntity(org.bukkit.event.entity.EntityBreakDoorEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        // 生物破坏门由地图重置系统处理
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPickupByEntity(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        // 实体拾取物品由地图重置系统处理
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
                // 脚手架放置由地图重置系统处理
            }
        }
    }
    
}


