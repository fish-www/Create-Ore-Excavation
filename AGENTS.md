# Create Ore Excavation — 开发与维护指南

用 Create 的旋转动力驱动钻机开采「矿脉」的 Minecraft 模组。矿脉不是真实方块，而是**按区块存储的世界数据**。

- 作者：tom5454 · 协议：MIT · Mod ID：`createoreexcavation`
- 主线：**NeoForge / MC 1.21.1**（版本 `1.7.0`，见 `NeoForge/gradle.properties`）
- 旧线：**Fabric / MC 1.20.1**（版本 `1.5.4`），构建路径失效，见 [`docs/porting.md`](docs/porting.md)
- 详细文档在 [`docs/`](docs/README.md)

> 本文件是给「接手这个仓库的人 / AI Agent」的入口。改代码前先读 [`docs/architecture.md`](docs/architecture.md) 和 [`docs/development.md`](docs/development.md)。

## 定位：模组只提供机制

模组本体（jar 里）**没有矿脉、没有矿簇、没有钻探与抽取配方，也没有特殊矿簇的物品**：它注册三种配方类型、机器、手持钻、图鉴和各类集成。矿脉与产出表由**整合包/数据包作者**提供，写法见 [`docs/data-and-recipes.md`](docs/data-and-recipes.md)（仓库里不带任何示例内容）。

留在模组里的矿物物品只有 `raw_diamond`/`raw_emerald`/`raw_redstone`——它们参与模组自己的 Create 加工配方（`COERecipes.java`），属于机制。

## 下一步做什么

- 想跑起来：`cd NeoForge && sh gradlew runClient`（自己往 `runs/client/kubejs/` 里放一条矿脉，否则没有矿可挖，写法见 `docs/data-and-recipes.md`）
- 改注册器/模组自带配方/语言键：`cd NeoForge && sh gradlew runData`，然后提交 `src/generated/resources`
- 改共享逻辑：优先动 `NeoForge/src/platform-shared/java`，不要只改 `main`
- 改内容（矿脉、物品、名字）：写数据包或 KubeJS 脚本，见 `docs/data-and-recipes.md`

## 仓库结构

```
.
├── NeoForge/                  # 主线工程（MC 1.21.1 / Java 21 / NeoForge）
│   ├── build.gradle
│   ├── gradle.properties      # 版本号与依赖版本都在这
│   ├── src/main/java          # 加载器专属代码（入口、配置、数据生成、网络、能力）
│   ├── src/platform-shared/java   # 跨加载器共享代码（方块、配方、GUI、KubeJS、多方块…）
│   ├── src/main/resources     # neoforge.mods.toml
│   ├── src/platform-shared/resources  # 静态 assets/lang/贴图、data/tags、pack.mcmeta
│   └── src/generated/resources     # 数据生成产物（勿手改）
├── Fabric/                    # 旧 1.20.1 移植（当前不可编译）
├── version-check.json         # 游戏内更新检查用的 JSON（发布时更新）
└── Credits.md, README.md, LICENSE
```

两个工程各自是独立的 Gradle 构建（各自有 `gradlew` / `settings.gradle`），**不是**一个多模块构建。

## 代码归属规则（最关键）

写代码前先判断它属于哪一侧：

| 归属 | 放哪 | 例子 |
|---|---|---|
| 加载器无关或仅主线用 | `NeoForge/src/platform-shared/java` | `Registration`、`VeinRecipe`、`OreData`、多方块方块、JEI/KubeJS 插件、菜单、网络包 |
| NeoForge 专属 | `NeoForge/src/main/java` | `CreateOreExcavation`（`@Mod` 入口）、`ForgeConfig`、`DataGenerators`、`COECommand`、`NetworkHandler`、`OreDataAttachment`、能力注册 |
| Fabric 专属 | `Fabric/src/main/java` | 该加载器的对应实现（旧线） |
| 内容（不是代码，不进这个仓库） | 整合包侧的 `<实例>/kubejs/` 或数据包 | 矿脉/矿簇/钻探/抽取配方、群系标签、物品注册脚本、贴图与语言；格式见 [`docs/data-and-recipes.md`](docs/data-and-recipes.md) |

⚠️ `platform-shared` **并非严格与加载器无关**：`Config.java` 直接用 NeoForge 的 `ModConfigSpec`。新增共享代码时别假设它能直接给 Fabric 用。

## 常用命令

从 `NeoForge/` 目录执行（`gradlew` 没有可执行位，用 `sh gradlew`）：

```bash
sh gradlew runClient     # 开发客户端
sh gradlew runServer     # 开发服务端（无头验证，约 25-30 秒可进退）
sh gradlew runData       # 数据生成，输出到 src/generated/resources
sh gradlew build         # 打包 + sources jar
sh gradlew gameTestServer  # 跑 gametest（没有注册用例，会退出）
```

