package org.luminolcraft.randomitempvp;

import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Worlds 插件集成类
 * 使用反射调用 Worlds 插件的 API，避免编译时依赖
 * 
 * Worlds 插件主页: https://modrinth.com/plugin/worlds-1
 * GitHub: https://github.com/TheNextLvl/Worlds
 */
public class WorldsIntegration {
    private static boolean initialized = false;
    private static boolean worldsAvailable = false;
    private static boolean hasLoggedClassLoadError = false;
    
    private static Object worldsApi = null;
    private static Logger logger = null;
    
    // Worlds API 方法缓存
    private static Method methodCopyWorld = null; // cloneAsync 方法
    private static Method methodDeleteWorldInstance = null; // deleteAsync 或 deleteNow 方法
    private static Method methodGetOrCreateWorldInstance = null; // cloneAsync 方法
    private static Method methodLoadWorld = null; // read 方法
    private static Method methodFindFreeName = null; // findFreeName 方法
    
    /**
     * 初始化 Worlds 插件集成
     * 必须在插件启用时调用
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        initialized = true;
        logger = Bukkit.getLogger();
        
        // 检查 Worlds 插件是否已加载
        Plugin worldsPlugin = Bukkit.getPluginManager().getPlugin("Worlds");
        if (worldsPlugin == null || !worldsPlugin.isEnabled()) {
            worldsAvailable = false;
            logger.warning("[RandomItemPVP] Worlds 插件未安装或未启用");
            return;
        }
        
        worldsAvailable = true;
        try {
            String version = worldsPlugin.getDescription().getVersion();
            // 已检测到 Worlds 插件
        } catch (Exception e) {
            // 已检测到 Worlds 插件
        }
        
        // 获取 Worlds API
        // 使用 Worlds 插件的类加载器来加载类，确保正确的类加载上下文
        ClassLoader worldsClassLoader = worldsPlugin.getClass().getClassLoader();
        
        try {
            // 标准方式：通过 WorldsAPI 单例获取 API
            Class<?> worldsApiClass = Class.forName("net.thenextlvl.worlds.api.WorldsAPI", true, worldsClassLoader);
            Method getInstanceMethod = worldsApiClass.getMethod("getInstance");
            worldsApi = getInstanceMethod.invoke(null);
            
            if (worldsApi != null) {
                initializeMethods();
            } else {
                logger.warning("[RandomItemPVP] Worlds API 实例为 null");
                worldsAvailable = false;
            }
            
        } catch (NoClassDefFoundError e) {
            // 关键：如果触发 NoClassDefFoundError，说明在访问 WorldsAPI 时触发了可选依赖的类加载
            // 例如：WorldsAPI 的方法签名中可能引用了 net.thenextlvl.perworlds.GroupProvider
            // 既然用户完全信任 Worlds 插件，我们直接使用插件实例作为 API
            worldsApi = worldsPlugin;
            if (!initializeMethods()) {
                logger.warning("[RandomItemPVP] 无法初始化 Worlds API 方法");
                worldsAvailable = false;
            }
            
        } catch (ClassNotFoundException e) {
            // WorldsAPI 类不存在，尝试通过插件实例获取
            try {
                Class<?> worldsPluginClass = worldsPlugin.getClass();
                
                // 方法1: 查找 getApi() 方法或可能的API获取方法
                Method[] methods;
                try {
                    methods = worldsPluginClass.getDeclaredMethods();
                } catch (NoClassDefFoundError ncdfe) {
                    methods = new Method[0];
                }
                
                for (Method method : methods) {
                    String methodName = method.getName().toLowerCase();
                    // 查找可能的API获取方法
                    if ((methodName.equals("getapi") || methodName.equals("api") || 
                         methodName.contains("getinstance") || methodName.contains("instance")) && 
                        method.getParameterCount() == 0) {
                        method.setAccessible(true);
                        try {
                            Object apiObject = method.invoke(worldsPlugin);
                            if (apiObject != null && apiObject != worldsPlugin) {
                                worldsApi = apiObject;
                                if (initializeMethods()) {
                                    return;
                                }
                            }
                        } catch (NoClassDefFoundError ncdfe) {
                            // 忽略类加载错误
                        } catch (Exception invokeException) {
                            logger.warning("[RandomItemPVP] 调用 " + method.getName() + " 失败: " + invokeException.getMessage());
                        }
                    }
                }
                
                // 也检查公共方法
                try {
                    Method[] publicMethods = worldsPluginClass.getMethods();
                    for (Method method : publicMethods) {
                        String methodName = method.getName().toLowerCase();
                        if ((methodName.contains("api") || methodName.contains("getinstance")) && 
                            method.getParameterCount() == 0 && !method.getDeclaringClass().equals(Object.class)) {
                            method.setAccessible(true);
                            try {
                                Object apiObject = method.invoke(worldsPlugin);
                                if (apiObject != null && apiObject != worldsPlugin) {
                                    worldsApi = apiObject;
                                    if (initializeMethods()) {
                                        return;
                                    }
                                }
                            } catch (NoClassDefFoundError ncdfe) {
                                // 忽略类加载错误
                            } catch (Exception invokeException) {
                                // 忽略
                            }
                        }
                    }
                } catch (NoClassDefFoundError ncdfe2) {
                    // 忽略类加载错误
                }
                
                // 方法2: 尝试通过 levelView() 方法获取 API 对象
                try {
                    Method levelViewMethod = null;
                    for (Method method : methods) {
                        if (method.getName().equals("levelView") && method.getParameterCount() == 0) {
                            levelViewMethod = method;
                            break;
                        }
                    }
                    
                    if (levelViewMethod != null) {
                        levelViewMethod.setAccessible(true);
                        try {
                            Object levelView = levelViewMethod.invoke(worldsPlugin);
                            if (levelView != null && levelView != worldsPlugin) {
                                // 先尝试使用 levelView 作为 API
                                worldsApi = levelView;
                                if (initializeMethods()) {
                                    return;
                                }
                            }
                        } catch (NoClassDefFoundError ncdfe) {
                            // 忽略类加载错误
                        } catch (Exception ex) {
                            logger.warning("[RandomItemPVP] 调用 levelView() 失败: " + ex.getMessage());
                        }
                    }
                } catch (Exception ex2) {
                    // 忽略
                }
                
                // 方法3: 查找字段中的API对象
                try {
                    java.lang.reflect.Field[] fields = worldsPluginClass.getDeclaredFields();
                    for (java.lang.reflect.Field field : fields) {
                        try {
                            String fieldName = field.getName().toLowerCase();
                            if (fieldName.contains("api") || fieldName.contains("instance") || 
                                fieldName.contains("level") || fieldName.contains("view")) {
                                field.setAccessible(true);
                                try {
                                    Object fieldValue = field.get(worldsPlugin);
                                    if (fieldValue != null && fieldValue != worldsPlugin) {
                                        worldsApi = fieldValue;
                                        if (initializeMethods()) {
                                            return;
                                        }
                                    }
                                } catch (Exception fe) {
                                    // 忽略
                                }
                            }
                        } catch (NoClassDefFoundError ncdfe3) {
                            // 跳过这个字段
                            continue;
                        }
                    }
                } catch (Exception fe) {
                    // 忽略字段访问错误
                }
                
                // 方法4: 尝试直接通过类加载器加载WorldsAPI（可能有不同的包名）
                try {
                    String[] possibleApiClasses = {
                        "net.thenextlvl.worlds.api.WorldsAPI",
                        "net.thenextlvl.worlds.WorldsAPI",
                        "net.thenextlvl.worlds.api.API",
                        "net.thenextlvl.worlds.Worlds",
                    };
                    
                    for (String className : possibleApiClasses) {
                        try {
                            Class<?> possibleApiClass = Class.forName(className, true, worldsClassLoader);
                            Method getInstanceMethod = possibleApiClass.getMethod("getInstance");
                            Object apiInstance = getInstanceMethod.invoke(null);
                            if (apiInstance != null) {
                                worldsApi = apiInstance;
                                if (initializeMethods()) {
                                    return;
                                }
                            }
                        } catch (Exception ce) {
                            // 继续尝试下一个
                        }
                    }
                } catch (Exception ce) {
                    // 忽略
                }
                
                // 如果所有方法都失败，直接使用插件实例
                worldsApi = worldsPlugin;
                if (!initializeMethods()) {
                    logger.warning("[RandomItemPVP] 无法初始化 Worlds API 方法");
                    worldsAvailable = false;
                }
            } catch (Exception e2) {
                logger.severe("[RandomItemPVP] 初始化 Worlds API 失败: " + e2.getMessage());
                e2.printStackTrace();
                worldsAvailable = false;
            }
        } catch (NoSuchMethodException e) {
            logger.severe("[RandomItemPVP] WorldsAPI.getInstance() 方法不存在");
            worldsAvailable = false;
        } catch (Exception e) {
            logger.severe("[RandomItemPVP] 初始化 Worlds API 时出错: " + e.getMessage());
            e.printStackTrace();
            worldsAvailable = false;
        }
    }
    
    /**
     * 初始化 Worlds API 方法
     * @return 是否成功初始化了至少一个方法
     */
    private static boolean initializeMethods() {
        if (worldsApi == null) {
            return false;
        }
        
        boolean hasAtLeastOneMethod = false;
        
        Class<?> apiClass = worldsApi.getClass();
        
        try {
            Method[] allMethods = null;
            try {
                allMethods = apiClass.getDeclaredMethods();
            } catch (NoClassDefFoundError e) {
                // 即使 getDeclaredMethods() 也可能触发类加载错误
                allMethods = new Method[0]; // 设置为空数组
            }
            
            // 如果 worldsApi 是 WorldsPlugin，尝试通过 levelView() 等方法获取真正的 API
            if (worldsApi != null && worldsApi.getClass().getName().equals("net.thenextlvl.worlds.WorldsPlugin")) {
                try {
                    Method levelViewMethod = apiClass.getDeclaredMethod("levelView");
                    levelViewMethod.setAccessible(true);
                    Object levelView = levelViewMethod.invoke(worldsApi);
                    if (levelView != null) {
                        // 尝试使用 levelView 作为 API
                        Object oldApi = worldsApi;
                        worldsApi = levelView;
                        // 在这个对象上查找方法
                        Class<?> levelViewClass = levelView.getClass();
                        try {
                            // 尝试在这个对象上初始化方法
                            if (initializeMethods()) {
                                return hasAtLeastOneMethod;
                            }
                        } catch (NoClassDefFoundError e) {
                            // 忽略类加载错误
                        }
                        worldsApi = oldApi;
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
            
            // 尝试查找字段中是否有API对象
            try {
                java.lang.reflect.Field[] fields = apiClass.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    try {
                        String fieldName = field.getName().toLowerCase();
                        if (fieldName.contains("api") || fieldName.contains("instance")) {
                            field.setAccessible(true);
                            try {
                                Object fieldValue = field.get(worldsApi);
                                if (fieldValue != null) {
                                    // 尝试使用这个字段作为API
                                    Object possibleApi = field.get(worldsApi);
                                    if (possibleApi != null && !possibleApi.equals(worldsApi)) {
                                        Object oldApi = worldsApi;
                                        worldsApi = possibleApi;
                                        if (initializeMethods()) {
                                            return hasAtLeastOneMethod;
                                        }
                                        worldsApi = oldApi;
                                    }
                                }
                            } catch (Exception e) {
                                // 忽略
                            }
                        }
                    } catch (NoClassDefFoundError e) {
                        continue;
                    }
                }
            } catch (Exception e) {
                // 忽略字段访问错误
            }
            
        } catch (Exception e) {
            // 忽略错误
        }
        
        // 尝试查找 cloneAsync 方法（Worlds 插件的实际方法名）
        try {
            // cloneAsync(World, Consumer, boolean) -> CompletableFuture
            methodCopyWorld = findMethodFlexible(apiClass, "cloneAsync", 3);
            if (methodCopyWorld != null) {
                hasAtLeastOneMethod = true;
            }
        } catch (NoClassDefFoundError e) {
            // 忽略可选依赖缺失
        } catch (Exception e) {
            // 尝试 cloneInternal
            try {
                methodCopyWorld = findMethodFlexible(apiClass, "cloneInternal", 3);
                if (methodCopyWorld != null) {
                    hasAtLeastOneMethod = true;
                }
            } catch (Exception e2) {
                // 忽略
            }
        }
        
        // 尝试查找 deleteAsync 方法（Worlds 插件的实际方法名）
        try {
            // deleteAsync(World, boolean) -> CompletableFuture
            methodDeleteWorldInstance = findMethodFlexible(apiClass, "deleteAsync", 2);
            if (methodDeleteWorldInstance != null) {
                hasAtLeastOneMethod = true;
            }
        } catch (NoClassDefFoundError e) {
            // 忽略可选依赖缺失
        } catch (Exception e) {
            // 尝试 deleteNow
            try {
                methodDeleteWorldInstance = findMethodFlexible(apiClass, "deleteNow", 1);
                if (methodDeleteWorldInstance != null) {
                    hasAtLeastOneMethod = true;
                }
            } catch (Exception e2) {
                // 忽略
            }
        }
        
        // 尝试查找 findFreeName 方法（用于生成唯一的世界名）
        try {
            methodFindFreeName = findMethodFlexible(apiClass, "findFreeName", 1);
            if (methodFindFreeName != null) {
                hasAtLeastOneMethod = true;
            }
        } catch (Exception e) {
            // 忽略
        }
        
        // 尝试查找 read 方法（用于加载世界）
        try {
            methodLoadWorld = findMethodFlexible(apiClass, "read", 1);
            if (methodLoadWorld != null) {
                hasAtLeastOneMethod = true;
            }
        } catch (Exception e) {
            // 尝试其他可能的方法名
            try {
                methodLoadWorld = findMethodFlexible(apiClass, "loadWorld", 1);
                if (methodLoadWorld != null) {
                    hasAtLeastOneMethod = true;
                }
            } catch (Exception e2) {
                // 忽略
            }
        }
        
        // 尝试查找 findFreeName 和 cloneAsync 来实现 getOrCreateWorldInstance
        try {
            // 使用 findFreeName + cloneAsync 来实现
            if (methodFindFreeName != null && methodCopyWorld != null) {
                methodGetOrCreateWorldInstance = methodCopyWorld; // 使用 cloneAsync 作为基础
                hasAtLeastOneMethod = true;
            }
        } catch (Exception e) {
            // 忽略
        }
        
        // 尝试查找 read 方法（用于加载世界）
        try {
            // read(Path) -> Optional
            methodLoadWorld = findMethodFlexible(apiClass, "read", 1);
            if (methodLoadWorld != null) {
                hasAtLeastOneMethod = true;
            }
        } catch (NoClassDefFoundError e) {
            // 忽略可选依赖缺失
        } catch (Exception e) {
            // 忽略
        }
        
        return hasAtLeastOneMethod;
    }
    
    /**
     * 灵活查找方法：只检查方法名和参数数量，不检查参数类型
     * @param clazz 类
     * @param methodName 方法名
     * @param parameterCount 参数数量
     * @return 方法对象，如果找不到返回 null
     */
    private static Method findMethodFlexible(Class<?> clazz, String methodName, int parameterCount) throws Exception {
        // 遍历所有方法，只匹配方法名和参数数量
        // 注意：getDeclaredMethods() 可能触发类加载错误
        Method[] methods;
        try {
            methods = clazz.getDeclaredMethods();
        } catch (NoClassDefFoundError e) {
            // 如果获取方法列表都触发类加载错误，说明无法访问方法信息
            throw new Exception("无法获取方法列表: " + e.getMessage(), e);
        }
        
        for (Method method : methods) {
            try {
                if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (NoClassDefFoundError e) {
                // 访问方法信息时触发类加载错误，跳过该方法
                continue;
            }
        }
        
        throw new NoSuchMethodException("方法 " + methodName + " 未找到（参数数量: " + parameterCount + "）");
    }
    
    /**
     * 查找方法（处理重载）
     */
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws Exception {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            // 尝试查找所有同名方法
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == parameterTypes.length) {
                        boolean match = true;
                        for (int i = 0; i < params.length; i++) {
                            if (!params[i].isAssignableFrom(parameterTypes[i])) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            method.setAccessible(true);
                            return method;
                        }
                    }
                }
            }
            throw e;
        } catch (NoClassDefFoundError e) {
            // Worlds 插件的可选依赖缺失
            throw e;
        }
    }
    
    /**
     * 检查 Worlds 插件是否可用
     * @return 如果 Worlds 插件已安装并启用，返回 true
     */
    public static boolean isWorldsAvailable() {
        if (!initialized) {
            initialize();
        }
        return worldsAvailable;
    }
    
    /**
     * 检查是否有可用的方法
     * @return 如果至少有一个方法可用，返回 true
     */
    public static boolean hasAvailableMethods() {
        return methodCopyWorld != null || methodDeleteWorldInstance != null || 
               methodGetOrCreateWorldInstance != null || methodLoadWorld != null;
    }
    
    /**
     * 复制世界（克隆模板世界为实例世界）
     * @param templateKey 模板世界的 key
     * @param instanceKey 实例世界的 key
     * @return 是否成功
     */
    public static boolean copyWorld(String templateKey, String instanceKey) {
        if (!isWorldsAvailable() || worldsApi == null || methodCopyWorld == null) {
            return false;
        }
        
        try {
            // 先通过 key 获取模板世界
            World templateWorld = Bukkit.getWorld(templateKey);
            if (templateWorld == null) {
                logger.warning("[RandomItemPVP] 模板世界不存在: " + templateKey);
                return false;
            }
            
            // cloneAsync(World, Consumer, boolean) 需要：
            // 1. World 对象（模板世界）
            // 2. Consumer<World> 回调（克隆完成后的处理，可能在克隆过程中调用，用于设置世界名）
            // 3. boolean 标志（是否卸载原世界）
            
            // 创建一个 Consumer 来处理克隆过程（Worlds 3.x 会传入 LevelData.Builder）
            java.util.function.Consumer<Object> consumer = (builderCandidate) ->
                configureCloneBuilder(builderCandidate, instanceKey, instanceKey);
            
            // 调用 cloneAsync 方法
            // cloneAsync(World, Consumer, boolean)
            Object result = methodCopyWorld.invoke(worldsApi, templateWorld, consumer, true);
            
            // 方法返回 CompletableFuture<World>，等待完成
            if (result != null && result instanceof java.util.concurrent.CompletableFuture) {
                @SuppressWarnings("unchecked")
                java.util.concurrent.CompletableFuture<World> future = (java.util.concurrent.CompletableFuture<World>) result;
                try {
                    World clonedWorld = future.get(); // 等待克隆完成
                    return clonedWorld != null;
                } catch (Exception e) {
                    logger.warning("[RandomItemPVP] 等待克隆完成时出错: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
            
            return result != null;
        } catch (Exception e) {
            logger.warning("[RandomItemPVP] 复制世界失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 删除世界实例
     * @param instanceKey 实例世界的 key
     * @return 是否成功
     */
    public static boolean deleteWorldInstance(String instanceKey) {
        if (!isWorldsAvailable() || worldsApi == null || methodDeleteWorldInstance == null) {
            return false;
        }
        
        try {
            // 先通过 key 获取世界对象
            World world = Bukkit.getWorld(instanceKey);
            if (world == null) {
                // 世界可能已经卸载，认为删除成功
                return true;
            }
            
            // deleteAsync(World, boolean) 或 deleteNow(World)
            Object result;
            if (methodDeleteWorldInstance.getParameterCount() == 2) {
                // deleteAsync(World, boolean) - 异步删除，第二个参数是是否卸载
                result = methodDeleteWorldInstance.invoke(worldsApi, world, true);
            } else {
                // deleteNow(World) - 立即删除
                result = methodDeleteWorldInstance.invoke(worldsApi, world);
            }
            
            // 如果返回 CompletableFuture，等待完成
            if (result != null && result instanceof java.util.concurrent.CompletableFuture) {
                @SuppressWarnings("unchecked")
                java.util.concurrent.CompletableFuture<?> future = (java.util.concurrent.CompletableFuture<?>) result;
                try {
                    future.get(); // 等待删除完成
                    return true;
                } catch (Exception e) {
                    logger.warning("[RandomItemPVP] 等待删除完成时出错: " + e.getMessage());
                    return false;
                }
            }
            
            // 如果返回 Boolean 或其他类型
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return result != null;
        } catch (Exception e) {
            logger.warning("[RandomItemPVP] 删除世界实例失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取或创建世界实例
     * @param templateKey 模板世界的 key
     * @param instanceKey 实例世界的 key
     * @return World 对象，如果失败返回 null
     */
    public static World getOrCreateWorldInstance(String templateKey, String instanceKey) {
        if (!isWorldsAvailable() || worldsApi == null || methodGetOrCreateWorldInstance == null) {
            return null;
        }
        
        try {
            // 先检查实例世界是否已存在
            World existingWorld = Bukkit.getWorld(instanceKey);
            if (existingWorld != null) {
                return existingWorld; // 如果已存在，直接返回
            }
            
            // 获取模板世界
            World templateWorld = Bukkit.getWorld(templateKey);
            if (templateWorld == null) {
                logger.warning("[RandomItemPVP] 模板世界不存在: " + templateKey);
                return null;
            }
            
            // 使用 cloneAsync 克隆世界
            // cloneAsync(World, Consumer, boolean)
            // Consumer 回调在克隆过程中调用，可能用于设置世界属性
            
            // 注意：Worlds 插件可能自动生成世界名，而不是使用我们提供的 instanceKey
            // 所以我们需要通过 findFreeName 生成一个唯一名称，或者接受 Worlds 插件生成的名称
            java.util.function.Consumer<Object> consumer = builderCandidate ->
                configureCloneBuilder(builderCandidate, instanceKey, instanceKey);
            
            Object result = methodGetOrCreateWorldInstance.invoke(worldsApi, templateWorld, consumer, true);
            
            // 方法返回 CompletableFuture<World>
            if (result != null && result instanceof java.util.concurrent.CompletableFuture) {
                @SuppressWarnings("unchecked")
                java.util.concurrent.CompletableFuture<World> future = (java.util.concurrent.CompletableFuture<World>) result;
                try {
                    World clonedWorld = future.get(); // 等待克隆完成
                    return clonedWorld;
                } catch (Exception e) {
                    logger.warning("[RandomItemPVP] 等待克隆完成时出错: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
            
            // 如果返回的是 World 对象（不应该，但兼容处理）
            if (result instanceof World) {
                return (World) result;
            }
            
            return null;
        } catch (Exception e) {
            logger.warning("[RandomItemPVP] 获取或创建世界实例失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 加载世界
     * @param worldKey 世界的 key
     * @return World 对象，如果失败返回 null
     */
    public static World loadWorld(String worldKey) {
        if (!isWorldsAvailable() || worldsApi == null) {
            return null;
        }
        
        // 先尝试从 Bukkit 获取已加载的世界
        World world = Bukkit.getWorld(worldKey);
        if (world != null) {
            return world;
        }
        
        // 如果没有加载，尝试使用 Worlds 插件的 read 方法
        if (methodLoadWorld != null) {
            // read(Path) 方法，需要传入 Path 对象
            // 需要将 worldKey 转换为 Path
            try {
                java.nio.file.Path worldPath = Bukkit.getWorldContainer().toPath().resolve(worldKey);
                Object result = methodLoadWorld.invoke(worldsApi, worldPath);
                
                // read 方法返回 Optional，需要处理
                if (result != null) {
                    // 尝试获取 Optional 的值
                    try {
                        Method getMethod = result.getClass().getMethod("get");
                        Object optionalValue = getMethod.invoke(result);
                        if (optionalValue instanceof World) {
                            return (World) optionalValue;
                        }
                        // Optional 可能包含其他类型，尝试加载世界
                        if (optionalValue != null) {
                            // 可能需要通过 Bukkit API 加载
                            return Bukkit.createWorld(new org.bukkit.WorldCreator(worldKey));
                        }
                    } catch (Exception e) {
                        // 不是 Optional 或获取失败
                    }
                }
            } catch (Exception e) {
                logger.warning("[RandomItemPVP] 使用 read 方法加载世界失败: " + e.getMessage());
            }
            
            // 如果 read 方法失败，使用 Bukkit API 加载
            return Bukkit.createWorld(new org.bukkit.WorldCreator(worldKey));
        }
        
        // 使用标准的 Bukkit API 加载世界
        return Bukkit.createWorld(new org.bukkit.WorldCreator(worldKey));
    }

    private static void configureCloneBuilder(Object builderCandidate, String instanceKey, String displayName) {
        if (builderCandidate == null || builderCandidate instanceof World) {
            return;
        }

        Class<?> builderClass = builderCandidate.getClass();
        String sanitizedKey = sanitizeWorldKey(instanceKey);

        try {
            Method keyMethod = builderClass.getMethod("key", Key.class);
            keyMethod.invoke(builderCandidate, Key.key("randomitempvp", sanitizedKey));
        } catch (NoSuchMethodException ignored) {
            // Older Worlds versions might not expose key method
        } catch (Exception ex) {
            logger.warning("[RandomItemPVP] 无法为克隆世界设置 key: " + ex.getMessage());
        }

        try {
            Method nameMethod = builderClass.getMethod("name", String.class);
            nameMethod.invoke(builderCandidate, displayName);
        } catch (NoSuchMethodException ignored) {
            // Optional method
        } catch (Exception ex) {
            logger.warning("[RandomItemPVP] 无法为克隆世界设置名称: " + ex.getMessage());
        }

        try {
            Method pathMethod = builderClass.getMethod("path", Path.class);
            pathMethod.invoke(builderCandidate, Bukkit.getWorldContainer().toPath().resolve(sanitizedKey));
        } catch (NoSuchMethodException ignored) {
            // Optional method
        } catch (Exception ex) {
            logger.warning("[RandomItemPVP] 无法为克隆世界设置路径: " + ex.getMessage());
        }
    }

    private static String sanitizeWorldKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '/') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }
    
    /**
     * 列出可用的方法（用于调试）
     */
    public static void listAvailableMethods() {
        if (logger == null) {
            return;
        }
        
        logger.info("[RandomItemPVP] ========== Worlds API 方法状态 ==========");
        logger.info("[RandomItemPVP] Worlds 插件可用: " + worldsAvailable);
        logger.info("[RandomItemPVP] copyWorld: " + (methodCopyWorld != null ? "可用" : "不可用"));
        logger.info("[RandomItemPVP] deleteWorldInstance: " + (methodDeleteWorldInstance != null ? "可用" : "不可用"));
        logger.info("[RandomItemPVP] getOrCreateWorldInstance: " + (methodGetOrCreateWorldInstance != null ? "可用" : "不可用"));
        logger.info("[RandomItemPVP] loadWorld: " + (methodLoadWorld != null ? "可用" : "不可用"));
        logger.info("[RandomItemPVP] ==========================================");
    }
}

