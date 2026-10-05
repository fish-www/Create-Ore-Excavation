# 双加载器结构（NeoForge / Fabric）

两条线是**两个独立的 Gradle 工程**，不是多模块构建。改一处不会自动影响另一处。

## 1. 现状

| | NeoForge（主线） | Fabric（旧线） |
|---|---|---|
| MC / Java | 1.21.1 / 21 | 1.20.1 / 17 |
| 加载器 | NeoForge 21.1.211 | Fabric Loader 0.15.7 + Fabric API |
| Mod 版本 | 1.6.8 | 1.5.4 |
| 共享源码 | `NeoForge/src/platform-shared/` | 期望 `../Forge/src/platform-shared/`（**已不存在**） |
| 配置 | NeoForge `ModConfigSpec` | Forge Config API Port |
| Create | 6.0.7（原生） | 0.5.1-f（Create Fabric） |
| 渲染 | Flywheel | Flywheel-Fabric |

**Fabric 当前无法构建**：`Fabric/build.gradle` 的 `sourceSets` 仍引用 `../Forge/src/platform-shared/java` 与 `.../resources`。该目录在 2025-03 的提交 `Rename folder` 里被重命名为 `NeoForge`。

## 2. 共享目录的由来

历史上仓库结构是 `Forge/` + `Fabric/`，两个工程都从 `Forge/src/platform-shared/` 取共享代码：

- Forge 侧用原生 Forge API；
- Fabric 侧通过 **Porting Lib + Forge Config API Port** 复用同一批「Forge 风味」的 API（所以 Fabric 的 `CreateOreExcavation` 里还能看到 `net.minecraftforge.fml.config.ModConfig`）。

后来 `Forge/` 被升级并改名为 `NeoForge/`（MC 1.21），共享代码随之改用 NeoForge API。Fabric 线的路径没跟着更新，于是断掉。

## 3. 为什么不能只改路径

把 `Fabric/build.gradle` 的 `../Forge/...` 改成 `../NeoForge/src/platform-shared` **并不够**：`platform-shared` 里混着 NeoForge 专属代码，最明显的是 `Config.java` 直接用 `net.neoforged.neoforge.common.ModConfigSpec`。同时还有两个用 NeoForge 流体 API 的文件（`recipe/ExtractorRecipe.java`、`util/FluidIngredient.java`）已经从共享目录移到了 `main`，Fabric 侧拿不到。

所以 `platform-shared` 目前是「主线内部的共享层」，不是「跨加载器可移植层」。要真正恢复 Fabric 线，需要把那批 NeoForge 依赖重新抽象（或从旧的 1.20 分支取回对应实现）。

## 4. 源码归属速查（NeoForge）

放在 `src/main/java`（NeoForge 专属）：

```
CreateOreExcavation.java   # @Mod 入口、DeferredRegister、attachment、data component、能力
ForgeConfig.java           # 配置加载/重载事件
COECommand.java
OreDataAttachment.java     # 区块 attachment
client/CCClientInit.java, ClientEventHandler.java, COEClient.java, PlatformClient.java
data/COERecipes.java, DataGenerators.java
jei/JeiPlatform.java
jm/JMEventListener.java
network/NetworkHandler.java
recipe/ExtractorRecipe.java
util/FluidIngredient.java, IOBlockType.java, PlatformMenuProvider.java, QueueInventory.java
block/entity/{DrillBlockEntity, ExcavatingBlockEntityImpl, ExtractorBlockEntity, IOBlockEntity, MultiblockCapHandler}.java
```

放在 `src/platform-shared/java`（其余全部，含 `Registration`、`Config`、`OreData`、`OreVeinGenerator`、方块、共享方块实体、配方、菜单、网络包、KubeJS/JEI/JM 插件、工具类）。

> 注意 `block/entity` 的划分并不整齐：`ExcavatingBlockEntity`、`MultiblockBlockEntity`、`SampleDrillBlockEntity` 在共享侧，而 `DrillBlockEntity`、`ExtractorBlockEntity` 在 main 侧。加/改方块实体前先确认它在哪。

## 5. 差异对照（跨界改动时最容易踩）

- **数据目录**：1.21 用单数 `recipe/`、`loot_table/`、`advancement/`；1.20 用复数 `recipes/`、`loot_tables/`、`advancements/`。
- **注册**：NeoForge 用 `DeferredRegister` + `Registrate`；Fabric 侧直接 `Registry.register`。
- **能力/传输**：NeoForge 用 `RegisterCapabilitiesEvent`（`Capabilities.ItemHandler/FluidHandler/EnergyStorage`）；Fabric 用 Porting Lib 的对应实现。
- **配置**：NeoForge `ModConfig` + `ModConfigSpec`；Fabric 经 Forge Config API Port。
- **网络**：NeoForge `RegisterPayloadHandlersEvent` + `CustomPacketPayload`；Fabric 自定义 `Packets` 通道。
- **序列化**：主线用 Mojang `Codec` / `StreamCodec`；Fabric 1.20 侧更早，部分用旧的 `FriendlyByteBuf` 手写。

## 6. 加一个功能时怎么做

1. 先判断能否放 `platform-shared`（加载器无关）。
2. 加载器专属部分各写一份：NeoForge 进 `NeoForge/src/main/java`，Fabric 进 `Fabric/src/main/java`。
3. 跨加载器共享的注册 ID / 资源路径 / 语言键保持**完全一致**。
4. 各自跑数据生成（`runData` / `runDatagen`）并分别验证。

## 7. 若要修复 Fabric 线（未执行）

1. 把 `Fabric/build.gradle` 的两处 `../Forge/src/platform-shared/...` 指向正确目录。
2. 处理 API 差异：把 `platform-shared` 里的 NeoForge 依赖（`Config` 等）抽到加载器侧，或为 Fabric 另建等价实现。
3. 对齐 1.20 与 1.21 的数据目录名与配方 JSON 格式。

以上属于独立工作项，本次文档未改动任何构建文件。
