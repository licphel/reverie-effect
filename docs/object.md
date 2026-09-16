# 多格 Object 架构设计

## 1. 目标

本设计用于实现类似 Minecraft 多格方块、Terraria 家具与 Starbound Object 的静态世界对象。

系统需要满足：

- 一个对象可以占据一个或多个格子，并跨越多个区块。
- 点击、交互、破坏任意部件时，能够定位唯一的逻辑对象。
- 碰撞、渲染、光照、流体等局部查询不依赖根区块已加载。
- 方块交互、放置和破坏可以主动加载所需区块。
- 渲染和碰撞查询不得触发区块加载。
- 区块未加载或尚未完成加载时，不能将其视为空气参与写入判定。
- 跨区块放置、破坏和存档必须保持原子性，不能留下半个对象。
- 对象销毁后在相同位置重新放置时，旧区块中的延迟数据不能误指向新对象。

不在本阶段处理：

- 网格变形或骨骼动画。
- JSON 导入格式的兼容设计。
- 运动中的刚体对象；本设计中的 Object 默认是附着在世界格子上的静态对象。

## 2. 核心原则

不能把一切查询都无条件路由到根格子。

对象具有两类职责：

1. **全局逻辑职责**：对象身份、交互、方块实体、生命周期、掉落和持久状态，由根记录统一负责。
2. **局部空间职责**：碰撞、渲染、选择框、遮光、流体阻挡和寻路，由当前格子的 Part 数据直接负责。

因此路由规则是：

- 需要“这个对象是谁、要执行什么逻辑”时，路由到根对象。
- 只需要“这个格子在空间中表现为什么”时，读取本地 Part，不加载根区块。

关键不变量：

- 一个对象拥有不可复用的 `long objectId`。
- 一个对象只有一份根记录和至多一个方块实体。
- 每个占用格子都有明确的 `ObjectPartRef`，包括根格子，不能用 `(0, 0)` 同时表示“根”和“无对象”。
- Part 引用通过 `long objectId + revision` 指向根记录，不能仅通过根坐标建立身份。
- 对象的碰撞只由 Part 提供，根对象不能再额外提供一份重复碰撞。
- 所有需要修改整个对象的操作必须先加载完整作用域，再一次性提交。

## 3. 总体结构

```text
Level
├── geometry()       世界空间、形状与局部几何查询
├── collision()      碰撞检测与移动求解
├── objects()        多格对象解析、交互与事务
└── chunkManager()   区块状态、interest、loader、saver、generator

ObjectDefinitionRegistry
└── MultiBlockDefinition
    ├── rootPartIndex
    ├── parts[]
    ├── anchors[]
    └── behavior

World storage
├── Chunk A: ObjectPartRef...
├── Chunk B: ObjectPartRef...
└── Root record store: ObjectRecord + ObjectBlockEntity data
```

`Level` 只提供明确的子系统入口，不直接堆积对象放置、区块加载、碰撞和存档实现。

## 4. 数据模型

### 4.1 MultiBlockDefinition

`MultiBlockDefinition` 是注册表中的不可变对象定义，描述对象的静态结构：

```java
public record MultiBlockDefinition(
        ResourceId id,
        List<MultiBlockPart> parts,
        int rootPartIndex,
        List<BlockPosition> anchors,
        ObjectBreakPolicy breakPolicy,
        ObjectBehavior behavior
) {}
```

要求：

- `id` 在注册表中唯一。
- `parts` 非空，所有相对坐标唯一。
- `rootPartIndex` 必须指向合法 Part。
- 根格子不要求位于左下角或包围盒内的特定位置。
- 镜像状态在放置时形成确定的 `boolean mirrorX`。

### 4.2 MultiBlockPart

每个 Part 保存局部空间所需的全部静态信息：

```java
public record MultiBlockPart(
        Vector2i offset,
        Shape collisionShape,
        ObjectPartRender render,
        boolean selectable,
        boolean blocksFluid,
        boolean blocksLight
) {}
```

