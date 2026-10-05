# 编写矿脉与挖掘配方

模组只提供**机制**：它注册 `vein`、`drilling`、`extracting` 三种配方类型，以及机器、手持钻、图鉴和各类集成。**矿脉、矿簇、产出表、特殊矿簇物品都不在模组里**——由数据包或整合包作者提供。本文档给出把这些内容写出来所需的最小信息。

需要了解运行时怎么挑矿脉、怎么算储量，见 [`architecture.md`](architecture.md) 第 4、5 节。

## 1. 放在哪里

| 方式 | 位置 | 需要 |
|---|---|---|
| KubeJS 的数据包 | `<实例>/kubejs/data/createoreexcavation/**` | 装 KubeJS |
| 普通数据包 | `<存档>/datapacks/<任意名>/data/createoreexcavation/**` | 什么都不用装 |
| KubeJS 脚本 | `<实例>/kubejs/server_scripts/*.js` | 装 KubeJS（见第 10 节） |

- 数据包目录用**单数**（MC 1.21）：`recipe/`、`tags/`、`loot_table/`。
- KubeJS 会把 `kubejs/data` 当数据包加载，且**插在所有 mod 之前**，所以同路径文件会覆盖模组的文件。
- `recipe/` 下的子目录名就是配方 ID 的一部分：`data/<ns>/recipe/ore_vein_type/foo.json` 的 ID 是 `<ns>:ore_vein_type/foo`（**不是** `<ns>:foo`）。`veinId` 写错只会表现为机器上「Couldn't find a valid recipe for this vein」。

```
data/<你的命名空间>/
├── recipe/
│   ├── ore_vein_type/   # 矿脉与矿簇
│   ├── drilling/        # 钻机（产出物品）
│   └── extractor/       # 抽取器（产出流体）
└── tags/worldgen/biome/ # 群系分组
```

## 2. 一条矿脉：`createoreexcavation:vein`

```json
{
  "type": "createoreexcavation:vein",
  "name": "{\"translate\":\"vein.coe.name\",\"with\":[{\"translate\":\"mypack.mineral.copper\"}]}",
  "priority": 0,
  "rarity": "common",
  "biomeWhitelist": "minecraft:is_overworld",
  "finite": "default",
  "density": 256,
  "reserve": { "min": 20000, "max": 200000, "mean": 40000, "sigma": 10000 },
  "regenTicks": 5184000,
  "waypointColor": "gold",
  "placement": { "spacing": 64, "separation": 8, "salt": 902453979 },
  "icon": { "count": 1, "id": "minecraft:raw_copper" }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | 文本组件（JSON 字符串） | 显示名。用翻译键 + 语言文件（第 7 节），或直接写 `{"text":"Copper"}`（不跟随玩家语言） |
| `priority` | int | 生成优先级；同一区块多条矿脉竞争时高者胜 |
| `biomeWhitelist` / `biomeBlacklist` | 群系标签，可省 | 各只能填**一个**标签；白名单存在时只在这些群系生成，黑名单优先排除 |
| `finite` | `never` / `default` / `always` | 是否有限；`default` 由 serverconfig 的 `defaultInfinite` 决定。矿簇恒为 `always` |
| `rarity` | `common` / `rare` | 只是标签（JEI 显示「常见/稀有矿物」）与航标回退色，不决定数值 |
| `density` | int | **所有群系**的网格间距（单位区块）：**负数 = 不上网格**，`0` = 每区块一条，`n>0` = 每 n×n 区块一条 |
| `reserve` | `{min,max,mean,sigma}` | **所有群系**的储量：正态采样（以 `mean` 为中心、`sigma` 为宽）后截断在 `min`–`max`；`finite: never` 时用不到 |
| `regenTicks` | int，可省 | 枯竭后回满所需刻数，默认 `5184000`（3 天）；`0` = 永不恢复 |
| `biomeOverrides` | 数组，可省 | 特殊群系里**额外一层**分布，见第 3 节 |
| `chunks` | 数组，可省 | **定点区块** `[[x, z], ...]`，见第 3 节 |
| `waypointColor` | 字符串，可省 | Xaero 航标颜色名（`black`/`gold`/`light_blue`…，大小写不敏感）。不写：矿脉按 `rarity`（稀有金、常见白），矿簇灰 |
| `cluster` | bool | 矿簇，见第 4 节 |
| `placement` | `{spacing, separation, salt}` | 复用原版 `RandomSpreadStructurePlacement`；`spacing` 只给 `density` 为负又没有 `chunks` 的情况当枚举回退，`salt` 决定网格相位（**每条矿脉必须不同**） |
| `icon` | 物品栈 | JEI/图鉴里的图标 |

## 3. 多层分布与定点区块

`density`/`reserve` 是**所有群系**都有的基础层；`biomeOverrides` 的每条 entry 在它命中的群系里**再加一层**。判定按书写顺序：entry 在前、基础层最后，**第一个命中的层胜出**：

```json
"density": -1,
"reserve": { "min": 20000, "max": 200000, "mean": 40000, "sigma": 10000 },
"biomeOverrides": [
  { "target": "#mypack:mountain", "density": 128, "reserve": { "min": 20000, "max": 200000, "mean": 150000, "sigma": 25000 } },
  { "target": "minecraft:stony_peaks", "density": 64 }
]
```

- `target`：**一个**群系标签（`#minecraft:is_mountain`）或**一个**群系 id（`minecraft:stony_peaks`，不带 `#`）。要「多个群系」就自己建标签（第 6 节）。
- 省略 `density` 或 `reserve` = 沿用基础层的值；`density: -1` 的基础层不上网格，于是只在命中的群系里生成。
- 「到处都稀疏 + 山里更密更大」= 基础层 + 一条山地 entry，两张网格会叠加，同种矿脉在山里更密、更富。
- 每条分布都会在 JEI 里单独占一页，页名是 `铜矿脉 （山地与丘陵）`。

