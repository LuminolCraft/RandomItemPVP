package org.luminolcraft.randomitempvp;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Logger;

/**
 * 负责在插件首次启动时将 resources 中的模块化配置复制到数据目录。
 */
public final class ConfigBootstrapper {

    private static final String MODULE_FOLDER = "config-modules";
    private static final String[] MODULE_FILES = {
        "arena.yml",
        "border.yml",
        "database.yml",
        "events.yml",
        "items.yml",
        "maps.yml",
        "allies.yml"
    };

    private ConfigBootstrapper() {}

    public static void ensureModularConfigs(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.severe("[RandomItemPVP] 无法创建插件数据目录：" + dataFolder.getAbsolutePath());
            return;
        }

        File modulesDir = new File(dataFolder, MODULE_FOLDER);
        if (!modulesDir.exists() && !modulesDir.mkdirs()) {
            logger.severe("[RandomItemPVP] 无法创建模块化配置目录：" + modulesDir.getAbsolutePath());
            return;
        }

        for (String moduleFile : MODULE_FILES) {
            String resourcePath = MODULE_FOLDER + "/" + moduleFile;
            File target = new File(modulesDir, moduleFile);

            if (target.exists()) {
                continue;
            }

            try {
                plugin.saveResource(resourcePath, false);
                logger.info("[RandomItemPVP] 已创建模块化配置：" + resourcePath);
            } catch (IllegalArgumentException ex) {
                logger.warning("[RandomItemPVP] 无法复制模块化配置 " + resourcePath + " ：" + ex.getMessage());
            }
        }
    }
}




