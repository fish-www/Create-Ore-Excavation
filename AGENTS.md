# Create Ore Excavation — 开发与维护指南

用 Create 的旋转动力驱动钻机开采「矿脉」的 Minecraft 模组。矿脉不是真实方块，而是**按区块存储的世界数据**。

- 作者：tom5454 · 协议：MIT · Mod ID：`createoreexcavation`
- 主线：**NeoForge / MC 1.21.1**（版本 `1.6.8`，见 `NeoForge/gradle.properties`）
- 旧线：**Fabric / MC 1.20.1**（版本 `1.5.4`），移植滞后且当前编译不可用，见 `docs/porting.md`
- 详细文档在 [`docs/`](docs/README.md)

> 本文件是给「接手这个仓库的人 / AI Agent」的入口。改代码前先读 `docs/architecture.md` 和 `docs/development.md`。

## 下一步做什么

- 想跑起来：`cd NeoForge && ./gradlew runClient`
- 改了注册器/配方/语言文本：`cd NeoForge && ./gradlew runData` 后提交 `src/generated/resources`
- 改共享逻辑：优先动 `NeoForge/src/platform-shared/java`，不要只改 `main`

## 仓库结构

```
.
├── NeoForge/                  # 主线工程（MC 1.21.1 / Java 21 / NeoForge）
│   ├── build.gradle
│   ├── gradle.properties      # 版本号与依赖版本都在这
│   ├── src/main/java          # 加载器专属代码（入口、配置、数据生成、网络、能力）
│   ├── src/platform-shared/java   # 跨加载器共享代码（方块、配方、GUI、KubeJS、多方块…）
│   ├── src/main/resources     # neoforge.mods.toml
│   ├── src/platform-shared/resources  # 静态 assets/lang/贴图/pack.mcmeta
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
| 加载器无关或仅主线用 | `NeoForge/src/platform-shared/java` | `Registration`、`VeinRecipe`、`OreData`、多方块方块、JEI/KubeJS/JM 插件、菜单、网络包 |
| NeoForge 专属 | `NeoForge/src/main/java` | `CreateOreExcavation`（`@Mod` 入口）、`ForgeConfig`、`DataGenerators`、`NetworkHandler`、`OreDataAttachment`、能力注册 |
| Fabric 专属 | `Fabric/src/main/java` | 该加载器的对应实现 |

⚠️ `platform-shared` **并非严格与加载器无关**：`Config.java` 直接用 NeoForge 的 `ModConfigSpec`。新增共享代码时别假设它能直接给 Fabric 用。

## 常用命令

从 `NeoForge/` 目录执行：

```bash
./gradlew runClient     # 开发客户端
./gradlew runServer     # 开发服务端
./gradlew runData       # 数据生成，输出到 src/generated/resources
./gradlew build         # 打包 + sources jar
./gradlew gameTestServer  # 跑 gametest（当前没有注册用例，会退出）
```

Fabric（1.20.1，需先修复源集路径，见 `docs/porting.md`）：

```bash
cd Fabric
./gradlew runDatagen    # 数据生成
./gradlew runClient
```

## 约定

- 代码用 `com.tom.createores` 包名（注意是 `createores`，不是 `createoreexcavation`）。
- 注册一律走 **Registrate**（`Registration.java`），模型/语言/掉落物也用它做数据生成。
- `ResourceLocation` 用 `ResourceLocation.tryBuild` / `ResourceLocation.parse`。
- 语言键统一前缀：`chat.coe.` / `info.coe.` / `tooltip.coe.` / `config.coe.` / `command.coe.` / `jei.coe.` / `jm.coe.`，在 `Registration.register()` 里用 `add(key, value)` 登记，生成进 `lang/en_us.json`；其余语种在 `platform-shared/resources/.../lang/`。
- 1.21 的数据目录用**单数**：`data/<ns>/recipe/`、`loot_table/`、`advancement/`；Fabric 1.20 用复数。
- 提交信息用 Conventional Commits，例如 `fix(jei): restore ore vein tooltips in JEI`。

## 已知坑

1. **Fabric 构建已损坏**：`Fabric/build.gradle` 仍引用 `../Forge/src/platform-shared/...`，而该目录已重命名为 `NeoForge`。修复方式见 `docs/porting.md`。
2. **不要手改 `src/generated/resources`**：那是 `runData` 的产物，手改会在下次生成时被覆盖。
3. **持久化 ID 不能随意改名**：方块/物品/配方类型/数据组件的注册 ID 与 NBT/配方存档绑定；例如数据组件 ID 存在拼写 `ore_vein_altas_data`，改名需要迁移逻辑。
4. **发布要更新两处**：`NeoForge/gradle.properties` 的 `mod_version` 与根目录 `version-check.json` 的 promos（游戏内更新检查读它）。
5. **Fabric 的 `test` 任务被绑到 `runClient`**：不在 IDE 里跑时加 `-DuseLib=true` 跳过。