**定点矿脉**：`chunks` 列出的区块一定有这条矿脉，**不看群系与网格**（多条定点矿脉争同一区块时 `priority` 高者胜）。配合 `density: -1` 就是「只在这些区块生成」：

```json
"density": -1,
"chunks": [[100, 200], [101, 200]],
"placement": { "spacing": 64, "separation": 8, "salt": 424242 }
```

区块坐标就是 F3 的 `Chunk` 行；`placement` 仍要填（`/coe locate` 靠它枚举）。想写死储量就把 `reserve` 写成 `{ "min": N, "max": N, "mean": N, "sigma": 0 }`。

## 4. 矿簇

同一条 `vein` 配方加 `"cluster": true`：**只用手持钻开采/探测**，探矿杖、钻机与抽取器都看不到它，枯竭后从世界消失且不再生（`regenTicks` 无效）。常见约定：

```json
{
  "type": "createoreexcavation:vein",
  "name": "{\"translate\":\"vein.coe.cluster_name\",\"with\":[{\"text\":\"Copper\"}]}",
  "priority": 0,
  "biomeWhitelist": "minecraft:is_overworld",
  "finite": "always",
  "density": 8,
  "reserve": { "min": 32, "max": 128, "mean": 64, "sigma": 16 },
  "placement": { "spacing": 64, "separation": 8, "salt": 123456 },
  "icon": { "count": 1, "id": "minecraft:raw_copper" },
  "cluster": true
}
```

## 5. 产出：`drilling` 与 `extracting`

一条矿脉的产出由 `veinId` 指向它的配方决定；没有则机器显示「Couldn't find a valid recipe for this vein」。

```json
{
  "type": "createoreexcavation:drilling",
  "veinId": "mypack:ore_vein_type/copper",
  "drill": { "tag": "createoreexcavation:drills" },
  "priority": 0,
  "ticks": 200,
  "stress": 256,
  "output": [
    { "id": "minecraft:raw_copper" },
    { "chance": 0.1, "id": "minecraft:raw_gold" }
  ]
}
```

```json
{
  "type": "createoreexcavation:extracting",
  "veinId": "mypack:ore_vein_type/water",
  "priority": 0,
  "ticks": 20,
  "stress": 256,
  "output": { "amount": 500, "id": "minecraft:water" }
}
```

| 字段 | 说明 |
|---|---|
| `veinId` | 某条 `vein` 配方的 ID（**带目录名**，见第 1 节） |
| `drill` | 物品 `Ingredient`，可省（省略 = 任意钻头）；一般写 `{ "tag": "createoreexcavation:drills" }` |
| `ticks` | 32 RPM 下完成一次开采的 tick 数 |
| `stress` | 应力倍率（× RPM），默认 256 |
| `fluid` | `SizedFluidIngredient`，可省（钻井液） |
| `priority` | 同一矿脉多条配方时的优先级（升序，后面覆盖前面） |
| `output` | 物品列表（Create `ProcessingOutput`，可带 `chance`）/ 单个流体栈 |

## 6. 群系标签与它的显示名

「一组群系」就是一个普通的群系标签：数据包写 `data/<ns>/tags/worldgen/biome/<name>.json`：

```json
{ "values": ["#minecraft:is_mountain", "#minecraft:is_hill"] }
```

配方里用 `"biomeWhitelist": "<ns>:<name>"` 或 `"target": "#<ns>:<name>"`。**标签写错或缺失不会报错**，只会静默变成空标签 → 那条分布永不生成。

标签本身没有名字，JEI 页标题（`铜矿脉 （山地与丘陵）`）与图鉴都要显示它，所以按 `biome_tag.<标签命名空间>.<路径>` 查翻译键（单个群系仍用原版 `biome.<命名空间>.<路径>`）：

```json
{ "biome_tag.mypack.mountain": "山地与丘陵" }
```

没有这个键时**直接显示标签 id**，不报错。译文放资源包，装了 KubeJS 就放 `kubejs/assets/<命名空间>/lang/<语种>.json`。

## 7. 名字与语言

