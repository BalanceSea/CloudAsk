# CloudAsk

CloudAsk 是一个 Spigot/Paper 1.20.1 聊天问答插件。玩家可以直接在聊天栏输入答案，答对者在其所在子服执行奖励命令。

## 使用

```text
/cloudask ask <答案> <问题>
/cloudask ask <多词答案> | <问题>
/cloudask stop
/cloudask status
/cloudask reload
```

`ask` 需要 `cloudask.ask`，停止和重载需要 `cloudask.admin`。普通玩家默认拥有 `cloudask.answer`。

## 自动发布问答

自动问答使用独立的 `automatic.yml`。启用并配置题库：

```yaml
enabled: true
schedule:
  mode: cron
  initial-delay-seconds: 30
  interval-seconds: 300
  cron: "0 0/5 * * * ?"
  timezone: "Asia/Shanghai"
  retry-when-busy-seconds: 10
order: random # random 或 sequential
asker-name: "自动问答"
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

`schedule.mode` 可选 `interval` 或 `cron`。Cron 支持 UNIX 5 段和 Quartz 6/7 段表达式，并按照 `timezone` 计算。自动发布不会覆盖正在进行的问题；到达执行时间但有题目进行时，会按 `retry-when-busy-seconds` 延后重试。

`random` 会随机抽题并避免连续重复，`sequential` 会按配置顺序循环。修改后执行 `/cloudask reload` 即可重新读取 `automatic.yml` 并重建调度任务。

自动题库中的每一道题都拥有独立的 `rewards` 命令列表。省略 `rewards` 或设置为 `[]` 时，该题答对后不发奖励。手动通过 `/cloudask ask` 发布的问题仍使用 `config.yml` 的 `rewards.commands` 默认奖励。

## 群组服配置

1. 将 `config.yml` 的 `mode` 改为 `redis`。
2. 所有子服填写相同的 `redis.host`、端口、认证信息、数据库和 `redis.namespace`。
3. 为每个子服设置不同的 `server-id`，例如 `lobby`、`survival`；不要让多个子服使用同一个标识。
4. 将构建出的 `build/libs/CloudAsk-1.0-SNAPSHOT.jar` 放入每个子服的 `plugins` 目录，并重启服务器。

Redis 用 Lua 脚本原子判定胜者，同一问题不会在多个子服重复发奖。Redis 不可用时，群组模式会提示错误并拒绝发布或判定答案，不会静默切换成本地模式。

## 奖励命令

在 `rewards.commands` 中填写控制台命令，不要写开头的 `/`。可用变量为 `{player}`、`{uuid}`、`{question}`、`{asker}`、`{server}`。命令在获胜玩家所在的子服执行，例如：

```yaml
rewards:
  commands:
    - "give {player} diamond 1"
    - "eco give {player} 100"
```

作者：MoutainSeaL（QQ：3643203568）
