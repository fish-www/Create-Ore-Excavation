# 架构

面向 NeoForge / MC 1.21.1 主线。所有路径相对 `NeoForge/`。

> **定位**：模组本体**只提供机制**——矿脉/矿簇的世界数据、钻机与抽取器、手持钻、图鉴、JEI/KubeJS/Xaero 集成。它**不带任何矿脉、矿簇、钻探与抽取配方，也不带矿物物品**（`raw_diamond`/`raw_emerald`/`raw_redstone` 除外，见第 3 节）：矿脉与产出表由整合包/数据包作者写，格式见 [`data-and-recipes.md`](data-and-recipes.md)。

## 1. 依赖与运行环境

- Java 21，NeoForge 21.1.211，Minecraft 1.21.1（`gradle.properties`）。
- **Create 6.0.7**：机器靠 Create 的旋转动力（kinetic）工作，界面上走 Create 的 `SmartBlockEntity` / Ponder / goggle tooltip。
- **Registrate**：注册与数据生成（方块、物品、方块实体、菜单、模型、语言）。
- JEI（编译+运行）、KubeJS、Architectury、Xaero 的小地图/世界地图、CC:Tweaked 为可选集成。

## 2. 源码集划分

NeoForge 工程把两个目录都编进主源集（`build.gradle` 的 `sourceSets.main`）：

- `src/main/java` — 依赖 NeoForge API 的代码：入口、配置事件、数据生成、网络注册、区块 attachment、能力注册、`ExtractorRecipe`/`FluidIngredient`（用 NeoForge 流体 API）。
- `src/platform-shared/java` — 其余大部分游戏逻辑：`Registration`、`Config`、`OreData`、`OreVeinGenerator`、方块与方块实体、`DrillingRecipe`/`VeinRecipe`/`ExcavatingRecipe`、JEI/KubeJS/Xaero 插件、菜单与界面、网络包、工具类。

`src/platform-shared/resources` 放静态资源（贴图、`pack.mcmeta`、`kubejs.plugins.txt`、各语种 lang）；`src/generated/resources` 是数据生成产物。

这样分是为了尽量把游戏逻辑与加载器隔离，方便旧 Fabric 线复用——但 `platform-shared` **不是**严格与加载器无关：`Config` 直接用 NeoForge 的 `ModConfigSpec`。详见 `porting.md`。

## 3. 注册系统

主线入口 `src/main/java/com/tom/createores/CreateOreExcavation.java`（`@Mod`）：

- 建立 `CreateRegistrate`（`CreateOreExcavation.registrate()`）。
- 注册三种配方序列化器/类型（用 `DeferredRegister`）：`drilling`、`extracting`、`vein`，包成 `RecipeTypeGroup<T>`。
- 物品标签 `createoreexcavation:drills`、方块标签 `createoreexcavation:handheld_drill_surface`。
- 区块 attachment：`ore_vein` → `OreDataAttachment`。
- 数据组件：`ore_vein_altas_data`（矿脉图鉴内容，注意拼写是 *altas*）、`ore_vein_finder_filtered`、`hand_drill_fuel`。
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
| `raw_diamond` / `raw_emerald` / `raw_redstone` | 物品（模组自带，见下） | — |
| `vein_finder` / `vein_atlas` | 物品 | `OreVeinFinderItem` / `OreVeinAtlasItem` |
| `vein_atlas` | 菜单 | `OreVeinAtlasMenu` / `OreVeinAtlasScreen` |
| `create_ore_excavation` | 创造模式标签页 | — |

`raw_diamond` / `raw_emerald` / `raw_redstone` 是模组自带的三个物品：数据生成器为它们写了 Create 加工配方（`milling`/`crushing` → 红石、`cutting` → 钻石/绿宝石，见 `data/COERecipes.java`），所以它们属于机制的一部分，留在模组里。矿脉把它们当产物或图标使用（见 `kubejs/data/**/ore_vein_type/*.json`）。矿簇用的物品（例如特殊矿簇的图标）模组**不提供**：数据包不能注册物品，所以这类物品由整合包用 KubeJS 启动脚本注册，见 [`data-and-recipes.md`](data-and-recipes.md) 第 8 节。

