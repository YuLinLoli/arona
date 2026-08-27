# 独立运行

原生mirai-console直接把插件丢进plugin目录即可

现本插件可独立使用java运行

首次启动会在 JAR 同级目录创建：

运行直接用java -jar arona-\*.\*.\*-onebotAndMirai-all.jar即可

可选参数：--test-notify

（启动后20秒立即向配置文件中启用的群发送‘三服活动日历’）

- `arona-standalone/onebot.yml`（OneBot 协议连接配置）
- `arona-standalone/arona.yml`（Arona 业务配置：群、管理员、每日推送）
- `arona-standalone/images/`
- `arona-standalone/data/`

Mirai Console 插件入口 `net.diyigemt.arona.Arona` 保持不变；插件模式仍使用 Mirai Console 自己的配置目录，不读取这些文件。

插件模式仍使用 Mirai Console 自己的配置目录中的 `arona.yml`（`AutoSavePluginConfig("arona")` 原配置，格式不变）。管理员可用 `/config` 指令查看/修改该配置并热更新：`/config` 以合并转发查看全部配置，`/config <配置名> <值>` 修改后自动保存并重载。

## 配置结构

配置文件为 YAML 格式。`connections` 是键值表，每个连接的**键名即连接类型**

支持的连接名称：

- `ws-forward`：正向 WebSocket 客户端，主动连接 OneBot 服务端，填 `url`。
- `ws-reverse`：反向 WebSocket 服务端，监听 `host:port`，等待 OneBot 服务端连入。
- `http`：正向 HTTP API 服务端，监听 `host:port/path` 对外提供 OneBot API。
- `http-reverse`：反向 HTTP 客户端，向 `url` POST 上报事件。

各连接字段说明：

- `enable`：是否启用该连接。
- `host` / `port` / `path`：监听地址（反向 WS、正向 HTTP 使用）。
- `url`：目标地址（正向 WS、反向 HTTP 使用）。
- `token`：鉴权 token，留空关闭鉴权；非空时使用 `Authorization: Bearer <token>`。
- `heartbeat_interval`：心跳间隔（毫秒），0 表示关闭心跳。
- `reconnect_interval`：正向连接失败重试间隔（毫秒）。

## 业务配置 arona.yml

`groups`、`managers`、`notify` 等非 OneBot 业务配置独立放在 `arona-standalone/arona.yml`，与 `onebot.yml` 分开，避免混淆


首次启动若检测到旧版 `onebot.yml` 里还残留 `groups/managers/notify`，会自动迁移到 `arona.yml`，旧文件里的协议配置不受影响。

`arona.yml` 支持热重载：修改保存后会自动重新加载（`groups`/`managers`/`notify` 立即生效，`every_day_hour` 变更会重建每日推送定时任务），无需重启。旧版 `arona.yaml`/`onebot.yaml` 首次启动会自动迁移到 `.yml`。

## 每日活动推送

到点后会把国服/国际服/日服的活动日历图片推送到目标群。可用 `java -jar arona-standalone-xxx-all.jar --test-notify` 在启动 20 秒后触发一次推送，用于验证推送链路。

## 控制台输出

- 启动时打印黄色 Arona 横幅与版本号，并逐行显示已启用的连接方式。
- 收到的消息按 Mirai Console 风格输出：私聊 `V/Bot.<botId>: 昵称(QQ) -> 内容`；群聊 `V/Bot.<botId>: [群名(群号)] 群名片(QQ) -> 内容`（群名首次通过 `get_group_info` 获取并缓存）。
- 自身触发的消息（命令回复、定时推送等）以黄色输出：`<时间> V/Bot.<botId>: Group(群号)/Friend(QQ) <- 内容`。
- 心跳、原始 JSON 载荷不在黑窗口输出。
- 正向连接（`ws-forward`、`http` 等）每 `reconnect_interval` 重试一次；连续 5 次失败后休息 30 秒再重试，每次失败与休息都会在黑窗口提示。

