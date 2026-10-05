# 开发环境与流程

## 1. 前置

- **JDK 21**（NeoForge 主线）；Fabric 线用 JDK 17。工程自带 Gradle wrapper，不用另装 Gradle。
- 首次构建需联网拉依赖（仓库列表见 `NeoForge/build.gradle`，含 Create/Registrate/KubeJS 等自定义 maven、以及作者的 `raw.githubusercontent.com/tom5454/maven`）。
- `NeoForge/gradle.properties` 设了 `org.gradle.jvmargs=-Xmx3G`、`org.gradle.daemon=false`（反编译 MC 需要内存，首次会慢）。

## 2. 构建与运行（从 `NeoForge/`，`gradlew` 没有可执行位就写 `sh gradlew`）

```bash
sh gradlew build              # 打包 jar + sources jar
sh gradlew runClient          # 开发客户端
sh gradlew runServer          # 开发服务端（--nogui）
sh gradlew runData            # 数据生成 → src/generated/resources
sh gradlew gameTestServer     # 跑 gametest（没有注册用例，会直接结束）
sh gradlew clean
```

- 运行配置在 `build.gradle` 的 `runs { ... }` 里定义：`client`、`server`、`gameTestServer`、`data`；工作目录在 `runs/<名>/`。
- **开发环境额外加载的 mod**（`build.gradle` 里的 `implementation`，**不是**编译依赖，也不在 `neoforge.mods.toml` 里声明）：
  - Xaero 的小地图/世界地图 —— 为了能真的跑起来测航点集成（详见 `integrations.md`）。
  - `maven.modrinth:create-tfmg:uDi14nbt`（Create: The Factory Must Grow 1.2.0）与 `maven.modrinth:superb-warfare:bl7W8fjU`（Superb Warfare 0.8.9.2）—— 为了验证默认内容里「可选依赖 mod 的矿石」那 6 条矿脉/矿簇。
  - `maven.modrinth:kotlin-for-forge:uhJhCT7X`（Kotlin For Forge 5.12.0）—— **Superb Warfare 是 Kotlin mod，没有它直接 `ModLoadingException`**（“needs language provider kotlinforforge:5.8.0 or above”）。它自带的 SimpleBedrockModel 等依赖已内嵌在 jarjar 里，不用另加。
  - 所以 `runClient`/`runServer` 启动比空环境重；想跑纯净环境就把这几行注释掉（这也是验证 `neoforge:conditions` 生效的办法）。
- 发布到本地 maven：加 `-DmavenDir=<目录>`。

**模组自己不带任何矿脉**：要用开发环境试矿脉，自己写一条放进 `runs/client/kubejs/data/createoreexcavation/recipe/ore_vein_type/`（或 `runs/server/kubejs/server_scripts/*.js`），格式见 [`data-and-recipes.md`](data-and-recipes.md)。一条最简配方 + 一条同 `veinId` 的 `drilling` 配方就能在游戏里看到矿。

Fabric（1.20.1，当前不可构建，见 `porting.md`）：

```bash
cd Fabric
sh gradlew runDatagen
sh gradlew build
```

Fabric 的 `test` 任务依赖 `runClient`；在 IDE 外跑加 `-DuseLib=true` 跳过那段 hack。配方查看器由 `Fabric/gradle.properties` 的 `recipe_viewer` 切换（`jei` / `rei` / `emi` / `disabled`）。

## 3. 数据生成

**改了注册、模组自带配方或语言键后必须做这一步。**

- `sh gradlew runData` → `DataGenerators` 触发 `COERecipes`（工作台/机械合成/`raw_*` 加工）与 Registrate 的 LANG/模型/掉落物提供器（含 Ponder 文本）。
- 输出目录 `src/generated/resources`（同时被 `build.gradle` 当资源源集加载）；输入参照 `src/main/resources`、`src/platform-shared/resources`，以及 `--existing-mod create`（从 Create 取已有资源）。
- Fabric：`runDatagen` → `fabric.mod.json` 的 `fabric-datagen` 入口 `com.tom.createores.data.COEDataGenerator`。

