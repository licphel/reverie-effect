# Reverie Effect 架构瘦身审计

> 审计日期：2026-09-15  
> 审计对象：当前工作树，而非 `HEAD`。当前工作树包含大量未提交、未跟踪和重命名中的文件，因此本文行号只对应本次审计快照。

## 1. 结论

当前项目的主要问题不是“缺少分层”，而是同一个尚未成立的需求被提前扩展成了框架：为零个或一个调用方设计了接口、状态机、兼容层、双通道和通用数据模型。继续增加 Context、dispatcher、service、module 只会让问题更严重。

这次重整应该遵循一个方向：**先删除概念，再合并状态，最后才整理边界。**

优先级最高的五块是：

1. 区块生成使用命令日志、冲突仲裁、历史重放和两套发布流程，远超当前单一地形生成器的需要。
2. inventory、block property、quick2d skeleton 都是“一个或零个生产调用方”的通用框架。
3. 多方块对象为尚未实现的持久化和并发事务维护了重复记录、生命周期和解析状态。
4. 项目已决定不用 UDP，但完整 realtime 通道仍贯穿连接、握手、packet registry 和 codec。
5. 实体同步同时维护时钟包、生命周期包、物理包、pending physics、两张 tracker map 和已失效的 revision 状态。

在不删除现有玩法的前提下，第一轮合理目标是：

- 删除或合并 20–35 个生产类；
- 净减约 1,500–2,500 行 Java 逻辑；
- 若将每文件完整 MIT 文本换成 SPDX 标识，再减少约 3,900–4,200 行重复文本；
- 最终让 `core` 的物理行数从约 23,000 行降到 16,000–18,000 行。

这是目标区间，不是为了 LOC 指标强删算法。物理求解、流体、光照、四路 tile draw 等真实复杂度应保留。

## 2. 本次刻意不做什么

以下方案会增加概念数量，本次不采用：

- 不拆更多 Gradle module。先在现有 `core` 内删到边界稳定，再判断是否值得拆。
- 不恢复 `PacketContext`，不增加 client/server dispatcher/service 链。当前项目接受单 client、单 server runtime 的静态访问约束，`Packet#handle` 直接处理比多层转发更短。
- `Connection` 继续只负责网络，不持有 gameplay context。
- 不把 `drawBlock`、`drawBlockEdge`、`drawWall`、`drawWallEdge` 合成万能 `draw(pass)`。body/edge 必须处于独立批次，四个入口表达的是实际差异。
- 不为了消除几段相似代码创建抽象基类。只有第二个真实实现需要替换时才引入接口。

如果未来明确要求同进程多 client、多 server，届时再重新评估静态 runtime；它不是本次“代码瘦身”的阻塞项。

## 3. 规模快照

`core/src/main/java/io/viki/rf` 当前共有 198 个 Java 文件、约 23,051 个物理行：

| 范围 | 文件 | 物理行 | 近似逻辑行 | 注释行 |
|---|---:|---:|---:|---:|
| 全部 | 198 | 23,051 | 14,174 | 6,601 |
| `network` | 38 | 3,784 | 2,454 | 930 |
| `client` | 42 | 5,325 | 3,766 | 1,052 |
| `world.inventory` | 10 | 1,582 | 604 | 850 |
| `world.object` | 14 | 894 | 707 | 91 |
| `world.level` | 22 | 2,965 | 1,892 | 750 |
| `world.physics` | 13 | 1,852 | 1,450 | 217 |
| `world.light` | 10 | 2,038 | 928 | 955 |
| `util.property` | 8 | 703 | 245 | 391 |

完整 MIT header 约占 4,200 行。它不构成运行时架构，但明显放大了阅读噪音。仓库根目录另有四套 `.backup*`，合计约 323 MB；这些副本不应继续位于工作树内。

测试护栏当前也失效：本次审计执行过 `:core:test`，主源码编译成功，但 test source 编译出现 73 个错误，主要来自旧包名、旧 network API、抽象 `Level` 的旧实例化方式和 `TileShape` API 迁移残留。重构开始前必须先恢复最小可用测试基线。

## 4. 按设计冗余排序

### S0-1：区块生成是当前最过度设计的主链路

证据：

