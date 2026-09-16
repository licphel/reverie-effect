# 客户端/服务端架构

## 核心约束

- 服务端拥有世界的唯一权威状态。客户端只能提交操作意图，不能提交可信的位置、速度、物品数量或方块结果。
- 单人、LAN 和 dedicated server 使用同一套网络协议。单人模式也必须经过 loopback 连接，不能直接调用服务端世界。
- 网络线程只解码和排队。世界读取、校验和修改只能发生在对应的 client/server tick 线程。
- 非法包只关闭来源连接。任何客户端输入都不能让异常越过连接边界并终止服务端 tick。

## 网络对象

```text
Connection.createLAN(port)       -> server ConnectionHost
Connection.createDedicated(port) -> server ConnectionHost
Connection.createIPAddr(ip,port) -> client ConnectionHost

ConnectionHost
├── start()
├── process()
├── get(netUuid) -> Connection
└── connections()

Connection
├── netUuid()
├── send(packet)
├── state()
└── close()
```

`ConnectionHost` 是进程中的网络端点。服务端只有一个 Host，内部管理多个连接；客户端 Host 只管理到服务器的一条连接。`Connection` 只表示一条逻辑链路，不负责监听、玩家逻辑或世界逻辑。

服务端玩家保留 `netUuid`，定向发包使用 Host 按 UUID 路由。客户端玩家持有自己的 `Connection`，向服务端发包时不需要知道服务端的玩家对象。

## Level 分离

```text
Level（共享只读查询契约）
├── ServerLevel
│   ├── 权威 tick、物理、流体、AI
│   ├── 方块/实体/Object 修改
│   └── ServerChunkManager
└── ClientLevel
    ├── 只应用服务端快照和增量包
    ├── 渲染查询与插值
    └── 不生成、不保存、不运行权威物理
```

`ServerLevel` 与 `ClientLevel` 必须是明确类型，不能依靠运行时布尔值判断当前 side。共享 `Level` 不暴露无条件写接口；服务端写操作和客户端网络快照应用分别进入各自的受限 API。

## Player 分离

`ServerPlayer` 保存 `netUuid`、服务端输入状态、背包及权威实体状态。它不信任客户端上传的运动结果。

`ClientPlayer` 保存 `Connection`、本地输入采样、预测和渲染状态。预测只改善手感，收到服务端快照后必须校正。

两者可以共享玩家外观和可序列化数据，但不能共享控制逻辑或直接持有对方的 Level。

## ChunkManager.Interest

区块加载由服务端 `ChunkManager.Interest` 独占管理，实体本身不再请求或生成区块。

每个 `ServerPlayer` 对应一个 Interest，至少包含：

- 由服务端权威位置计算出的中心区块；
- 服务端限制后的模拟半径和发送半径；
- 已请求、已发送以及等待卸载的区块集合。

Interest 负责调度 loader、generator、saver，并在区块准备完成后发送快照。客户端移动到未加载边界时，服务端阻止继续前进但不伪造碰撞反弹；Interest 在后台准备目标区块。客户端只接收区块，不触发服务端加载。

## 包模型

服务端入站只接受意图：

- `PlayerInputPacket(sequence, horizontal, jump, fallThrough)`；
- `InteractBlockPacket(sequence, position, action, selectedSlot)`；
- 后续的容器、聊天和管理命令包。

服务端必须根据当前玩家、距离、冷却、权限、背包、区块加载状态和序列号重新计算结果。客户端给出的坐标只能作为候选目标。

客户端入站只接受状态：

- 玩家/实体 spawn、snapshot、remove；
- chunk snapshot、block/object/liquid update、chunk unload；
- inventory/container acknowledgement；
- 操作拒绝与重同步。

每类包必须声明方向。握手包属于内部流量，业务层不能发送。

## 防护边界

- 帧和解压后数据都有硬上限，拒绝未知包、尾随数据和压缩炸弹。
- 每连接限制每秒包数和等待处理的包数；服务端另有全局队列上限和每 tick 预算。
- 解码失败、方向错误、限流超限和业务校验异常只关闭来源连接。
- 高频输入采用“最新状态覆盖旧状态”，不能让移动包线性堆积。
- 区块请求半径、交互距离、坐标范围、字符串长度、集合长度和序列号都由服务端限制。

## 实施顺序

1. 完成 `ConnectionHost`/`Connection`、方向校验、尺寸限制、限流和异常隔离。
2. 将 `Level` 拆成 `ServerLevel` 与 `ClientLevel`，把生成和保存移入服务端 `ChunkManager`。
3. 引入 `ServerPlayer`/`ClientPlayer`，把移动改为输入包和服务端状态快照。
4. 实现 Interest 驱动的 chunk snapshot/update/unload。
5. 把放置、破坏、液体、形状修改和物品投掷改为服务端校验的意图包。
6. 接入单人 loopback、LAN 开放和 headless dedicated 三种启动流程。
