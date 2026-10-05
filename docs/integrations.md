# 可选集成

| 集成 | 主线（NeoForge） | Fabric | 代码位置 |
|---|---|---|---|
| JEI | ✅ | ✅ | `platform-shared/…/jei/` |
| REI | ❌ | ✅ | `Fabric/…/rei/` |
| EMI | ❌ | ✅ | `Fabric/…/emi/` |
| JourneyMap | ✅ | ✅ | `platform-shared/…/jm/` |
| CC:Tweaked | ✅ | ✅ | `platform-shared/…/cc/` |
| KubeJS | ✅ | ✅ | `platform-shared/…/kubejs/` |
| Jade / TOP | 运行期依赖 | — | 仅用于 tooltip 展示 |

## JEI（主线）

- 插件：`platform-shared/java/com/tom/createores/jei/JEIHandler.java`，`@JeiPlugin`。
- 三个配方类别：`DrillingCategory`、`ExtractingCategory`、`VeinCategory`；在 `registerRecipes` 里直接从 `RecipeManager` 取三种配方类型。
- 催化剂：钻机 → drilling，抽取器 → extracting，寻脉器 → veins。
- 自定义原料 `Vein`（`Vein.java`）+ `VeinIngredient`（同时实现 `IIngredientHelper` 与 `IIngredientRenderer`），把矿脉作为可搜索的 JEI 原料。
- 加载器桥接：`main/…/jei/JeiPlatform.java`。

**改 tooltip 时注意**：JEI 19.x 通过 `IIngredientRenderer.getTooltip(ITooltipBuilder, …)` 构造 tooltip，其默认实现会回落到已废弃的 `getTooltip(Vein, TooltipFlag)`。因此矿脉名与「有限/无限」那两行要写在**废弃重载**里，再让 builder 版转调它——两条路径（富 tooltip 与搜索索引）才一致。这正是提交 `fix(jei): restore ore vein tooltips in JEI` 修的坑。

## REI / EMI（Fabric 线）

- REI：`Fabric/src/main/java/com/tom/createores/rei/`，`REIPlugin` + `DrillingCategory`/`ExcavatingCategory`/`ExtractingCategory`/`VeinCategory` 与对应 `*Display`。
- EMI：`Fabric/src/main/java/com/tom/createores/emi/`，`EMIPlugin` + 各类 `*EmiRecipe`。
- 主线不包含这两者；`JeiPlatform`/`ReiPlatform` 是加载器侧适配点。

## JourneyMap

- 插件：`platform-shared/java/com/tom/createores/jm/JMPlugin.java`，`@JourneyMapPlugin`。
- 覆盖层 `OreVeinsOverlay` 在小地图上画出已知矿脉；`OreVeinInfo`/`OreNearbyInfo`/`OreDistanceInfo` 描述数据。
- 全屏地图上加一个开关按钮，文本 `jm.coe.veinsOverlayToggle`，图标 `textures/gui/jm_coe_veins.png`。
- 加载器侧事件：`JMEventListener`（main 各一份）；入口里用 `ModList.isLoaded("journeymap")` 判断后再注册。

## CC:Tweaked

- 寻脉器可作为海龟升级：`platform-shared/java/com/tom/createores/cc/OreVeinFinderTurtle.java`（`AbstractTurtleUpgrade`），升级类型在 `CCRegistration` 注册，客户端初始化在 `CCClientInit`。
- 资源：`data/createoreexcavation/computercraft/turtle_upgrade/vein_finder.json`（Fabric 生成的是复数 `turtle_upgrades/`）。
- Lua API 只有一个方法：`local useSuccess, veinFound, veinId, veinSize = finder.search()`。
- 入口里用 `isModLoaded("computercraft")` 判断后初始化；`Registration.register()` 末尾也会调 `CCRegistration.init()`。

## KubeJS

- 插件：`KubeJSExcavation`，由 `platform-shared/resources/kubejs.plugins.txt` 声明；注册三种配方的 schema/factory、组件、类型包装，并绑定 `coeutil`。
- 与 `kubejs_create` 共存时会切换处理 `ProcessingOutput` 的方式（见 `KubeJSExcavation.registerRecipeComponents`）。
- 脚本写法与字段见 [`data-and-recipes.md`](data-and-recipes.md) 第 4 节。

## Jade / The One Probe

- 主线 `build.gradle` 依赖 `curse.maven:adorned`、`the-one-probe`、`jade` 作为运行期集成，用于在对应模组的 tooltip 中显示机器信息；逻辑通过 `TooltipUtil` / `util/BiomeTooltip` 与 goggle tooltip 共用。

## 加新集成的通用套路

1. 插件/入口放 `platform-shared`（若与加载器无关），加载器专属桥接放各自 `main`。
2. 在 `CreateOreExcavation`（或 Fabric 的 `onInitialize`）里用 `isModLoaded("<id>")` 判断后再注册，避免硬依赖。
3. 依赖声明：NeoForge 加在 `NeoForge/build.gradle`，Fabric 加在 `Fabric/build.gradle`。