- `Level.java:77-88` 同时持有 generators、legacy generator、provider、generating、generation winners、history 和 current step。
- `GeneratedBlock`、`GeneratedWall`、`GeneratedShape`、`GeneratedLiquid`、`GeneratedEntity` 重复携带坐标、priority、source、sequence、flags，并各自只做一次 setter 转发。
- `GenerationQueue.java:34-104` 为单区块维护并发追加、排序、冲突检测和 seal。
- `Level.java:532-590` 又做第二次 winner 仲裁和排序规则。
- `AsyncChunkProvider.java:134-170` 与 `:247-288` 分别维护异步和同步 publish，逻辑重复。
- `FlatTerrainGenerator.java:178` 自己 `runAsync`，外层 provider 又在 executor 中执行 `prepare`，形成嵌套异步。
- `ChunkProvider.java:62-71` 的存档入口尚未实现，却让 `PreparedChunk.loadedFromSave` 和多处分支长期存在。
- `GeneratedEntity` 只被放入 `Chunk.generatedEntities`，没有 materialize 消费链路。

当前只有 `FlatTerrainGenerator` 真正在产出 block/wall/shape，复杂度却按“多个生成器并发写跨区块结构、支持存档重放、可冲突恢复”设计。

目标设计：

```text
ChunkGenerator.generate(position, seed) -> ChunkData
AsyncChunkProvider: 每个 chunk 一个 Future<ChunkData>
world thread: publish(ChunkData) -> Chunk
```

具体删减：

- 用一个 `ChunkData`/`ChunkBuilder` 保存最终 block、wall、shape、liquid 数组。
- 并行发生在“不同 chunk 之间”；同一 chunk 内生成器按注册顺序执行。
- `request()` 和 `requestAsync()` 最终走同一个 `publish`，删除同步复制流程。
- 删除 `GenerationStep` 五个实现、priority/source/sequence/flags、winner/history/current step、replay。
- 删除 legacy `BiConsumer<Chunk, Long>` 构造路径；给 `ClientLevel` 明确的 storage-only 路径。
- 当前不做存档就删除 `readSaved` 与 `loadedFromSave`。开始做存档时，让 `ChunkStore` 直接读写 `ChunkData`。
- 跨区块结构应由确定性生成规则计算目标 chunk 的最终结果，不通过运行时跨 chunk 命令日志补写。

预计净减 600–900 行，并让 810 行的 `Level` 明显收缩。

### S0-2：三个“通用框架”没有足够的生产需求

#### Inventory

`world.inventory` 有 10 个文件、1,582 个物理行。`SimpleContainer`、`SimpleSlot`、`BuiltinContainerCodecs` 和 `StackOp` 在生产主流程外没有实例化，主要消费者是 inventory 测试。当前 thrown item 甚至不携带 `ItemStack`。

问题不是泛型写得不好，而是 feature 尚未进入游戏，框架却已经支持：item/fluid 双 category、自引用泛型、slot validator、input/output、force、simulate、listener、transfer 和 codec。

建议：

- 如果下一里程碑不做可玩 inventory，删除 `Container`、`Slot`、`SimpleContainer`、`SimpleSlot`、`StackOp`、`StackCategory`、`BuiltinContainerCodecs` 及对应测试。
- `Item`/`ItemStack` 当前也没有玩法消费者，可一并延后。
- `FluidStack` 若仅用于 `Liquid#onTouch`，改成一个简单 record，或者直接传 `Liquid + amount`，不要为了两字段依赖 inventory 泛型体系。
- 真正开始做箱子/熔炉时，先写一个具体 `Inventory`；出现第二种行为后再抽接口。

预计净减 900–1,400 行物理文本。

#### Block property

`util.property` 有 8 个文件、703 行，但当前主源码没有任何 `Property.of(...)`，也没有 block 覆盖 `collectProperties`。也就是说，每个 block 目前都只有一个 state。

建议保留 `BlockState` 这个玩法概念，但先缩成 `block + stateId` 的 flyweight：

- 立即删除完全未使用的 `MutablePropertyMap` 和 `MutablePropertyMapImpl`。
- 若近期没有第一个真实属性，暂时删除整个 Property/PropertyDef/Map 笛卡尔积系统。
- 第一个真实属性出现时，再围绕那个属性设计紧凑 schema；不要先支持任意泛型属性图。

预计净减 300–600 行。

#### Quick2D skeleton