`Registration.register()` 末尾还会登记所有语言键，并在装了 ComputerCraft 时初始化 `CCRegistration`。

## 4. 配方系统

三种类型，全部走 Minecraft 的 `Recipe` / `RecipeSerializer` 机制（而非 Create 的 `ProcessingRecipe` 体系）。字段与 JSON 例子见 [`data-and-recipes.md`](data-and-recipes.md)，这里只讲代码结构：

### `createoreexcavation:vein` — 矿脉定义（`recipe/VeinRecipe.java`）

不参与合成，只描述「哪里、刷什么矿」。字段：`name`、`priority`、`biomeWhitelist`/`biomeBlacklist`（各**一个**群系标签）、`finite`（`ThreeState`：`never`/`default`/`always`）、`density`（网格间距，单位区块）、`reserve`（`{min,max,mean,sigma}` 对象）、`regenTicks`（默认 `VeinRecipe.DEFAULT_REGEN_TICKS` = 3 天）、`regenBuffer`、`biomeOverrides`（`BiomeOverride` 列表：`{target, density?, reserve?}`）、`chunks`（定点区块列表）、`waypointColor`（航标颜色名）、`placement`（`{spacing, separation, salt}`）、`icon`（物品栈）、`cluster`。

间距、储量与再生时间都直接写在配方里，JEI 用 `layerForEntry(entry)` 取某个分布的间距与储量数字展示（见 `integrations.md` 的 JEI 一节）。

### `createoreexcavation:drilling` — 钻机产出物品（`recipe/DrillingRecipe.java`）

在 `ExcavatingRecipe` 公共字段（`veinId`、`drill` 匹配、`priority`、`ticks`、`stress`、可选 `fluid`）之上增加 `output`：Create `ProcessingOutput` 列表（带概率）。

### `createoreexcavation:extracting` — 抽取器产出流体（`src/main/.../recipe/ExtractorRecipe.java`）

同公共字段，产出单个 `FluidStack`。因使用 NeoForge 流体 API，放在 `main` 侧。

公共基类 `recipe/ExcavatingRecipe.java` 定义两条轨迹一样的编解码：`codec()`（JSON/数据包）与 `streamCodec()`（网络同步）。

**配方怎么被选中**：机器所在区块先取矿脉（见下节），再把本机配方里 `veinId` 匹配的筛出来，按 `priority` 升序排；只有一个就用它，多个则扫一遍，**取最后一个通过校验的**（即优先级最高且钻头/钻井液都满足的那个），一个都没通过时退回列表第一个。

## 5. 矿脉世界生成与区块数据

矿脉**不是方块**，而是每区块一份的持久化数据：

