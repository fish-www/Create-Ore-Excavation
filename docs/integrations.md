# 可选集成

| 集成 | 主线（NeoForge） | Fabric | 代码位置 |
|---|---|---|---|
| JEI | ✅ | ✅ | `platform-shared/…/jei/` |
| REI | ❌ | ✅ | `Fabric/…/rei/` |
| EMI | ❌ | ✅ | `Fabric/…/emi/` |
| Xaero's Minimap / World Map | ✅ | ❌ | `platform-shared/…/client/XaeroWaypoints.java` |
| CC:Tweaked | ✅ | ✅ | `platform-shared/…/cc/` |
| KubeJS | ✅ | ✅ | `platform-shared/…/kubejs/` |
| Jade / TOP | 运行期依赖 | — | 仅用于 tooltip 展示 |

地图标点：**矿脉**由探矿杖（右键，标记本区块 + 周围一圈；另有 `/coe locate`、`/coe discover`）标记到它所在的区块；**矿簇**由手持钻**右键**探测（同样是本区块 + 周围一圈，不自动探测）标记。矿簇挖空后服务端发 `OreVeinDiscoverPacket` 的 `REMOVE` 模式，客户端删掉对应航点。探矿杖与手持钻的定位逻辑共用 `RandomSpreadGenerator.locate`，并且都传 `VeinRegeneration.seedFor(...)`（`/coe regenerate` 设过的自定义种子，不设就是世界种子），否则预测与实际落盘的会对不上。

## 手持钻的交互

没有界面：任意一只手拿着钻对着**可钻方块**（`#createoreexcavation:handheld_drill_surface`）右键开采（开采前先检查燃料与矿簇），对着**别的方块或空气**右键探测 `handDrillRadius`（serverconfig，默认 1 = 3×3 区块）内的矿簇（本区块没矿簇时报最近矿簇的距离），另一只手拿可烧物品右键加注燃料（加燃料走 `onItemUseFirst`，所以右键箱子/工作台也会优先加燃料）。详见 `architecture.md` 第 12 节。

## JEI（主线）

