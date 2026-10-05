# 文档索引

`createoreexcavation` 的维护与开发文档。开始前先读上一级目录的 [`AGENTS.md`](../AGENTS.md)。

| 文档 | 什么时候读 |
|---|---|
| [architecture.md](architecture.md) | 想搞清「运行时到底怎么运作」：注册、配方、矿脉世界数据、机器 tick、多方块、网络 |
| [development.md](development.md) | 要搭环境、跑客户端/服务端、做数据生成、发版 |
| [data-and-recipes.md](data-and-recipes.md) | 要加/改配方、矿脉、KubeJS 脚本、语言与资源 |
| [integrations.md](integrations.md) | 要动 JEI / REI / EMI / JourneyMap / CC:Tweaked / KubeJS / Jade / TOP |
| [porting.md](porting.md) | 要碰 Fabric 侧、或理解 NeoForge 与 Fabric 的源码拆分 |

## 一句话架构

矿脉是**每区块计算出来的数据**（种子 + `RandomSpreadStructurePlacement` 决定），机器读所在区块的矿脉再匹配 `drilling`/`extracting` 配方产出资源；KubeJS 可以在运行时加更多矿脉与配方。

## 版本对照

| 工程 | MC | 加载器 | Mod 版本 | 状态 |
|---|---|---|---|---|
| `NeoForge/` | 1.21.1 | NeoForge 21.1.211 | 1.6.8 | 主线，活跃 |
| `Fabric/` | 1.20.1 | Fabric Loader 0.15.7 | 1.5.4 | 旧线，构建路径失效 |