Part 的碰撞形状使用格子局部坐标。定义应用 `mirrorX` 后，生成最终偏移和局部形状。

### 4.3 对象实例字段

对象实例身份直接使用不复用的 `long objectId`，状态直接使用 `int variant`：

```java
long objectId;
int variant;
boolean mirrorX;
```

不能只用根坐标作为身份。以下时序会导致坐标方案误关联：

1. 对象 A 被破坏。
2. 相同根位置放置对象 B。
3. 某个较晚加载的区块仍保存对象 A 的 Part。
4. 如果只看根坐标，旧 Part 会错误地归属于对象 B。

`objectId` 配合 `revision` 可以明确识别过期引用。

### 4.4 ObjectPartRef

每个被对象占用的格子保存一条明确引用：

```java
public record ObjectPartRef(
        long objectId,
        BlockPosition rootPosition,
        ResourceId definitionId,
        boolean mirrorX,
        int partIndex,
        int revision,
        int physicalState
) {}
```

字段职责：

- `objectId`：区分不同对象实例，直接使用 `long`，不再包装一层类型。
- `rootPosition`：快速定位根区块和根记录。
- `definitionId`：根未加载时仍可取得本地碰撞和渲染定义。
- `partIndex`：定位定义中的 Part。
- `revision`：拒绝过期写入和过期 Part。
- `variant`：决定定义的布局和渲染变体。
- `physicalState`：可选的局部物理状态。

Part 数据建议存放在区块的稀疏表中，以本地格子索引为键。不要继续使用 `rootDx/rootDy` 字节元数据：

- 它无法表达明确的“无对象”状态。
- 偏移范围被限制为 `[-127, 127]`。
- 不包含对象身份、定义、Part 序号和版本。
- 无法安全处理销毁后重放置和跨区块延迟加载。

### 4.5 ObjectRecord

根记录是对象实例的唯一权威状态：

```java
public record ObjectRecord(
        long objectId,
        ResourceId definitionId,
        BlockPosition rootPosition,
        boolean mirrorX,
        int revision,
        ObjectLifecycle lifecycle,
        Set<ChunkPosition> involvedChunks,
        int variant
) {}
```

生命周期：

```text
PREPARING -> ACTIVE -> REMOVING -> REMOVED
```

- `PREPARING`：事务已开始，但对象尚不可交互。
- `ACTIVE`：对象完整存在。
- `REMOVING`：正在删除，任何重复删除都必须合并为同一操作。
- `REMOVED`：墓碑状态，用于拒绝旧 Part 和防止生成器复活对象。

### 4.6 ObjectBlockEntity

需要运行时逻辑或可变数据的对象可以拥有一个 `ObjectBlockEntity`：

- 以 `long objectId` 或根记录为键。
- 只在根记录对应的运行环境激活。
- Part 格子不能各自创建方块实体。
- 根区块未加载时，状态保存在持久层中；加载根记录后再实例化运行时对象。

纯装饰对象不需要方块实体。

## 5. 查询与路由

### 5.1 API 分层

底层原始接口只供加载、生成和事务内部使用：

```java
BlockState getBlockRaw(BlockPosition position);
void setBlockRaw(BlockPosition position, BlockState state);
Optional<ObjectPartRef> getObjectPartRaw(BlockPosition position);
```

普通游戏逻辑使用语义化接口：

```java
ObjectResolution resolveObjectIfLoaded(BlockPosition position);
InteractionResult interactAt(BlockPosition position, InteractionContext context);
BreakResult breakAt(BlockPosition position, BreakContext context);
PlacementResult placeObject(ObjectPlacement placement);
```

禁止让 `Level.getBlock()` 自动返回根格子的状态。这会混淆本地空间查询和对象逻辑查询，并使碰撞、渲染等热路径意外跨区块。

### 5.2 操作路由表

