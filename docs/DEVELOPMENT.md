# 开发说明

玩家向的安装、局域网用法和键位见 [README](../README.md)。发布的 jar 在 [Releases](https://github.com/Spencer320/lansharedmap/releases)。

## 版本与工具链

按 2026-10-11 能拿到的官方工具链核对过：

| 组件 | 版本 |
| --- | --- |
| Minecraft | **26.3** |
| Fabric Loader | **0.19.5** |
| Fabric API | **0.162.0+26.3** |
| Java | **25** |
| Fabric Loom | **1.18-SNAPSHOT**（官方 `fabric-example-mod` 的 `26.3` 分支；[Fabric 26.3 公告](https://fabricmc.net/2026/09/15/263.html) 里写的是 Loom 1.17 / Gradle 9.6.0，示例工程已经升到 Loom 1.18 与 Gradle 9.7.1） |
| Gradle | **9.7.1** |

26.1 起游戏本体不再混淆，所以这里用 Mojang 名，**没有 Yarn**。Loom 插件 id 是 `net.fabricmc.fabric-loom`（不要用旧的 `fabric-loom` / `fabric-loom-remap`）。

版本号写在 `gradle.properties` 的 `version`。`fabric.mod.json` 里的 `version` 在 `processResources` 时展开。

## 构建

需要 JDK 25。

```shell
./gradlew build
```

产物：

- `build/libs/lansharedmap-1.1.0.jar` — 放进 `mods/` 的文件
- `build/libs/lansharedmap-1.1.0-sources.jar` — 源码包

开发环境进游戏：

```shell
./gradlew runClient
```

## 存档

每个维度一份 Saved Data，id 为 `lansharedmap:explored`。26.3 把它写成：

`<维度数据目录>/lansharedmap/explored.dat`

主世界一般是存档里的 `data/lansharedmap/explored.dat`。下界、末地在各自维度目录的 `data/lansharedmap/explored.dat`。跟随世界自动保存，不需要额外导出。

## 路点文件

路点是可选的，只存在**本机**，不会同步给其他玩家。文件在：

`config/lansharedmap/<世界名与种子的哈希>.json`

世界 id 由服务器用「世界名 + 种子」发给客户端，用来区分不同存档。

## 包

| 方向 | id | 作用 |
| --- | --- | --- |
| S2C | `lansharedmap:map_hello` | 进服或重新要快照时清空客户端缓存，并带上世界 id |
| S2C | `lansharedmap:map_chunks` | 快照分批，或探索增量。每批最多 20 个区块 |
| S2C | `lansharedmap:player_positions` | 每 10 tick 刷新在线玩家位置 |
| C2S | `lansharedmap:request_snapshot` | 打开地图时如果还没收到 hello，再要一次快照 |

探索数据由服务器根据玩家位置写入，客户端不能自行上报区块。原版客户端会被跳过，不会收到这些包。

## 代码结构

- `dev.lansharedmap.network` — 自定义 payload 与注册
- `dev.lansharedmap.map` — 服务器探索记录、地表采样、`SavedData`
- `dev.lansharedmap.client` — 键位、客户端缓存、路点文件
- `dev.lansharedmap.client.screen` — 全屏地图

源码集按 Fabric Loom 的 `splitEnvironmentSourceSets()` 分开：`src/main` 是通用与服务器逻辑，`src/client` 只在客户端。

## 实现限制

- 采样 `WORLD_SURFACE` 上每一格的颜色：草方块用生物群系草颜色，树叶用树叶颜色，植物在树叶色和方块地图色之间混合，水用生物群系水色并按水深变暗，其余方块用 `MapColor`。明暗看北侧邻列的高度差，用来读出坡面。没有洞穴层，没有实体雷达，没有小地图 HUD。
- 颜色算法版本记在存档里（`v`）。玩家走到已经点亮、但还是旧颜色的区块旁边时，服务器会按当前算法重采样并同步，每名玩家每 tick 仍最多 6 个区块。没人再走近的旧区块会保持原样。
- 区块重采样之后，颜色再次固定。之后挖掉或铺上的方块不会继续刷新。
- 旁观者模式不会点亮地图。创造、生存会。
- 只点亮玩家周围约 7×7 个已加载区块（Chebyshev 距离 3），不是整个视距。想看更远的地方需要走过去。
- 每个玩家每 tick 最多新采样 6 个区块，避免一下子扫太多列。
- 不做跨服账号同步。路点不同步。
- 独立服务器和集成服务器都要装这个模组。
