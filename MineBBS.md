# CloudAsk | 云问答

一款面向 Spigot / Paper 服务器的聊天问答插件，支持自动题库、Cron 调度、每题独立奖励与 Redis 群组服同步。

玩家不需要输入额外命令，直接在聊天栏发送答案即可参与抢答。无论问题来自手动发布还是自动题库，插件都会识别首位正确回答者，并在该玩家所在的子服执行对应奖励命令。

> 当前版本：`1.0-SNAPSHOT`  
> 适用服务端：Spigot / Paper `1.20+`  
> Java 版本：Java `17`  
> 作者：`MoutainSeaL`  
> 联系 QQ：`3643203568`

## 功能特色

### 聊天栏直接抢答

- 玩家直接发送聊天消息参与回答，无需学习答题命令。
- 可配置是否忽略英文字母大小写。
- 可配置是否移除答案首尾空格。
- 可将连续空白字符视为一个空格。
- 正确答案消息可以自动从公共聊天中隐藏，避免提前泄露答案。
- 每次只允许一道问题进行，防止多道题目的答案互相干扰。

### 自动问答题库

自动问答使用独立的 `automatic.yml`，不会与 Redis、消息等主配置混在一起。

- 支持随机抽题，并避免连续抽到相同题目。
- 支持按照配置顺序循环出题。
- 支持固定时间间隔自动发布。
- 支持 UNIX 5 段 Cron 表达式。
- 支持 Quartz 6/7 段 Cron 表达式。
- 支持 `Asia/Shanghai`、`UTC` 等 IANA 时区。
- 到达发布时间但当前仍有问题时，会等待空闲后再发布，不会覆盖正在进行的问题。

### 每道题独立奖励

每一道自动问题都可以配置自己的控制台奖励命令：

```yaml
questions:
  - question: "6 × 7 等于多少？"
    answer: "42"
    rewards:
      - "give {player} diamond 2"
      - "eco give {player} 100"

  - question: "中国的首都是什么？"
    answer: "北京"
    rewards:
      - "give {player} emerald 5"
```

省略 `rewards` 或填写 `rewards: []` 时，该题不会发放奖励。手动发布的问题则使用 `config.yml` 中的默认奖励列表。

### Redis 群组服同步

启用 Redis 模式后：

- 所有子服玩家都能收到同一道问题。
- 任意子服的玩家都可以参与回答。
- 问题、答案、来源服务器和奖励列表会同步到所有节点。
- 使用 Redis Lua 脚本原子判定胜者。
- 多个子服同时出现正确答案时，只会产生一名获奖者。
- 奖励只在获胜玩家实际所在的插件实例执行一次。
- Redis 订阅断线后会自动重连并补回仍在进行的问题。
- 不需要额外安装 BungeeCord 或 Velocity 端插件。

Redis 不可用时，群组模式会明确拒绝发布或判定答案，不会静默切换到本地模式，从而避免不同子服分别发奖。

## 运行环境

| 项目 | 要求 |
| --- | --- |
| Minecraft | `1.20.1` |
| 服务端 | Spigot / Paper |
| Java | `17` 或更高 |
| Redis | 仅群组服的 `redis` 模式需要 |
| 前置插件 | 无 |

插件使用 Spigot / Paper LibraryLoader 加载以下运行库：

```yaml
libraries:
  - redis.clients:jedis:5.2.0
  - com.google.code.gson:gson:2.11.0
  - com.cronutils:cron-utils:9.2.1
```

这些依赖不会打包在插件 JAR 中。服务器首次加载插件时必须能够访问 Maven Central；依赖成功缓存后，后续是否需要联网取决于服务端的本地缓存状态。

## 安装方法

1. 下载 `CloudAsk-1.0-SNAPSHOT.jar`。
2. 将 JAR 放入服务端的 `plugins` 目录。
3. 确认服务器首次启动时能够访问 Maven Central。
4. 启动服务器，插件会生成：

```text
plugins/CloudAsk/
├── config.yml
└── automatic.yml
```

5. 单服可以直接使用默认的 `local` 模式。
6. 群组服需要修改 Redis 配置，并在所有子服安装相同版本的插件。

## 命令说明

主命令：`/cloudask`  
命令别名：`/cask`

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/cloudask` | 显示命令帮助 | 无 |
| `/cloudask help` | 显示命令帮助 | 无 |
| `/cloudask ask <答案> <问题...>` | 手动发布单词答案的问题 | `cloudask.ask` |
| `/cloudask ask <多词答案> \| <问题...>` | 手动发布多词答案的问题 | `cloudask.ask` |
| `/cloudask stop` | 停止当前问题 | `cloudask.admin` |
| `/cloudask status` | 查看当前问题、来源和剩余时间 | 无 |
| `/cloudask reload` | 重载主配置、Redis 和自动题库 | `cloudask.admin` |

手动发布示例：

```text
/cloudask ask 北京 中国的首都是什么？
/cloudask ask New York | 美国人口最多的城市是？
```

多词答案模式中的 `|` 必须作为独立参数，与答案和问题之间保留空格。

## 权限说明

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `cloudask.answer` | 所有玩家 | 允许通过聊天参与回答 |
| `cloudask.ask` | OP | 允许手动发布问题 |
| `cloudask.admin` | OP | 允许停止问题和重载配置 |

## 单服配置

默认配置即为单服模式：

```yaml
mode: local
server-id: auto

question:
  timeout-seconds: 60
  cancel-correct-answer-message: true
  ignore-case: true
  trim: true
  collapse-spaces: true
