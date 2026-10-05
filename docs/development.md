# 开发环境与流程

## 1. 前置

- **JDK 21**（NeoForge 主线）；Fabric 线用 JDK 17。工程用 Gradle wrapper，无需自己装 Gradle。
- 首次构建需联网拉依赖（仓库列表见 `NeoForge/build.gradle`，含 Create/Registrate/KubeJS 等自定义 maven、以及作者的 `raw.githubusercontent.com/tom5454/maven`）。
- NeoForge 的 `gradle.properties` 设了 `org.gradle.jvmargs=-Xmx3G`、`org.gradle.daemon=false`（反编译 MC 需要内存，首次会慢）。

## 2. 构建与运行（从 `NeoForge/`）

```bash
./gradlew build              # 打包 jar + sources jar
./gradlew runClient          # 开发客户端
./gradlew runServer          # 开发服务端（--nogui）
./gradlew runData            # 数据生成 → src/generated/resources
./gradlew gameTestServer     # 跑 gametest（当前没注册用例，会直接结束）
./gradlew clean
```

- 运行配置在 `build.gradle` 的 `runs { ... }` 里定义：`client`、`server`、`gameTestServer`、`data`。
- 发布到本地 maven：加 `-DmavenDir=<目录>`。

Fabric（1.20.1）：

```bash
cd Fabric
./gradlew runDatagen
./gradlew build
./gradlew runClient          # 注意：test 任务被绑到了 runClient
```

Fabric 的 `test` 任务默认依赖 `runClient`；在 IDE 外跑加 `-DuseLib=true` 跳过那段 hack。配方查看器由 `Fabric/gradle.properties` 的 `recipe_viewer` 切换（`jei` / `rei` / `emi` / `disabled`，当前 `emi`）。

## 3. 数据生成

**这是改注册/配方/语言后必须做的一步。**

- NeoForge：`./gradlew runData` → `DataGenerators` 触发 `COERecipes`（合成/机械合成/山铜等配方）与 Registrate 的 LANG 提供器（含 Ponder 文本）。
- 输出目录：`src/generated/resources`；它同时被 `build.gradle` 当资源源集加载。
- 输入参照：`src/main/resources`、`src/platform-shared/resources`，以及 `--existing-mod create`（从 Create 取已有资源）。
- Fabric：`runDatagen` → `fabric.mod.json` 的 `fabric-datagen` 入口 `com.tom.createores.data.COEDataGenerator`。

规则：

- **永不手改 `src/generated/resources`**，改了下次生成会被覆盖。
- 非英语语言文件是**手写**的，放在 `src/platform-shared/resources/assets/createoreexcavation/lang/`，不走生成；只有 `en_us.json` / `en_ud.json` 由 datagen 生成。

## 4. 常见改动怎么做

1. **加方块/物品**：在 `Registration.java` 用 Registrate 链式定义（自带模型/掉落/语言），然后 `runData`。
2. **改语言文本**：在 `Registration.register()` 里 `add("键", "文本")`，然后 `runData` 生成 `en_us.json`。
3. **改配置项**：改 `Config.java`，并在 `Registration.add` 里补对应 `config.coe.*` 语言键，然后 `runData`。
4. **加配方类型**：在 `CreateOreExcavation` 里用 `recipe(name, serializer)` 注册，写序列化器，然后 `runData`。
5. **改机器逻辑**：先读 `docs/architecture.md` 第 6 节；共享部分在 `src/platform-shared/.../block/entity/ExcavatingBlockEntity.java`。

## 5. 测试

- 仓库**没有单元测试**，`gameTestServer` 也没注册用例。
- 验证靠 `runClient` 手动跑：`/coe setvein` 强制放一条矿脉、`/coe locate` 验证生成、装图鉴与寻脉器验证 UI 与网络。

## 6. 发版

1. 改 `NeoForge/gradle.properties` 的 `mod_version`（Fabric 在 `Fabric/gradle.properties`）。
2. 改根目录 `version-check.json`：更新 `promos` 中对应 MC 的 `latest`，并加上新版本条目。该文件被 `neoforge.mods.toml` 的 `updateJSONURL` 引用，游戏内更新检查读它。
3. `./gradlew build`，产物在 `build/libs/`。
4. 每个加载器工程的 `project.json` 描述发布元数据（`type`、`depProjects` 等），供作者的构建/发布流水线使用，**不参与** Gradle 构建。

## 7. 仓库里没有的东西

- `.github/` 下只有 `FUNDING.yml`，**没有 CI**。构建/测试全在本地。
- 根目录 `task.md` 是空的临时文件。