- 插件：`platform-shared/java/com/tom/createores/jei/JEIHandler.java`，`@JeiPlugin`。
- 四个配方类别：`DrillingCategory`（机器矿脉的「矿物开采」）、`HandheldDrillingCategory`（矿簇的「手持钻开采」，JEI 类型 `createoreexcavation:handheld_drilling`）、`ExtractingCategory`、`VeinCategory`。前三个直接从 `RecipeManager` 取配方（`drilling` 配方按「矿脉是不是矿簇」分流到前两类），`VeinCategory` 展示 `VeinDisplay`。
- **一页 = 一条矿脉的一个分布**：`VeinDisplay` = `{vein 配方, entry}`，entry 0 是基础分布，entry i 是 `biomeOverrides[i-1]`（`VeinRecipe.entryCount/layerForEntry`）；`JEIHandler.buildVeinDisplays()` 按「矿物 → 矿脉/矿簇 → 配方 id」排序、基础分布在前后注册，因此同一矿物在各群系的矿脉在列表里相邻。不会生成的分布没有页面：判断用 `VeinRecipe.entryGenerates(entry)`（`spacing > 0 || 有 chunks`，机台页的矿脉槽也用同一个），只写 `chunks` + `density: -1` 的定点矿脉也算会生成。`VeinIngredient` 注册的原料列表用**同一份**列表，保证列表名字与页面标题一致。
- 名称：`vein.coe.name`（`%s矿脉`）与 `vein.coe.cluster_name`（`%s矿簇`，参数是矿物名）→ `铁矿簇`。这个名字也进配方 JSON，所以地图航点、手持钻提示、图鉴都是它。**基础分布只用矿脉名**（`铁矿脉`），其他分布再套 `vein.coe.name.biome`（→ `铁矿脉（山地与丘陵）`）：target 是群系 id 时用原版的 `biome.<id>`，是标签时用 `biome_tag.<ns>.<路径>`（没有这个键就显示标签 id）。`getUid` 必须带 entry，否则同一矿脉的各条分布会被 JEI 当成同一个原料。搜索「矿脉」「铁」「粗铁」都能找到。
- 物品页（原料 tooltip，`VeinIngredient.getTooltip` → `VeinInfoUtil.details`）：稀有度、`tooltip.coe.reserve`（中值 + `min - max`）、`jei.coe.vein_density`（约间距几区块）、`jei.coe.cluster_depleted`（矿簇）/`tooltip.coe.regen`（矿脉，具体再生耗时，见 `VeinRecipe.getRegenDescription`）；图标里矿簇画手持钻、矿脉画钻头。`getDisplayName` 用 `VeinInfoUtil.title(...)`。
- 配方页（`VeinCategory`，一页一分布）：中间是矿脉槽，右侧图标——树苗 = 白名单（`tooltip.coe.biome.whitelist`）、树苗 + 屏障 = 黑名单（`tooltip.coe.biome.blacklist`），列的是**实际生效的群系**（`VeinRecipe.entryApplies()` 逐群系判断：基础分布除黑名单外全部生效，其他分布只在其 target 命中的群系生效，且该分布 spacing > 0 或有定点区块）；**配方带 `chunks` 时额外画一个地图图标（`Items.FILLED_MAP`，y=45；只看 `chunks` 不跑网格的分布改画在 y=5，替掉群系图标），悬停列出区块坐标**（`BiomeTooltip.list`，与群系名单同一套分页）。鼠标在图标上才展开列表，超过 16 条自动分页。页面 tooltip 与槽位 tooltip 都走 `VeinInfoUtil`，**JEI 只会显示二者之一**（鼠标在槽位上时只调 slot 的 tooltip，否则才调 `IRecipeCategory.getTooltip`），所以两处都要写。
- 用途页：机器矿脉 = 钻机页（钻头槽 + 灌浆槽 + 矿脉槽，悬停动画显示刻数与应力）；矿簇 = 手持钻页（只有矿脉槽 + 产物表，画手持钻，悬停显示开采耗时与消耗燃料）。矿脉槽里放的是**该矿脉全部生效的分布**，所以每条分布都能查到这个用途页。
- 产物表按概率降序排、每行 7 个，行数多了自动加高页面（`getHeight()` 按该类配方里最长的产物表算，所以短配方页会多出空白——JEI 的页高是分类级的）。
- 催化剂：钻机 → drilling，抽取器 → extracting，寻脉器 → veins。
- 自定义原料 `Vein`（`Vein.java`）+ `VeinIngredient`（同时实现 `IIngredientHelper` 与 `IIngredientRenderer`），把矿脉作为可搜索的 JEI 原料。
- 加载器桥接：`main/…/jei/JeiPlatform.java`。

**改 tooltip 时注意**：JEI 19.x 用 `IIngredientRenderer.getTooltip(ITooltipBuilder, …)` 构造槽位 tooltip，其默认实现会回落到已废弃的 `getTooltip(Vein, TooltipFlag)`（搜索索引也走后者）；两个重写都指向 `VeinInfoUtil`，所以两条路径一致。另一条坑见上面「JEI 只会显示二者之一」：只写在槽位或只写在页面都不行。群系名单在 `VeinInfoUtil.biomes()` 里按 `(配方, entry, 生成/不生成)` 缓存（每帧都重建会很慢），`JEIHandler.buildVeinDisplays()` 在注册时清一次缓存。

## REI / EMI（Fabric 线）

- REI：`Fabric/src/main/java/com/tom/createores/rei/`，`REIPlugin` + `DrillingCategory`/`ExcavatingCategory`/`ExtractingCategory`/`VeinCategory` 与对应 `*Display`。
- EMI：`Fabric/src/main/java/com/tom/createores/emi/`，`EMIPlugin` + 各类 `*EmiRecipe`。
- 主线不包含这两者；`JeiPlatform`/`ReiPlatform` 是加载器侧适配点。

## Xaero's Minimap / World Map

