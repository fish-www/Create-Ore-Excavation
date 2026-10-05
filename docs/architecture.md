# 架构

面向 NeoForge / MC 1.21.1 主线。所有路径相对 `NeoForge/`。

## 1. 依赖与运行环境

- Java 21，NeoForge 21.1.211，Minecraft 1.21.1（`gradle.properties`）。
- **Create 6.0.7**：机器靠 Create 的旋转动力（kinetic）工作，界面上走 Create 的 `SmartBlockEntity` / Ponder / goggle tooltip。
- **Registrate**：注册与数据生成（方块、物品、方块实体、菜单、模型、语言）。
- JEI（编译+运行）、KubeJS、Architectury、JourneyMap、CC:Tweaked 为可选集成。

## 2. 源码集划分

NeoForge 工程把两个目录都编进主源集（`build.gradle` 的 `sourceSets.main`）：

- `src/main/java` — 依赖 NeoForge API 的代码：入口、配置事件、数据生成、网络注册、区块 attachment、能力注册、`ExtractorRecipe`/`FluidIngredient`（用 NeoForge 流体 API）。
- `src/platform-shared/java` — 其余大部分游戏逻辑：`Registration`、`Config`、`OreData`、`OreVeinGenerator`、方块与方块实体、`DrillingRecipe`/`VeinRecipe`/`ExcavatingRecipe`、JEI/KubeJS/JourneyMap 插件、菜单与界面、网络包、工具类。

`src/platform-shared/resources` 放静态资源（贴图、`pack.mcmeta`、`kubejs.plugins.txt`、各语种 lang）；`src/generated/resources` 是数据生成产物。

分工背后的意图：共享目录里的逻辑尽量与加载器无关，方便旧 Fabric 线复用。历史上 Fabric 线通过 `../Forge/src/platform-shared` 直接引用它。但共享目录里仍混有 NeoForge 专属代码（如 `Config`）。详见 `porting.md`。

## 3. 注册系统

主线入口 `src/main/java/com/tom/createores/CreateOreExcavation.java`（`@Mod`）：

- 建立 `CreateRegistrate`（`CreateOreExcavation.registrate()`）。
- 注册三种配方序列化器/类型（用 `DeferredRegister`）：`drilling`、`extracting`、`vein`，包成 `RecipeTypeGroup<T>`。
- 物品标签 `createoreexcavation:drills`。
- 区块 attachment：`ore_vein` → `OreDataAttachment`。
- 数据组件：`ore_vein_altas_data`（矿脉图鉴内容，注意历史上拼成 *altas*）、`ore_vein_finder_filtered`。
- 注册 `Config` 的 COMMON/SERVER 规格、`NetworkHandler`，调用 `Registration.register()`。
- 注册 IO 方块实体的能力（物品/流体/能量），注册命令事件与 `TagsUpdatedEvent`（失效矿脉缓存）。

具体内容定义在 `src/platform-shared/java/com/tom/createores/Registration.java`（Registrate 静态字段）：

| 注册 ID | 类型 | 类 |
|---|---|---|
| `drilling_machine` | 方块 + `MultiBlockItem` | `DrillBlock` / `DrillBlockEntity` |
| `extractor` | 方块 + `MultiBlockItem` | `ExtractorBlock` / `ExtractorBlockEntity` |
| `sample_drill` | 方块 + 物品 | `SampleDrillBlock` / `SampleDrillBlockEntity` |
| `kinetic_input` | 方块（多方块动态输入） | `KineticInputBlock` / `KineticInputBlockEntity` |
| `io_block` | 方块（多方块物品/流体/能量口） | `IOBlock` / `IOBlockEntity` |
| `multiblock` | 占位方块（ghost） | `MultiblockBlock` / `MultiblockBlockEntity` |
| `drill` / `diamond_drill` / `netherite_drill` | 物品（`drills` 标签） | — |
| `raw_diamond` / `raw_emerald` / `raw_redstone` | 物品 | — |
| `vein_finder` / `vein_atlas` | 物品 | `OreVeinFinderItem` / `OreVeinAtlasItem` |
| `vein_atlas` | 菜单 | `OreVeinAtlasMenu` / `OreVeinAtlasScreen` |
| `create_ore_excavation` | 创造模式标签页 | — |

`Registration.register()` 末尾还会登记所有语言键，并在装了 ComputerCraft 时初始化 `CCRegistration`。

## 4. 配方系统

三种类型，全部走 Minecraft 的 `Recipe` / `RecipeSerializer` 机制（而非 Create 的 `ProcessingRecipe` 体系）：

### `createoreexcavation:vein` — 矿脉定义（`recipe/VeinRecipe.java`）