```

`server-id: auto` 会根据服务端端口生成标识，例如 `server-25565`。

## 群组服配置

所有子服需要连接同一个 Redis 数据库，并使用相同的 `namespace`：

```yaml
mode: redis
server-id: survival

redis:
  host: 127.0.0.1
  port: 6379
  username: ""
  password: ""
  database: 0
  ssl: false
  timeout-millis: 3000
  namespace: cloudask
```

群组服部署要求：

1. 每个子服安装相同版本的 CloudAsk。
2. 所有节点使用相同的 Redis `host`、`port`、`database` 和 `namespace`。
3. 建议为每个子服设置清晰且不同的 `server-id`，例如 `lobby`、`survival`、`minigame`。
4. 所有节点保持相同的答案匹配配置。
5. 不要公开 Redis 密码或包含真实密码的配置文件。

## 自动发布配置

自动问答位于 `automatic.yml`，默认关闭。

### 固定间隔模式

```yaml
enabled: true

schedule:
  mode: interval
  initial-delay-seconds: 30
  interval-seconds: 300
  retry-when-busy-seconds: 10

order: random
asker-name: "自动问答"
```

上述配置表示插件启动 30 秒后尝试发布第一题，此后每 300 秒尝试发布一次。

### Cron 模式

```yaml
enabled: true

schedule:
  mode: cron
  cron: "0 0/5 * * * ?"
  timezone: "Asia/Shanghai"
  retry-when-busy-seconds: 10

order: sequential
asker-name: "自动问答"
```

`0 0/5 * * * ?` 是 Quartz 表达式，表示每 5 分钟的第 0 秒触发一次。

Cron 支持：

- UNIX 5 段，例如 `*/5 * * * *`
- Quartz 6 段，例如 `0 0/5 * * * ?`
- Quartz 7 段，包括年份字段

Cron 表达式或时区无效时，自动问答不会启动，并会在服务端日志中输出错误原因。

## 奖励命令变量

奖励命令由控制台执行，不要填写开头的 `/`。

| 变量 | 内容 |
| --- | --- |
| `{player}` | 获胜玩家名称 |
| `{uuid}` | 获胜玩家 UUID |
| `{question}` | 当前问题文本 |
| `{asker}` | 手动发布者或自动发布者名称 |
| `{server}` | 获胜玩家所在子服标识 |

手动问题默认奖励示例：

```yaml
rewards:
  commands:
    - "give {player} diamond 1"
    - "eco give {player} 100"
```

奖励命令拥有控制台权限，请仅配置可信命令，不要将不可信文本拼接到高权限管理命令中。

## 消息自定义

全部问题、答对、超时、停止、状态和错误消息均可在 `config.yml` 的 `messages` 中修改，并支持传统 `&` 颜色代码。

主要消息变量：

| 消息 | 可用变量 |
| --- | --- |
| `messages.question` | `{question}`、`{seconds}`、`{server}`、`{asker}` |
| `messages.answered` | `{player}`、`{answer}`、`{question}`、`{server}`、`{elapsed}` |
| `messages.expired` | `{answer}`、`{question}` |
| `messages.busy` | `{question}` |
| `messages.status` | `{question}`、`{seconds}`、`{server}` |

## 性能与安全

- 普通聊天只在本地匹配到正确答案时才提交 Redis，不会让每条聊天消息都访问 Redis。
- Redis 操作位于独立 I/O 线程，不会在主线程执行网络请求。
- Redis Lua 脚本保证问题发布、胜者登记和停止操作的原子性。
- 自动调度器每秒进行一次轻量时间检查。
- 当前问题在 Redis 中设置与答题时间一致的 TTL，答对或停止后立即删除。
- Redis 故障时不会自动进入可能重复发奖的降级模式。

## 重载说明

执行：

```text
/cloudask reload
```

会执行以下操作：

- 取消旧的自动问答任务。
- 关闭旧 Redis 连接。
- 重新读取 `config.yml`。
- 重新读取 `automatic.yml`。
- 重新创建 Redis 或本地后端。
- 根据新配置重新建立自动调度。

Redis 模式下，重载后会重新同步仍在进行的问题。`local` 模式的问题只存在于内存中，因此不建议在问题进行期间执行重载。

插件不会覆盖已经存在的配置文件。版本更新后新增的配置项不会自动写入旧文件，请根据新版默认配置手动补充。

## 常见问题

### 单服必须安装 Redis 吗？

不需要。保持 `mode: local` 即可完整使用手动问答、自动题库、Cron 和奖励功能。

### 群组服需要代理端插件吗？

不需要。所有子服通过 Redis 直接同步，不依赖 BungeeCord 或 Velocity 插件消息。

### 两个子服同时有人答对会重复发奖吗？

不会。胜者由 Redis Lua 脚本原子判定，并使用插件实例标识保证奖励只执行一次。

### 为什么首次启动时提示依赖下载失败？

CloudAsk 使用 LibraryLoader。请确认服务端可以访问 Maven Central，并检查防火墙、代理和 DNS 设置。

### 修改题库后如何生效？

保存 `automatic.yml` 后执行 `/cloudask reload`。已经发布的问题不会被修改，新题会使用重载后的配置。

### 可以让某一道题没有奖励吗？

可以，删除该题的 `rewards` 或填写：

```yaml
rewards: []
```

## 问题反馈

反馈问题时建议提供：

- 服务端核心及完整版本。
- Java 版本。
- CloudAsk 版本。
- 是否启用 Redis 模式。
- 脱敏后的相关配置。
- 问题发生时的完整控制台日志。

请勿公开 Redis 密码或其他服务器凭据。

作者：MoutainSeaL  
联系 QQ：3643203568