- `OreData`（`platform-shared/.../OreData.java`）保存一个区块上的槽位列表 `List<VeinInstance>`：每个实例记录配方 ID、总储量 `total`、已开采 `extracted`、上次恢复时刻 `lastRegen` 与小数缓冲 `regenBuffer`、是否为矿簇 `cluster`、再生刻数 `regenTicks`（选脉时从配方的 `regenTicks` 抄下来）；另有 `gen` = 这个区块上次选矿脉时的**重新生成代次**、`clusterGen` = 上次选矿簇时的代次。用 `OreData.Serialized` + `Codec` 做 NBT 序列化。
- 一个区块最多两条：一条**矿脉**（`getRecipe()`，钻机开采、探矿杖探测）和一条**矿簇**（`getClusterRecipe()`，手持钻开采/探测）。两者互不影响，用不同的 `RandomSource` 独立选址。
- 总储量 `computeTotal()` = 该区块命中的那一**层分布**（`VeinRecipe.Layer`）的 `reserve` 范围做正态采样（`ReserveRange.sample`：以 `mean` 为中心、`sigma` 为宽，截断在 `min`–`max`）；`finite=never`（水、岩浆）或 `defaultInfinite=true` 时为 0（无限）。
- `getResourcesRemaining(recipeId, gameTime)` 先做**惰性再生**：按 `(gameTime - lastRegen) * total / regenTicks` 回补 `extracted`（`regenTicks` 是该区块矿脉自己的，不是全局配置），上限为原定 `total`；`regenTicks=0` 或矿簇不再生。返回 `-1` 表示枯竭。
- **矿簇枯竭后从世界消失**（`getClusterRecipe()` 返回 null）**且不再生**，其地图标点与图鉴点位会被自动清除（`HandheldDrillItem.cleanUpDepletedCluster`）；矿脉则按下文再生。
- `canExtract()` 执行 `maxExtractorsPerVein` 限制。
- `OreDataAttachment`（`main/.../OreDataAttachment.java`）把它挂到 `LevelChunk` 的 `ore_vein` attachment 上；`getData(chunk)` 首次访问时惰性调用 `OreData.populate(chunk)`，客户端访问直接抛异常。
- `OreVeinGenerator`（`platform-shared/.../OreVeinGenerator.java`）持有 `AtomicReference<RandomSpreadGenerator>` 缓存；`TagsUpdatedEvent`（数据包/标签重载）时 `invalidate()`。
- `RandomSpreadGenerator`（`platform-shared/.../util/RandomSpreadGenerator.java`）：
  - `loadAll()` 把 `vein` 配方分成**矿脉**与**矿簇**两组，各自按「负的生成优先级 + id」排序（优先级高者先）。
  - `pick(level, chunkPos, cluster, seed)`：先按种子 + 区块坐标采一次生物群系，再取该矿脉在这个群系的分布层列表 `VeinRecipe.layers(biome, registries)`（每层 = 一个网格间距 + 一个储量范围，`placementFor(recipe, spacing)` 把 placement 缩放到该层间距；`spacing=1` 就是每区块），逐层用 `getPotentialStructureChunk(seed, x, z)` 判断该区块属不属于这个网格，命中即返回 `PickResult(recipe, biome, layer)`；`canGenerate` 在此之前过滤群系。**配方带 `chunks` 时先查定点表**（`isFixedChunk`，懒建的 `LongSet`）：命中就直接返回该矿脉的默认层，不看群系也不看网格。
  - `locate(pos, level, radius, cluster, filter, seed)`：供 `/coe locate`、探矿杖与手持钻使用；按矿脉 `spacings()` 里的**每个间距各枚举一遍**候选区块（不同间距的网格互不包含，只按最小间距枚举会漏），再加上每条配方 `chunks` 里半径内的定点区块，再用同一张 `list`（矿脉或矿簇）逐个校验；已加载区块还会与 `OreData` 里的真实数据对一遍（矿簇走 `getClusterRecipe`）。

> ⚠️ 重缩放的 placement 必须**按对象身份**缓存（`IdentityHashMap<placement, Map<spacing, placement>>`），不能按 `spacing/separation/spreadType` 缓存：placement 的 `salt` 读不出来，按值缓存会让**间距相同**的矿脉共用同一个网格，排序靠前的会遮蔽其余全部，表现为「全图只有煤矿脉」。

一句话：**矿脉位置由「世界种子（或 `/coe regenerate` 设的自定义种子）+ 配方的 placement（再按该群系的分布层间距缩放）+ 生物群系 + 配方的定点区块 `chunks`」决定**，可复现、可定位。

### 5.1 群系分组用标签

「一组群系」就是一个普通的群系标签：数据包写在 `data/<ns>/tags/worldgen/biome/<name>.json`，配方里用 `#<ns>:<name>` 引用（`biomeWhitelist`/`biomeBlacklist`/`biomeOverrides[].target` 各处都一样）。标签的**显示名**用 `biome_tag.<命名空间>.<路径>` 给出，没这个键就直接显示标签 id（见 `data-and-recipes.md`）。

### 5.2 一条矿脉的分布层（`density` / `reserve` / `biomeOverrides`）

