# 双加载器结构（NeoForge / Fabric）

两条线是**两个独立的 Gradle 工程**，不是多模块构建。改一处不会自动影响另一处。

## 1. 现状

| | NeoForge（主线） | Fabric（旧线） |
|---|---|---|
| MC / Java | 1.21.1 / 21 | 1.20.1 / 17 |
| 加载器 | NeoForge 21.1.211 | Fabric Loader 0.15.7 + Fabric API |
| Mod 版本 | 1.7.0 | 1.5.4 |
| 共享源码 | `NeoForge/src/platform-shared/` | 引用 `../Forge/src/platform-shared/`（该目录现在叫 `NeoForge`，所以**构不成**） |
| 配置 | NeoForge `ModConfigSpec` | Forge Config API Port |
| Create | 6.0.7（原生） | 0.5.1-f（Create Fabric） |
| 渲染 | Flywheel | Flywheel-Fabric |

**Fabric 当前无法构建**：`Fabric/build.gradle` 的 `sourceSets` 指向 `../Forge/src/platform-shared/java` 与 `.../resources`，这两个路径不存在。Fabric 线也没有默认内容、手持钻与矿簇——那些只存在于主线。

## 2. 共享目录的定位

仓库结构是 `Forge/` + `Fabric/` 两个工程，两边都从 `Forge/src/platform-shared/` 取共享代码；后来 `Forge/` 升级到 MC 1.21 并改名为 `NeoForge/`，共享代码随之改用 NeoForge API，Fabric 线没有跟着更新。

所以 `platform-shared` 现在是**主线内部的共享层**，不是「跨加载器可移植层」：里面混着 NeoForge 专属代码，最明显的是 `Config.java`（直接用 `net.neoforged.neoforge.common.ModConfigSpec`）。另外两个用 NeoForge 流体 API 的文件（`recipe/ExtractorRecipe.java`、`util/FluidIngredient.java`）放在 `main` 侧。

## 3. 源码归属速查（NeoForge）

`src/main/java`（NeoForge 专属）：

```
CreateOreExcavation.java   # @Mod 入口、DeferredRegister、attachment、data component、能力
ForgeConfig.java           # 配置加载/重载事件
COECommand.java
OreDataAttachment.java     # 区块 attachment
client/CCClientInit.java, ClientEventHandler.java, COEClient.java, PlatformClient.java
data/COERecipes.java, DataGenerators.java
jei/JeiPlatform.java
network/NetworkHandler.java
recipe/ExtractorRecipe.java
util/FluidIngredient.java, IOBlockType.java, PlatformMenuProvider.java, QueueInventory.java
block/entity/{DrillBlockEntity, ExcavatingBlockEntityImpl, ExtractorBlockEntity, IOBlockEntity, MultiblockCapHandler}.java
```

`src/platform-shared/java`：其余全部，含 `Registration`、`Config`、`OreData`、`OreVeinGenerator`、`VeinRegeneration`、方块、共享方块实体、配方、菜单、网络包、KubeJS/JEI/Xaero 插件、工具类。

> `block/entity` 的划分并不整齐：`ExcavatingBlockEntity`、`MultiblockBlockEntity`、`SampleDrillBlockEntity` 在共享侧，而 `DrillBlockEntity`、`ExtractorBlockEntity` 在 main 侧。加/改方块实体前先确认它在哪。

## 4. 差异对照（跨界改动时最容易踩）

- **数据目录**：1.21 用单数 `recipe/`、`loot_table/`、`advancement/`、`tags/`；1.20 用复数。
- **注册**：NeoForge 用 `DeferredRegister` + `Registrate`；Fabric 侧直接 `Registry.register`。
- **能力/传输**：NeoForge 用 `RegisterCapabilitiesEvent`（`Capabilities.ItemHandler/FluidHandler/EnergyStorage`）；Fabric 用 Porting Lib 的对应实现。
- **配置**：NeoForge `ModConfig` + `ModConfigSpec`；Fabric 经 Forge Config API Port。
- **网络**：NeoForge `RegisterPayloadHandlersEvent` + `CustomPacketPayload`；Fabric 自定义 `Packets` 通道。
- **序列化**：主线用 Mojang `Codec` / `StreamCodec`；Fabric 1.20 侧部分仍是旧的手写 `FriendlyByteBuf`。

## 5. 加一个功能时怎么做

1. 先判断能否放 `platform-shared`（加载器无关）。
2. 加载器专属部分各写一份：NeoForge 进 `NeoForge/src/main/java`，Fabric 进 `Fabric/src/main/java`。
3. 跨加载器共享的注册 ID / 资源路径 / 语言键保持**完全一致**。
4. 各自跑数据生成（`runData` / `runDatagen`）并分别验证。

## 6. 若要修复 Fabric 线（未执行）

1. 把 `Fabric/build.gradle` 的两处 `../Forge/src/platform-shared/...` 指向 `../NeoForge/src/platform-shared/...`。
2. 处理 API 差异：把 `platform-shared` 里的 NeoForge 依赖（`Config` 等）抽到加载器侧，或为 Fabric 另建等价实现。
3. 对齐 1.20 与 1.21 的数据目录名与配方 JSON 格式。
4. 移植主线这轮之后新增的内容（默认内容包、手持钻、矿簇、`biome_tag`、`waypointColor` 等）。
