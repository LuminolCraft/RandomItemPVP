package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RipvpCommand implements CommandExecutor, TabCompleter {
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private final PlayerStatsManager statsManager;
    private static final String DEFAULT_ARENA = "default"; // 默认房间名

    public RipvpCommand(ArenaManager arenaManager, ConfigManager configManager, PlayerStatsManager statsManager) {
        this.arenaManager = arenaManager;
        this.configManager = configManager;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 无参数 -> 帮助
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // 控制台可执行的命令：status, create, delete, list, reload
        boolean consoleAllowed = args.length >= 1 && (
            args[0].equalsIgnoreCase("status") ||
            args[0].equalsIgnoreCase("create") ||
            args[0].equalsIgnoreCase("delete") ||
            args[0].equalsIgnoreCase("list") ||
            args[0].equalsIgnoreCase("arenas") ||
            args[0].equalsIgnoreCase("reload")
        );
        
        // 仅玩家可执行（除了控制台允许的命令）
        if (!(sender instanceof Player) && !consoleAllowed) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以执行此命令！");
            return true;
        }

        Player player = (sender instanceof Player) ? (Player) sender : null;

        // 处理子命令
        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "start": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 如果提供了房间名，使用多房间系统
                    if (args.length >= 2) {
                        String startArenaName = args[1];
                        GameArena startArena = arenaManager.getArena(startArenaName);
                        
                        // 如果房间不存在，显示错误消息
                        if (startArena == null) {
                            player.sendMessage(ChatColor.RED + "房间 '" + startArenaName + "' 不存在！");
                            player.sendMessage(ChatColor.YELLOW + "使用 /ripvp create <房间名> 创建房间");
                            player.sendMessage(ChatColor.GRAY + "使用 /ripvp list 查看所有可用房间");
                            return true;
                        }
                        
                        // 检查房间状态
                        if (startArena.isRunning()) {
                            player.sendMessage(ChatColor.RED + "房间 '§6" + startArenaName + "§c' 正在进行游戏！");
                            return true;
                        }
                        
                        if (startArena.isPreparing()) {
                            player.sendMessage(ChatColor.RED + "房间 '§6" + startArenaName + "§c' 正在准备中！");
                            return true;
                        }
                        
                        // 确保玩家在房间中
                        if (!arenaManager.isPlayerInArena(player) || !startArenaName.equals(arenaManager.getPlayerArena(player))) {
                            if (!arenaManager.joinArena(player, startArenaName)) {
                                player.sendMessage(ChatColor.RED + "加入房间失败！");
                                return true;
                            }
                        }
                        
                        // 启动游戏倒计时
                        GameInstance startInstance = startArena.getGameInstance();
                        Set<Player> startParticipantsSet = startInstance.getParticipants();
                        
                        if (startParticipantsSet.size() >= configManager.getMinPlayers()) {
                            List<Player> startParticipants = new ArrayList<>(startParticipantsSet);
                            startInstance.startGameWithCountdown(startParticipants);
                            player.sendMessage(ChatColor.GREEN + "房间 '§6" + startArenaName + "§a' 游戏倒计时已开始！");
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "玩家数不足！当前：§e" + startParticipantsSet.size() + 
                                "§7/§e" + configManager.getMinPlayers() + 
                                "§7，需要至少 §e" + configManager.getMinPlayers() + " §7人才能开始！");
                            player.sendMessage(ChatColor.GRAY + "其他玩家可以使用 /ripvp join " + startArenaName + " 加入");
                        }
                        return true;
                    }
                    
                    // 没有提供房间名，检查玩家是否在房间中
                    String playerArenaName = arenaManager.getPlayerArena(player);
                    if (playerArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你没有在任何房间中！");
                        player.sendMessage(ChatColor.YELLOW + "使用以下方式开始游戏：");
                        player.sendMessage(ChatColor.WHITE + "  1. /ripvp start <房间名> - 启动指定房间的游戏");
                        player.sendMessage(ChatColor.WHITE + "  2. /ripvp join <房间名> - 加入已有房间，然后等待管理员启动");
                        player.sendMessage(ChatColor.GRAY + "使用 /ripvp list 查看所有可用房间");
                        return true;
                    }
                    
                    // 玩家在房间中，启动该房间的游戏
                    GameArena playerArena = arenaManager.getArena(playerArenaName);
                    if (playerArena == null) {
                        player.sendMessage(ChatColor.RED + "你所在的房间不存在！");
                        return true;
                    }
                    
                    // 检查房间状态
                    if (playerArena.isRunning()) {
                        player.sendMessage(ChatColor.RED + "房间 '§6" + playerArenaName + "§c' 正在进行游戏！");
                        return true;
                    }
                    
                    if (playerArena.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "房间 '§6" + playerArenaName + "§c' 正在准备中！");
                        return true;
                    }
                    
                    // 检查权限（只有管理员可以启动游戏，或房间内玩家数达到最少玩家数时任何人都可以启动）
                    Set<Player> participantsSet = playerArena.getGameInstance().getParticipants();
                    boolean canStart = player.hasPermission("ripvp.admin") || 
                                      participantsSet.size() >= configManager.getMinPlayers();
                    
                    if (!canStart) {
                        player.sendMessage(ChatColor.RED + "你没有权限启动游戏！");
                        player.sendMessage(ChatColor.YELLOW + "需要至少 §e" + configManager.getMinPlayers() + 
                            " §7人才能开始，或者需要管理员权限");
                        return true;
                    }
                    
                    // 启动游戏倒计时
                    GameInstance arenaInstance = playerArena.getGameInstance();
                    if (participantsSet.size() >= configManager.getMinPlayers()) {
                        List<Player> participants = new ArrayList<>(participantsSet);
                        arenaInstance.startGameWithCountdown(participants);
                        player.sendMessage(ChatColor.GREEN + "房间 '§6" + playerArenaName + "§a' 游戏倒计时已开始！");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "玩家数不足！当前：§e" + participantsSet.size() + 
                            "§7/§e" + configManager.getMinPlayers() + 
                            "§7，需要至少 §e" + configManager.getMinPlayers() + " §7人才能开始！");
                        player.sendMessage(ChatColor.GRAY + "其他玩家可以使用 /ripvp join " + playerArenaName + " 加入");
                    }
                    return true;
                }

                case "stop": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    // 如果玩家在房间中，停止该房间；否则停止所有运行中的房间
                    String stopArenaName = arenaManager.getPlayerArena(player);
                    if (stopArenaName != null) {
                        GameArena stopArena = arenaManager.getArena(stopArenaName);
                        if (stopArena != null && stopArena.isRunning()) {
                            GameInstance stopInstance = stopArena.getGameInstance();
                            stopInstance.forceStop();
                            player.sendMessage(ChatColor.RED + "房间 '" + stopArenaName + "' 的游戏已停止！");
                        } else {
                            player.sendMessage(ChatColor.RED + "你所在的房间没有正在运行的游戏！");
                        }
                    } else {
                        // 停止所有运行中的房间
                        boolean found = false;
                        for (String name : arenaManager.getArenaNames()) {
                            GameArena stopArena = arenaManager.getArena(name);
                            if (stopArena != null && stopArena.isRunning()) {
                                stopArena.getGameInstance().forceStop();
                                found = true;
                            }
                        }
                        if (found) {
                            player.sendMessage(ChatColor.RED + "所有运行中的游戏已停止！");
                        } else {
                            player.sendMessage(ChatColor.RED + "没有正在运行的游戏！");
                        }
                    }
                    return true;
                }

                case "status": {
                    // 如果玩家在房间中，显示该房间状态；否则显示所有房间状态
                    if (player != null) {
                        String statusArenaName = arenaManager.getPlayerArena(player);
                        if (statusArenaName != null) {
                            GameArena statusArena = arenaManager.getArena(statusArenaName);
                            if (statusArena != null) {
                                statusArena.syncStatus();
                                sender.sendMessage(ChatColor.AQUA + "===== 房间状态 =====");
                                sender.sendMessage(ChatColor.WHITE + "房间名：§e" + statusArenaName);
                                sender.sendMessage(ChatColor.WHITE + "状态：" + getArenaStatusText(statusArena));
                                sender.sendMessage(ChatColor.WHITE + "玩家数：§e" + statusArena.getPlayerCount());
                                if (statusArena.isRunning()) {
                                    GameInstance statusInstance = statusArena.getGameInstance();
                                    sender.sendMessage(ChatColor.WHITE + "存活玩家：§e" + statusInstance.getSurvivingPlayers().size());
                                }
                                sender.sendMessage(ChatColor.AQUA + "===================");
                                return true;
                            }
                        }
                    }
                    // 显示所有房间状态
                    Set<String> statusArenaNames = arenaManager.getArenaNames();
            if (statusArenaNames.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "当前没有房间！");
                sender.sendMessage(ChatColor.YELLOW + "请在 config-modules/arenas.yml 中配置固定房间");
                return true;
            }
                    sender.sendMessage(ChatColor.AQUA + "===== 所有房间状态 =====");
                    for (String name : statusArenaNames) {
                        GameArena statusArena = arenaManager.getArena(name);
                        if (statusArena != null) {
                            statusArena.syncStatus();
                            sender.sendMessage(ChatColor.WHITE + "  " + name + " - " + getArenaStatusText(statusArena) + 
                                " §7(" + statusArena.getPlayerCount() + "人)");
                        }
                    }
                    sender.sendMessage(ChatColor.AQUA + "===================");
                    return true;
                }

                case "reload": {
                    if (!sender.hasPermission("ripvp.admin")) {
                        sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 检查是否有游戏在进行
                    boolean hasRunningGame = false;
                    for (String name : arenaManager.getArenaNames()) {
                        GameArena reloadArena = arenaManager.getArena(name);
                        if (reloadArena != null && (reloadArena.isRunning() || reloadArena.isPreparing())) {
                            hasRunningGame = true;
                            break;
                        }
                    }
                    
                    if (hasRunningGame) {
                        sender.sendMessage(ChatColor.RED + "有游戏正在进行或准备中，无法热加载配置！");
                        sender.sendMessage(ChatColor.YELLOW + "请先使用 /ripvp stop 停止所有游戏。");
                        return true;
                    }
                    
                    // 热加载配置
                    List<String> reloadedFiles = configManager.reloadConfig();
                    
                     // 重新加载房间配置
                    arenaManager.loadArenas();
                    List<String> fixedArenas = configManager.getFixedArenas();
                    
                    // 显示重载信息
                    sender.sendMessage(ChatColor.GREEN + "✓ 配置文件已热加载！");
                    sender.sendMessage(ChatColor.AQUA + "当前配置：");
                    sender.sendMessage(ChatColor.WHITE + "  - 竞技场半径: " + ChatColor.YELLOW + configManager.getArenaRadius());
                    sender.sendMessage(ChatColor.WHITE + "  - 最少玩家数: " + ChatColor.YELLOW + configManager.getMinPlayers());
                    sender.sendMessage(ChatColor.WHITE + "  - 倒计时时长: " + ChatColor.YELLOW + configManager.getStartCountdown() + "秒");
                    sender.sendMessage(ChatColor.WHITE + "  - 边界伤害: " + ChatColor.YELLOW + configManager.getBorderDamageAmount() + "/秒");
                    sender.sendMessage(ChatColor.WHITE + "  - 物品发放间隔: " + ChatColor.YELLOW + (configManager.getItemInterval() / 20.0) + "秒");
                    sender.sendMessage(ChatColor.WHITE + "  - 物品种类数: " + ChatColor.YELLOW + configManager.getItemWeights().size());
                    if (configManager.hasItemsConfig()) {
                        sender.sendMessage(ChatColor.GRAY + "  - 物品配置来源: items.yml（独立文件）");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + "  - 物品配置来源: config.yml（主配置）");
                    }
                    sender.sendMessage(ChatColor.WHITE + "  - 缩圈间隔: " + ChatColor.YELLOW + (configManager.getShrinkInterval() / 20.0) + "秒");
                    if (!reloadedFiles.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "  - 已读取文件: " + ChatColor.YELLOW + String.join(ChatColor.GRAY + ", " + ChatColor.YELLOW, reloadedFiles));
                    }
                    if (!fixedArenas.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "  - 已加载房间: " + ChatColor.YELLOW + String.join(ChatColor.GRAY + ", " + ChatColor.YELLOW, fixedArenas));
                    }
                    sender.sendMessage(ChatColor.YELLOW + "新配置已生效，可以开始新游戏。");

                    RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
                    if (pluginInstance != null) {
                        pluginInstance.getLogger().info("配置热加载完成 -> " + String.join(", ", reloadedFiles));
                        if (!fixedArenas.isEmpty()) {
                            pluginInstance.getLogger().info("已重新加载 " + fixedArenas.size() + " 个固定房间：" + String.join(", ", fixedArenas));
                        }
                    }
                    return true;
                }
                
                case "join": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 如果提供了房间名，使用房间系统
                    if (args.length >= 2) {
                        String joinArenaName = args[1];
                        if (arenaManager.joinArena(player, joinArenaName)) {
                            // joinArena 内部已经发送了消息
                            return true;
                        } else {
                            player.sendMessage(ChatColor.RED + "无法加入房间 '" + joinArenaName + "'！");
                            return true;
                        }
                    }
                    
                    // 否则自动加入默认房间
                    String defaultArenaName = DEFAULT_ARENA;
                    if (arenaManager.joinArena(player, defaultArenaName)) {
                        // joinArena 内部已经发送了消息
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "无法加入默认房间！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp create <房间名> 创建房间");
                        return true;
                    }
                }
                
                case "leave": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 先尝试从房间系统离开
                    if (arenaManager.leaveArena(player)) {
                        return true; // leaveArena 内部已经发送了消息
                    }
                    
                    // 如果 leaveArena 失败，说明玩家不在任何房间中
                    player.sendMessage(ChatColor.RED + "你没有在任何房间中！");
                    return true;
                }
                
                case "cancel": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    // 如果玩家在房间中，取消该房间的游戏
                    String cancelArenaName = arenaManager.getPlayerArena(player);
                    if (cancelArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你不在任何房间中！");
                        return true;
                    }
                    GameArena cancelArena = arenaManager.getArena(cancelArenaName);
                    if (cancelArena == null) {
                        player.sendMessage(ChatColor.RED + "房间不存在！");
                        return true;
                    }
                    if (cancelArena.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已经开始，使用 /ripvp stop 停止游戏。");
                        return true;
                    }
                    if (!cancelArena.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "当前没有准备中的游戏！");
                        return true;
                    }
                    cancelArena.getGameInstance().cancelGame();
                    player.sendMessage(ChatColor.GREEN + "房间 '" + cancelArenaName + "' 的游戏已取消！");
                    return true;
                }
                

                
                case "delete": {
                    if (!sender.hasPermission("ripvp.admin")) {
                        sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "用法：/ripvp delete <房间名>");
                        return true;
                    }
                    String deleteName = args[1];
                    if (arenaManager.deleteArena(deleteName)) {
                        sender.sendMessage(ChatColor.GREEN + "✓ 房间 '" + deleteName + "' 已删除！");
                    } else {
                        sender.sendMessage(ChatColor.RED + "房间 '" + deleteName + "' 不存在！");
                    }
                    return true;
                }
                
                case "vote": {
                    if (player == null) return true;
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "用法：/ripvp vote <地图名>");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp vote cancel 取消投票（弃票）");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp list 查看正在投票的房间");
                        return true;
                    }
                    
                    // 检查玩家是否在房间中
                    String voteArenaName = arenaManager.getPlayerArena(player);
                    if (voteArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你不在任何房间中！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp join <房间名> 加入房间");
                        return true;
                    }
                    
                    // 获取投票管理器
                    RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
                    if (pluginInstance == null) {
                        player.sendMessage(ChatColor.RED + "插件未初始化！");
                        return true;
                    }
                    
                    MapVoteManager voteManager = pluginInstance.getMapVoteManager();
                    if (voteManager == null) {
                        player.sendMessage(ChatColor.RED + "投票系统未初始化！");
                        return true;
                    }
                    

                    
                    // 投票（支持弃票）
                    String voteMapId = args[1];
                    
                    // 检查房间是否正在投票
                    if (!voteManager.isVoting(voteArenaName)) {
                        // 投票已结束，重新开始投票
                        RandomItemPVP pluginInstance2 = RandomItemPVP.getInstance();
                        if (pluginInstance2 != null) {
                            GameArena voteArena = arenaManager.getArena(voteArenaName);
                            if (voteArena != null && !voteArena.isRunning() && !voteArena.isPreparing()) {
                                // 重新开始投票
                                int playerCount = voteArena.getPlayerCount();
                                int minPlayers = configManager.getMinPlayers();
                                if (voteManager.startVote(voteArenaName, playerCount, minPlayers)) {
                                    player.sendMessage(ChatColor.GREEN + "房间 '" + voteArenaName + "' 的投票已重新开始！");
                                    // 再次执行投票
                                    if (voteManager.vote(voteArenaName, player, voteMapId)) {
                                        // 投票成功消息已在 voteManager 中发送
                                    } else {
                                        player.sendMessage(ChatColor.RED + "投票失败！请检查地图名是否正确。");
                                    }
                                } else {
                                    player.sendMessage(ChatColor.RED + "无法重新开始投票！");
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "房间状态不允许重新投票！");
                            }
                        }
                        return true;
                    }
                    if (voteManager.vote(voteArenaName, player, voteMapId)) {
                        // 投票成功消息已在 voteManager 中发送
                    } else {
                        player.sendMessage(ChatColor.RED + "投票失败！请检查地图名是否正确。");
                    }
                    return true;
                }
                
                case "list":
                case "arenas": {
                    Set<String> listArenaNames = arenaManager.getArenaNames();
                    if (listArenaNames.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "当前没有可用房间！");
                        sender.sendMessage(ChatColor.YELLOW + "请在 config-modules/arenas.yml 中配置固定房间");
                        return true;
                    }
                    sender.sendMessage(ChatColor.AQUA + "===== 可用房间 =====");
                    for (String name : listArenaNames) {
                        GameArena listArena = arenaManager.getArena(name);
                        if (listArena != null) {
                            // 同步状态，确保显示正确
                            listArena.syncStatus();
                            
                            int playerCount = listArena.getPlayerCount();
                            // 使用当前地图的最少玩家数（如果有），否则使用全局配置
                            String currentMapId = listArena.getCurrentMapId();
                            int minPlayers = currentMapId != null ? configManager.getMapMinPlayers(currentMapId) : configManager.getMinPlayers();
                            String statusText = getArenaStatusText(listArena);
                            
                            // 显示格式：房间名 - 状态 (当前玩家数人，最少需要minPlayers人)
                            if (playerCount < minPlayers) {
                                sender.sendMessage(ChatColor.WHITE + "  " + name + " - " + 
                                    statusText + ChatColor.WHITE + " (" + 
                                    ChatColor.YELLOW + playerCount + 
                                    ChatColor.WHITE + "人，最少需要" + 
                                    ChatColor.YELLOW + minPlayers + 
                                    ChatColor.WHITE + "人)");
                            } else {
                                sender.sendMessage(ChatColor.WHITE + "  " + name + " - " + 
                                    statusText + ChatColor.WHITE + " (" + 
                                    ChatColor.YELLOW + playerCount + 
                                    ChatColor.WHITE + "人)");
                            }
                        }
                    }
                    sender.sendMessage(ChatColor.AQUA + "===================");
                    sender.sendMessage(ChatColor.GRAY + "使用 /ripvp join <房间名> 加入房间");
                    return true;
                }
                
                case "remap": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    // 检查玩家是否在房间中
                    String remapArenaName = arenaManager.getPlayerArena(player);
                    if (remapArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你不在任何房间中！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp join <房间名> 加入房间");
                        return true;
                    }
                    
                    GameArena remapArena = arenaManager.getArena(remapArenaName);
                    if (remapArena == null) {
                        player.sendMessage(ChatColor.RED + "房间不存在！");
                        return true;
                    }
                    
                    // 只能在准备阶段或等待阶段重新选择地图
                    if (remapArena.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏进行中无法重新选择地图！");
                        return true;
                    }
                    
                    // 如果提供了地图ID，使用该地图；否则随机选择
                    if (args.length >= 2) {
                        String remapMapId = args[1];
                        if (configManager.mapExists(remapMapId)) {
                            if (arenaManager.reselectMap(remapArenaName, remapMapId)) {
                                String remapMapName = configManager.getMapName(remapMapId);
                                player.sendMessage(ChatColor.GREEN + "✓ 房间 '" + remapArenaName + "' 已重新选择地图：§e" + remapMapName);
                            } else {
                                player.sendMessage(ChatColor.RED + "重新选择地图失败！");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "地图 '" + remapMapId + "' 不存在！");
                            player.sendMessage(ChatColor.YELLOW + "使用 /ripvp list 查看可用地图");
                        }
                    } else {
                        // 随机选择地图
                        if (arenaManager.reselectMap(remapArenaName, null)) {
                            player.sendMessage(ChatColor.GREEN + "✓ 房间 '" + remapArenaName + "' 已随机选择地图");
                        } else {
                            player.sendMessage(ChatColor.RED + "重新选择地图失败！");
                        }
                    }
                    return true;
                }
                
                case "setspawn": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    // 如果提供了房间名，设置房间出生点
                    if (args.length >= 2) {
                        String spawnArenaName = args[1];
                        GameArena spawnArena = arenaManager.getArena(spawnArenaName);
                        if (spawnArena == null) {
                            player.sendMessage(ChatColor.RED + "房间 '" + spawnArenaName + "' 不存在！");
                            return true;
                        }
                        // TODO: 实现设置房间出生点的功能
                        player.sendMessage(ChatColor.YELLOW + "房间出生点功能待实现");
                        return true;
                    }
                    // 否则设置全局出生点（保存到配置文件）
                    configManager.saveSpawnLocation(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "✓ 全局游戏出生点已设置为当前位置！");
                    player.sendMessage(ChatColor.GREEN + "✓ 已保存到配置文件，重启后不会丢失！");
                    player.sendMessage(ChatColor.YELLOW + "位置：" + 
                        String.format("世界=%s, X=%.1f, Y=%.1f, Z=%.1f", 
                        player.getWorld().getName(),
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ()));
                    player.sendMessage(ChatColor.GRAY + "提示：新创建的房间将使用此出生点");
                    return true;
                }
                
                case "savemap": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    // 手动记录地图
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "请指定地图ID！例如：/ripvp savemap map1");
                        return true;
                    }
                    
                    String mapId = args[1];
                    
                    // 检查地图是否存在
                    if (!configManager.mapExists(mapId)) {
                        player.sendMessage(ChatColor.RED + "地图ID '" + mapId + "' 不存在！请检查maps.yml文件中的配置");
                        return true;
                    }
                    
                    // 加载地图配置
                    org.bukkit.Location center = configManager.loadMapSpawnLocation(mapId);
                    if (center == null) {
                        player.sendMessage(ChatColor.RED + "加载地图坐标失败！请检查maps.yml文件中地图 '" + mapId + "' 的配置");
                        return true;
                    }
                    
                    int radius = configManager.getMapRadius(mapId);
                    String worldName = center.getWorld().getName();
                    
                    // 创建MapResetter实例并保存初始状态
                    org.bukkit.World world = center.getWorld();
                    MapResetter mapResetter = new MapResetter(RandomItemPVP.getInstance(), world, center, radius, mapId);
                    mapResetter.saveInitialMapState(true); // 强制保存，即使文件已存在
                    
                    player.sendMessage(ChatColor.GREEN + "✓ 地图记录完成！");
                    player.sendMessage(ChatColor.YELLOW + "世界：" + worldName);
                    player.sendMessage(ChatColor.YELLOW + "地图ID：" + mapId);
                    player.sendMessage(ChatColor.YELLOW + "地图名称：" + configManager.getMapName(mapId));
                    player.sendMessage(ChatColor.YELLOW + "记录中心：" + 
                        String.format("X=%.1f, Y=%.1f, Z=%.1f", 
                        center.getX(),
                        center.getY(),
                        center.getZ()));
                    player.sendMessage(ChatColor.YELLOW + "记录半径：" + radius + "格");
                    player.sendMessage(ChatColor.GRAY + "提示：地图数据已保存为JSON文件，下次游戏将直接使用此数据");
                    return true;
                }
                
                case "stats": {
                    if (player == null) return true;
                    // 查看自己的统计或指定玩家的统计
                    if (args.length == 1) {
                        // 查看自己的统计
                        showPlayerStats(player, player);
                    } else {
                        // 查看指定玩家的统计
                        Player target = Bukkit.getPlayer(args[1]);
                        if (target == null) {
                            player.sendMessage(ChatColor.RED + "玩家不在线！");
                            return true;
                        }
                        showPlayerStats(player, target);
                    }
                    return true;
                }
                
                case "top": {
                    if (player == null) return true;
                    // 排行榜类型：wins（胜利）、kills（击杀）、kd（KD比率）
                    String rankType = args.length >= 2 ? args[1].toLowerCase() : "wins";
                    showLeaderboard(player, rankType);
                    return true;
                }
                
                case "ready": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 检查玩家是否在房间中
                    String readyArenaName = arenaManager.getPlayerArena(player);
                    if (readyArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你没有在任何房间中！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp join <房间名> 加入房间");
                        return true;
                    }
                    
                    GameArena readyArena = arenaManager.getArena(readyArenaName);
                    if (readyArena == null) {
                        player.sendMessage(ChatColor.RED + "房间不存在！");
                        return true;
                    }
                    
                    GameInstance readyInstance = readyArena.getGameInstance();
                    if (readyInstance.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已经开始，无法准备！");
                        return true;
                    }
                    
                    if (!readyInstance.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "游戏未在准备中，无法准备！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp start <房间名> 启动游戏");
                        return true;
                    }
                    
                    // 执行准备操作
                    if (readyInstance.ready(player)) {
                        // 准备成功消息已在 ready() 方法中发送
                    } else {
                        player.sendMessage(ChatColor.RED + "准备失败！");
                    }
                    return true;
                }
                
                case "unready": {
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 检查玩家是否在房间中
                    String unreadyArenaName = arenaManager.getPlayerArena(player);
                    if (unreadyArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你没有在任何房间中！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp join <房间名> 加入房间");
                        return true;
                    }
                    
                    GameArena unreadyArena = arenaManager.getArena(unreadyArenaName);
                    if (unreadyArena == null) {
                        player.sendMessage(ChatColor.RED + "房间不存在！");
                        return true;
                    }
                    
                    GameInstance unreadyInstance = unreadyArena.getGameInstance();
                    if (unreadyInstance.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已经开始，无法取消准备！");
                        return true;
                    }
                    
                    // 执行取消准备操作
                    if (unreadyInstance.unready(player)) {
                        // 取消准备成功消息已在 unready() 方法中发送
                    } else {
                        player.sendMessage(ChatColor.RED + "取消准备失败！");
                    }
                    return true;
                }

                default:
                    sendHelp(sender);
                    return true;
            }
        }
        return true;
    }

    // 命令补全
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "join", "leave", "cancel", "delete", "list", "setspawn", "status", "reload", "stats", "top", "vote", "remap", "ready", "unready");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "start":
                    // /ripvp start 可以补全房间名
                    List<String> arenaNames = new ArrayList<>(arenaManager.getArenaNames());
                    return arenaNames.isEmpty() ? null : arenaNames;
                case "join":
                case "delete":
                case "setspawn":
                    // 返回房间名列表
                    return new ArrayList<>(arenaManager.getArenaNames());
                case "vote":
                    // 如果玩家在房间中，返回可用地图列表
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        String playerArenaName = arenaManager.getPlayerArena(player);
                        if (playerArenaName != null) {
                            RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
                            if (pluginInstance != null) {
                                MapVoteManager voteManager = pluginInstance.getMapVoteManager();
                                if (voteManager != null && voteManager.isVoting(playerArenaName)) {
                                    return new ArrayList<>(configManager.getAvailableMaps());
                                }
                            }
                        }
                    }
                    return null;
                case "top":
                    return Arrays.asList("wins", "kills", "kd");
                case "remap":
                    // 如果玩家在房间中，返回可用地图列表
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        String playerArenaName = arenaManager.getPlayerArena(player);
                        if (playerArenaName != null) {
                            return new ArrayList<>(configManager.getAvailableMaps());
                        }
                    }
                    return null;
                case "reload":
                case "ready":
                case "unready":
                    // 这些命令不需要参数
                    return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
    
    // 获取房间状态文本
    private String getArenaStatusText(GameArena arena) {
        switch (arena.getStatus()) {
            case WAITING:
                return ChatColor.GREEN + "等待中";
            case PREPARING:
                return ChatColor.YELLOW + "倒计时中";
            case RUNNING:
                return ChatColor.RED + "游戏中";
            case ENDING:
                return ChatColor.GRAY + "结束中";
            default:
                return ChatColor.WHITE + "未知";
        }
    }

    // 发送帮助信息
    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender instanceof Player && sender.hasPermission("ripvp.admin");
        
        sender.sendMessage(ChatColor.YELLOW + "===== /ripvp 命令帮助 =====");
        sender.sendMessage(ChatColor.GREEN + "玩家命令：");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp start [房间名] - 发起游戏或开启房间");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp join [房间名] - 加入游戏或房间");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp leave - 退出游戏或房间");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp list - 查看所有房间");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp vote <地图名> - 投票选择地图（在房间中时）");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp ready - 准备游戏（在准备房间中时）");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp unready - 取消准备（在准备房间中时）");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp status - 查看游戏状态");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp stats [玩家] - 查看统计数据");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp top [wins|kills|kd] - 查看排行榜");
        
        if (isAdmin) {
            sender.sendMessage(ChatColor.RED + "管理员命令：");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp delete <房间名> - 删除房间（控制台可用）");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp remap [地图名] - 重新选择地图（准备阶段）");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp setspawn [房间名] - 设置游戏出生点");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp stop - 强制停止当前游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp cancel - 取消准备中的游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp reload - 热加载配置文件（控制台可用）");
        }
        
        sender.sendMessage(ChatColor.YELLOW + "==========================");
    }
    
    /**
     * 显示玩家统计数据
     */
    private void showPlayerStats(Player viewer, Player target) {
        viewer.sendMessage(ChatColor.AQUA + "正在加载统计数据...");
        
        statsManager.getPlayerStats(target.getUniqueId(), target.getName()).thenAccept(stats -> {
            Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                viewer.sendMessage(ChatColor.GOLD + "========== " + stats.getPlayerName() + " 的统计 ==========");
                viewer.sendMessage(ChatColor.YELLOW + "胜利次数：" + ChatColor.GREEN + stats.getWins());
                viewer.sendMessage(ChatColor.YELLOW + "失败次数：" + ChatColor.RED + stats.getLosses());
                viewer.sendMessage(ChatColor.YELLOW + "总场次：" + ChatColor.WHITE + stats.getGamesPlayed());
                viewer.sendMessage(ChatColor.YELLOW + "胜率：" + ChatColor.AQUA + String.format("%.1f%%", stats.getWinRate()));
                viewer.sendMessage(ChatColor.YELLOW + "击杀数：" + ChatColor.GREEN + stats.getKills());
                viewer.sendMessage(ChatColor.YELLOW + "死亡数：" + ChatColor.RED + stats.getDeaths());
                viewer.sendMessage(ChatColor.YELLOW + "KD比率：" + ChatColor.GOLD + String.format("%.2f", stats.getKDRatio()));
                viewer.sendMessage(ChatColor.GOLD + "=========================================");
            });
        });
    }
    
    /**
     * 显示排行榜
     */
    private void showLeaderboard(Player player, String type) {
        player.sendMessage(ChatColor.AQUA + "正在加载排行榜...");
        
        switch (type) {
            case "wins":
                statsManager.getTopWins(10).thenAccept(topPlayers -> {
                    Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                        player.sendMessage(ChatColor.GOLD + "========== 胜利排行榜 TOP 10 ==========");
                        int rank = 1;
                        for (PlayerStatsManager.PlayerStats stats : topPlayers) {
                            String medal = getMedalForRank(rank);
                            player.sendMessage(ChatColor.YELLOW + "#" + rank + " " + medal + " " + 
                                ChatColor.WHITE + stats.getPlayerName() + " - " + 
                                ChatColor.GREEN + stats.getWins() + " 胜 " + 
                                ChatColor.GRAY + "(" + stats.getGamesPlayed() + " 场)");
                            rank++;
                        }
                        player.sendMessage(ChatColor.GOLD + "=========================================");
                    });
                });
                break;
            
            case "kills":
                statsManager.getTopKills(10).thenAccept(topPlayers -> {
                    Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                        player.sendMessage(ChatColor.GOLD + "========== 击杀排行榜 TOP 10 ==========");
                        int rank = 1;
                        for (PlayerStatsManager.PlayerStats stats : topPlayers) {
                            String medal = getMedalForRank(rank);
                            player.sendMessage(ChatColor.YELLOW + "#" + rank + " " + medal + " " + 
                                ChatColor.WHITE + stats.getPlayerName() + " - " + 
                                ChatColor.RED + stats.getKills() + " 击杀 " + 
                                ChatColor.GRAY + "(KD: " + String.format("%.2f", stats.getKDRatio()) + ")");
                            rank++;
                        }
                        player.sendMessage(ChatColor.GOLD + "=========================================");
                    });
                });
                break;
            
            case "kd":
                statsManager.getTopKD(10).thenAccept(topPlayers -> {
                    Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                        player.sendMessage(ChatColor.GOLD + "========== KD比率排行榜 TOP 10 ==========");
                        player.sendMessage(ChatColor.GRAY + "（需要至少 10 场游戏才能上榜）");
                        int rank = 1;
                        for (PlayerStatsManager.PlayerStats stats : topPlayers) {
                            String medal = getMedalForRank(rank);
                            player.sendMessage(ChatColor.YELLOW + "#" + rank + " " + medal + " " + 
                                ChatColor.WHITE + stats.getPlayerName() + " - " + 
                                ChatColor.GOLD + String.format("%.2f", stats.getKDRatio()) + " KD " + 
                                ChatColor.GRAY + "(" + stats.getKills() + "/" + stats.getDeaths() + ")");
                            rank++;
                        }
                        player.sendMessage(ChatColor.GOLD + "=========================================");
                    });
                });
                break;
            
            default:
                player.sendMessage(ChatColor.RED + "无效的排行榜类型！可用: wins, kills, kd");
                break;
        }
    }
    
    /**
     * 获取排名对应的奖牌
     */
    private String getMedalForRank(int rank) {
        switch (rank) {
            case 1: return "🥇";
            case 2: return "🥈";
            case 3: return "🥉";
            default: return "  ";
        }
    }
}