Fabric（1.20.1，当前无法构建）：

```bash
cd Fabric
sh gradlew runDatagen
```

## 约定

- 代码用 `com.tom.createores` 包名（注意是 `createores`，不是 `createoreexcavation`）。
- 注册一律走 **Registrate**（`Registration.java`），模型/语言/掉落物也用它做数据生成。
- `ResourceLocation` 用 `ResourceLocation.tryBuild` / `ResourceLocation.parse`。
- 模组自带的语言键前缀：`chat.coe.` / `info.coe.` / `tooltip.coe.` / `config.coe.` / `command.coe.` / `jei.coe.` / `vein.coe.`，在 `Registration.register()` 里用 `add(key, value)` 登记，生成进 `lang/en_us.json`；其余语种在 `platform-shared/resources/.../lang/`。内容包的名字（`vein.coe.mineral.*`、`item.createoreexcavation.*`、`biome_tag.*`）由包自己提供，见 [`docs/data-and-recipes.md`](docs/data-and-recipes.md) 第 5 节。
- 1.21 的数据目录用**单数**：`data/<ns>/recipe/`、`loot_table/`、`advancement/`；Fabric 1.20 用复数。
- 提交信息用 Conventional Commits，例如 `feat(vein): colour map markers from the recipe`。

## 已知坑

