# 数据、配方与 KubeJS

路径相对 `NeoForge/`。

## 1. 数据目录

MC 1.21 起使用**单数**目录名：

```
data/createoreexcavation/
├── recipe/
│   ├── drilling/        # createoreexcavation:drilling
│   ├── extractor/       # createoreexcavation:extracting
│   └── ore_vein_type/   # createoreexcavation:vein
├── loot_table/blocks/   # 方块掉落
└── tags/item/           # 例如 drills
assets/createoreexcavation/
├── lang/                # en_us 生成；其它语种手写（在 platform-shared/resources）
├── blockstates/  models/  textures/  ponder/  atlases/
```

> Fabric 1.20 线用的是复数 `recipes/`、`loot_tables/`、`advancements/`、`tags/items/`。

## 2. 矿脉：`createoreexcavation:vein`

描述「什么矿脉、在哪里生成」。例（`recipe/ore_vein_type/copper.json`）：

```json
{
  "type": "createoreexcavation:vein",
  "name": "{\"translate\":\"item.minecraft.raw_copper\"}",
  "priority": 0,
  "biomeWhitelist": "minecraft:is_overworld",
  "finite": "default",
  "amountMultiplierMin": 10.0,
  "amountMultiplierMax": 30.0,
  "placement": { "spacing": 128, "separation": 8, "salt": 277506605 },
  "icon": { "count": 1, "id": "minecraft:raw_copper" }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | 文本组件（JSON 字符串） | 显示名，可 `{"translate":...}` 或 `{"text":...}` |
| `priority` | int | 生成优先级；同一位置多矿脉竞争时高者胜 |
| `biomeWhitelist` / `biomeBlacklist` | 生物群系标签，可省 | 白名单存在时只在这些群系生成；黑名单优先排除 |
| `finite` | `never` / `default` / `always` | 是否有限；`default` 由配置 `defaultInfinite` 决定 |
| `amountMultiplierMin` / `Max` | float | 有限矿脉总量倍率（乘配置 `finiteAmountBase`） |
| `placement` | `{spacing, separation, salt}` | 复用原版 `RandomSpreadStructurePlacement` 算法决定区块 |
| `icon` | 物品栈 | JEI/图鉴里的图标 |

矿脉**不是方块**，是靠「世界种子 + placement + 生物群系」在区块上算出来的数据，详见 `architecture.md` 第 5 节。

## 3. 采矿：`drilling` 与 `extracting`

### 钻机（物品产出）`createoreexcavation:drilling`

```json
{
  "type": "createoreexcavation:drilling",
  "veinId": "createoreexcavation:ore_vein_type/coal",
  "drill": { "tag": "createoreexcavation:drills" },
  "priority": 0,
  "ticks": 200,
  "stress": 256,
  "output": [ { "id": "minecraft:coal" } ]
}
```

### 抽取器（流体产出）`createoreexcavation:extracting`

```json
{
  "type": "createoreexcavation:extracting",
  "veinId": "createoreexcavation:ore_vein_type/water",
  "drill": { "tag": "createoreexcavation:drills" },
  "priority": 0,
  "ticks": 20,
  "stress": 256,
  "output": { "amount": 500, "id": "minecraft:water" }
}
```

公共字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `veinId` | 资源位置 | 必须是某条 `vein` 配方的 ID |
| `drill` | 物品 `Ingredient`，可省 | 需要的钻头；省略则任意 |
| `priority` | int | 同一矿脉多条配方时的选择优先级（升序，后面覆盖前面） |
| `ticks` | int | 32 RPM 下完成一次开采的 tick 数 |
| `stress` | int | 应力倍率（× RPM），默认 256 |
| `fluid` | `SizedFluidIngredient`，可省 | 需要的钻井液 |
| `output` | 物品列表 / 单个流体栈 | 见上两例；物品输出支持 Create `ProcessingOutput`（带概率） |

配方默认由 `data/COERecipes.java` 生成；也可写成数据包/`kubejs` 脚本。

## 4. KubeJS

插件入口由 `platform-shared/resources/kubejs.plugins.txt` 声明：`com.tom.createores.kubejs.KubeJSExcavation`。相关类在 `platform-shared/java/com/tom/createores/kubejs/`（`VeinRecipeJS`、`DrillingRecipeJS`、`ExtractorRecipeJS` 等）。

1.21 语法（`coeutil` 是注册的绑定）：

```js
ServerEvents.recipes(event => {
  // 加矿脉：.placement(spacing, separation, salt)
  // 三项相同会互相覆盖；用 .priority(n) 设生成优先级
  event.recipes.createoreexcavation.vein('{"text": "My redstone vein"}', 'minecraft:redstone')
    .placement(1024, 128, 64825185)
    .id("kubejs:my_redstone_vein")

  // 钻机配方：输出、矿脉 ID、tick 数
  event.recipes.createoreexcavation.drilling('minecraft:redstone', 'kubejs:my_redstone_vein', 100)
    .id("kubejs:my_vein1")

  // 有限矿脉（5x–8x 基础量），带 5% 钻石概率，需钻石钻头 + 岩浆
  event.recipes.createoreexcavation.vein('{"text": "My coal vein"}', 'minecraft:coal')
    .placement(2048, 128, 64457512).alwaysFinite().veinSize(5, 8).id("kubejs:my_coal_vein")

  event.recipes.createoreexcavation.drilling(
      [ 'minecraft:coal_block', coeutil.processingOutput('minecraft:diamond', 0.05) ],
      'kubejs:my_coal_vein', 500)
    .drill('createoreexcavation:diamond_drill').fluid('minecraft:lava').priority(1)
    .id("kubejs:my_coal2")

  // 生物群系限制 + 更高应力
  event.recipes.createoreexcavation.vein('{"text": "My iron vein"}', 'minecraft:iron_ore')
    .placement(1024, 128, 6894685).veinSize(3, 8.5).biomeWhitelist('forge:is_overworld')
    .id("kubejs:my_iron_vein")
  event.recipes.createoreexcavation.drilling('minecraft:raw_iron', 'kubejs:my_iron_vein', 100)
    .stress(512).id("kubejs:my_vein3")

  // 流体抽取
  event.recipes.createoreexcavation.vein('{"text": "Water well"}', 'minecraft:water_bucket')
    .placement(1024, 128, 64630185).alwaysInfinite().id("kubejs:my_water_well")
  event.recipes.createoreexcavation.extracting('2Bx minecraft:water', 'kubejs:my_water_well', 10)
    .fluid('10x minecraft:lava').id("kubejs:test")
});
```

要点：

- `coeutil.processingOutput(item, chance)`：带概率的物品输出（1.21 相比 1.20 的变化）。
- `.alwaysFinite()` / `.alwaysInfinite()` / `.veinSize(min, max)` 控制有限性与倍率范围。
- `.biomeWhitelist(...)` / `.biomeBlacklist(...)`。
- `.drill(...)` / `.fluid(...)` / `.stress(...)` / `.priority(...)`。
- 新钻头物品要加进 `#createoreexcavation:drills` 标签；钻头渲染贴图放 `assets/<modid>/textures/entity/drill/<item>.png`。