`client.render.quick2d.bone` 的约 1,000 行通用骨骼框架只有 `PlayerRenderer` 一个消费者。它并非一定要删除，但必须明确归属：

- 若它是 Momentum 的可复用能力，移动到 Momentum，并要求至少第二个 consumer 和独立测试。
- 若它只画当前玩家，改成 `PlayerRenderer` 内部的紧凑 rig/part 数组，保留父子变换和摆臂行为，不保留 builder、definition、runtime object 三套表示。

不要继续把它作为“看起来可复用”的私有平台留在 game core。

### S0-3：多方块对象重复保存同一事实

证据：

- `ObjectRecord.java:16-17` 保存 definition、root、mirror、revision、lifecycle、involved chunks、variant。
- `ObjectPartRef.java:12-13` 在每个占用 cell 再复制 root、definition、mirror、revision、variant 和 physical state。
- `ObjectLifecycle` 有四态，但 place/break 都在 world thread 的同步方法内完成，中间态没有正常观察者。
- `ObjectBreakPolicy` 只有 `WHOLE_OBJECT` 一个值，`ObjectManager.java:178` 对其他值直接报错。
- `ObjectResolution` 枚举八种状态，但 `resolveIfLoaded` 没有调用方。
- `ObjectStore` 只是 `ObjectManager` 的一张 map 和 id 计数器。
- `ObjectLayout` 与 `ObjectRenderProvider` 都是单实现函数式扩展点；当前 built-in 是 fixed layout。

目标设计只保留：

```text
MultiBlockDefinition(parts, root, anchors)
PlacedObject(id, definitionId, root, mirror, variant)
cell ref = (objectId, partIndex)
ObjectManager = Map<objectId, PlacedObject> + place/break
```

具体删减：

- 删除单值 `ObjectBreakPolicy`，当前 break 固定整物体。
- 删除同步事务不可见的 PREPARING/REMOVING；删除完成后直接移除记录，不保留永久 tombstone。
- 删除无调用的 `ObjectResolution`；普通未找到返回 `@Nullable`，损坏状态在加载/校验边界集中报错。
- 将 `ObjectStore` 合入 `ObjectManager`。
- cell 只保存 `objectId + partIndex`，其他字段从 canonical object record 解析。
- 没有动态 variant layout 之前，definition 直接持有 `List<MultiBlockPart>`。
- 动态渲染需求真正出现前，不保留 `ObjectRenderProvider`。

预计净减 250–400 行，同时消除最明显的数据重复。

### S1-4：已停用的 UDP 仍占据完整网络架构

当前没有 gameplay packet 使用 UDP；只有 `RealtimeHelloPacket` 和 `RealtimeWelcomePacket` 标记为 `UNRELIABLE_SEQUENCED`。但以下代码仍存在：

- `ClientConnection.java:88-104,206-216,284-289,354-358,381-404,522-549`
- `ServerConnection.java:118,217-235,422-423,437-440,484-490,605-634`
- `RealtimePacketCodec`
- `PacketDelivery`
- 两个 realtime packet
- `ConnectionOpenPacket.realtimeToken`

应整套删除，而不是保留“以后也许会用”的 fallback：

- 连接只建立 TCP channel。
- `Packet#delivery()` 和 `PacketDelivery` 删除，所有 packet 走 ordered stream。
- handshake 只保留 connection id/ack。
- 删除 token、address、probe、timeout、datagram codec/handler/channel。

保留 `ConnectionHost`、`Connection`、`PacketRegistry`、`PacketFlow` 和 integrated transport；它们都有真实的第二实现或职责。

预计净减 250–350 行逻辑和 4–5 个类。

### S1-5：实体同步为不会发生的乱序维护额外状态

当前发送顺序是：

1. `GameServer.publishTime()` 发送 `StepUpdatePacket`；
2. `publishPlayers()` 先发送 `EntityLifecyclePacket`；
3. 随后发送 `EntityPhysicsPacket`。

在 TCP ordered stream 下，spawn 与随后 physics 不会交换顺序。因此：

- `ClientLevel.pendingPhysics` 及 4,096 上限是在补偿已删除 transport 的乱序模型。
- `StepUpdatePacket` 和另外两个 packet 都在更新同一个 interpolation 时钟。
- `InterpolationTracker` 用 `snapshots` 和 `lastServerTimes` 两张 UUID map 表示同一个 per-entity track。
- `Entity.networkRevision` 在 `Entity.java:51,112-198` 被多处递增，但没有任何读取方。
- `Entity.lastTick` 完全没有读取方。
- `Moveable#setPositionInterpolated` 没有调用方。