| 操作 | 数据来源 | 是否加载根区块 | 是否加载完整占用区块 |
| --- | --- | ---: | ---: |
| 渲染 | 本地 Part | 否 | 否 |
| 碰撞 | 本地 Part | 否 | 否 |
| 选择框 | 本地 Part | 否 | 否 |
| 遮光、流体、寻路 | 本地 Part | 否 | 否 |
| 查询对象身份 | 本地 PartRef | 否 | 否 |
| 与任意 Part 交互 | 根记录/方块实体 | 是 | 按行为需要 |
| 破坏任意 Part | 根记录和完整 footprint | 是 | 是 |
| 放置对象 | 完整 footprint 和锚点 | 按范围加载 | 是 |
| 修改影响物理的对象状态 | 根状态并镜像到 Part | 是 | 是 |

渲染和碰撞只能读取当前已经发布为 `READY` 的区块。遇到未加载边界时，由世界边界策略提供不可穿越的 `Null`/未知碰撞，不得为渲染或实体移动主动加载区块。

## 6. 放置事务

当前世界策略允许方块交互和放置强制加载区块，因此放置采用“先加载完整作用域，再提交”的事务模型。

流程：

1. 根据定义、根位置和变换计算完整 footprint、锚点和涉及区块。
2. 通过 `ChunkManager` 请求所有相关区块进入 `READY`。
3. 等待期间不修改世界；可建立短期位置 reservation，避免两个放置请求竞争同一区域。
4. 所有区块就绪后，在世界线程重新验证：
   - 所有占用格子可替换。
   - 锚点有效。
   - 液体规则满足。
   - 玩家距离、权限和物品仍然有效。
5. 分配新的 `objectId`。
6. 写入 `PREPARING` 根记录。
7. 写入所有 PartRef。
8. 创建可选的方块实体数据。
9. 将根记录切换为 `ACTIVE`。
10. 标记涉及的网格、光照、流体、碰撞缓存和邻居为脏。
11. 最后消耗物品并返回成功。

`ABSENT`、`QUEUED`、`PREPARING`、`OVERLAY_ONLY` 等非 `READY` 状态都不能被当作空气。任何加载失败或重新验证失败都必须在没有世界写入的情况下结束。

## 7. 交互流程

点击任意 Part 时：

1. 点击目标所在区块已经因交互而加载。
2. 从本地格子读取 `ObjectPartRef`。
3. 使用 `rootPosition` 请求根区块加载到 `READY`。
4. 读取根记录，并验证 `objectId`、`definitionId` 和 `revision` 一致。
5. 重新验证玩家距离、权限和当前动作，因为加载过程可能跨越多个 tick。
6. 将交互交给根记录对应的 `ObjectBehavior` 或 `ObjectBlockEntity`。

如果根记录不存在、已删除或版本不符，不能将操作路由到根位置上后来出现的其他对象。

## 8. 破坏事务

破坏任意一个 Part 等价于请求删除整个对象：

1. 读取目标 PartRef。
2. 强制加载根区块并解析根记录。
3. 根据根记录重新计算完整 footprint。
4. 强制加载所有涉及区块到 `READY`。
5. 重新验证所有 Part 的 `objectId` 和 `revision`。
6. 将生命周期从 `ACTIVE` 原子切换为 `REMOVING`。
7. 对碰撞和交互立即视为对象已不存在。
8. 清除所有 PartRef。
9. 删除方块实体一次。
10. 生成掉落一次。
11. 更新 footprint 外边界上的邻居、网格、光照、流体和寻路数据。
12. 写入 `REMOVED` 墓碑或提交删除记录。

删除接口必须幂等：

```text
remove(objectId, expectedRevision)
```

爆炸或范围工具可能同时命中多个 Part。调用侧应先按 `objectId` 去重，事务层仍需保证重复请求不会重复掉落。

## 9. 跨区块存档与加载

### 9.1 存储归属

- 每个区块保存位于自身格子内的 `ObjectPartRef`。
- 根区块或独立对象存储保存 `ObjectRecord`。
- 方块实体数据与根记录一起保存，不复制到 Part 区块。

因此 Part 区块可以独立加载。根未加载不代表 Part 无效：

- 碰撞和渲染继续读取本地 Part。
- 交互时再强制加载根。
- 不能因为当前找不到已加载的根就自动删除 Part。

