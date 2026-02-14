package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldBorder;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 盟友生物管理器
 * 管理玩家通过刷怪蛋召唤的盟友生物
 */
public class AllyMobManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final NamespacedKey ownerKey;
    
    // 记录生物的所有者：生物UUID -> 玩家UUID
    private final Map<UUID, UUID> mobOwners = new ConcurrentHashMap<>();
    
    // 记录玩家的盟友生物：玩家UUID -> 生物UUID列表
    private final Map<UUID, Set<UUID>> playerAllies = new ConcurrentHashMap<>();
    
    public AllyMobManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.ownerKey = new NamespacedKey(plugin, "ally_owner");
    }
    
    /**
     * 检查是否启用了盟友功能
     */
    public boolean isEnabled() {
        return config.isAlliesEnabled();
    }
    
    /**
     * 创建盟友刷怪蛋
     * @param entityType 生物类型
     * @return 刷怪蛋物品
     */
    public ItemStack createAllySpawnEgg(EntityType entityType) {
        Material eggMaterial;
        try {
            eggMaterial = Material.valueOf(entityType.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            // 如果没有对应的刷怪蛋，使用通用刷怪蛋
            eggMaterial = Material.SPAWNER;
        }
        
        ItemStack egg = new ItemStack(eggMaterial, 1);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l盟友刷怪蛋 §7(" + getEntityTypeName(entityType) + ")");
            List<String> lore = new ArrayList<>();
            lore.add("§7右键使用召唤盟友");
            lore.add("§7盟友不会攻击你");
            meta.setLore(lore);
            
            // 使用 PersistentDataContainer 存储生物类型
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "ally_type"),
                PersistentDataType.STRING,
                entityType.name()
            );
            
            egg.setItemMeta(meta);
        }
        return egg;
    }
    
    /**
     * 获取生物类型的中文名称
     */
    private String getEntityTypeName(EntityType type) {
        Map<EntityType, String> names = new HashMap<>();
        names.put(EntityType.ZOMBIE, "僵尸");
        names.put(EntityType.SKELETON, "骷髅");
        names.put(EntityType.SPIDER, "蜘蛛");
        names.put(EntityType.CREEPER, "苦力怕");
        names.put(EntityType.ENDERMAN, "末影人");
        names.put(EntityType.WOLF, "狼");
        names.put(EntityType.IRON_GOLEM, "铁傀儡");
        names.put(EntityType.PIGLIN, "猪灵");
        names.put(EntityType.PIGLIN_BRUTE, "猪灵蛮兵");
        names.put(EntityType.HOGLIN, "疣猪兽");
        names.put(EntityType.ZOGLIN, "僵尸疣猪兽");
        names.put(EntityType.PILLAGER, "掠夺者");
        names.put(EntityType.VINDICATOR, "卫道士");
        names.put(EntityType.EVOKER, "唤魔者");
        names.put(EntityType.VEX, "恼鬼");
        names.put(EntityType.WITCH, "女巫");
        names.put(EntityType.PHANTOM, "幻翼");
        names.put(EntityType.HUSK, "尸壳");
        names.put(EntityType.DROWNED, "溺尸");
        names.put(EntityType.STRAY, "流浪者");
        names.put(EntityType.WITHER_SKELETON, "凋灵骷髅");
        names.put(EntityType.CAVE_SPIDER, "洞穴蜘蛛");
        names.put(EntityType.ZOMBIE_VILLAGER, "僵尸村民");
        names.put(EntityType.POLAR_BEAR, "北极熊");
        names.put(EntityType.ZOMBIFIED_PIGLIN, "僵尸猪灵");
        
        return names.getOrDefault(type, type.name());
    }
    
    /**
     * 处理玩家使用刷怪蛋
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getAction() == null) {
            return;
        }
        
        ItemStack item = event.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        
        // 检查是否是盟友刷怪蛋
        if (!meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "ally_type"),
            PersistentDataType.STRING
        )) {
            return;
        }
        
        // 检查玩家是否在游戏中
        if (!(plugin instanceof RandomItemPVP)) {
            return;
        }
        
        RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
        ArenaManager arenaManager = pluginInstance.getArenaManager();
        if (arenaManager == null || !arenaManager.isPlayerInArena(event.getPlayer())) {
            return;
        }
        
        String entityTypeName = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "ally_type"),
            PersistentDataType.STRING
        );
        
        if (entityTypeName == null) {
            return;
        }
        
        try {
            final EntityType entityType = EntityType.valueOf(entityTypeName);
            final JavaPlugin finalPlugin = plugin;
            
            // 延迟生成，确保位置正确（使用 Folia 兼容的调度器）
            final Player player = event.getPlayer();
            Location spawnLoc = player.getLocation();
            
            // 移除边界检查，直接使用玩家位置生成生物
            final Location finalSpawnLoc = spawnLoc;
            final ItemStack finalItem = item;
            
            Bukkit.getRegionScheduler().run(finalPlugin, finalSpawnLoc, task -> {
                Entity spawned = finalSpawnLoc.getWorld().spawnEntity(finalSpawnLoc, entityType);
                
                // 设置所有者
                setAllyOwner(spawned, player);
                
                // 应用配置的属性
                applyAllyProperties(spawned, player);
                
                player.sendMessage("§a已召唤盟友：" + getEntityTypeName(entityType));
                
                // 减少刷怪蛋数量
                if (finalItem.getAmount() > 1) {
                    finalItem.setAmount(finalItem.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            });
            
            event.setCancelled(true);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的生物类型: " + entityTypeName);
        }
    }
    
    /**
     * 设置生物的所有者
     */
    public void setAllyOwner(Entity entity, Player owner) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        
        UUID entityId = entity.getUniqueId();
        UUID ownerId = owner.getUniqueId();
        
        mobOwners.put(entityId, ownerId);
        
        // 添加到玩家的盟友列表
        playerAllies.computeIfAbsent(ownerId, k -> ConcurrentHashMap.newKeySet()).add(entityId);
        
        // 使用元数据标记
        entity.setMetadata("ally_owner", new FixedMetadataValue(plugin, ownerId.toString()));
    }
    
    /**
     * 应用盟友属性（生命值、攻击力等）
     */
    private void applyAllyProperties(Entity entity, Player owner) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        
        LivingEntity living = (LivingEntity) entity;
        
        // 应用生命值倍数
        double healthMultiplier = config.getAllyHealthMultiplier();
        if (healthMultiplier > 1.0) {
            // 使用属性系统设置最大生命值
            org.bukkit.attribute.AttributeInstance maxHealthAttr = living.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) {
                double baseMaxHealth = maxHealthAttr.getBaseValue();
                maxHealthAttr.setBaseValue(baseMaxHealth * healthMultiplier);
                living.setHealth(living.getMaxHealth());
            }
        }
        
        // 如果是可驯服的生物，尝试驯服
        if (living instanceof Tameable) {
            Tameable tameable = (Tameable) living;
            if (!tameable.isTamed()) {
                tameable.setOwner(owner);
            }
        }
        
        // 如果是狼，设置为友好
        if (living instanceof Wolf) {
            Wolf wolf = (Wolf) living;
            wolf.setAngry(false);
        }
    }
    
    /**
     * 处理生物生成事件，标记盟友
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled() || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        
        Entity entity = event.getEntity();
        if (entity.hasMetadata("ally_owner")) {
            // 已经是盟友，不需要处理
            return;
        }
    }
    
    /**
     * 防止盟友攻击主人（只保护召唤者，其他玩家都会被攻击）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity target = event.getTarget();
        Entity attacker = event.getEntity();

        if (!(target instanceof Player) || !(attacker instanceof LivingEntity)) {
            return;
        }

        Player targetPlayer = (Player) target;
        UUID attackerId = attacker.getUniqueId();

        // 检查攻击者是否是目标的盟友（只有主人受保护）
        if (mobOwners.containsKey(attackerId)) {
            UUID ownerId = mobOwners.get(attackerId);
            if (ownerId.equals(targetPlayer.getUniqueId())) {
                // 盟友试图攻击主人，取消攻击
                event.setCancelled(true);
            }
            // 如果不是主人，则允许攻击
        }
    }

    /**
     * 防止盟友伤害主人（只保护召唤者，其他玩家都会被攻击）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (!(victim instanceof Player) || !(damager instanceof LivingEntity)) {
            return;
        }

        Player victimPlayer = (Player) victim;
        UUID damagerId = damager.getUniqueId();

        // 检查攻击者是否是受害者的盟友（只有主人受保护）
        if (mobOwners.containsKey(damagerId)) {
            UUID ownerId = mobOwners.get(damagerId);
            if (ownerId.equals(victimPlayer.getUniqueId())) {
                // 盟友试图伤害主人，取消伤害
                event.setCancelled(true);
            }
            // 如果不是主人，则允许造成伤害
        }
    }
    
    /**
     * 获取玩家的盟友生物列表
     */
    public Set<UUID> getPlayerAllies(UUID playerId) {
        return playerAllies.getOrDefault(playerId, Collections.emptySet());
    }
    
    /**
     * 清理玩家的所有盟友（游戏结束时调用）
     */
    public void clearPlayerAllies(UUID playerId) {
        Set<UUID> allies = playerAllies.remove(playerId);
        if (allies != null) {
            for (UUID allyId : allies) {
                mobOwners.remove(allyId);
                Entity entity = Bukkit.getEntity(allyId);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
    }
    
    /**
     * 清理所有盟友（插件禁用时调用）
     */
    public void clearAllAllies() {
        for (Set<UUID> allies : playerAllies.values()) {
            for (UUID allyId : allies) {
                Entity entity = Bukkit.getEntity(allyId);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        mobOwners.clear();
        playerAllies.clear();
    }
}