- **不是官方开放 API，而是 minimap 里的「第三方航点」接口**：`xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints.add(String id, Waypoint)`。
- 获取方式：`BuiltInHudModules.MINIMAP.getCurrentSession()` → `getWorldManager().getAutoRootContainer()` → `root.addSubContainer(root.getPath().resolve(dimensionDirectory))` → `getThirdPartyWaypointManager().get(originId)`。参考 Xaero 自己的 `xaero.hud.compat.mods.SupportWaystones`。
- 依赖（`build.gradle`，仓库 `https://chocolateminecraft.com/maven`）：
  `compileOnly "xaero.lib:xaerolib-neoforge-1.21.1:1.7.3"`（API 编译），
  `implementation "xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0"` 与 `implementation "xaero.map:xaeroworldmap-neoforge-1.21.1:1.47.0"`（只为了在开发环境里能真的跑起来测试；**不是**硬依赖，`neoforge.mods.toml` 里没有声明）。
- 入口：`CreateOreExcavation.xaero` 标志 + `DiscoveredVeinHandler` 门控；**Xaero 类只允许在 `XaeroWaypoints` 里引用**，否则没装 Xaero 时会 `NoClassDefFoundError`。
- 航点来源：`/coe discover`、`/coe locate`（`OreVeinDiscoverPacket`，`ADD`/`REMOVE`/`CLEAR`）与探矿杖/手持钻探测（探矿杖：`OreVeinInfoPacket` 的 `found` 项 + 周围一圈的 `VeinMarkers`；手持钻：`handDrillRadius` 的 3×3 区块扫描）。
- 航点 id 是 `vein|配方@区块X/区块Z` / `cluster|配方@区块X/区块Z`，所以同一位置只会有一个航点，且 `clear vein`/`clear cluster` 不用查配方就能只清一类；服务端 `VeinMarkers` 只发新点位，避免四处走动时反复弹提示。
- 航点名字直接用矿脉名（`vein.coe.name`/`vein.coe.cluster_name` 拼出来的 `铁矿脉`/`铁矿簇`）。
- 颜色写在**配方**里（`waypointColor`，如 `"gold"`）：`XaeroWaypoints.color()` 读 `VeinRecipe.getWaypointColor()` → `WaypointColor.valueOf(name.toUpperCase())`，名字不认识就记一条日志并当作没写。**不写**时的回退：矿脉按 `rarity`（稀有 `GOLD`、常见 `WHITE`），矿簇 `GRAY`。想让某条矿脉有专属颜色就在它的配方里写 `waypointColor`；同一物品的矿脉与矿簇可以各算各的。
- 改颜色后**已经标出的旧航点不会自动变色**（同 id 会跳过），要 `/coe discover clear` 后重新探测。

## CC:Tweaked

- 寻脉器可作为海龟升级：`platform-shared/java/com/tom/createores/cc/OreVeinFinderTurtle.java`（`AbstractTurtleUpgrade`），升级类型在 `CCRegistration` 注册，客户端初始化在 `CCClientInit`。
- 资源：`data/createoreexcavation/computercraft/turtle_upgrade/vein_finder.json`（Fabric 生成的是复数 `turtle_upgrades/`）。
- Lua API 只有一个方法：`local useSuccess, veinFound, veinId, veinSize = finder.search()`。
- 入口里用 `isModLoaded("computercraft")` 判断后初始化；`Registration.register()` 末尾也会调 `CCRegistration.init()`。

## KubeJS

- 插件：`KubeJSExcavation`，由 `platform-shared/resources/kubejs.plugins.txt` 声明；注册三种配方的 schema/factory、组件、类型包装，并绑定 `coeutil`。
- 与 `kubejs_create` 共存时会切换处理 `ProcessingOutput` 的方式（见 `KubeJSExcavation.registerRecipeComponents`）。
- 脚本写法与字段见 [`data-and-recipes.md`](data-and-recipes.md) 第 6 节。

## Jade / The One Probe

- 主线 `build.gradle` 依赖 `curse.maven:adorned`、`the-one-probe`、`jade` 作为运行期集成，用于在对应模组的 tooltip 中显示机器信息；逻辑通过 `TooltipUtil` / `util/BiomeTooltip` 与 goggle tooltip 共用。

## 加新集成的通用套路

1. 插件/入口放 `platform-shared`（若与加载器无关），加载器专属桥接放各自 `main`。
2. 在 `CreateOreExcavation`（或 Fabric 的 `onInitialize`）里用 `isModLoaded("<id>")` 判断后再注册，避免硬依赖。
3. 依赖声明：NeoForge 加在 `NeoForge/build.gradle`，Fabric 加在 `Fabric/build.gradle`。