配方里的 `density`/`reserve` 是这个矿脉在**所有群系**的默认分布（`density: -1` 则不上网格，只在 entry 命中的群系里生成）；`biomeOverrides` 里的每条 entry 在它匹配的群系里**再加一层**，默认那层**永远**在列表最后参与（命中 entry 时才由 entry 优先）：

```json
"density": -1,
"reserve": { "min": 20000, "max": 200000, "mean": 40000, "sigma": 10000 },
"biomeOverrides": [
  { "target": "#createoreexcavation:mountain", "density": 256, "reserve": { "min": 20000, "max": 200000, "mean": 150000, "sigma": 25000 } },
  { "target": "minecraft:stony_peaks", "density": 128 }
]
```

`target` 两种写法：`#群系标签`（可以自己建标签把多个原版标签合起来）或 `modid:群系`（单个群系，不带 `#`）。两条 entry 都命中同一个群系就叠两层网格；省略 `density` 或 `reserve` 表示沿用默认值。判定顺序就是 `layers()` 的返回顺序：entry 按配方里书写的顺序在前，默认层最后；`pick()` 逐层测网格，**第一个命中的层胜出**——所以一个区块依然最多只有一条同类型矿脉，但各层网格覆盖到的区块会合并在一起。默认层始终参与，所以「主世界到处都有的稀疏小矿脉 + 山地里更密更大的同种矿脉」是**两张网格叠在一起**：一座山会同时吃到默认层与山地层，其他地方只有默认层。

JEI 按**每个 entry 一页**展示（默认分布一页 + `biomeOverrides` 各一页），取值用 `layerForEntry(entry)`。某条分布在某个群系里到底生不生成，用 `entryApplies(biome, entry, registries)` 判断，配方页的白名单/黑名单就是逐群系跑这个判断的结果。

## 6. 机器运行流程

抽象基类 `platform-shared/.../block/entity/ExcavatingBlockEntity.java`（实现 `IDrill`、`MultiblockCapHandler`，继承 Create 的 `SmartBlockEntity`）：

- `updateRecipe()`：拿所在区块 `OreData` → 取矿脉 → 选配方（见第 4 节），同时把 `data`、`vein`、`current` 缓存下来。
- `tick()`（服务端）：若 `current != null` 且状态正常，检查四项——有多方块动力学输入、转速 ≥ `SpeedLevel.MEDIUM`、钻头匹配配方、机器下方（`worldPosition.below(2)`）为实心方块。满足则 `progress += 转速/基准`，达到 `ticks` 时 `onFinished()` + `data.extract(veinId, 1, gameTime)` + `completedOneCycle = true`。
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
- `OreVeinAtlasItem` + `OreVeinAtlasMenu` + `OreVeinAtlasScreen` + `PagedListWidget`：展示已知矿脉，可显示/隐藏/设为寻脉目标/排除某类型；状态存于 `ore_vein_altas_data` 数据组件（拼写就是 *altas*，改名需要迁移逻辑）。
- 图鉴里存的是**真实储量**（`amount`，0 = 无限），所以界面直接显示；`OreVeinAtlasItem.findAtlas(player)` 是找玩家图鉴的公共入口。
- **矿簇也被记入图鉴**：手持钻挖到矿簇时调用 `addToAtlas`（消息 `chat.coe.cluster.addedToAtlas`），于是图鉴的类型列表里可以「排除」该矿簇类型；`HandheldDrillItem.clusterFilter` 只认图鉴的 `exclude`（矿脉的 `target` 不影响矿簇）。
- `OreVeinFinderItem`（探矿杖）：只探测**矿脉**（`OreData.getRecipe`），结果受图鉴的 `exclude`/`target` 过滤；探测到的矿脉会标进地图：**本区块的总量读数经 `OreVeinInfoPacket` 送出并标点，周围一圈（`Config.veinFinderNear`，默认 ±1 区块 = 3×3）的矿脉由服务端 `VeinMarkers.sendNew` 直接标点**（与手持钻标矿簇同一套去重）。另会提示最近 `Config.veinFinderFar` 精度内的矿脉名称与距离。

## 10. 网络