## 5. 语言与资源

- 语言键前缀：`chat.coe.*`、`info.coe.*`、`tooltip.coe.*`、`config.coe.*`、`command.coe.*`、`jei.coe.*`、`jm.coe.*`、`tag.item.createoreexcavation.drills`、`upgrade.createoreexcavation.*`。
- 在 `Registration.register()` 里用 `add(key, value)` 登记，`runData` 生成 `assets/.../lang/en_us.json`（与反向的 `en_ud.json`）。
- 其它语种手写在 `src/platform-shared/resources/assets/createoreexcavation/lang/`：`es_es`、`es_mx`、`it_it`、`ja_jp`、`ko_kr`、`nl_nl`、`pt_br`、`ru_ru`、`zh_cn`。
- 贴图/模型/Ponder 脚本：`src/platform-shared/resources/assets/createoreexcavation/`（`textures/block`、`textures/item`、`textures/entity/drill`、`models`、`ponder/*.nbt`、`atlases`）。

## 6. 生成这些数据的代码

- `src/main/java/com/tom/createores/data/COERecipes.java` — 所有原版配方（工作台、机械合成、冶炼等），`buildRecipes()` 里逐个构造。
- `src/main/java/com/tom/createores/data/DataGenerators.java` — 注册 `COERecipes` 与 Registrate LANG 提供器。
- 生成命令：`cd NeoForge && ./gradlew runData`。