字段：`name`（文本组件）、`priority`、`biomeWhitelist`/`biomeBlacklist`（生物群系标签，可空）、`finite`（`ThreeState`：`never`/`default`/`always`）、`amountMultiplierMin/Max`、`placement`（`RandomSpreadStructurePlacement`：spacing/separation/salt）、`icon`（物品栈）。

它不参与合成，只描述「哪里、刷什么矿脉」。客户端侧 `isInfiniteClient`/`getMinAmountClient` 会结合配置 `defaultInfinite`、`finiteAmountBase` 换算展示。

### `createoreexcavation:drilling` — 钻机产出物品（`recipe/DrillingRecipe.java`）

在 `ExcavatingRecipe` 公共字段（`veinId`、`drill` 匹配、`priority`、`ticks`、`stress`、可选 `fluid`）之上增加 `output`：Create `ProcessingOutput` 列表（带概率）。

### `createoreexcavation:extracting` — 抽取器产出流体（`src/main/.../recipe/ExtractorRecipe.java`）

同公共字段，产出单个 `FluidStack`。因使用 NeoForge 流体 API，放在 `main` 侧。

公共基类 `recipe/ExcavatingRecipe.java` 定义两条轨迹一样的编解码：`codec()`（JSON/数据包）与 `streamCodec()`（网络同步）。

**配方怎么被选中**：机器所在区块先取矿脉（见下节），再把本机配方里 `veinId` 匹配的筛出来，按 `priority` 升序排；只有一个就用它，多个则取「第一个能通过 `drill.test(当前钻头)` 校验」的，仍无则取第一个。

## 5. 矿脉世界生成与区块数据

矿脉**不是方块**，而是每区块一份的持久化数据：

- `OreData`（`platform-shared/.../OreData.java`）保存：矿脉配方 ID、`loaded`、已开采量 `extractedAmount`、随机倍率 `randomMul`、在该矿脉上登记的抽取器位置集合。用 `OreData.Serialized` + `Codec` 做 NBT 序列化；`getResourcesRemaining()` 按 `round(((max-min)*randomMul+min) * finiteAmountBase)` 算有限矿脉总量；`canExtract()` 执行 `maxExtractorsPerVein` 限制。
- `OreDataAttachment`（`main/.../OreDataAttachment.java`）把它挂到 `LevelChunk` 的 `ore_vein` attachment 上；`getData(chunk)` 首次访问时惰性调用 `OreData.populate(chunk)`，客户端访问直接抛异常。
- `OreVeinGenerator`（`platform-shared/.../OreVeinGenerator.java`）持有 `AtomicReference<RandomSpreadGenerator>` 缓存；`TagsUpdatedEvent`（数据包/标签重载）时 `invalidate()`。
- `RandomSpreadGenerator`（`platform-shared/.../util/RandomSpreadGenerator.java`）：
  - `loadAll()` 读全部 `vein` 配方，按「负的生成优先级 + id」排序（优先级高者先）。
  - `pick(chunk)`：对每个配方用 `placement.getPotentialStructureChunk(seed, x, z)` 判断该区块是否为候选，再按种子+区块坐标采随机数取生物群系，通过 `canGenerate` 生物群系过滤后返回配方。
  - `locate(...)`：供 `/coe locate` 与寻脉器使用的螺旋搜索。

一句话：**矿脉位置完全由「世界种子 + 每个 vein 配方的 placement + 生物群系」决定**，可复现、可定位。

## 6. 机器运行流程

抽象基类 `platform-shared/.../block/entity/ExcavatingBlockEntity.java`（实现 `IDrill`、`MultiblockCapHandler`，继承 Create 的 `SmartBlockEntity`）：

- `updateRecipe()`：拿所在区块 `OreData` → 取矿脉 → 选配方（见第 4 节），同时把 `data`、`vein`、`current` 缓存下来。
- `tick()`（服务端）：若 `current != null` 且状态正常，检查四项——有多方块动力学输入、转速 ≥ `SpeedLevel.MEDIUM`、钻头匹配配方、机器下方（`worldPosition.below(2)`）为实心方块。满足则 `progress += 转速/基准`，达到 `ticks` 时 `onFinished()` + `data.extract(1)` + `completedOneCycle = true`。
- 状态机 `ExcavatorState`：`NO_VEIN` / `VEIN_EMPTY` / `NO_RECIPE` / `TOO_MANY_EXCAVATORS` / `NO_ERROR`，驱动 goggle tooltip 的错误提示。
- 钻头槽：交互手持 `#createoreexcavation:drills` 装入，空手取回；装图鉴（vein_atlas）可把矿脉信息写入图鉴（需先完成一个开采循环）。
- 客户端同步：`write/read` 在 `clientPacket=true` 时附带矿脉 ID、剩余资源、当前配方 ID、是否有转速、状态，避免客户端自己算。
- 子类：`DrillBlockEntity`（物品产出）、`ExtractorBlockEntity`（流体产出）、`SampleDrillBlockEntity`（采样）。