- 主线 `main/.../network/NetworkHandler.java` 用 NeoForge `RegisterPayloadHandlersEvent` 注册，版本 `"1"`。
- 包（`platform-shared/.../network/`）：`OreVeinAtlasClickPacket`（客户端→服务端）、`OreVeinInfoPacket`（服务端→客户端，探矿杖的读数）、`OreVeinDiscoverPacket`（服务端→客户端，地图标点；`Mode` 为 `ADD`/`REMOVE`/`CLEAR`，`Kind` 为 `VEIN`/`CLUSTER`）；公共接口 `Packet`（`handleClient` / `handleServer`）。
- `VeinMarkers`：服务端按玩家记录**已发送过的标点**，只有新点位才发包（否则四处走动会反复刷屏）；矿簇枯竭时发 `REMOVE`。
- 处理统一 `context.enqueueWork(...)` 切回主线程。

## 11. 客户端与 Ponder

- `platform-shared/.../client/ClientRegistration.java` 注册 Ponder 插件 `COEPonderPlugin`（场景在 `PonderScenes`，脚本 `assets/.../ponder/*.nbt`）。
- 渲染：`DrillRenderer`、`KineticInputBlockEntityRenderer`、`KineticInputVisual`；`PlatformClient` 是加载器侧的客户端初始化入口。
- NeoForge 侧 `client/` 还有 `COEClient`、`ClientEventHandler`、`CCClientInit`。
- `client/DrillBars` 是仅客户端的手持钻进度条实现（见第 12 节）。

## 12. 手持钻（`platform-shared/…/item/HandheldDrillItem.java`）

**没有 GUI、没有菜单、没有网络包**：

| 操作 | 效果 |
|---|---|
| 任意一只手拿着钻，**对着可钻方块**右键 | 开始开采；`onUseTick` 每 `handDrillTicks` 刻抽一份**矿簇**，并在动作栏实时显示剩余储量 |
| **另一只手拿着可烧物品右键** | 把 1 个燃料烧成燃料单位存进钻（受 `handDrillFuelCapacity` 限制） |
| **对着别的方块或空气右键**（另一只手没有可烧物品） | 不消耗这次右键（返回 `PASS`），所以箱子/工作台照常打开，同时执行探矿；探测 `handDrillRadius`（serverconfig，默认 `1` = **本区块 + 相邻一圈**，即 3×3 区块）内的**矿簇**（只在右键时，不自动）：聊天栏报告本区块储量 + 本次新标记数量并标进地图；**本区块没有矿簇时**，像探矿杖一样提示**最近矿簇的名称与距离**（`handDrillSearchRadius`，默认 `256` 格），本区块矿簇已枯竭时先提示枯竭；范围内已枯竭的矿簇会被**从地图上移除** |

- 可开采的方块：方块标签 `#createoreexcavation:handheld_drill_surface`（`data/createoreexcavation/tags/block/handheld_drill_surface.json`，默认 = `#minecraft:base_stone_overworld` + `#minecraft:base_stone_nether` + 基岩）。加自定义石头直接往标签里加，不用改代码。
- 加燃料走 `onItemUseFirst`，先于方块交互，所以对着箱子/工作台右键也是加燃料而不是打开界面。
- **开采前先检查**（`useOn`，不是等一轮挖完）：燃料不足、这里没有矿簇、矿簇已枯竭 → 立刻提示并**不开采**。
- 燃料判定用 `ItemStack.getBurnTime`（NeoForge 的 `furnace_fuels` 数据表），没有白名单，所以木头也能烧；1 个煤炭 = 1600，`handDrillFuelPerUnit = 200` = 8 次开采，满油 20000 = 100 次。燃料只存一个 `int`（数据组件 `hand_drill_fuel`）——不能用 `ItemStack` 当组件值，它没有 `equals`/`hashCode`，客户端解包会崩。
- **物品栏耐久条**在开采中显示**本次开采进度**（绿），平时显示**燃料**（蓝）——原版物品条只能有一条，所以二者切换。进度由客户端专用类 `client/DrillBars` 提供。
- ⚠️ **物品类里不能出现任何客户端类**：`HandheldDrillItem` 直接读 `Minecraft.getInstance()` 会在类校验时加载 `LocalPlayer`，**专用服务端崩在 `Registration.<clinit>`**。现在只调用 `client/DrillBars`（方法签名里没有客户端类型，客户端专用类不会被初始化）。
- ⚠️ **不能在客户端读矿脉数据**（`OreDataAttachment.getData` 会抛 `Ore Data accessed from client`）：`use()`/`useOn()` 里一切矿脉判断都在 `!isClientSide` 分支内。
- 为什么副手也能用：`Minecraft.startUseItem` 会按 `[MAIN_HAND, OFF_HAND]` 依次尝试，主手物品没有消耗这次右键（煤/木棍等非 BlockItem 燃料）时会轮到副手的钻。