1. **不要手改 `src/generated/resources`**：那是 `runData` 的产物，手改会在下次生成时被覆盖。
2. **持久化 ID 不能随意改名**：方块/物品/配方类型/数据组件的注册 ID 与 NBT/配方存档绑定；例如数据组件 ID 存在拼写 `ore_vein_altas_data`，改名需要迁移逻辑。
3. **发布要更新两处**：`NeoForge/gradle.properties` 的 `mod_version` 与根目录 `version-check.json` 的 promos（游戏内更新检查读它）。
4. **`NeoForge/gradlew` 没有可执行位**：用 `sh gradlew <task>`，或先 `chmod +x`。
5. **矿脉/矿簇/钻探/抽取配方全是整合包内容**：改它们不用编译，改了也不要在模组里加默认值。模组侧只剩 `data/createoreexcavation/tags/{block,item}/` 与 CC:Turtle upgrade。
6. **`OreData` 存档格式**（每区块每条矿脉）：attachment 里是 `veins` 列表，矿簇标志键 `cluster`，`VeinInstance` 有 `total`/`extracted`/`lastRegen`/`regenBuffer`/`regenTicks`（选脉时从配方抄下来）；另有 `gen`（矿脉的重生成代次）与 `clusterGen`（矿簇的）。
7. **改配方后旧区块不会自己变**：用 `/coe regenerate [vein|cluster] all`（当前维度）/ `<dimension> all` / `<radius>`，**惰性生效**（区块下次被读时才重选），见 `docs/architecture.md` 13.1。命令还接受可选 `[seed]`：不写 = 世界种子，写了就换成它重选（`VeinRegeneration` 按维度 + 类别存 `veinSeed`/`clusterSeed`，`clear` 会一并丢掉）；种子参与网格 `getPotentialStructureChunk` 与储量随机，**预测入口（`/coe discover`、`/coe locate`、探矿杖、手持钻）也必须传同一个 seed**，否则预测与实际对不上。
8. **群系分组用群系标签**：要「一组群系」就在包里建 `data/<ns>/tags/worldgen/biome/<name>.json`（或 KubeJS 的 `ServerEvents.tags('worldgen/biome', ...)`），配方里写 `#<ns>:<name>`；它的**显示名**用 `biome_tag.<ns>.<路径>`（没这个键就显示标签 id）。标签写错/缺失不会报错，只会静默变成空标签 → 那条分布永不生成。
9. **`vein` 配方的 `density` / `reserve` / `regenTicks` 语义**：`density` 是**网格间距（单位区块）**，负数 = 不上网格（只在 `chunks` 或 `biomeOverrides` 命中时生成），`0` = 每区块一条，`n>0` = 每 n×n 区块一条；`reserve` 是内联对象 `{min,max,mean,sigma}`（正态采样后截断在 `min`–`max`）；`regenTicks` 默认 `VeinRecipe.DEFAULT_REGEN_TICKS` = 5184000（3 天），`0` = 不恢复，矿簇忽略。
10. **航标颜色在配方里**：`waypointColor` 是 `WaypointColor` 枚举名（`black`/`gold`/`light_blue`…，大小写不敏感），不认识就记日志回退。不写时矿脉按 `rarity`（稀有 `GOLD`、常见 `WHITE`）、矿簇 `GRAY`。该字段走配方网络同步（`toNetwork`/`fromNetwork` 里紧跟 separation），改同步顺序要两边一起改。改颜色后旧航点不会自动变色，要 `/coe discover clear`。
11. **给可选依赖 mod 的矿石加矿脉/矿簇要带 `neoforge:conditions`**：`{"type": "neoforge:mod_loaded", "modid": "tfmg"}`（键名就是 `neoforge:conditions`，NeoForge 在配方 codec 之前读它）。一组内容要 4 个 JSON（`ore_vein_type/<矿>(_cluster).json` + `drilling/<矿>(_cluster).json`），**四个都要带、且成对的矿脉与钻探配方条件要一致**，否则没装那个 mod 的实例解码就炸（图标物品 id 未知）。用 KubeJS **脚本**路线时没有条件键，得自己 `if (!Platform.isLoaded('<modid>')) return`。
12. **内容在整合包侧，不在这个仓库**：配方/标签写在 `<实例>/kubejs/data/...` 或 `<存档>/datapacks/.../data/...`，物品注册写在 `kubejs/startup_scripts/`，贴图与语言写在 `kubejs/assets/`。KubeJS 把 `kubejs/data` 当数据包加载且**优先级高于各 mod**（同路径文件会顶掉模组与其他数据包的文件），把 `kubejs/assets` 当资源包。写法见 `docs/data-and-recipes.md`。
13. **数据包不能注册物品、不能带语言文件**：想给自己的矿簇加物品只能用 KubeJS 启动脚本（`StartupEvents.registry('item', ...)`，KubeJS 建的物品描述键就是原版 `item.<ns>.<path>`，所以语言仍可写在资源包 `lang/` 里）；配方里的名字要用翻译键 + 包自己的语言文件，写 `{"text":"Copper"}` 虽然能跑但永远不会跟随玩家语言。
14. **KubeJS 物品注册注意**：`event.create('createoreexcavation:magnetite')` 的 id 用你写的命名空间；`ItemBuilder.group()` 已弃用且会打印错误，进创造模式标签页要用 `StartupEvents.modifyCreativeTab(...)`；物品模型由 KubeJS 自动生成（`item/generated` + `item/<路径>` 贴图），贴图放对位置即可。
15. **KubeJS 自定义组件的空值坑**：`asList()` 给的 `IntBounds.DEFAULT`（下限 1）**不允许空列表**，会让整条配方创建失败（`Component '...' is not allowed to be empty!`），必须用 `ListRecipeComponent.create(comp, false, false, IntBounds.OPTIONAL, Optional.empty())`；`StringComponent.STRING` 不允许空串，所以字符串键不要在 `initValues` 里预先 `setValue("")`。
16. **矿脉 placement 的重缩放缓存要按对象身份**（`IdentityHashMap<placement, Map<spacing, placement>>`）：placement 的 `salt` 是 protected 读不到，按 `spacing/separation` 按值缓存会让间距相同的矿脉共用网格，表现为「全图只有煤矿脉」。
17. **物品类里绝对不能出现客户端类**：`HandheldDrillItem` 曾直接读 `Minecraft.getInstance().player` 算进度，类校验时加载 `LocalPlayer` → **专用服务端崩在 `Registration.<clinit>`**。客户端取数放 `client/DrillBars`（方法签名不含客户端类型），服务端就能正常加载。改完务必 `sh gradlew runServer` 验证。
18. **手持钻的数值都在 serverconfig**：`handDrillTicks`/`handDrillFuelPerUnit`/`handDrillFuelCapacity`/`handDrillRadius`（探测半径，区块，默认 1 = 本区块 + 相邻一圈）/`handDrillSearchRadius`（默认 256 格，找最近矿簇）。燃料走 NeoForge 的 `getBurnTime` 数据表（没有白名单），加燃料在 `onItemUseFirst` 里先于方块交互执行。能钻的方块是方块标签 `#createoreexcavation:handheld_drill_surface`（默认 = `#minecraft:base_stone_overworld` + `#minecraft:base_stone_nether` + 基岩）。改了默认值要删掉实例里的旧 `createoreexcavation-server.toml` 才会生效。
19. **Xaero 集成是软依赖**：开发环境里用 `implementation` 引入小地图/世界地图（为了能真的跑起来测），但 `neoforge.mods.toml` 不声明依赖；所有 Xaero 类引用必须在 `client/XaeroWaypoints` 内，入口用 `CreateOreExcavation.xaero` 门控。
20. **调试服务端不用开客户端**：`sh gradlew runServer` 可无头验证数据加载与矿脉生成（配置写在 `runs/server/`），比开客户端快很多。要连矿脉一起验就自己往 `runs/server/kubejs/` 里放一条（见 `docs/data-and-recipes.md`）；**临时脚本最后一定要 `server.runCommand('stop')`**，否则脚本一抛异常服务端就会跑到超时。`runData` 不执行 KubeJS 脚本。
21. **改注册数据组件/存档键要同步清理旧数据**：开发阶段不保留向前兼容（`OreData` 的 `cluster` 键、`hand_drill_fuel` 组件），旧的 `runs/` 或存档直接删掉重生成。