建议保持当前“客户端独立物理 + 服务端状态混合”的视觉行为，不再重写插值算法；只删重复状态：

- 删除 `StepUpdatePacket`，server time 由 physics/lifecycle snapshot 携带。
- TCP 前提下删除 `pendingPhysics`；为 spawn 后第一帧、第一份 sync 写集成测试。
- 将 tracker 两张 map 合成 `Map<UUID, Track>`，每个 `Track` 保存 last server time 和 snapshot deque。
- snapshot 保存绝对 server time，不要每 tick 遍历并修改所有 snapshot 的相对 time。
- 删除 `networkRevision`、相关 setter overrides、`lastTick` 和未调用 position setter。

预计净减 120–220 行。这里的成功标准是轨迹不变，不是追求更短算法。

### S1-6：client 生命周期有多个 owner，render 初始化把失败变成常态分支

证据：

- `IntegratedBootstrap.Session` 与 `LanBootstrap.Session` 有几乎相同的 `close`。
- `DemoWorld` 保存 client/server，`ClientSession` 又拥有 host/level。
- `ClientSession.close()` 关闭 host 和 level；`DemoWorld.close()` 随后再次关闭 process/server 和 level。
- `DemoWorld.updateInterest()` 是空方法，但 `Controller` 每 tick 调用。
- `GameClient` 中 shape interval、time scale 和 `cycleBlockShape` 是 Controller 迁移后的残留。
- `TileRenderer.create`、`LiquidRenderer.create`、`ObjectRenderer.create` 吞异常并返回 null，导致 `ClientRenderer` 的字段、render 和 close 到处判空。
- `ClientRenderer` 保存 `level`，同时每次 `render` 又接收同一个 level；`open` 接收的 player/camera 没有参与初始化。

建议：

- `DemoWorld` 作为唯一 runtime owner；`ClientSession` 借用 level，不关闭 level。
- bootstrap 只负责构造，最终资源只由一个 owner 关闭一次；合并两个重复的 Session record。
- 删除空方法、重复常量/helper 和无用参数。
- 必需 renderer 使用 `open` 并 fail-fast，字段改为 non-null；加载失败保留原异常上下文。
- `ClientRenderer` 要么持有 level，要么由 render 参数传入，只选一种。
- 提取 ClientRenderer 内 wall/front 两段完全相同的 compose 操作作为私有方法即可，不创建新 renderer interface。
- 删除 `client.render.SkyRenderer` 兼容 facade，直接使用 ambient renderer。

预计净减 180–300 行，并消除重复 close 与 nullable 分支。

### S2-7：Level/Chunk/BlockState API 同时暴露多种表示

`Level` 当前 810 行，既是 world、chunk repository、generation coordinator、clock、entity ticker、fluid/light/object service locator，又同时提供：

- `ChunkPos` 与 packed long 两套 chunk API；
- `BlockPos` 与 `int x/y` 两套 tile API；
- 会加载与不加载两套读取；
- gameplay setter 与带 generation flags 的 setter；
- `byte` 与 `TileShape` 两种 shape 表示。

其中语义还不一致：`getBlock` 会加载，`getWall` 不加载，`getWallIfLoaded` 只是 `getWall` 的别名。

建议不是再拆十个 service，而是先统一规则：

- 热路径内部统一用 `int x, int y` 和 packed chunk key；边界对象只保留少量 convenience method。
- 读取方法命名明确为 `require/load` 与 `find/ifLoaded`，同类数据保持一致语义。
- `TileShape` 在 domain API 中始终使用 enum；byte 只存在于 Chunk 数组和序列化边界。
- 因此删除 `Block`/`BlockState` 的 byte overload，避免每层重复 `TileShape.byId`。
- `BlockState` 继续作为外部调用入口；它对 `Block` 的行为转发是本项目明确选择，不在本次删除。
- `frontDirty/backDirty` 是 renderer 状态，不应由 Chunk 暴露。改为 front/back revision，mesh 自己记录已构建 revision。
- `Chunk.isLoaded`、provider status、map presence 三套 lifecycle 收敛为一个状态来源。