## 13. 命令

- `/coe setvein <pos> <recipe> [multiplier]` / `removevein <pos>`：手动写入/清除区块矿脉（写入时会记下当前的重新生成代次，所以**之后**才创建的标记才会把它重选掉）。
- `/coe regenerate [vein|cluster] all [seed]`：把**当前维度**的每个区块标为待重生成；加 `vein`/`cluster` 只标其中一类。
- `/coe regenerate [vein|cluster] <radius> [seed]` / `[vein|cluster] <pos> <radius> [seed]`：只标记半径内的区块，半径单位是区块（`pos` 省略时为执行者所在区块）。
- `/coe regenerate [vein|cluster] <dimension> all [seed]` / `<dimension> <radius> [seed]` / `<dimension> <pos> <radius> [seed]`：指定维度，维度参数带补全。
- `/coe regenerate clear [vein|cluster]`（可带 `<dimension>`）：丢弃该维度待重生成的标记与自定义种子，不给 kind 就两类都清。
- `[seed]` 可选，不写就是**世界种子**；写了就换成那个种子来重选（`12345` 之类），再跑一次不带种子的命令就回到世界种子。改种子会把矿脉/矿簇挪到完全不同的地方，适合服务器定期重置矿簇：`/coe regenerate cluster all <每次不同的数字>`。
- `/coe locate <recipe>`：找最近的该类型矿脉（枚举候选区块），并把它标记到地图上。
- `/coe discover <radius>` / `/coe discover <pos> <radius>`：扫指定区块半径内**所有**矿脉与矿簇并标记到地图上，半径上限 `MAX_DISCOVER_RADIUS = 128`。
- `/coe discover vein <radius>` / `vein <pos> <radius>`：只扫**矿脉**；把 `vein` 换成 `cluster` 则只扫**矿簇**（一个区块两类可以同时存在）。
- `/coe discover clear [vein|cluster]`：清除地图标记，不给参数就两类都清。

地图标记经 `OreVeinDiscoverPacket` 发到客户端，由 `DiscoveredVeinHandler` 分发给 Xaero（`XaeroWaypoints`）。均需权限等级 2，定位实现在 `RandomSpreadGenerator.locate`。

### 13.1 重新生成矿脉（`/coe regenerate`）

区块里的矿脉是**落盘数据**，改了矿脉配方（新增矿脉、改分布、用 `chunks` 定点）后，已经生成过的区块不会自己变。`/coe regenerate` 就是刷新它们的手段：

- 矿脉与矿簇是**分开标记**的（`vein` / `cluster`，不给就是两者），因为一个区块可以同时持有两条，而且矿簇会枯竭消失、矿脉会缓慢再生，常常只需要重选其中一类。
- 重生成是**惰性**的：命令只写下一个**代次**（`VeinRegeneration`，每个维度一份 `SavedData`，每条标记记 `{盒子, 代次, kind}`），区块**下次被访问/加载**时才对比自己存的代次（`OreData` 的 `gen` / `clusterGen`）决定要不要丢掉那一类的旧数据、重选一遍并标脏存档。所以「整个维度重生成」不会去遍历几百万个区块，没被再次加载的区块保留原样。
  - 「下次被访问」对**矿脉**是钻机/抽取器（lazy tick，约 1 秒内）、探矿杖、CC 乌龟、`/coe locate`；对**矿簇**是手持钻与 `/coe discover`。也就是说命令之后你**用手持钻钻哪个区块，那个区块当场就换新矿簇**；只有从来没人再读它的区块（附近既无机器也无玩家）才保持原样。
