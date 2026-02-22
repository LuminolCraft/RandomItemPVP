package org.luminolcraft.randomitempvp;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 日志工具类，用于管理调试信息
 * 控制调试信息的输出，过滤重复的调试消息，提供不同级别的日志方法
 */
public class DebugLogger {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ConcurrentMap<String, Long> messageTimestamps;
    private final Set<String> recentMessages;
    private static final long MESSAGE_EXPIRY_TIME = TimeUnit.SECONDS.toMillis(5); // 消息过期时间（5秒）
    private static final int MAX_RECENT_MESSAGES = 100; // 最大最近消息数

    /**
     * 构造方法
     * @param plugin 插件实例
     * @param config 配置管理器
     */
    public DebugLogger(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.messageTimestamps = new ConcurrentHashMap<>();
        this.recentMessages = ConcurrentHashMap.newKeySet();
    }

    /**
     * 调试信息（基本级别）
     * @param message 消息内容
     */
    public void debug(String message) {
        debug(message, "basic");
    }

    /**
     * 调试信息（指定级别）
     * @param message 消息内容
     * @param level 调试级别
     */
    public void debug(String message, String level) {
        if (!config.isDebugEnabled()) {
            return;
        }

        String currentLevel = config.getDebugLevel();
        if ("basic".equals(currentLevel) && !"basic".equals(level)) {
            return;
        }

        if (config.isFilterRepeatingEnabled() && isDuplicateMessage(message)) {
            return;
        }

        plugin.getLogger().info("[DEBUG] " + message);
    }

    /**
     * 信息消息
     * @param message 消息内容
     */
    public void info(String message) {
        plugin.getLogger().info(message);
    }

    /**
     * 警告消息
     * @param message 消息内容
     */
    public void warning(String message) {
        plugin.getLogger().warning(message);
    }

    /**
     * 错误消息
     * @param message 消息内容
     */
    public void error(String message) {
        plugin.getLogger().severe(message);
    }

    /**
     * 检查是否为重复消息
     * @param message 消息内容
     * @return 是否为重复消息
     */
    private boolean isDuplicateMessage(String message) {
        long currentTime = System.currentTimeMillis();
        
        // 清理过期的消息时间戳
        messageTimestamps.entrySet().removeIf(entry -> currentTime - entry.getValue() > MESSAGE_EXPIRY_TIME);
        
        // 限制最近消息数
        if (recentMessages.size() > MAX_RECENT_MESSAGES) {
            recentMessages.clear();
        }
        
        // 检查消息是否在最近出现过
        if (recentMessages.contains(message)) {
            return true;
        }
        
        // 检查消息是否在短时间内重复
        if (messageTimestamps.containsKey(message)) {
            return true;
        }
        
        // 添加消息到时间戳和最近消息集合
        messageTimestamps.put(message, currentTime);
        recentMessages.add(message);
        
        return false;
    }

    /**
     * 清理过期消息
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        messageTimestamps.entrySet().removeIf(entry -> currentTime - entry.getValue() > MESSAGE_EXPIRY_TIME);
        
        if (recentMessages.size() > MAX_RECENT_MESSAGES) {
            recentMessages.clear();
        }
    }

    /**
     * 重置消息记录
     */
    public void reset() {
        messageTimestamps.clear();
        recentMessages.clear();
    }

    /**
     * 获取当前消息记录大小
     * @return 消息记录大小
     */
    public int getMessageCount() {
        return messageTimestamps.size();
    }
}
