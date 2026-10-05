# 文档索引

`createoreexcavation` 的维护与开发文档。开始前先读上一级目录的 [`AGENTS.md`](../AGENTS.md)。

| 文档 | 什么时候读 |
|---|---|
| [architecture.md](architecture.md) | 想搞清「运行时到底怎么运作」：注册、配方、矿脉世界数据、机器 tick、多方块、网络、命令、配置 |
| [development.md](development.md) | 要搭环境、跑客户端/服务端、做数据生成、无头验证、发版 |
| [data-and-recipes.md](data-and-recipes.md) | 要写矿脉、矿簇、钻探/抽取配方、群系标签、物品、语言 —— 模组的**内容**都由整合包提供，这是唯一的编写文档 |
| [integrations.md](integrations.md) | 要动 JEI / REI / EMI / Xaero / CC:Tweaked / KubeJS / Jade / TOP |
| [porting.md](porting.md) | 要碰 Fabric 侧、或理解 NeoForge 与 Fabric 的源码拆分 |

## 三句话架构

- 模组只提供**机制**：矿脉/矿簇的世界数据、钻机与抽取器、手持钻、图鉴，以及 JEI/KubeJS/Xaero/CC 集成。
- 矿脉是**每区块计算出来的数据**（种子 + `RandomSpreadStructurePlacement` + 群系 + 定点区块），机器读所在区块的矿脉再匹配 `drilling`/`extracting` 配方产出资源。
- 所有**内容**（矿脉、矿簇、产出表、物品、名字）都由整合包/数据包作者提供，模组不带示例；写法见 [`data-and-recipes.md`](data-and-recipes.md)。

## 版本对照

| 工程 | MC | 加载器 | Mod 版本 | 状态 |
|---|---|---|---|---|
| `NeoForge/` | 1.21.1 | NeoForge 21.1.211 | 1.7.0 | 主线，活跃 |
| `Fabric/` | 1.20.1 | Fabric Loader 0.15.7 | 1.5.4 | 旧线，构建路径失效（见 `porting.md`） |
