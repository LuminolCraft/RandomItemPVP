package org.luminolcraft.randomitempvp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有游戏房间
 */
public class ArenaManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PlayerStatsManager statsManager;
    
    // 房间列表 <房间名, 房间>
    private final Map<String, GameArena> arenas = new ConcurrentHashMap<>();
    
    // 玩家所在房间 <玩家, 房间名>
    private final Map<Player, String> playerArena = new ConcurrentHashMap<>();
    
    // 自动启动延迟任务 <房间名, 任务>
    private final Map<String, ScheduledTask> autoStartTasks = new ConcurrentHashMap<>();
    
    // 地图锁定机制 <地图ID, 房间名>
    private final Map<String, String> lockedMaps = new ConcurrentHashMap<>();
    
    public ArenaManager(JavaPlugin plugin, ConfigManager config, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.config = config;
        this.statsManager = statsManager;
    }
    
    /**
     * 向房间内的所有玩家发送消息
     * @param arenaName 房间名
     * @param message 消息内容
     */
    private void sendMessageToArena(String arenaName, String message) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) return;
        
        GameInstance instance = arena.getGameInstance();
        Set<Player> participants = instance.getParticipants();
        
        for (Player player : participants) {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
    
    /**
     * 向房间内的所有玩家广播消息
     * @param arenaName 房间名
     * @param messages 消息内容数组
     */
    private void sendMessagesToArena(String arenaName, String... messages) {
        for (String message : messages) {
            sendMessageToArena(arenaName, message);
        }
    }
    
    /**
     * 发送地图选择消息（使用更明显的方式）
     */
    private void sendMapSelectedMessage(String arenaName, String mapName) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) return;
        
        GameInstance instance = arena.getGameInstance();
        Set<Player> participants = instance.getParticipants();
        
        for (Player player : participants) {
            if (player.isOnline()) {
                // 使用标题显示（非常明显）
                player.sendTitle(
                    "§a§l地图已选择！",
                    "§e" + mapName,
                    10, 60, 20
                );
                
                // 发送聊天消息
                player.sendMessage("§6═══════════════════════════════════");
                player.sendMessage("§a§l【地图选择完成】");
                player.sendMessage("§7已选择地图：§e§l" + mapName);
                player.sendMessage("§7游戏即将开始，请做好准备！");
                player.sendMessage("§6═══════════════════════════════════");
                
                // 使用动作栏持续显示
                player.sendActionBar(net.kyori.adventure.text.Component.text("§a§l已选择地图：§e§l" + mapName + " §7| 游戏即将开始！"));
            }
        }
    }
    
    /**
     * 创建房间（已禁用，只支持从配置文件加载固定房间）
     * @param arenaName 房间名
     * @param creator 创建者（可选，如果提供则自动加入房间）
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Player creator) {
        if (creator != null) {
            creator.sendMessage("§c错误：已禁用动态创建房间功能！");
            creator.sendMessage("§c请在 config-modules/arenas.yml 中配置固定房间");
        } else {
            plugin.getLogger().warning("已禁用动态创建房间功能，请在 config-modules/arenas.yml 中配置固定房间");
        }
        return false;
    }
    
    /**
     * 创建房间（已禁用，只支持从配置文件加载固定房间）
     * @param arenaName 房间名
     * @param spawnLocation 出生点
     * @param creator 创建者（可选，如果提供则自动加入房间）
     * @param presetName 配置预设名称（可选，如果为null则使用主配置）
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Location spawnLocation, Player creator, String presetName) {
        if (creator != null) {
            creator.sendMessage("§c错误：已禁用动态创建房间功能！");
            creator.sendMessage("§c请在 config-modules/arenas.yml 中配置固定房间");
        } else {
            plugin.getLogger().warning("已禁用动态创建房间功能，请在 config-modules/arenas.yml 中配置固定房间");
        }
        return false;
    }
    
    /**
     * 创建房间（已禁用，只支持从配置文件加载固定房间）
     * @param arenaName 房间名
     * @param spawnLocation 出生点
     * @param creator 创建者（可选，如果提供则自动加入房间）
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Location spawnLocation, Player creator) {
        return createArena(arenaName, spawnLocation, creator, null);
    }
    
    /**
     * 创建房间（已禁用，只支持从配置文件加载固定房间）
     * @param arenaName 房间名
     * @param spawnLocation 出生点
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Location spawnLocation) {
        return createArena(arenaName, spawnLocation, null);
    }
    
    /**
     * 删除房间
     * @param arenaName 房间名
     * @return 是否删除成功
     */
    public boolean deleteArena(String arenaName) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) {
            return false; // 房间不存在
        }
        
        // 无论什么状态，都先强制停止游戏实例（确保倒计时和游戏都被停止）
        GameInstance instance = arena.getGameInstance();
        instance.forceStop(); // 强制停止游戏实例
        
        // 取消自动启动任务
        cancelAutoStart(arenaName);
        
        // 取消地图投票
        RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
        if (pluginInstance != null) {
            MapVoteManager voteManager = pluginInstance.getMapVoteManager();
            if (voteManager != null) {
                voteManager.cancelVote(arenaName);
            }
        }
        
        // 从玩家房间映射中移除所有在此房间的玩家
        List<Player> toRemove = new ArrayList<>();
        for (Map.Entry<Player, String> entry : playerArena.entrySet()) {
            if (entry.getValue().equals(arenaName)) {
                toRemove.add(entry.getKey());
            }
        }
        for (Player player : toRemove) {
            playerArena.remove(player);
        }
        
        // 从房间列表中移除（最后一步，确保所有清理都完成了）
        arenas.remove(arenaName);
        
        // 释放地图锁定
        String currentMapId = arena.getCurrentMapId();
        if (currentMapId != null && lockedMaps.containsKey(currentMapId) && lockedMaps.get(currentMapId).equals(arenaName)) {
            lockedMaps.remove(currentMapId);
            plugin.getLogger().info("[房间 " + arenaName + "] 已释放地图锁定: " + currentMapId);
        }
        
        // 从配置文件删除
        removeArenaConfig(arenaName);
        
        plugin.getLogger().info("房间 '" + arenaName + "' 已删除");
        return true;
    }
    
    /**
     * 验证世界 key 是否为实例世界（安全措施）
     * 实例世界的格式应该是：<模板key>_<房间名>
     * @param worldKey 世界 key
     * @param arenaName 房间名（用于验证）
     * @return 是否为有效的实例世界 key
     */
    private boolean isValidInstanceWorldKey(String worldKey, String arenaName) {
        if (worldKey == null || arenaName == null || arenaName.isEmpty()) {
            return false;
        }
        
        // 实例世界 key 应该包含下划线和房间名
        String normalizedArenaName = arenaName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        
        // 检查是否以下划线+房间名结尾
        if (!worldKey.toLowerCase().endsWith("_" + normalizedArenaName)) {
            return false;
        }
        
        // 确保包含下划线（模板名_房间名格式）
        if (!worldKey.contains("_")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 清理房间的世界实例（游戏结束后调用）
     * 专注于释放地图锁定
     * @param arena 房间
     */
    public void cleanupArenaWorld(GameArena arena) {
        // 地图重置由外部系统处理，无需手动清理世界
        plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 地图重置由外部系统处理，无需手动清理世界");

        
        // 释放当前地图的锁定
        String currentMapId = arena.getCurrentMapId();
        if (currentMapId != null && lockedMaps.containsKey(currentMapId) && lockedMaps.get(currentMapId).equals(arena.getArenaName())) {
            lockedMaps.remove(currentMapId);
            plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 已释放地图锁定: " + currentMapId);
        }
        
        // 获取投票管理器
        RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
        MapVoteManager voteManager = pluginInstance != null ? pluginInstance.getMapVoteManager() : null;
        
        // 清除当前地图选择，确保下次游戏时重新投票
        arena.setCurrentMapId(null);
        
        // 清除投票管理器中保存的地图选择
        if (voteManager != null) {
            voteManager.cancelVote(arena.getArenaName());
        }
    }
    
    /**
     * 玩家加入房间
     * @param player 玩家
     * @param arenaName 房间名
     * @return 是否加入成功
     */
    public boolean joinArena(Player player, String arenaName) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) {
            return false; // 房间不存在
        }
        
        // 检查玩家是否已在其他房间
        String currentArena = playerArena.get(player);
        if (currentArena != null) {
            if (currentArena.equals(arenaName)) {
                // 玩家已经在当前房间中，检查房间状态
                GameInstance instance = arena.getGameInstance();
                // 如果游戏不在运行或准备中，允许重新加入（更新位置等）
                if (!instance.isRunning() && !instance.isPreparing()) {
                    // 允许重新加入（更新原始位置和传送）
                    if (instance.joinGame(player)) {
                        player.sendMessage("§a已重新加入房间 '§6" + arenaName + "§a'！");
                        
                        int playerCount = arena.getPlayerCount();
                        int minPlayers = config.getMinPlayers();
                        
                        // 检查地图投票
                        RandomItemPVP pluginInstance2 = (RandomItemPVP) plugin;
                        MapVoteManager voteManager2 = null;
                        if (pluginInstance2 != null) {
                            voteManager2 = pluginInstance2.getMapVoteManager();
                        }
                        
                        if (playerCount < minPlayers) {
                            player.sendMessage("§7当前玩家数：§e" + playerCount + "§7/§e" + minPlayers);
                            player.sendMessage("§7还差 §e" + (minPlayers - playerCount) + " §7人即可开始投票！");
                            cancelAutoStart(arenaName);
                        } else {
                            // 人数足够，检查是否需要开始投票
                            if (voteManager2 != null && !voteManager2.isVoting(arenaName)) {
                                // 达到最少玩家数，开始投票
                                if (voteManager2.startVote(arenaName, playerCount, minPlayers)) {
                                    sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 玩家数已足够，地图投票开始！");
                                }
                            } else if (voteManager2 != null && voteManager2.isVoting(arenaName)) {
                                // 正在投票，延长投票时间
                                voteManager2.extendVoteTime(arenaName, 5); // 延长5秒
                            }
                            
                            if (!arena.isPreparing() && !arena.isRunning()) {
                                if (autoStartTasks.containsKey(arenaName)) {
                                    cancelAutoStart(arenaName);
                                    sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 有新玩家加入！延迟计时已重置...");
                                }
                                scheduleAutoStart(arena);
                            }
                        }
                        return true;
                    }
                } else {
                    player.sendMessage("§e你已经在房间 '" + arenaName + "' 中了！");
                    return false;
                }
            } else {
                leaveArena(player); // 离开之前的房间
            }
        }
        
        // 检查房间状态
        if (!arena.canJoin()) {
            player.sendMessage("§c房间 '" + arenaName + "' 正在进行游戏，无法加入！");
            return false;
        }
        
        // 加入房间
        playerArena.put(player, arenaName);
        
        // 确保房间的准备房间位置已正确设置
        // 优先加载房间特定的准备房间位置
        Location arenaLobby = config.loadArenaLobbyLocation(arenaName);
        if (arenaLobby != null) {
            arena.setLobbyLocation(arenaLobby);
        } else {
            // 如果没有房间特定配置，尝试加载地图的准备房间位置
            String currentMapId = arena.getCurrentMapId();
            if (currentMapId != null) {
                Location mapLobby = config.loadMapLobbyLocation(currentMapId);
                if (mapLobby != null) {
                    arena.setLobbyLocation(mapLobby);
                }
            }
        }
        
        // 通过 GameInstance 加入游戏
        GameInstance instance = arena.getGameInstance();
        if (instance.joinGame(player)) {
            player.sendMessage("§a已加入房间 '§6" + arenaName + "§a'！");
            
            int playerCount = arena.getPlayerCount();
            // 使用当前地图的最少玩家数（如果有），否则使用全局配置
            String currentMapId = arena.getCurrentMapId();
            int minPlayers = currentMapId != null ? config.getMapMinPlayers(currentMapId) : config.getMinPlayers();
            
            // 检查地图投票
            RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
            MapVoteManager voteManager = null;
            if (pluginInstance != null) {
                voteManager = pluginInstance.getMapVoteManager();
            }
            
            if (playerCount < minPlayers) {
                player.sendMessage("§7当前玩家数：§e" + playerCount + "§7/§e" + minPlayers);
                player.sendMessage("§7还差 §e" + (minPlayers - playerCount) + " §7人即可开始投票！");
                
                // 取消可能存在的自动启动任务（玩家数不足时）
                cancelAutoStart(arenaName);
            } else {
                // 人数足够，检查是否需要开始投票
                if (voteManager != null && !voteManager.isVoting(arenaName)) {
                    // 达到最少玩家数，开始投票
                    if (voteManager.startVote(arenaName, playerCount, minPlayers)) {
                        sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 玩家数已足够，地图投票开始！");
                    }
                } else if (voteManager != null && voteManager.isVoting(arenaName)) {
                    // 正在投票，延长投票时间
                    voteManager.extendVoteTime(arenaName, 5); // 延长5秒
                }
                
                // 人数足够，自动开始倒计时（带延迟）
                if (!arena.isPreparing() && !arena.isRunning()) {
                    // 如果已有延迟任务在运行，重置它（重新计时）
                    if (autoStartTasks.containsKey(arenaName)) {
                        cancelAutoStart(arenaName);
                        sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 有新玩家加入！延迟计时已重置...");
                    }
                    scheduleAutoStart(arena);
                }
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * 从玩家房间映射中移除玩家（不发送消息，用于清理）
     * @param player 玩家
     */
    public void removePlayerFromArena(Player player) {
        playerArena.remove(player);
    }
    
    /**
     * 玩家离开房间
     * @param player 玩家
     * @return 是否离开成功
     */
    public boolean leaveArena(Player player) {
        String arenaName = playerArena.remove(player);
        if (arenaName == null) {
            return false; // 不在任何房间
        }
        
        GameArena arena = arenas.get(arenaName);
        if (arena != null) {
            GameInstance instance = arena.getGameInstance();
            
            // 尝试通过 GameInstance 离开
            boolean leftFromGame = instance.leaveGame(player);
            
            // 如果游戏运行中，使用强制离开
            if (!leftFromGame && instance.isRunning()) {
                leftFromGame = instance.forceLeaveGame(player);
                if (leftFromGame) {
                    player.sendMessage("§a已离开房间 '" + arenaName + "'（游戏运行中）");
                    return true;
                }
            }
            
            // 如果离开成功（非游戏运行中），leaveGame 内部已经发送了消息
            if (leftFromGame) {
                return true;
            }
            
            // 如果玩家不在参与者列表中（可能已经不在游戏中），但仍然在房间映射中
            // 确保传送回原位置
            Location originalLoc = instance.getPlayerOriginalLocation(player);
            if (originalLoc != null) {
                player.teleportAsync(originalLoc).thenRun(() -> {
                    player.sendMessage("§a已传送回原位置！");
                });
            } else if (arena.getWorld() != null) {
                // 没有原位置，传送到世界出生点
                Location spawnLoc = arena.getWorld().getSpawnLocation();
                player.teleportAsync(spawnLoc).thenRun(() -> {
                    player.sendMessage("§a已传送到世界出生点！");
                });
            }
        }
        
        player.sendMessage("§a已离开房间 '" + arenaName + "'");
        return true;
    }
    
    /**
     * 获取玩家所在房间
     * @param player 玩家
     * @return 房间名，如果不在房间则返回 null
     */
    public String getPlayerArena(Player player) {
        return playerArena.get(player);
    }
    
    /**
     * 检查玩家是否在某个房间中
     * @param player 玩家
     * @return 是否在房间中
     */
    public boolean isPlayerInArena(Player player) {
        return playerArena.containsKey(player);
    }
    
    /**
     * 获取房间
     * @param arenaName 房间名
     * @return 房间，如果不存在则返回 null
     */
    public GameArena getArena(String arenaName) {
        return arenas.get(arenaName);
    }
    
    /**
     * 获取所有房间
     * @return 房间名列表
     */
    public Set<String> getArenaNames() {
        return new HashSet<>(arenas.keySet());
    }
    
    /**
     * 获取所有房间
     * @return 房间列表
     */
    public Collection<GameArena> getArenas() {
        return new ArrayList<>(arenas.values());
    }
    
    /**
     * 获取所有房间（别名方法，用于 GameEventListener）
     */
    public Collection<GameArena> getAllArenas() {
        return getArenas();
    }
    
    /**
     * 获取配置管理器（用于 GameEventListener）
     */
    public ConfigManager getConfig() {
        return config;
    }
    
    /**
     * 计划自动启动（带延迟）
     */
    private void scheduleAutoStart(GameArena arena) {
        String arenaName = arena.getArenaName();
        
        // 如果已经有任务在运行，不重复启动
        if (autoStartTasks.containsKey(arenaName)) {
            return;
        }
        
        int delay = config.getAutoStartDelay();
        
        // 如果延迟为0，立即开始
        if (delay <= 0) {
            startCountdown(arena);
            return;
        }
        
        // 广播等待消息
        sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 玩家数已足够！将在 §6" + delay + "§e 秒后开始倒计时...");
        sendMessageToArena(arenaName, "§7使用 /ripvp join " + arenaName + " 快速加入");
        
        // 延迟后启动倒计时
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            autoStartTasks.remove(arenaName);
            
            // 再次检查玩家数和状态
            GameInstance instance = arena.getGameInstance();
            int playerCount = instance.getParticipantCount();
            int minPlayers = config.getMinPlayers();
            
            if (playerCount >= minPlayers && !arena.isPreparing() && !arena.isRunning()) {
                startCountdown(arena);
            } else {
                sendMessageToArena(arenaName, "§c[房间 " + arenaName + "] 自动启动已取消（玩家数不足或游戏状态已改变）");
            }
        }, delay * 20L); // 延迟秒数转换为 ticks
        
        autoStartTasks.put(arenaName, task);
    }
    
    /**
     * 取消自动启动任务
     */
    private void cancelAutoStart(String arenaName) {
        ScheduledTask task = autoStartTasks.remove(arenaName);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * 开始倒计时
     */
    private void startCountdown(GameArena arena) {
        if (arena.isPreparing() || arena.isRunning()) {
            return;
        }
        
        // 取消可能存在的自动启动任务
        cancelAutoStart(arena.getArenaName());
        
        // 检查是否有地图投票正在进行，如果有则等待投票结束
        RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
        if (pluginInstance != null) {
            MapVoteManager voteManager = pluginInstance.getMapVoteManager();
            if (voteManager != null && voteManager.isVoting(arena.getArenaName())) {
                // 投票正在进行，延迟启动倒计时（等待投票结束）
                String arenaName = arena.getArenaName();
                // 使用当前地图的投票时长（如果有），否则使用全局配置
                String currentMapId = arena.getCurrentMapId();
                int voteDuration = currentMapId != null ? config.getMapVoteDuration(currentMapId) : config.getVoteDuration();
                
                // 等待投票结束后直接开始游戏，不需要倒计时
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    // 投票结束后检查选中地图并更新出生点
                String selectedMapId = voteManager.getSelectedMap(arenaName);
                if (selectedMapId != null) {
                    Location mapSpawn = config.loadMapSpawnLocation(selectedMapId);
                    if (mapSpawn != null) {
                        // 检查地图是否已被其他房间锁定
                        if (lockedMaps.containsKey(selectedMapId) && !lockedMaps.get(selectedMapId).equals(arenaName)) {
                            String lockingArena = lockedMaps.get(selectedMapId);
                            sendMessageToArena(arenaName, "§c[房间 " + arenaName + "] 地图 " + config.getMapName(selectedMapId) + " 已被房间 " + lockingArena + " 锁定，无法使用");
                            // 随机选择其他地图
                            selectRandomMap(arena);
                        } else {
                            // 设置地图并锁定
                            setupMap(arena, selectedMapId, mapSpawn);
                            // 优先加载房间特定的准备房间位置
                            Location arenaLobby = config.loadArenaLobbyLocation(arenaName);
                            if (arenaLobby != null) {
                                arena.setLobbyLocation(arenaLobby);
                            } else {
                                // 如果没有房间特定配置，加载地图的准备房间位置
                                Location mapLobby = config.loadMapLobbyLocation(selectedMapId);
                                if (mapLobby != null) {
                                    arena.setLobbyLocation(mapLobby);
                                }
                            }
                            String mapName = config.getMapName(selectedMapId);
                            // 使用更明显的方式显示地图选择结果
                            sendMapSelectedMessage(arenaName, mapName);
                        }
                    } else {
                        // 地图加载失败，使用默认出生点或随机选择
                        selectRandomMap(arena);
                    }
                } else {
                    // 投票未完成或没有选中地图，使用默认出生点或随机选择
                    selectRandomMap(arena);
                }
                    
                    // 直接开始游戏，不需要倒计时
                    arena.setStatus(GameArena.ArenaStatus.PREPARING);
                    
                    GameInstance instance = arena.getGameInstance();
                    Set<Player> participantsSet = instance.getParticipants();
                    
                    // 使用当前地图的最少玩家数（如果有），否则使用全局配置
                    String arenaCurrentMapId = arena.getCurrentMapId();
                    int minPlayers = arenaCurrentMapId != null ? config.getMapMinPlayers(arenaCurrentMapId) : config.getMinPlayers();
                    if (participantsSet.size() >= minPlayers) {
                        // 确保有出生点
                        if (arena.getSpawnLocation() != null) {
                            List<Player> participants = new ArrayList<>(participantsSet);
                            // 直接开始游戏，不使用倒计时
                            instance.startRound();
                            
                            // 广播消息
                            sendMessageToArena(arena.getArenaName(), "§a房间 '§6" + arena.getArenaName() + "§a' 游戏开始！");
                            sendMessageToArena(arena.getArenaName(), "§7地图：§e" + config.getMapName(arenaCurrentMapId != null ? arenaCurrentMapId : "未知"));
                        } else {
                            sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 错误：未设置游戏出生点！无法开始游戏。");
                        }
                    }
                }, (voteDuration + 1) * 20L); // 等待投票时间结束 + 1秒
                return;
            } else {
                // 投票已结束或未开始，检查选中地图
                String selectedMapId = voteManager != null ? voteManager.getSelectedMap(arena.getArenaName()) : null;
                if (selectedMapId != null) {
                    Location mapSpawn = config.loadMapSpawnLocation(selectedMapId);
                    if (mapSpawn != null) {
                        // 检查地图是否已被其他房间锁定
                        if (lockedMaps.containsKey(selectedMapId) && !lockedMaps.get(selectedMapId).equals(arena.getArenaName())) {
                            String lockingArena = lockedMaps.get(selectedMapId);
                            sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 地图 " + config.getMapName(selectedMapId) + " 已被房间 " + lockingArena + " 锁定，无法使用");
                            // 随机选择其他地图
                            selectRandomMap(arena);
                        } else {
                            // 设置地图并锁定
                            setupMap(arena, selectedMapId, mapSpawn);
                            // 优先加载房间特定的准备房间位置
                            Location arenaLobby = config.loadArenaLobbyLocation(arena.getArenaName());
                            if (arenaLobby != null) {
                                arena.setLobbyLocation(arenaLobby);
                            } else {
                                // 如果没有房间特定配置，加载地图的准备房间位置
                                Location mapLobby = config.loadMapLobbyLocation(selectedMapId);
                                if (mapLobby != null) {
                                    arena.setLobbyLocation(mapLobby);
                                }
                            }
                            String mapName = config.getMapName(selectedMapId);
                            // 使用更明显的方式显示地图选择结果
                            sendMapSelectedMessage(arena.getArenaName(), mapName);
                        }
                    } else {
                        // 地图加载失败，使用随机选择
                        selectRandomMap(arena);
                    }
                } else if (arena.getSpawnLocation() == null) {
                    // 没有选中地图且没有出生点，随机选择
                    selectRandomMap(arena);
                }
            }
        }
        
        arena.setStatus(GameArena.ArenaStatus.PREPARING);
        
        GameInstance instance = arena.getGameInstance();
        Set<Player> participantsSet = instance.getParticipants();
        
        // 使用当前地图的最少玩家数（如果有），否则使用全局配置
        String currentMapId = arena.getCurrentMapId();
        int minPlayers = currentMapId != null ? config.getMapMinPlayers(currentMapId) : config.getMinPlayers();
        if (participantsSet.size() >= minPlayers) {
            // 确保有出生点
            if (arena.getSpawnLocation() != null) {
                List<Player> participants = new ArrayList<>(participantsSet);
                // 直接开始游戏，不使用倒计时
                instance.startRound();
                
                // 广播消息
                sendMessageToArena(arena.getArenaName(), "§a房间 '§6" + arena.getArenaName() + "§a' 游戏开始！");
                sendMessageToArena(arena.getArenaName(), "§7地图：§e" + config.getMapName(currentMapId != null ? currentMapId : "未知"));
            } else {
                            // 尝试使用配置的默认地图
                            String defaultMapId = config.getDefaultMapId();
                            if (defaultMapId == null || !config.mapExists(defaultMapId)) {
                                // 如果默认地图未配置或不存在，使用第一个可用地图
                                List<String> availableMaps = config.getAvailableMaps();
                                if (!availableMaps.isEmpty()) {
                                    defaultMapId = availableMaps.get(0);
                                } else {
                                    sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 错误：没有可用地图！无法开始游戏。");
                                    return;
                                }
                            }
                            Location mapSpawn = config.loadMapSpawnLocation(defaultMapId);
                            if (mapSpawn != null) {
                                // 设置地图并锁定
                                setupMap(arena, defaultMapId, mapSpawn);
                                List<Player> participants = new ArrayList<>(participantsSet);
                                // 直接开始游戏，不使用倒计时
                                instance.startRound();
                                
                                // 广播消息
                                sendMessageToArena(arena.getArenaName(), "§a房间 '§6" + arena.getArenaName() + "§a' 游戏开始！");
                                sendMessageToArena(arena.getArenaName(), "§7地图：§e" + config.getMapName(defaultMapId));
                            } else {
                                sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 错误：无法加载默认地图！无法开始游戏。");
                                return;
                            }
                        }
        }
    }
    
    /**
     * 设置地图（移除世界实例化，添加地图锁定）
     * @param arena 房间
     * @param mapId 地图ID
     * @param templateSpawn 模板出生点
     */
    private void setupMap(GameArena arena, String mapId, Location templateSpawn) {
        // 检查地图是否已被其他房间锁定
        if (lockedMaps.containsKey(mapId)) {
            String lockingArena = lockedMaps.get(mapId);
            plugin.getLogger().warning("[房间 " + arena.getArenaName() + "] 地图 " + mapId + " 已被房间 " + lockingArena + " 锁定，无法使用");
            // 向房间内的玩家发送消息
            sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 地图 " + config.getMapName(mapId) + " 已被房间 " + lockingArena + " 锁定，无法使用");
            return;
        }
        
        // 锁定地图
        lockedMaps.put(mapId, arena.getArenaName());
        plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 已锁定地图: " + mapId);
        
        // 直接使用模板世界（移除世界实例化）
        arena.setSpawnLocation(templateSpawn);
        arena.setWorld(templateSpawn.getWorld());
        arena.setInstanceWorldKey(null);
        arena.setCurrentMapId(mapId);
        
        plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 使用地图: " + mapId);
        plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 使用地图重置系统，游戏结束后将自动恢复地图");
    }
    
    /**
     * 重新选择地图（在准备阶段可以调用）
     * @param arenaName 房间名
     * @param mapId 地图ID，如果为null则随机选择
     * @return 是否成功
     */
    public boolean reselectMap(String arenaName, String mapId) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) {
            return false; // 房间不存在
        }
        
        // 只能在准备阶段或等待阶段重新选择地图
        if (arena.isRunning()) {
            return false; // 游戏进行中不能重新选择
        }
        
        // 释放当前地图的锁定
        String currentMapId = arena.getCurrentMapId();
        if (currentMapId != null && lockedMaps.containsKey(currentMapId) && lockedMaps.get(currentMapId).equals(arenaName)) {
            lockedMaps.remove(currentMapId);
            plugin.getLogger().info("[房间 " + arenaName + "] 已释放地图锁定: " + currentMapId);
        }
        
        // 如果提供了地图ID，使用该地图；否则随机选择
        if (mapId != null && config.mapExists(mapId)) {
            Location mapSpawn = config.loadMapSpawnLocation(mapId);
            if (mapSpawn != null) {
                // 检查地图是否已被其他房间锁定
                if (lockedMaps.containsKey(mapId)) {
                    String lockingArena = lockedMaps.get(mapId);
                    sendMessageToArena(arenaName, "§c[房间 " + arenaName + "] 地图 " + config.getMapName(mapId) + " 已被房间 " + lockingArena + " 锁定，无法使用");
                    return false;
                }
                
                // 设置地图并锁定
                setupMap(arena, mapId, mapSpawn);
                // 优先加载房间特定的准备房间位置
                Location arenaLobby = config.loadArenaLobbyLocation(arenaName);
                if (arenaLobby != null) {
                    arena.setLobbyLocation(arenaLobby);
                } else {
                    // 如果没有房间特定配置，加载地图的准备房间位置
                    Location mapLobby = config.loadMapLobbyLocation(mapId);
                    if (mapLobby != null) {
                        arena.setLobbyLocation(mapLobby);
                    }
                }
                String mapName = config.getMapName(mapId);
                sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 已重新选择地图：§e" + mapName);
                return true;
            }
        } else {
            // 随机选择地图
            selectRandomMap(arena);
            return true;
        }
        
        return false;
    }
    
    /**
     * 随机选择地图（当投票未选中或地图加载失败时）
     * @param arena 房间
     */
    private void selectRandomMap(GameArena arena) {
        List<String> availableMaps = config.getAvailableMaps();
        if (!availableMaps.isEmpty()) {
            // 过滤掉已被锁定的地图
            List<String> unlockedMaps = new ArrayList<>();
            for (String mapId : availableMaps) {
                if (!lockedMaps.containsKey(mapId)) {
                    unlockedMaps.add(mapId);
                }
            }
            
            // 如果所有地图都被锁定，使用配置的默认地图
            if (unlockedMaps.isEmpty()) {
                sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 所有地图都已被其他房间锁定，使用默认地图");
                // 使用配置的默认地图（如果有）
                String defaultMapId = config.getDefaultMapId();
                if (defaultMapId == null || !config.mapExists(defaultMapId)) {
                    // 如果默认地图未配置或不存在，使用第一个可用地图
                    if (!availableMaps.isEmpty()) {
                        defaultMapId = availableMaps.get(0);
                    } else {
                        return;
                    }
                }
                Location defaultMapSpawn = config.loadMapSpawnLocation(defaultMapId);
                if (defaultMapSpawn != null) {
                    // 设置地图并锁定
                    setupMap(arena, defaultMapId, defaultMapSpawn);
                    // 优先加载房间特定的准备房间位置
                    Location arenaLobby = config.loadArenaLobbyLocation(arena.getArenaName());
                    if (arenaLobby != null) {
                        arena.setLobbyLocation(arenaLobby);
                    } else {
                        // 如果没有房间特定配置，加载地图的准备房间位置
                        Location mapLobby = config.loadMapLobbyLocation(defaultMapId);
                        if (mapLobby != null) {
                            arena.setLobbyLocation(mapLobby);
                        }
                    }
                    String mapName = config.getMapName(defaultMapId);
                    // 使用更明显的方式显示地图选择结果
                    sendMapSelectedMessage(arena.getArenaName(), mapName);
                }
                return;
            }
            
            // 从解锁的地图中随机选择
            String randomMapId = unlockedMaps.get(new java.util.Random().nextInt(unlockedMaps.size()));
            Location mapSpawn = config.loadMapSpawnLocation(randomMapId);
            if (mapSpawn != null) {
                // 设置地图并锁定
                setupMap(arena, randomMapId, mapSpawn);
                // 优先加载房间特定的准备房间位置
                Location arenaLobby = config.loadArenaLobbyLocation(arena.getArenaName());
                if (arenaLobby != null) {
                    arena.setLobbyLocation(arenaLobby);
                } else {
                    // 如果没有房间特定配置，加载地图的准备房间位置
                    Location mapLobby = config.loadMapLobbyLocation(randomMapId);
                    if (mapLobby != null) {
                        arena.setLobbyLocation(mapLobby);
                    }
                }
                String mapName = config.getMapName(randomMapId);
                // 使用更明显的方式显示地图选择结果
                sendMapSelectedMessage(arena.getArenaName(), mapName);
            } else {
                // 随机地图加载失败，使用配置的默认地图
                sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 地图加载失败，使用默认地图");
                // 使用配置的默认地图（如果有）
                String defaultMapId = config.getDefaultMapId();
                if (defaultMapId == null || !config.mapExists(defaultMapId)) {
                    // 如果默认地图未配置或不存在，使用第一个可用地图
                    if (!availableMaps.isEmpty()) {
                        defaultMapId = availableMaps.get(0);
                    } else {
                        return;
                    }
                }
                Location defaultMapSpawn = config.loadMapSpawnLocation(defaultMapId);
                if (defaultMapSpawn != null) {
                    // 设置地图并锁定
                    setupMap(arena, defaultMapId, defaultMapSpawn);
                    // 优先加载房间特定的准备房间位置
                    Location arenaLobby = config.loadArenaLobbyLocation(arena.getArenaName());
                    if (arenaLobby != null) {
                        arena.setLobbyLocation(arenaLobby);
                    } else {
                        // 如果没有房间特定配置，加载地图的准备房间位置
                        Location mapLobby = config.loadMapLobbyLocation(defaultMapId);
                        if (mapLobby != null) {
                            arena.setLobbyLocation(mapLobby);
                        }
                    }
                    String mapName = config.getMapName(defaultMapId);
                    // 使用更明显的方式显示地图选择结果
                    sendMapSelectedMessage(arena.getArenaName(), mapName);
                }
            }
        } else {
            // 没有可用地图，使用配置的默认地图
            sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 没有可用地图，使用默认地图");
            // 使用配置的默认地图（如果有）
            String defaultMapId = config.getDefaultMapId();
            if (defaultMapId != null && config.mapExists(defaultMapId)) {
                Location defaultMapSpawn = config.loadMapSpawnLocation(defaultMapId);
                if (defaultMapSpawn != null) {
                    // 设置地图并锁定
                    setupMap(arena, defaultMapId, defaultMapSpawn);
                    // 优先加载房间特定的准备房间位置
                    Location arenaLobby = config.loadArenaLobbyLocation(arena.getArenaName());
                    if (arenaLobby != null) {
                        arena.setLobbyLocation(arenaLobby);
                    } else {
                        // 如果没有房间特定配置，加载地图的准备房间位置
                        Location mapLobby = config.loadMapLobbyLocation(defaultMapId);
                        if (mapLobby != null) {
                            arena.setLobbyLocation(mapLobby);
                        }
                    }
                    String mapName = config.getMapName(defaultMapId);
                    // 使用更明显的方式显示地图选择结果
                    sendMapSelectedMessage(arena.getArenaName(), mapName);
                }
            }
        }
    }
    
    /**
     * 保存房间配置
     */
    private void saveArenaConfig(GameArena arena) {
        // TODO: 实现配置保存
        // 保存到 config.yml 的 arenas 部分
    }
    
    /**
     * 删除房间配置
     */
    private void removeArenaConfig(String arenaName) {
        // TODO: 实现配置删除
    }
    
    /**
     * 从配置文件加载所有房间
     */
    public void loadArenas() {
        // 清空现有房间列表
        arenas.clear();
        
        // 加载固定房间配置
        List<String> fixedArenas = config.getFixedArenas();
        for (String arenaName : fixedArenas) {
            String defaultMap = config.getFixedArenaDefaultMap(arenaName);
            
            if (defaultMap != null && config.mapExists(defaultMap)) {
                Location mapSpawn = config.loadMapSpawnLocation(defaultMap);
                if (mapSpawn != null) {
                    // 创建固定房间（有默认地图）
                    GameArena arena = new GameArena(arenaName, mapSpawn, plugin, config, statsManager);
                    arena.setCurrentMapId(defaultMap);
                    
                    // 优先加载房间特定的准备房间位置
                    Location arenaLobby = config.loadArenaLobbyLocation(arenaName);
                    if (arenaLobby != null) {
                        arena.setLobbyLocation(arenaLobby);
                    } else {
                        // 如果没有房间特定配置，加载地图的准备房间位置
                        Location mapLobby = config.loadMapLobbyLocation(defaultMap);
                        if (mapLobby != null) {
                            arena.setLobbyLocation(mapLobby);
                        }
                    }
                    
                    arenas.put(arenaName, arena);
                    plugin.getLogger().info("已从配置文件加载固定房间: " + arenaName + " (默认地图: " + defaultMap + ")");
                } else {
                    plugin.getLogger().warning("无法加载固定房间 " + arenaName + " 的地图出生点");
                }
            } else {
                // 没有默认地图或地图无效，创建没有默认地图的房间
                // 不使用默认出生点作为临时位置，而是使用 null
                GameArena arena = new GameArena(arenaName, null, plugin, config, statsManager);
                arena.setCurrentMapId(null); // 未设置默认地图，使用投票系统
                
                // 加载房间特定的准备房间位置
                Location arenaLobby = config.loadArenaLobbyLocation(arenaName);
                if (arenaLobby != null) {
                    arena.setLobbyLocation(arenaLobby);
                }
                
                arenas.put(arenaName, arena);
                plugin.getLogger().info("已从配置文件加载固定房间: " + arenaName + " (使用投票系统选择地图)");
            }
        }
    }
    
    /**
     * 保存所有房间到配置文件
     */
    public void saveAllArenas() {
        // TODO: 保存所有房间到配置文件
    }
}