### 9.2 崩溃一致性

即使运行时在写入前加载了全部区块，各区块文件仍可能分别保存。进程在保存中途退出时可能产生半提交状态，因此持久层需要以下方案之一：

- 支持原子事务的世界数据库；或
- 追加式对象事务日志和墓碑。

建议日志阶段：

```text
PLACE_BEGIN
PARTS_WRITTEN
PLACE_COMMIT

REMOVE_BEGIN
PARTS_CLEARED
DROP_EMITTED
REMOVE_COMMIT
```

掉落状态必须进入事务，否则崩溃恢复可能复制掉落。

### 9.3 区块加载顺序

建议加载管线：

1. 解码存档或生成基础地形。
2. 应用已持久化的对象事务、墓碑和恢复补丁。
3. 校验本区块的 PartRef。
4. 根记录在本区块时，恢复根状态和方块实体。
5. 完成网格、光照和碰撞缓存。
6. 最后将区块发布为 `READY`。

生成器不能覆盖墓碑并复活已经删除的生成对象。

## 10. 未加载与异常状态

解析结果应显式表达状态，而不是返回模糊的 `null`：

```text
RESOLVED
ROOT_UNLOADED
PENDING_LOAD
TOMBSTONED
STALE_REVISION
ORPHANED
CORRUPT
```

处理策略：

- `ROOT_UNLOADED`：正常状态；局部物理继续工作，逻辑操作请求加载。
- `PENDING_LOAD`：暂停当前操作，加载完成后从验证阶段重新开始。
- `TOMBSTONED`：清理旧 Part，不允许对象复活。
- `STALE_REVISION`：清理过期引用，不连接到新对象。
- `ORPHANED`：在存储确认前保守保留碰撞，避免角色掉入尚未确认的空洞。
- `CORRUPT`：拒绝修改并记录对象 ID、根坐标、区块和版本等上下文。

典型边界情况：

- **Part 已加载、根未加载**：正常渲染和碰撞；交互加载根。
- **根已加载、部分 Part 未加载**：根逻辑可存在，但不能执行需要完整 footprint 的修改。
- **加载中执行破坏**：等待所有范围区块 `READY` 后重新验证，不在中途清理 Part。
- **根缺失**：不得猜测根对象，也不得路由到同坐标的新对象。
- **旧 Part 延迟加载**：通过 `objectId + revision` 识别并清理。

## 11. 碰撞、渲染与动态状态

### 11.1 单一碰撞来源

静态 Object 不应再作为带整对象碰撞箱的 `Entity` 存在。每个 Part 在自己的格子中提供局部形状，`Level.geometry()` 收集这些形状，`Level.collision()` 只负责碰撞求解。

这可以避免：

- 每个 Part 重复整对象碰撞。
- 根区块卸载后碰撞消失。
- 碰撞查询为了寻找根而触发区块加载。

### 11.2 局部渲染

渲染器根据本地 `ObjectPartRef`、定义和镜像状态绘制当前 Part，或绘制被当前区块裁剪后的对象片段。可见 Part 不能因根区块未加载而消失。

纹理、材质、颜色和渲染层应来自对象定义或状态，而不是硬编码在 Renderer 中。

### 11.3 动态物理状态

门、开关等对象的根状态可能改变碰撞或渲染。此时：

1. 根记录是状态权威。
2. 与局部空间有关的最小状态编码到 `physicalState`。
3. 更新前加载所有受影响区块。
4. 在同一对象事务中更新根 revision 和所有 Part 镜像。

不能要求碰撞热路径临时访问方块实体。

## 12. 锚点和邻居更新

锚点由定义描述，并在放置时完整验证。对象存在期间不应由 `WorldObject.tick()` 每帧扫描所有锚点。

建议维护反向支撑索引：

- Part 注册其依赖的支撑格子。
- 支撑格子发生变化时，向依赖对象发送一次校验事件。
- 校验涉及未加载区块时，按对象修改操作的规则加载完整范围。

放置、状态变化和破坏后，需要更新整个 footprint 的外边界，而不仅是根格子邻居：