- **种子**：`VeinRegeneration` 按维度 + 类别存 `veinSeed`/`clusterSeed`（默认 `null` = 用世界种子），命令里的可选 `[seed]` 会把它换成给定值。种子参与重选的两处：网格 `getPotentialStructureChunk` 与储量随机（`OreData.addVein` 的 `rngFromChunk`），群系采样 `sampleBiome` 也用它。`/coe regenerate clear` 会连同自定义种子一起丢掉。
  - 预测路径也走同一种子（`/coe discover`、`/coe locate`、探矿杖、手持钻的扫描），否则「预测到的矿脉」和真正落盘的会对不上。
  - 该存储的读取者只有 `OreDataAttachment`（重选）与上面那些查询入口，所以改种子后**已经落盘的区块**同样要先被重新选中才会变。
- 什么都没标记时区块的 `gen`/`clusterGen` 与代次都是 0：**默认只在新区块生成矿脉**，旧区块一个字节都不改。
- `all` / `<radius>` / `<pos> <radius>` 写的是「当前代次 +1」的区域，可叠加；`clear [vein|cluster]` 只丢弃未生效的标记（连同自定义种子），已经被重选过的区块不会回滚。
- `/coe setvein` 会把两类代次都盖成当前值，所以**之后**才创建的标记才会把手工写入的矿脉重选掉。

## 14. 配置

`platform-shared/.../Config.java`：COMMON 规格只放一条「设置已移到 serverconfig」的说明，实际可调项都在 **SERVER**（每世界，位于 `saves/<world>/serverconfig/createoreexcavation-server.toml`）：

| 键 | 默认 | 含义 |
|---|---|---|
| `defaultInfinite` | **false** | `finite=default` 的矿脉是否无限；关闭后储量由矿脉配方的 `reserve` 范围决定 |
| `maxExtractorsPerVein` | 0 | 每条矿脉最大抽取器数，0 = 无限 |
| `handDrillTicks` | 30 | 手持钻每挖出一份矿物的刻数 |
| `handDrillFuelPerUnit` | 200 | 手持钻每挖出一份矿物消耗的燃料 |
| `handDrillFuelCapacity` | 20000 | 手持钻燃料上限（1 个煤炭 = 1600） |
| `handDrillRadius` | 1 | 手持钻探测矿簇的区块半径（1 = 本区块 + 相邻一圈） |
| `handDrillSearchRadius` | 256 | 本区块没有矿簇时找最近矿簇的距离（格） |
| `veinFinderNear` | 1 | 寻脉器「附近发现」范围（区块） |
| `veinFinderFar` | 25 | 寻脉器「踪迹」精度 |
| `veinFinderCd` | 100 | 寻脉器冷却（tick） |

所有矿脉数值（间距、储量、再生时间、群系限制、航标颜色）都在**配方**里，群系分组用标签（见 5.1 节），两者都不在配置文件里。改了 serverconfig 的默认值后要删掉实例里的旧文件才会生效。服务端与客户端两份 serverconfig 需要各自一致（JEI 用客户端那份）。

`ForgeConfig`（main）监听加载/重载，把值刷进 `Config` 的静态字段；KubeJS 也可以在运行时加矿脉。

## 15. 工具类速查

`platform-shared/.../util/`：`RandomSpreadGenerator`（矿脉放置）、`ThreeState`、`DimChunkPos`、`NumberFormatter`（大数字格式化）、`TimeFormatter`（再生时间文案）、`TooltipUtil`/`BiomeTooltip`（工具提示与分页名单）、`ComponentJoiner`、`IOBlockType`、`PlatformMenuProvider`。

根包下另有 `OreData`（区块矿脉数据）、`OreVeinGenerator`（每维度的放置器缓存）、`VeinRegeneration`（每维度的重生成代次，`SavedData`）。