模组自带三个**格式**键：`vein.coe.name`（`%s Vein` / `%s矿脉`）、`vein.coe.cluster_name`（`%s Cluster` / `%s矿簇`）、`vein.coe.name.biome`（`%s (%s)`，多分布页）。围绕它们用自己的矿物键：

```json
"name": "{\"translate\":\"vein.coe.name\",\"with\":[{\"translate\":\"mypack.mineral.copper\"}]}"
```

```json
{ "mypack.mineral.copper": "铜" }
```

**数据包不能带语言文件**，译文要放资源包（KubeJS 的 `kubejs/assets/` 就是资源包，见第 10 节）；不想翻译就直接写 `{"text":"Copper"}`，但它不会跟随玩家语言。

## 8. 自定义物品与贴图

模组**不带**任何矿物物品（除了 `raw_diamond`/`raw_emerald`/`raw_redstone`——它们参与模组自己的 Create 加工配方）。要自己的物品当图标或产物，用数据包做不到，得用 KubeJS 启动脚本注册：

```js
StartupEvents.registry('item', event => {
  event.create('mypack:magnetite')            // 模型自动生成，贴图放 kubejs/assets/mypack/textures/item/magnetite.png
});

// 想放进模组的创造标签页（ItemBuilder.group() 已弃用）
StartupEvents.modifyCreativeTab('createoreexcavation:create_ore_excavation', event => {
  event.add('mypack:magnetite');
});
```

名字是原版描述键（`item.mypack.magnetite`），写在 `kubejs/assets/mypack/lang/<语种>.json` 里；动画贴图再加一个同名 `.png.mcmeta`（`{ "animation": { "frametime": 2 } }`）。

## 9. 用别的 mod 的物品

配方里出现别的 mod 的物品时**必须**加条件，否则没装那个 mod 的实例解码就炸（物品 id 未知）：

```json
"neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "tfmg" }]
```

NeoForge 在交给配方 codec **之前**读这个键（键名就是 `neoforge:conditions`），条件不满足时整条配方被跳过。**成对的矿脉与钻探配方要带相同的条件**，否则会出现「有矿脉没配方」。用 KubeJS **脚本**路线时没有条件键，自己挡：`if (!Platform.isLoaded('tfmg')) return`。

## 10. KubeJS 脚本写法

脚本与数据包二选一即可。带概率的产物用 `coeutil.processingOutput(item, chance)`：

```js
ServerEvents.recipes(event => {
  event.recipes.createoreexcavation.vein('{"translate":"vein.coe.name","with":[{"translate":"mypack.mineral.copper"}]}', 'minecraft:raw_copper')
    .placement(64, 8, 902453979)        // spacing, separation, salt
    .density(256)                        // 网格间距（区块），负数 = 不上网格，0 = 每区块
    .reserve(20000, 200000, 40000).reserveSigma(10000)
    .regenTicks(5184000)
    .waypointColor('gold')
    .biomeWhitelist('minecraft:is_overworld')          // 各一个标签
    .biomeOverride('#minecraft:is_mountain', 128, 20000, 200000, 150000, 25000)
    .biomeOverrideDensity('#minecraft:is_plains', 64)
    .chunk(100, 200)                    // 定点区块，可连调
    .priority(0)
    .id("mypack:ore_vein_type/copper")
  event.recipes.createoreexcavation.drilling(
      [ 'minecraft:raw_copper', coeutil.processingOutput('minecraft:raw_gold', 0.1) ],
      'mypack:ore_vein_type/copper', 200)
    .drill('createoreexcavation:drills').stress(256)
    .id("mypack:drilling/copper")

  // 矿簇 / 流体
  event.recipes.createoreexcavation.vein('{"text":"My cluster"}', 'minecraft:raw_gold')
    .placement(64, 8, 123456).cluster().rare().id("mypack:ore_vein_type/my_cluster")
  event.recipes.createoreexcavation.extracting('2Bx minecraft:water', 'mypack:ore_vein_type/water', 10)
    .fluid('10x minecraft:lava').id("mypack:extractor/water")
});
```

其它可用函数：`.alwaysFinite()` / `.alwaysInfinite()` / `.finite(...)`、`.rare()` / `.common()`，钻探侧的 `.drill(...)` / `.fluid(...)` / `.stress(...)` / `.priority(...)`。

自己的钻头物品要加进 `#createoreexcavation:drills` 物品标签；钻头渲染贴图放 `assets/<modid>/textures/entity/drill/<item>.png`。

## 11. 改完怎么生效

矿脉是**每区块的落盘数据**，所以：

- `/reload` 或重启会重新加载配方，但**已经有数据的区块不会自己变**。用 `/coe regenerate [vein|cluster] all`（当前维度）/ `<dimension> all` / `<radius>` 标记要重选的区块，**惰性生效**（区块下次被读取时才重选），详见 [`architecture.md`](architecture.md) 13.1。
- 想连种子一起换（例如重排全网矿簇）：`/coe regenerate cluster all <数字>`。
- 删掉文件不会移除已经生成过的区块里的矿脉，同样要 `/coe regenerate`。