协议层支持 OneBot 11 动作、响应、事件、`echo`、Bearer token、心跳和断线重连：

- `ws-forward` 断线/连接失败后按 `reconnect_interval` 自动重试，连续 5 次失败休息 30 秒。
- `ws-reverse`、`http-reverse` 按 `heartbeat_interval` 发送 `get_status` 心跳。
- `http-reverse` 同时承担事件上报：收到的每条消息事件都会 POST 到目标 URL，外部服务端可在响应中返回动作。
- 同一份消息事件只会转发给其他连接，不会回显给来源连接，避免反向 WS 客户端收到自己的消息。

## 独立模式命令

- `/arona status`：查看账号、连接数量和数据目录。
- `/arona services`：查看当前启用的 OneBot 连接。
- `/arona version`：查看版本信息。
- `/arona help` 或 `/帮助`：查看独立模式帮助。

独立命令通过跨平台 `CommandContext` 分发，不依赖 Mirai Console 的命令类型。业务回复支持文本、`@` 与图片消息段；OneBot 输出为消息段数组（`text`/`at`/`image`），Mirai 插件模式会上传图片后组合为消息链。收到的 OneBot 消息会解析 CQ 码（如 `[CQ:at,qq=...]`）并输出到黑窗口。

## 独立模式业务命令

独立模式复用了插件核心的抽卡、名字、塔罗、活动等数据库逻辑，数据存放在 `arona-standalone/data/arona.db`：

- `/单抽`、`/十连`、`/狗叫`、`/历史`：抽卡与排行（需先 `update` 远端卡池）。
- `/游戏名 <名字>`、`/谁是 <游戏名>`：游戏名记录与反查。
- `/叫我 <名字>`：自定义群内昵称。
- `/塔罗牌`：抽一张塔罗牌（`data/tarot` 图片缓存）。
- `/活动 日服|国际服|国服`：活动查询，数据优先读数据库缓存（启动时已把三服活动日历写入 `schale.db`），缓存过期后自动联网刷新。
- `/攻略 日服活动|国际服活动|国服活动|日程笔记`：拉取 GameKee 活动攻略与日程笔记图片。
- `/攻略 <学生名>`：查询学生立绘/攻略图（首次会下载到 `data/image/student_rank`）。
- `/抽卡 list|setpool|reset|1s|2s|3s|p2s|p3s|time|limit|update`：抽卡配置管理（管理员）。
- `/config`：查看全部配置（合并转发）；`/config <配置名>` 查看单个；`/config <配置名> <值>` 修改配置并热重载（管理员）。
- `/紧急停止`：非管理员投票制停用全部服务（默认 5 票 / 5 分钟）。
- `/arona service list|enable <名称>|disable <名称>`：服务启停（管理操作需管理员）。

`arona.db` 首次启动会自动建表。抽卡池可通过 `/抽卡 update <id>` 从远端拉取；塔罗牌数据与插件模式一样依赖预置数据，如需继承插件已有的卡池/塔罗数据，直接把插件数据目录里的 `arona.db` 复制到 `arona-standalone/data/` 即可（停止运行后覆盖）。

## 数据文件

- `arona-standalone/data/arona.db`：业务数据库，存放抽卡池、抽卡记录、游戏名/昵称、塔罗牌数据与抽牌记录、图片索引等。
- `arona-standalone/data/schale.db`：SchaleDB 数据缓存，存放学生（用于生日）、总力战、活动本地化名称、当前卡池/活动/总力战（来自 SchaleDB 新版 `data/config.json`）等。
- `arona-standalone/data/image/`：本地图片缓存，含字体、塔罗牌图片（`tarot/`）、学生立绘（`student_rank/`）、GameKee 攻略（`gamekee/`）等。

启动时后台会自动完成：学生/总力战/本地化/当前活动数据同步（每小时刷新）、三服活动日历写库（缓存 12 小时）、塔罗牌 44 张图片预下载、中文字体下载。