规则：

- **永不手改 `src/generated/resources`**，改了下次生成会被覆盖（datagen 会把不再生成的文件删掉并更新 `.cache/`）。
- 非英语语言文件是**手写**的，放在 `src/platform-shared/resources/assets/createoreexcavation/lang/`，不走生成；只有 `en_us.json` / `en_ud.json` 由 datagen 生成。
- 矿脉/矿簇/钻探/抽取配方**不经过 datagen**——它们是整合包侧的数据包 JSON（或 KubeJS 脚本）。

## 4. 常见改动怎么做

1. **加/改矿脉、矿簇、钻探/抽取配方**：写数据包 JSON 或 KubeJS 脚本（格式见 `data-and-recipes.md`），不用编译。
2. **加方块/物品**：在 `Registration.java` 用 Registrate 链式定义（自带模型/掉落/语言），然后 `runData`。
3. **改语言文本**：在 `Registration.register()` 里 `add("键", "文本")`，然后 `runData` 生成 `en_us.json`；其余语种手写。
4. **改配置项**：改 `Config.java`，并在 `Registration.add` 里补对应 `config.coe.*` 语言键，然后 `runData`。
5. **加配方类型**：在 `CreateOreExcavation` 里用 `recipe(name, serializer)` 注册，写序列化器，然后 `runData`。
6. **改机器逻辑**：先读 `docs/architecture.md` 第 6 节；共享部分在 `src/platform-shared/.../block/entity/ExcavatingBlockEntity.java`。

## 5. 测试

仓库没有单元测试，`gameTestServer` 也没注册用例；验证靠跑客户端手动点，或用服务端做无头检查。

无头验证矿脉逻辑（放置、定点区块、重生成、存档）可以只跑 `sh gradlew runServer`，**约 25–30 秒**：

1. 往 `runs/server/kubejs/` 里放要测的内容（模组自己不带矿脉）。
2. 在 `runs/server/kubejs/server_scripts/` 放一个临时脚本，在 `ServerEvents.loaded` 里用 `server.runCommand('coe ...')` 发命令、用 `Java.loadClass('com.tom.createores.OreVeinGenerator')` / `.VeinRegeneration` 直接读数据并 `console.info` 出来，**最后必须 `server.runCommand('stop')` 正常关服**（`SavedData` 才会落盘）。
   - 脚本一旦抛异常就没人调 `stop`，服务端会一直跑到外面的 `timeout` 被杀，看起来像「跑了十几分钟」。
   - 命令源权限是 4，所以能执行 `stop`/`save-all`；数据包里的 `function` 只有权限 2，跑不了这些。
3. 验完删掉 `runs/server/kubejs/server_scripts/` 里的临时脚本，以及 `runs/server/world`（清掉测试区块数据）。

> `runData` **不会**执行 KubeJS 脚本（启动脚本在 datagen 环境里被跳过），所以 KubeJS 的物品注册、配方 schema、语言文件只能在客户端/服务端里验。

## 6. 发版

1. 改 `NeoForge/gradle.properties` 的 `mod_version`（Fabric 在 `Fabric/gradle.properties`）。
2. 改根目录 `version-check.json`：更新 `promos` 中对应 MC 的 `latest`，并加上新版本条目。该文件被 `neoforge.mods.toml` 的 `updateJSONURL` 引用，游戏内更新检查读它。
3. `sh gradlew build`，产物在 `build/libs/`。
4. 每个加载器工程的 `project.json` 描述发布元数据（`type`、`depProjects` 等），供作者的构建/发布流水线使用，**不参与** Gradle 构建。

## 7. 仓库里没有的东西

- `.github/` 下只有 `FUNDING.yml`，**没有 CI**。构建/测试全在本地。