- 方块邻居事件。
- 当前及相邻区块网格。
- 光照传播。
- 流体连通与阻挡。
- 寻路和碰撞缓存。

事件应按格子或区块去重。

## 13. 普通方块写入约束

普通 `setBlock` 不能静默覆盖对象中的单个 Part，否则会产生孤儿对象。

接口应区分：

- `setBlockRaw`：仅供生成、加载和已建立范围的内部事务。
- `replaceBlock`：游戏逻辑替换；遇到 Object 时拒绝，或先请求删除整个对象。
- `breakAt`：玩家、爆炸和工具的统一破坏入口。
- `placeObject`：多格对象的统一放置入口。

所有绕过对象事务的原始写入都应限制在包内或内部模块。

## 14. 与现有实现的迁移关系

现有 `ObjectConfig + WorldObject + rootDx/rootDy` 方案应逐步拆分：

- `ObjectConfig` 的静态配置迁移为 `MultiBlockDefinition` 和 `MultiBlockPart`。
- `WorldObject` 的实例身份和状态迁移为 `ObjectRecord`。
- 需要运行逻辑的部分迁移为可选 `ObjectBlockEntity`。
- 每 tick 锚点扫描迁移为事件驱动支撑校验。
- 根偏移字节迁移为稀疏 `ObjectPartRef`。
- 根 `Entity` 碰撞迁移为各 Part 的局部碰撞。
- Renderer 中的纹理和材质硬编码迁移到定义或对象状态。

如果需要兼容旧存档，应提供一次性转换：

1. 扫描旧根和偏移元数据。
2. 为每个可恢复对象分配新的 `objectId`。
3. 创建根记录和完整 PartRef。
4. 对缺失根或冲突数据生成迁移报告，不静默猜测。

当前 `ChunkProvider.readSaved()` 尚未完成时，不能宣称跨卸载对象已经可靠支持；持久层和恢复事务必须先补齐。

## 15. 推荐实施顺序

1. 建立 `MultiBlockDefinition`、`MultiBlockPart`、`ObjectPartRef` 和 `ObjectRecord` 类型；对象身份、变体和镜像直接使用基础字段。
2. 在区块中加入稀疏 Part 存储，并明确序列化格式。
3. 建立 `ObjectService`，分离 raw API、解析 API 和游戏行为 API。
4. 让 geometry、render、light、fluid 只消费本地 Part。
5. 实现需要完整加载范围的放置事务。
6. 实现幂等破坏事务和 `objectId` 去重。
7. 接入可选方块实体和动态状态镜像。
8. 实现对象事务日志、墓碑、区块恢复和异常修复。
9. 编写旧数据转换，再删除 `WorldObject` 和 `rootDx/rootDy` 旧路径。

迁移过程中不能长期保留两套碰撞或两套对象身份来源。每完成一个垂直功能切片，就应将对应旧路径彻底移除。

## 16. 验收场景

至少验证以下情况：

- 对象全部位于同一区块。
- 对象跨越两个和四个区块。
- Part 已加载但根未加载时仍有正确碰撞和渲染。
- 点击非根 Part 可以加载并交互根对象。
- 根已加载但其他 Part 区块未加载时，不执行半次破坏。
- 加载等待期间玩家离开、对象被其他操作删除或位置被占用。
- 对象删除后原位置重新放置，旧 Part 延迟加载不会接入新对象。
- 爆炸同时命中多个 Part，只删除和掉落一次。
- 两个待处理放置请求竞争相同格子。
- `PREPARING` 状态下发生加载、保存或退出。
- 生成对象被删除后，区块重新生成不会复活它。
- 动态碰撞状态在区块卸载和重载后保持一致。
- 锚点和对象分别位于不同区块。
- 在放置和删除事务的每个持久化阶段模拟崩溃并恢复。
- 普通方块写入不能只覆盖一个 Part 并留下孤儿数据。

以上场景全部成立后，多格 Object 才可以视为同时具备运行时正确性和跨区块持久化正确性。