目标是让 `Level` 在完成 generation 删除后降到约 450–550 行，而不是机械地把 810 行搬进更多类。

### S2-8：零散 dead code 与兼容代码应一次清空

高置信度可直接删除：

- `BFSLightEngine`：已 `@Deprecated`，无实例化，约 299 行。
- `SlopeType`：无引用，约 40 行。
- `EchoPacket`：生产代码无发送方，仅旧测试使用，约 115 行。
- `InterestPacket`：无生产构造方；位置 packet 已更新 interest，约 37 行。
- `client.render.SkyRenderer`：纯兼容转发 facade，约 82 行。
- `GameClient` 重复常量、`cycleBlockShape`、`defaultState` 转发。
- `DemoWorld.updateInterest` 与调用点。
- `Liquids.initialize` 空方法与调用点。
- `Connection.createDedicated`（与 `createLAN` 相同）。
- `Connection.netUuid`、`remoteId`（当前都只返回 `id`）。
- `ObjectPartRender.NONE`、`ChunkStatus.OVERLAY_ONLY` 和已确认无调用的 world convenience API。
- `LightEmitter` 三层 marker interface：光照引擎没有通过接口分派；若确认没有 mod API 兼容承诺，可删除约 130 行。

第一批 dead-code PR 可独立净减约 650–900 行，风险最低。

## 5. 应保留的真实抽象

以下代码复杂，但复杂度对应真实行为，不应为了 LOC 强行合并：

- `ConnectionHost`：Netty 与 integrated transport 的共同边界。
- `Connection`：每个 peer 的发送、身份和关闭。
- `PacketRegistry` / `PacketFlow`：wire codec 与方向检查。
- `ClientLevel`：客户端镜像确实禁止自主生成 chunk。
- `ChunkProvider` 的“worker 产出、world thread 发布”边界；实现要大幅简化，但边界应保留。
- `MotionSolver` / `SatSolver`：承载真实碰撞算法。可降低可见性，不应随意揉回 `Moveable`。
- `FluidEngine` 与当前 light engine。
- `TileRenderer`、`LiquidRenderer`、`ObjectRenderer`：资源和渲染语义不同。
- `BlockClientExtension` 的四个 draw 方法以及 `visible`、`animatedRendering` 判断。
- `BlockState` 作为 gameplay 调用入口。

## 6. 目标形态

保持一个 game `core`，不增加层数：

```text
GameClient
  ├─ DemoWorld                 唯一 client/world/network 生命周期 owner
  ├─ Controller                输入 -> 本地预测 + packet
  └─ ClientRenderer            GPU owner
       ├─ TileRenderer -> BlockClientExtension
       ├─ LiquidRenderer
       └─ ObjectRenderer

GameServer
  ├─ ConnectionHost           TCP/integrated transport
  ├─ players                  每连接一个聚合状态
  └─ Level
       ├─ ChunkProvider       Future<ChunkData> -> publish
       ├─ EntityMap
       ├─ FluidEngine
       ├─ LightEngine
       └─ ObjectManager

Packet
  └─ read / write / flow / handle(Connection)
       直接访问 GameClient 或 GameServer 的单 runtime 静态状态
```

关键约束：

- 每份 mutable state 只有一个 owner。
- 每种信息只有一个 canonical representation；byte 只留在存储/wire 边界。
- 没有已发布兼容承诺时，不保留 compatibility constructor/facade/alias。
- 接口必须至少有两个真实实现，或者承担明确的外部 SPI；否则先用 final class/private helper。
- production 中没有调用方的 future feature 默认删除，不以测试本身作为保留理由。

## 7. 分阶段迁移

### PR 0：建立可回退基线

- 将当前工作树保存为明确 checkpoint，不在脏工作树上同时做多轮架构迁移。
- 将 `.backup*` 移出仓库工作树，完善 build/IDE 输出忽略规则。
- 修到 `:core:test` 至少能完成 test source 编译。
- 删除或迁移明显针对旧 API 的测试，不给 compatibility path 续命。

退出条件：main/test 均可编译，CI 能稳定报告真实回归。

### PR 1：纯删除

- 删除 BFS、SlopeType、Echo、Interest、Sky facade、空初始化/空调用、重复 helper/alias。
- 清理 stale Javadoc 引用，如 `Session`/`SessionState`。
- renderer 加载改 fail-fast，去除 nullable 分支。

