# RandomItemPVP

<div align="center">

**一款功能强大的 Minecraft 大逃杀类小游戏插件**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Server-Paper%20%7C%20Folia-blue.svg)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Fully%20Supported-purple.svg)](https://papermc.io/software/folia)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Version](https://img.shields.io/badge/Version-3.0.0-red.svg)](https://github.com/Narcssu/RandomItemPVP/releases)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

</div>

---

<p align="center">
  <img src="https://placehold.co/800x200/1a1a2e/FFF?text=RandomItemPVP" alt="RandomItemPVP Banner" width="800"/>
</p>

<p align="center">
  <strong>⚡ 高性能 | 🎲 强随机性 | 🛠️ 可配置 | 👥 多房间</strong>
</p>

---

## 📋 目录

- [项目概述](#项目概述)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [环境要求](#环境要求)
- [安装指南](#安装指南)
- [配置说明](#配置说明)
- [使用指南](#使用指南)
- [指令与权限](#指令与权限)
- [PlaceholderAPI 集成](#placeholderapi-集成)
- [多房间系统](#多房间系统)
- [数据库配置](#数据库配置)
- [构建指南](#构建指南)
- [常见问题](#常见问题)
- [贡献指南](#贡献指南)
- [许可证](#许可证)
- [联系方式](#联系方式)

---

## 项目概述

### 📖 简介

RandomItemPVP 是一款专为 Paper 和 Folia 1.21+ 服务器设计的大逃杀类小游戏插件。该插件完全兼容 Folia 多线程架构，能够支持大规模并发玩家同时参与游戏。玩家将在不断缩小的边界内展开激烈战斗，通过随机获得的物品和特殊能力争夺最后的生存机会。

插件采用模块化设计理念，将配置管理、游戏逻辑、数据库存储等功能分离到独立的 Manager 类中，既保证了代码的清晰结构，又便于后续的功能扩展和维护。每个游戏房间独立运行，支持多房间并发，管理员可以根据需要创建多个不同配置的竞技场，满足不同玩家群体的需求。

### 🎯 设计理念

RandomItemPVP 的设计遵循以下几个核心原则。首先是**公平性**，通过随机物品发放机制确保每位玩家在游戏开始时拥有相同的机会，运气也是实力的一部分。其次是**竞技性**，快节奏的游戏设计（每局3-5分钟）要求玩家具备快速反应和灵活战术的能力。第三是**可扩展性**，高度可配置的参数允许服务器管理员根据自身需求调整游戏平衡性，创造独特的游戏体验。最后是**高性能**，充分利用 Folia 的多线程能力，确保在高负载情况下依然保持流畅的游戏体验。

### ✨ 为什么选择 RandomItemPVP

| 特性 | 优势 |
|------|------|
| Folia 原生支持 | 充分利用多线程架构，支持万人级服务器 |
| 模块化配置 | 所有参数可独立调整，满足个性化需求 |
| 双数据库支持 | SQLite 开箱即用，MySQL 支持跨服数据共享 |
| 多房间系统 | 支持多个独立竞技场，并发运行互不干扰 |
| 完整统计系统 | 记录胜利、击杀、KD 等数据，支持排行榜 |
| 丰富的随机事件 | 箭雨、怪物围攻、恶魂袭击等多种事件 |

---

## 核心功能

### 🎲 随机物品系统

游戏的核心机制之一是随机物品发放。玩家每隔固定时间间隔会自动获得一件随机物品，物品类型从基础武器到稀有道具不等。系统内置智能过滤机制，自动排除无用的物品类型（如花、红石粉等原材料），确保每次发放的物品都具有实战价值。武器和工具会自动附加适当的魔咒，例如弓会附带无限箭矢效果，让玩家能够持续输出伤害。

从 v2.0.1 版本开始，插件引入了物品权重系统，管理员可以为不同物品配置出现概率，稀有物品自然更难获得。药水性状也经过重新设计，所有发放的药水都会自动附带随机有用的效果（如治疗、力量、速度等），避免了获得无用药水的挫败感。

### 🌐 大逃杀机制

经典的缩圈机制迫使所有玩家逐渐靠近，最终在有限的区域内展开决战。边界可以穿越但会持续造成伤害，玩家必须在安全区域内寻找资源和战斗机会。每位玩家在游戏开始时会被传送到128格高空的基岩柱顶部，围成一个圆圈均匀分布，自动获得5秒的缓降效果防止摔落伤害。

随着游戏进程推进，边界会按设定的时间间隔逐步缩小。当边界接近最小尺寸时，游戏进入决战阶段，事件触发频率大幅提升，空投补给变得更加频繁，为最后的生存竞争增添紧张氛围。

### 🎁 空投系统

定期从空中降落的补给箱是玩家获取高级资源的重要途径。空投位置完全随机但保证在当前边界内，包含金苹果、末影珍珠、附魔装备等珍贵物品。空投落地时会有明显的粒子效果和音效提示，吸引附近玩家的注意。每个空投箱只能被打开一次，打开后物品进入玩家背包，箱子本身会在游戏结束时自动清理。

### ⚡ 随机事件

游戏过程中会随机触发各类事件，为对局增添变数和紧张感。目前支持的事件类型包括箭雨（从天而降的箭矢攻击随机玩家）、僵尸围城（大量僵尸突然出现在目标玩家周围）、恶魂袭击（恶魂向玩家发射火球）以及苦力怕雨（五只苦力怕从30格高空降落，自带缓降效果不会摔死）。所有事件的目标都是随机存活的玩家，确保公平性。

### 🏆 击杀奖励系统

成功击杀敌人会获得丰厚的奖励。击杀者立即恢复3颗心（6点生命值），并额外获得三件随机有用物品（如金苹果、末影珍珠、64支箭等）。系统还提供十种不同风格的死亡播报消息，每次击杀都会随机选择一种，为游戏增添趣味性和表现力。

### 🎯 特殊物品能力

游戏中的特殊物品拥有独特的能力。TNT可以右键投掷，以抛物线飞向目标位置，2.5秒后爆炸，威力足以破坏地形但不会伤害投掷者。火焰弹投掷速度更快（2秒冷却），只点燃目标不造成爆炸伤害，适合快速骚扰和区域封锁。末影水晶可以放置在任何固体方块上，被攻击后产生4.0威力的爆炸，是制造大规模破坏和区域控制的利器。

### 📊 数据统计系统

从 v2.2.0 版本开始，插件集成了完整的数据统计系统。系统记录每位玩家的胜利次数、失败次数、总击杀数、总死亡数和游戏场次，并自动计算KD比率和胜率。统计数据持久化存储，即使服务器重启也不会丢失。排行榜系统支持按胜利次数、击杀数和KD比率三种方式排序，管理员可以通过指令快速查看TOP10玩家。

### 👥 多房间系统

v3.0.0 版本引入了强大的多房间支持。每个房间完全独立运行，拥有自己的配置、地图和游戏状态。管理员可以创建多个不同配置的竞技场，满足不同水平玩家群体的需求。结合 Worlds 插件，每个房间可以使用独立的世界副本，确保地图在游戏之间完美重置。

---

## 技术栈

### 核心技术

| 技术 | 用途 |
|------|------|
| **Java 21** | 开发语言，利用现代语言特性 |
| **Paper API 1.21.1** | Minecraft 服务器 API，提供丰富的开发接口 |
| **Folia** | 多线程服务器核心，支持高并发 |
| **Maven** | 项目构建和管理 |
| **HikariCP** | MySQL 连接池，高性能数据库连接管理 |

### 数据库

| 数据库 | 类型 | 适用场景 |
|--------|------|----------|
| **SQLite** | 嵌入式 | 小型服务器，零配置，开箱即用 |
| **MySQL** | 关系型 | 大型服务器，跨服数据共享 |

### 依赖插件

| 插件 | 必需性 | 用途 |
|------|--------|------|
| **Worlds** | 必需 | 多世界管理，支持世界实例化 |
| **PlaceholderAPI** | 可选 | 提供变量扩展，支持记分板和 TAB 显示 |

### 项目结构

```
RandomItemPVP/
├── src/main/java/org/luminolcraft/randomitempvp/
│   ├── RandomItemPVP.java           # 插件主类
│   ├── ArenaManager.java            # 房间管理器
│   ├── GameArena.java               # 游戏房间类
│   ├── GameInstance.java            # 游戏实例类
│   ├── DatabaseManager.java         # 数据库管理器
│   ├── PlayerStatsManager.java      # 玩家统计管理器
│   ├── ConfigManager.java           # 配置管理器
│   ├── ItemAbilityManager.java      # 特殊物品能力管理器
│   ├── RewardManager.java           # 击杀奖励管理器
│   ├── AirdropManager.java          # 空投管理器
│   ├── AllyMobManager.java          # 盟友生物管理器
│   ├── MapVoteManager.java          # 地图投票管理器
│   ├── MapResetManager.java         # 地图重置管理器
│   ├── GameEventListener.java       # 游戏事件监听器
│   ├── WorldsIntegration.java       # Worlds 插件集成
│   ├── RipvpCommand.java            # 指令处理器
│   ├── MLGCommand.java              # MLG 指令
│   ├── MLGManager.java              # MLG 管理器
│   └── RandomItemPVPExpansion.java  # PlaceholderAPI 扩展
└── src/main/resources/
    ├── config.yml                   # 主配置文件
    ├── plugin.yml                   # 插件元数据
    └── config-modules/              # 模块化配置文件
        ├── arena.yml                # 竞技场配置
        ├── border.yml               # 边界配置
        ├── items.yml                # 物品权重配置
        ├── events.yml               # 随机事件配置
        ├── database.yml             # 数据库配置
        ├── maps.yml                 # 地图列表配置
        └── allies.yml               # 盟友生物配置
```

---

## 环境要求

### 服务器要求

| 项目 | 最低要求 | 推荐配置 |
|------|----------|----------|
| **服务器核心** | Paper 1.21+ 或 Folia | Folia（支持更高并发） |
| **Java 版本** | Java 21 | Java 21 LTS |
| **可用内存** | 2GB | 4GB 或更多 |
| **磁盘空间** | 500MB | 1GB 或更多 |

### 必需依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| **Worlds 插件** | 最新版 | 提供世界实例化功能，插件运行必需 |

### 可选依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| **PlaceholderAPI** | 2.11.5+ | 提供变量扩展，支持记分板和 TAB 显示 |

### 兼容性

| 服务器核心 | 兼容性 |
|------------|--------|
| Paper 1.21.x | ✅ 完全支持 |
| Paper 1.21.3 | ✅ 完全支持 |
| Folia 1.21.x | ✅ 完全支持 |

---

## 安装指南

### 方式一：下载预编译版本（推荐）

1. 访问项目的 [Releases 页面](https://github.com/Narcssu/RandomItemPVP/releases)

2. 下载最新版本的 JAR 文件（RandomItemPVP-3.0.0.jar）

3. 将下载的 JAR 文件放入服务器的 `plugins` 目录

4. 重启服务器等待插件加载

### 方式二：从源码构建

```bash
# 克隆仓库
git clone https://github.com/Narcssu/RandomItemPVP.git
cd RandomItemPVP

# 构建项目
mvn clean package

# 构建产物位于 target/ 目录
ls target/RandomItemPVP-*.jar
```

### 安装必需依赖

1. 下载 Worlds 插件（[Modrinth 页面](https://modrinth.com/plugin/worlds-1)）

2. 将 Worlds 插件放入服务器的 `plugins` 目录

3. 重启服务器

### 安装可选依赖（PlaceholderAPI）

1. 下载 PlaceholderAPI 插件（[Spigot 页面](https://www.spigotmc.org/resources/6245/)）

2. 将 PlaceholderAPI 插件放入服务器的 `plugins` 目录

3. 重启服务器

### 初始配置

1. 首次启动后，插件会自动创建配置文件

2. 进入游戏，使用管理员权限设置游戏出生点：

```bash
/ripvp setspawn
```

3. 根据需要编辑配置文件（见[配置说明](#配置说明)）

4. 使用指令重载配置：

```bash
/ripvp reload
```

---

## 配置说明

### 配置文件结构

插件采用模块化配置设计，所有配置文件位于 `plugins/RandomItemPVP/` 目录：

```
plugins/RandomItemPVP/
├── config.yml                    # 主配置文件
├── config-modules/
│   ├── arena.yml                # 竞技场设置
│   ├── border.yml               # 边界设置
│   ├── items.yml                # 物品权重
│   ├── events.yml               # 随机事件
│   ├── database.yml             # 数据库设置
│   ├── maps.yml                 # 地图列表
│   └── allies.yml               # 盟友生物
└── data.db                      # SQLite 数据库文件（自动创建）
```

### 主配置文件（config.yml）

主配置包含游戏的核心参数设置：

```yaml
# ==========================================
# RandomItemPVP 主配置文件
# ==========================================
# 修改后使用 /ripvp reload 重载配置
# ==========================================

# 竞技场设置
arena:
  radius: 48                      # 初始边界半径（格）
  min-players: 2                  # 最少玩家数才能开始
  start-countdown: 30             # 准备倒计时（秒）
  auto-start-delay: 5             # 达到最少玩家后延迟几秒开始
  vote-duration: 15               # 地图投票持续时间（秒）
  
  # 世界实例化设置（多房间支持）
  world-instancing:
    enabled: true                 # 启用世界实例化
    auto-cleanup: true            # 游戏结束后自动删除世界实例
  
  # 大厅设置（游戏结束后传送玩家回大厅）
  lobby:
    enabled: true                 # 启用大厅传送
    world: 'world'                # 大厅世界（Worlds key）
    x: 0.0
    y: 64.0
    z: 0.0

# 边界设置
border:
  damage: 3.0                     # 穿越边界每秒扣血（1.0=0.5心）
  min_diameter: 10                # 边界最小直径（格）
  shrink_amount_per_interval: 6.0 # 每次缩圈减少的直径（格）
  shrink_interval_ticks: 600      # 缩圈间隔（20ticks=1秒）
  first_shrink_delay_ticks: 200   # 首次缩圈延迟（ticks）

# 物品发放设置
items:
  interval_ticks: 100             # 物品发放间隔（100ticks=5秒）
  blacklist:                      # 禁用物品列表
    - AIR
    - BEDROCK
    - BARRIER
    - COMMAND_BLOCK

# 随机事件设置
events:
  delay_min_ticks: 600            # 最小延迟（ticks）
  delay_max_ticks: 2400           # 最大延迟（ticks）
  delay_min_ticks_final_circle: 200 # 最后一圈最小延迟
  delay_max_ticks_final_circle: 600 # 最后一圈最大延迟

# 数据库设置
database:
  type: SQLITE                    # SQLITE 或 MYSQL
  sqlite:
    file: 'plugins/RandomItemPVP/data.db'
```

### 模块化配置文件

每个模块化配置文件负责特定功能区域的详细配置：

- **arena.yml**：竞技场半径、大厅位置、自动开始设置
- **border.yml**：边界伤害、最小尺寸、缩圈参数
- **items.yml**：物品权重、掉落概率、黑名单
- **events.yml**：随机事件类型、触发概率、时间间隔
- **database.yml**：数据库连接参数、连接池配置
- **maps.yml**：地图列表、世界 key、出生点配置
- **allies.yml**：盟友生物类型、生成规则、行为设置

### 配置热加载

修改配置后，使用以下指令重载配置：

```bash
/ripvp reload
```

> **注意**：部分配置（如物品权重）需要在下一局游戏开始时才会生效。

---

## 使用指南

### 基本操作

#### 发起游戏

任何玩家都可以发起游戏：

```bash
/ripvp start
```

发起者会自动加入游戏并被传送到游戏出生点，其他玩家有30秒时间加入。

#### 加入游戏

在30秒准备倒计时期间，其他玩家可以主动加入：

```bash
/ripvp join
```

#### 退出游戏

在游戏开始前，玩家可以随时退出：

```bash
/ripvp leave
```

#### 查看游戏状态

```bash
/ripvp status
```

#### 查看统计数据

```bash
/ripvp stats              # 查看自己的统计
/ripvp stats PlayerName   # 查看其他玩家统计
```

#### 查看排行榜

```bash
/ripvp top           # 胜利排行榜
/ripvp top wins      # 同上
/ripvp top kills     # 击杀排行榜
/ripvp top kd        # KD比率排行榜（需至少10场）
```

### 完整游戏流程

```
┌─────────────────────────────────────────────────────────────┐
│  游戏流程                                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐                                           │
│  │ 1. 发起游戏   │ /ripvp start                              │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐                                           │
│  │ 2. 准备阶段   │ 30秒倒计时，玩家可加入/退出                 │
│  │   (30秒)     │ 屏幕显示大标题倒计时                        │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐                                           │
│  │ 3. 开局阶段   │ 玩家传送到高空基岩柱                        │
│  │              │ 缓降效果，自动分发物品                       │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐                                           │
│  │ 4. 战斗阶段   │ 每5秒获得随机物品                          │
│  │   (3-5分钟)   │ 边界每30秒缩小                            │
│  │              │ 每60秒空投补给                             │
│  │              │ 每2分钟随机事件                            │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐                                           │
│  │ 5. 决胜阶段   │ 边界接近最小值                             │
│  │              │ 事件频率提升                               │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐                                           │
│  │ 6. 游戏结束   │ 最后存活者获胜                             │
│  │              │ 清理所有游戏状态                            │
│  │              │ 玩家传送回原位置                            │
│  └──────────────┘                                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 胜利条件

大逃杀模式的胜利条件简单明了：成为最后存活的玩家。当场上只剩下一名玩家时，该玩家立即获胜，游戏结束。获胜者将获得胜利次数的统计记录，并可在排行榜上展示自己的实力。

### 特殊物品使用

游戏中有三种特殊物品供玩家使用。TNT需要右键投掷，物品会以抛物线飞向准心指向的位置，2.5秒后爆炸并造成区域破坏。火焰弹同样需要右键投掷，但冷却时间更短（2秒），只产生点燃效果不造成爆炸伤害。末影水晶可以放置在任何固体方块上，被其他玩家攻击后会产生强力爆炸，是区域控制和战术干扰的利器。

---

## 指令与权限

### 玩家指令

| 指令 | 描述 | 所需权限 |
|------|------|----------|
| `/ripvp start` | 发起一场新游戏 | 无（任何玩家可用） |
| `/ripvp join` | 加入当前等待中的游戏 | 无（任何玩家可用） |
| `/ripvp leave` | 退出当前参与的游戏 | 无（任何玩家可用） |
| `/ripvp status` | 查看当前游戏状态 | 无（任何玩家可用） |
| `/ripvp stats` | 查看个人统计数据 | 无（任何玩家可用） |
| `/ripvp stats <玩家名>` | 查看其他玩家统计数据 | 无（任何玩家可用） |
| `/ripvp top` | 查看排行榜 | 无（任何玩家可用） |
| `/ripvp top wins` | 查看胜利排行榜 | 无（任何玩家可用） |
| `/ripvp top kills` | 查看击杀排行榜 | 无（任何玩家可用） |
| `/ripvp top kd` | 查看KD比率排行榜 | 无（任何玩家可用） |
| `/mlg` | 执行 MLG 动作 | 无（任何玩家可用） |

### 管理员指令

| 指令 | 描述 | 所需权限 |
|------|------|----------|
| `/ripvp setspawn` | 设置游戏出生点 | `randomitempvp.admin` |
| `/ripvp reload` | 重载所有配置文件 | `randomitempvp.admin` |
| `/ripvp forceend` | 强制结束当前游戏 | `randomitempvp.admin` |
| `/ripvp forcestart` | 强制开始当前游戏 | `randomitempvp.admin` |
| `/ripvp resetstats <玩家名>` | 重置指定玩家统计数据 | `randomitempvp.admin` |
| `/ripvp resetallstats` | 重置所有玩家统计数据 | `randomitempvp.admin` |
| `/ripvp create <房间名>` | 创建新游戏房间 | `randomitempvp.admin` |
| `/ripvp delete <房间名>` | 删除指定游戏房间 | `randomitempvp.admin` |
| `/ripvp list` | 列出所有游戏房间 | `randomitempvp.admin` |
| `/ripvptp <房间名>` | 传送到指定房间 | `randomitempvp.admin` |

### 权限节点

| 权限节点 | 描述 | 默认值 |
|----------|------|--------|
| `randomitempvp.join` | 允许加入游戏 | OP |
| `randomitempvp.start` | 允许发起游戏 | OP |
| `randomitempvp.leave` | 允许退出游戏 | OP |
| `randomitempvp.stats` | 允许查看统计数据 | true |
| `randomitempvp.top` | 允许查看排行榜 | true |
| `randomitempvp.mlg` | 允许使用 MLG 指令 | true |
| `randomitempvp.admin` | 管理员完整权限 | OP |

---

## PlaceholderAPI 集成

### 安装 PlaceholderAPI

1. 下载 PlaceholderAPI 插件（[Spigot 页面](https://www.spigotmc.org/resources/6245/)）

2. 将插件放入服务器的 `plugins` 目录

3. 重启服务器

4. 安装 RandomItemPVP 扩展（通常会自动安装，或手动安装 RandomItemPVPExpansion）

### 可用变量

RandomItemPVP 提供以下 PlaceholderAPI 变量，用于记分板、TAB 列表等显示：

| 变量 | 描述 | 示例输出 |
|------|------|----------|
| `%randomitempvp_status` | 当前游戏状态 | waiting、running、ended |
| `%randomitempvp_players_alive` | 存活玩家数量 | 5 |
| `%randomitempvp_players_total` | 总参与玩家数量 | 8 |
| `%randomitempvp_current_kills` | 当前击杀数 | 3 |
| `%randomitempvp_current_deaths` | 当前死亡数 | 1 |
| `%randomitempvp_wins` | 历史胜利次数 | 15 |
| `%randomitempvp_kills` | 历史总击杀数 | 42 |
| `%randomitempvp_deaths` | 历史总死亡数 | 28 |
| `%randomitempvp_games` | 历史游戏场次 | 50 |
| `%randomitempvp_kd` | 历史KD比率 | 1.50 |
| `%randomitempvp_winrate` | 历史胜率 | 30% |
| `%randomitempvp_top_wins_1` | 胜利榜第1名 | Player123 |
| `%randomitempvp_top_kills_1` | 击杀榜第1名 | Player456 |
| `%randomitempvp_top_kd_1` | KD榜第1名 | Player789 |

### 使用示例

#### 记分板示例

在 scoreboard 插件的配置中可以这样使用：

```yaml
# Scoreboard Legacy 或 SuperiorSkyblock 等插件配置
lines:
  - '&7'
  - '&6生存玩家: &f%randomitempvp_players_alive%'
  - '&6总玩家数: &f%randomitempvp_players_total%'
  - '&7'
  - '&6你的击杀: &f%randomitempvp_current_kills%'
  - '&7'
  - '&8统计数据'
  - '&7胜利: &f%randomitempvp_wins%'
  - '&7击杀: &f%randomitempvp_kills%'
  - '&7KD: &f%randomitempvp_kd%'
```

#### TAB 列表示例

在 TAB 插件的配置中：

```yaml
# TAB 插件配置
header: |
  &6&lRandomItemPVP
  &7存活: &f%randomitempvp_players_alive% &7/ &f%randomitempvp_players_total%
footer: |
  &7你的击杀: &f%randomitempvp_current_kills% &7| &7KD: &f%randomitempvp_kd%
```

---

## 多房间系统

### 概述

v3.0.0 版本引入了完整的多房间支持，允许服务器同时运行多个独立的游戏房间。每个房间拥有自己的配置、地图、游戏状态和世界实例，互不干扰。这使得服务器可以同时接待不同水平的玩家群体，或举办不同规则的比赛。

### 世界实例化

多房间系统的核心是 Worlds 插件的世界实例化功能。每个游戏房间在开始时都会创建一个独立的世界副本，确保游戏的地图状态在每局之间完美重置，不会因为玩家的破坏而影响下一局游戏。

在配置文件中启用世界实例化后，系统会自动管理世界实例的创建和清理。游戏结束后，未使用的世界实例会被自动删除，释放服务器资源。管理员也可以手动管理世界实例，通过 Worlds 插件的指令进行操作。

### 配置多房间

每个房间的详细配置存储在 `config-modules/rooms/` 目录下的独立文件中。房间配置文件采用以下结构：

```yaml
room:
  id: "arena1"                    # 房间唯一标识符
  name: "主竞技场"                 # 房间显示名称
  world-template: "ripvp_template" # 世界模板（Worlds key）
  
  # 游戏参数
  min-players: 4                  # 最少玩家数
  max-players: 20                 # 最多玩家数
  start-countdown: 30             # 开始倒计时（秒）
  
  # 边界设置
  border:
    initial-radius: 48            # 初始半径
    min-radius: 10                # 最小半径
    shrink-interval: 30           # 缩圈间隔（秒）
    
  # 大厅位置
  lobby:
    world: "lobby"                # 大厅世界
    x: 0.0
    y: 100.0
    z: 0.0
```

### 房间管理指令

| 指令 | 描述 |
|------|------|
| `/ripvp create <房间名>` | 创建新房间 |
| `/ripvp delete <房间名>` | 删除指定房间 |
| `/ripvp list` | 列出所有房间及状态 |
| `/ripvp join <房间名>` | 加入指定房间 |
| `/ripvptp <房间名>` | 传送到指定房间 |
| `/ripvp setspawn <房间名>` | 设置指定房间的游戏出生点 |
| `/ripvp reload <房间名>` | 重载指定房间配置 |

---

## 数据库配置

### SQLite（默认）

SQLite 是默认的数据库选择，适合小型服务器或个人服务器使用。它不需要额外的数据库服务器进程，数据存储在本地文件中，零配置即可使用。

```yaml
database:
  type: SQLITE
  sqlite:
    file: 'plugins/RandomItemPVP/data.db'
```

SQLite 数据库文件会在插件首次运行时自动创建，位于 `plugins/RandomItemPVP/data.db`。这种方式简单方便，数据随插件备份自动保存，无需额外维护。

### MySQL（推荐大型服务器）

对于大型服务器或有跨服数据共享需求的场景，强烈建议使用 MySQL。MySQL 提供更好的性能和数据持久性，支持多服务器实例共享玩家数据。

```yaml
database:
  type: MYSQL
  mysql:
    host: "localhost"
    port: 3306
    database: "randomitempvp"
    username: "root"
    password: "your_password"
    # 连接池配置
    pool:
      minimum-idle: 5             # 最小空闲连接
      maximum-pool-size: 20       # 最大连接数
      connection-timeout: 30000   # 连接超时（毫秒）
      idle-timeout: 600000        # 空闲超时（毫秒）
      max-lifetime: 1800000       # 最大生命周期（毫秒）
```

### 数据库初始化

使用 MySQL 时，需要先创建数据库和表结构：

```sql
-- 创建数据库
CREATE DATABASE randomitempvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE randomitempvp;

-- 创建玩家统计表
CREATE TABLE IF NOT EXISTS player_stats (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    wins INT DEFAULT 0,
    losses INT DEFAULT 0,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    games_played INT DEFAULT 0,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建击杀记录表
CREATE TABLE IF NOT EXISTS kill_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    killer_uuid VARCHAR(36) NOT NULL,
    killer_name VARCHAR(16) NOT NULL,
    victim_uuid VARCHAR(36) NOT NULL,
    victim_name VARCHAR(16) NOT NULL,
    game_id VARCHAR(36) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建游戏记录表
CREATE TABLE IF NOT EXISTS game_records (
    game_id VARCHAR(36) PRIMARY KEY,
    room_id VARCHAR(36) NOT NULL,
    winner_uuid VARCHAR(36),
    winner_name VARCHAR(16),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    player_count INT
);
```

### 数据迁移

如需从 SQLite 迁移到 MySQL，可以使用以下步骤：

1. 停止服务器

2. 备份现有的 `data.db` 文件

3. 修改配置文件切换到 MySQL

4. 启动服务器（首次启动会创建新表结构）

5. 使用数据库管理工具（如 Navicat、MySQL Workbench）将 SQLite 数据导出后导入 MySQL

---

## 构建指南

### 环境准备

在开始构建之前，请确保系统已安装以下软件：

- **Java Development Kit 21** 或更高版本
- **Apache Maven 3.8+**

可以通过以下命令检查环境：

```bash
java -version
mvn -version
```

### Maven 构建

1. 克隆项目仓库：

```bash
git clone https://github.com/Narcssu/RandomItemPVP.git
cd RandomItemPVP
```

2. 清理并构建项目：

```bash
mvn clean package
```

3. 构建完成后，构建产物位于 `target/` 目录：

```
target/
├── RandomItemPVP-3.0.0.jar        # 带依赖的完整 JAR
└── original-RandomItemPVP-3.0.0.jar  # 不带依赖的 JAR
```

### Gradle 构建

项目也支持使用 Gradle 进行构建：

```bash
# 使用 Gradle Wrapper
./gradlew build

# 或使用系统 Gradle
gradle build
```

构建产物位于 `build/libs/` 目录。

### 自定义构建

如需修改版本号，可以编辑 `pom.xml` 文件：

```xml
<version>3.0.0</version>
```

修改后重新构建即可生成对应版本号的 JAR 文件。

### 跳过测试构建

如需快速构建（跳过测试）：

```bash
mvn clean package -DskipTests
```

---

## 常见问题

### 插件无法启动

**问题表现**：服务器启动时提示找不到依赖或类加载错误。

**解决方案**：

1. 确认服务器核心版本是否为 Paper 1.21+ 或 Folia 1.21+

2. 确认 Java 版本是否为 21 或更高

3. 检查是否正确安装了 Worlds 插件

4. 查看控制台错误信息，根据提示修复问题

### 数据库连接失败

**问题表现**：使用 MySQL 时提示连接被拒绝或认证失败。

**解决方案**：

1. 确认 MySQL 服务器正在运行

2. 检查配置文件中的主机地址、端口、数据库名、用户名和密码是否正确

3. 确认 MySQL 用户具有远程访问权限（如使用远程数据库）

4. 检查防火墙是否允许 MySQL 端口访问

5. 尝试使用telnet或nc测试数据库连通性

### 玩家无法加入游戏

**问题表现**：玩家尝试加入时提示无法加入或没有等待中的游戏。

**解决方案**：

1. 确认是否有人发起了游戏（使用 `/ripvp status` 查看）

2. 确认游戏房间是否已满（达到最大玩家数）

3. 确认玩家是否在黑名单中

4. 检查玩家是否被禁止参加游戏（权限问题）

### 游戏平衡性调整

**问题表现**：游戏节奏过快或过慢，物品掉落不均衡。

**解决方案**：

调整以下配置参数以适应服务器需求：

- `items.interval_ticks`：调整物品发放间隔（增大间隔降低频率）

- `border.shrink_interval_ticks`：调整缩圈速度（增大间隔减慢缩圈）

- `events.delay_max_ticks`：调整随机事件触发频率

- `items.yml`：调整物品权重以控制掉落概率

### PlaceholderAPI 变量不生效

**问题表现**：在记分板或 TAB 中使用的变量显示为原样文本。

**解决方案**：

1. 确认 PlaceholderAPI 插件已正确安装

2. 确认 RandomItemPVP 扩展已安装（使用 `/papi ecloud download RandomItemPVP`）

3. 重载 PlaceholderAPI（`/papi reload`）

4. 检查变量名称拼写是否正确

### 世界实例化失败

**问题表现**：游戏无法创建世界实例，或玩家卡在加载界面。

**解决方案**：

1. 确认 Worlds 插件已正确安装和配置

2. 确认世界模板（Worlds key）存在且配置正确

3. 检查服务器是否有足够的磁盘空间

4. 查看控制台错误信息排查问题

---

## 贡献指南

### 提交问题

如果您发现 bug 或有功能建议，请通过 GitHub Issues 提交。在提交前，请先搜索是否有类似问题已存在。

提交问题时，请包含以下信息：

- 使用的服务器核心版本（Paper 或 Folia）

- Minecraft 游戏版本

- RandomItemPVP 插件版本

- 详细的错误描述或功能需求说明

- 复现步骤（如有）

- 控制台错误日志（如有）

### 提交代码

1. **Fork** 本项目到您的 GitHub 账户

2. 创建您的特性分支：

```bash
git checkout -b feature/AmazingNewFeature
```

3. 提交您的更改：

```bash
git commit -m 'Add some AmazingNewFeature'
```

4. 推送您的分支到 GitHub：

```bash
git push origin feature/AmazingNewFeature
```

5. 创建一个 **Pull Request**，描述您的更改

### 代码规范

- 遵循 Java 代码规范，使用有意义的变量和方法名

- 添加适当的注释，特别是复杂的逻辑

- 确保代码通过现有的单元测试

- 新功能请提供对应的测试用例

---

## 许可证

本项目采用 MIT 许可证开源。

```
MIT License

Copyright (c) 2024 RandomItemPVP Contributors

特此免费授予任何获得本软件副本和相关文档文件（下称"软件"）的人，
不受限制地使用本软件，包括但不限于使用、复制、修改、合并、发布、
分发、再许可和/或销售本软件的副本，并允许获得本软件的人这样做，
但须符合以下条件：

上述版权声明和本许可声明应包含在软件的所有副本或重要部分中。

本软件按"原样"提供，不作任何明示或暗示的保证，包括但不限于对
适销性、特定用途适用性和非侵权性的保证。在任何情况下，作者或
版权持有人均不对因本软件或使用本软件而产生的任何索赔、损害或其
他责任负责，无论是在合同诉讼、侵权诉讼或其他诉讼中。
```

完整许可证文本请参见 [LICENSE](LICENSE) 文件。

---

## 联系方式

| 渠道 | 链接 |
|------|------|
| **GitHub 主页** | https://github.com/Narcssu/RandomItemPVP |
| **Issues 反馈** | https://github.com/Narcssu/RandomItemPVP/issues |
| **Releases 下载** | https://github.com/Narcssu/RandomItemPVP/releases |
| **Modrinth 页面** | https://modrinth.com/plugin/randomitempvp |

---

<div align="center">

**感谢您选择 RandomItemPVP！**

*如果这个项目对您有帮助，请考虑给个 ⭐ Star 支持一下*

</div>