## 7. 多方块结构

自定义的多方块框架（不是 Create 的 contraption）：

- 主控方块继承 `MultiblockController`（`DrillBlock`/`ExtractorBlock`/`SampleDrillBlock`），实现 `MultiblockPart.MultiblockMainPart`。
- 部件：
  - `KineticInputBlock` — 动态旋转输入（ghost 方块）；
  - `IOBlock` — 对外暴露物品/流体/能量能力；
  - `MultiblockBlock` — 通用占位（ghost）。
- `MultiblockBlockEntity` 协调部件，`MultiblockCapHandler` 与 `IOBlockEntity` 提供能力转发；`MultiblockCapHandler.dropInv()` 在结构被破坏时掉落背包。

## 8. 资源传输

- `IOBlockEntity` 实现物品/流体/能量能力；在入口类里通过 `RegisterCapabilitiesEvent` 注册到 `IO_TILE`（按面查询）。
- `QueueInventory`（`util/`）是内部用于排队输入/输出的库存包装。

## 9. 采样与矿脉图鉴

- `SampleDrillBlockEntity`：需要顶部有 Copper Backtank 供气，跑完**一个**开采循环后才能取样，把矿脉写入图鉴（`OreVeinAtlasItem.addVein`）。
- `OreVeinAtlasItem` + `OreVeinAtlasMenu` + `OreVeinAtlasScreen` + `PagedListWidget`：展示已知矿脉、可显示/隐藏/设为寻脉目标；状态存于 `ore_vein_altas_data` 数据组件。
- `OreVeinFinderItem`：按配置范围搜索矿脉，并受图鉴过滤状态影响。

## 10. 网络

- 主线 `main/.../network/NetworkHandler.java` 用 NeoForge `RegisterPayloadHandlersEvent` 注册，版本 `"1"`。
- 包（`platform-shared/.../network/`）：`OreVeinAtlasClickPacket`（客户端→服务端）、`OreVeinInfoPacket`（服务端→客户端）；公共接口 `Packet`（`handleClient` / `handleServer`）。
- 处理统一 `context.enqueueWork(...)` 切回主线程。

## 11. 客户端与 Ponder

- `platform-shared/.../client/ClientRegistration.java` 注册 Ponder 插件 `COEPonderPlugin`（场景在 `PonderScenes`，脚本 `assets/.../ponder/*.nbt`）。
- 渲染：`DrillRenderer`、`KineticInputBlockEntityRenderer`、`KineticInputVisual`；`PlatformClient` 加载器侧初始化入口。
- NeoForge 侧 `client/` 还有 `COEClient`、`ClientEventHandler`、`CCClientInit`。

## 12. 命令

`COECommand`（`main` 与 `platform-shared` 各一份，语义相同）：`/coe setvein <pos> <recipe> [multiplier]`、`/coe removevein <pos>`、`/coe locate <recipe>`，需要权限等级 2（locate 见 `RandomSpreadGenerator.locate`）。

## 13. 配置

`platform-shared/.../Config.java`：COMMON 规格几乎为空，实际可调项在 **SERVER**（每世界，位于 `saves/<world>/serverconfig/createoreexcavation-server.toml`）：

| 键 | 默认 | 含义 |
|---|---|---|
| `finiteAmountBase` | 1000 | 有限矿脉基础总量 |
| `defaultInfinite` | true | `finite=default` 的矿脉是否无限 |
| `maxExtractorsPerVein` | 0 | 每条矿脉最大抽取器数，0 = 无限 |
| `veinFinderNear` | 1 | 寻脉器「附近发现」范围（区块） |
| `veinFinderFar` | 25 | 寻脉器「踪迹」精度 |
| `veinFinderCd` | 100 | 寻脉器冷却（tick） |

`ForgeConfig`（main）监听加载/重载，把值刷进 `Config` 的静态字段；KubeJS 也可在运行时追加矿脉并调整基础数值。

## 14. 工具类速查

`platform-shared/.../util/`：`RandomSpreadGenerator`（矿脉放置）、`ThreeState`、`DimChunkPos`、`NumberFormatter`（大数字格式化）、`TooltipUtil`/`BiomeTooltip`（工具提示）、`ComponentJoiner`、`IOBlockType`、`PlatformMenuProvider`。