退出条件：行为不变，生产类和物理行显著下降。

### PR 2：TCP-only 网络

- 删除 realtime codec、packet、delivery、token、UDP channel/handler/probe。
- 简化 open/ack handshake。
- 保持 `Connection` transport-only，保持 packet 内直接处理。

退出条件：LAN 与 integrated 两条路径只通过 ordered stream 完成连接、spawn、sync、断开。

### PR 3：实体同步状态收敛

- 删除 StepUpdate、pending physics、networkRevision、lastTick 和未调用 setter。
- tracker 使用单个 per-entity Track。
- 不改变“客户端物理 + 权威状态混合”的外部轨迹。

退出条件：新增测试覆盖 request -> server spawn -> client spawn -> first sync；首段轨迹与稳定阶段连续，无首次回拽。

### PR 4：删除 speculative framework

- 根据近期 roadmap 决定 inventory 整包删除或缩成具体 `Inventory`。
- block property 缩到当前实际需求。
- quick2d 要么进入 Momentum，要么内联为 player rig。

退出条件：core 中不存在只有一个 consumer 却拥有 definition/builder/runtime 三套模型的子框架。

### PR 5：区块生成重写

- 引入最终 `ChunkData`；同 chunk 顺序生成，不同 chunk 异步。
- 合并 publish 路径，删除 command steps、replay、legacy 和未实现 save 分支。

退出条件：相同 seed 的 chunk 数据确定；同步/异步请求得到相同结果；world 只在 tick thread 安装 chunk。

### PR 6：对象与 world API 收缩

- 对象只保留 canonical record 和短 cell ref。
- 删除同步事务状态机、单值策略、无调用 resolution。
- 统一 Level 的 loaded/loading 与坐标 API。
- shape 的 enum/byte 边界下沉到 Chunk/codec。
- mesh revision 取代 world 中的 renderer dirty flag。

退出条件：`Level` 接近 450–550 行；对象 place/break 不重复保存 definition/root/variant。

### PR 7：文本与构建收尾

- 若许可证政策允许，每文件完整 MIT header 换成 `SPDX-License-Identifier: MIT`，根 `LICENSE` 保留全文。
- 删除解释 getter/setter 的模板 Javadoc，只保留约束、线程、生命周期和非显然算法说明。
- README 的 JDK 21 与 toolchain 25、入口类等漂移统一。

## 8. 验收指标

重整完成应同时满足：

- `:core:test` 可编译并通过；CI 不再依赖旧 API compatibility。
- 生产源码中没有无引用的 deprecated 实现、空 facade、空 hook。
- `PacketDelivery`、realtime packet/codec/datagram channel 全部不存在。
- 每个 chunk 只有一个 publish 流程和一个 lifecycle 来源。
- 每个实体的同步状态由一个 Track 持有，不存在平行 UUID map。
- 对象 cell ref 不复制 canonical object 的全部字段。
- `BlockClientExtension` 仍保留四路 draw，`TileRenderer` 只负责可见性、animated/static、mesh 与分发。
- 不新增仅有一个实现的 interface，不新增仅转发一次的方法链。
- 生产物理行目标 16,000–18,000；近似逻辑行目标不高于 12,500。若超出，必须由新增玩法而非框架样板解释。

## 9. 最先执行的十个具体动作

1. 修复 test source 编译，建立 checkpoint。
2. 删除 `BFSLightEngine`、`SlopeType`、`EchoPacket`、`InterestPacket`。
3. 删除 Sky facade、空 `updateInterest`、空 `Liquids.initialize`、GameClient 残留 helper。
4. renderer 创建改为 fail-fast，移除三组 nullable 判断。
5. 完整删除 UDP/realtime 和 `PacketDelivery`。
6. 删除 `StepUpdatePacket`、pending physics、entity dead revision/field。
7. 对 inventory/property/quick2d 做三项明确的 keep/delete 决策，不允许继续“先留着”。
8. 用 `ChunkData` 替换 generation command log，并合并 publish。
9. 缩短 object record/ref，删除 lifecycle/policy/resolution/store 包装。
10. 收敛 Level API 和 mesh revision，最后统一许可证/Javadoc 文本。

这套顺序先做高置信度删除，再碰行为复杂的生成和同步；每一步都应让概念和代码总量下降，而不是把代码从一个包搬到另一个包。
