package net.diyigemt.arona.standalone

import net.diyigemt.arona.cg.BuildConfig
import net.diyigemt.arona.onebot.ConnectionConfig
import net.diyigemt.arona.onebot.ConnectionType
import net.diyigemt.arona.onebot.OneBotConfig
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.CommandHandler
import net.diyigemt.arona.runtime.CommandRegistration
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeServices
import net.diyigemt.arona.runtime.SimpleCommandDispatcher
import net.diyigemt.arona.service.AronaServiceManager
import net.diyigemt.arona.standalone.commands.StandaloneActivity
import net.diyigemt.arona.standalone.commands.StandaloneTrainer
import net.diyigemt.arona.standalone.commands.StandaloneEmergencyStop
import net.diyigemt.arona.standalone.commands.StandaloneGacha
import net.diyigemt.arona.standalone.commands.StandaloneGachaAdmin
import net.diyigemt.arona.standalone.commands.StandaloneName
import net.diyigemt.arona.standalone.commands.StandaloneServiceInfo
import net.diyigemt.arona.standalone.commands.StandaloneServices
import net.diyigemt.arona.standalone.commands.StandaloneTarot

class StandaloneCommandDispatcher(
  private val config: OneBotConfig,
) {
  private val emergencyStop = StandaloneEmergencyStop()

  init {
    StandaloneServices.registerAll()
  }

  val dispatcher = SimpleCommandDispatcher(
    listOf(
      CommandRegistration(setOf("/arona", "arona"), "查看 Arona 状态与帮助", AronaCommandHandler(this)),
      CommandRegistration(setOf("/帮助", "/help"), "查看独立模式帮助", HelpCommandHandler(this)),
      CommandRegistration(
        setOf("/单抽", "gacha_one"), "单抽一次",
        GuardedHandler(StandaloneServices.GACHA_SINGLE) { context -> StandaloneGacha.singleDraw(context) },
      ),
      CommandRegistration(
        setOf("/十连", "gacha_multi"), "模拟十连",
        GuardedHandler(StandaloneServices.GACHA_MULTI) { context -> StandaloneGacha.multiDraw(context) },
      ),
      CommandRegistration(
        setOf("/狗叫", "gacha_dog"), "查看抽出pick的人",
        GuardedHandler(StandaloneServices.GACHA_DOG) { context -> StandaloneGacha.dogRanking(context) },
      ),
      CommandRegistration(
        setOf("/历史", "gacha_history"), "抽卡历史记录",
        GuardedHandler(StandaloneServices.GACHA_HISTORY) { context -> StandaloneGacha.historyRanking(context) },
      ),
      CommandRegistration(
        setOf("/游戏名", "game_name"), "记录游戏名与群名的对应关系",
        ArgumentGuardedHandler(StandaloneServices.GAME_NAME) { context, args -> StandaloneName.gameName(context, args.firstOrNull()) },
      ),
      CommandRegistration(
        setOf("/谁是", "谁叫", "game_name_search"), "根据游戏名反查群友",
        ArgumentGuardedHandler(StandaloneServices.GAME_NAME_SEARCH) { context, args -> StandaloneName.search(context, args.firstOrNull() ?: "") },
      ),
      CommandRegistration(
        setOf("/叫我", "call_me"), "给自己自定义昵称",
        ArgumentGuardedHandler(StandaloneServices.CALL_ME) { context, args -> StandaloneName.callMe(context, args.firstOrNull()) },
      ),
      CommandRegistration(
        setOf("/塔罗牌", "tarot"), "抽一张塔罗牌",
        GuardedHandler(StandaloneServices.TAROT) { context -> StandaloneTarot.tarot(context) },
      ),
      CommandRegistration(
        setOf("/活动", "active"), "通过wiki获取活动列表",
        ArgumentGuardedHandler(StandaloneServices.ACTIVITY) { context, args -> StandaloneActivity.activity(context, args) },
      ),
      CommandRegistration(
        setOf("/攻略", "trainer"), "主线地图、学生攻略与活动攻略",
        ArgumentGuardedHandler(StandaloneServices.TRAINER) { context, args -> StandaloneTrainer.trainer(context, args) },
      ),
      CommandRegistration(
        setOf("/紧急停止", "emergency_stop"), "非管理员投票制停止服务",
        GuardedHandler(StandaloneServices.EMERGENCY_STOP) { context -> emergencyStop.vote(context) },
      ),
      CommandRegistration(
        setOf("/抽卡", "gacha"), "抽卡配置管理",
        ArgumentGuardedHandler(StandaloneServices.GACHA_CONFIG) { context, args -> StandaloneGachaAdmin.handle(context, args) },
      ),
    ),
    fallbackHandler = { context ->
      // 非命令消息: 优先处理「/攻略」相近建议的数字回复
      StandaloneTrainer.resolveNumericReply(context)?.let { context.reply(it) }
    },
  )

  suspend fun handleArona(context: CommandContext, arguments: List<String>) {
    when (arguments.firstOrNull()?.lowercase()) {
      null, "status", "状态" -> context.reply(statusText())
      "version", "版本" -> context.reply(versionText())
      "help", "帮助" -> context.reply(helpText())
      "service", "services", "连接" -> handleService(context, arguments)
      else -> context.reply("未知子命令。使用 /arona help 查看帮助。")
    }
  }

  suspend fun handleHelp(context: CommandContext) {
    context.reply(helpText())
  }

  private suspend fun handleService(context: CommandContext, arguments: List<String>) {
    when (arguments.getOrNull(1)?.lowercase()) {
      "list", "列表" -> context.reply(serviceListText())
      "enable", "启用" -> {
        if (!context.isAdmin) {
          context.reply("权限不足")
          return
        }
        val name = arguments.getOrNull(2)
        if (name.isNullOrBlank()) {
          context.reply("用法: /arona service enable <名称>")
          return
        }
        val service = AronaServiceManager.enable(name)
        context.reply(if (service == null) "未找到服务: $name" else "服务已启用: ${service.name}")
      }
      "disable", "停用" -> {
        if (!context.isAdmin) {
          context.reply("权限不足")
          return
        }
        val name = arguments.getOrNull(2)
        if (name.isNullOrBlank()) {
          context.reply("用法: /arona service disable <名称>")
          return
        }
        val service = AronaServiceManager.disable(name)
        context.reply(if (service == null) "未找到服务: $name" else "服务已停用: ${service.name}")
      }
      else -> context.reply(connectionText())
    }
  }

  private fun statusText(): String = buildString {
    appendLine("Arona 独立模式运行中")
    appendLine("账号: ${config.nickname} (${config.selfId})")
    appendLine("连接: ${config.connections.values.count { it.enable }}/${config.connections.size} 已启用")
    append("数据目录: ${RuntimeServices.dataRoot?.toAbsolutePath() ?: "未设置"}")
  }

  private fun helpText(): String = buildString {
    appendLine("Arona 独立模式命令")
    appendLine("/arona status - 查看运行状态")
    appendLine("/arona services - 查看 OneBot 连接")
    appendLine("/arona service list - 查看已注册服务")
    appendLine("/arona version - 查看版本信息")
    appendLine("/单抽 /十连 /狗叫 /历史 - 抽卡")
    appendLine("/游戏名 /谁是 /叫我 - 名字记录")
    appendLine("/塔罗牌 /活动 /攻略 - 娱乐与攻略查询")
    appendLine("/紧急停止 - 投票停止服务")
    append("/arona help - 查看本帮助")
  }

  private fun versionText(): String = buildString {
    appendLine("Arona 版本信息")
    appendLine("版本: ${BuildConfig.version}")
    appendLine("插件 ID: ${BuildConfig.id}")
    append("名称: ${BuildConfig.name}")
  }

  private fun connectionText(): String {
    val enabled = config.connections.filterValues { it.enable }
    if (enabled.isEmpty()) return "当前没有启用 OneBot 连接。"
    return enabled.entries.mapIndexed { index, (name, connection) ->
      val type = ConnectionType.fromName(name)
      "${index + 1}. ${type.displayName()} ${connection.address(type)}"
    }.joinToString("\n", prefix = "已启用的 OneBot 连接:\n")
  }

  private fun serviceListText(): String {
    val services = AronaServiceManager.getAllService()
    if (services.isEmpty()) return "当前没有注册服务"
    return services.joinToString("\n", prefix = "已注册服务:\n") { service ->
      "${service.name}(${service.id}): ${if (service.enable) "已启用" else "已停用"}"
    }
  }

  private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WEBSOCKET -> "WebSocket 正向"
    ConnectionType.WEBSOCKET_REVERSE -> "WebSocket 反向"
    ConnectionType.HTTP -> "HTTP 正向"
    ConnectionType.HTTP_REVERSE -> "HTTP 反向"
  }

  private fun ConnectionConfig.address(type: ConnectionType): String = when (type) {
    ConnectionType.WEBSOCKET, ConnectionType.HTTP_REVERSE -> url
    ConnectionType.WEBSOCKET_REVERSE, ConnectionType.HTTP -> "$host:$port$path"
  }
}

private class AronaCommandHandler(
  private val owner: StandaloneCommandDispatcher,
) : CommandHandler {
  override suspend fun handle(context: CommandContext, arguments: List<String>) {
    owner.handleArona(context, arguments)
  }
}

private class HelpCommandHandler(
  private val owner: StandaloneCommandDispatcher,
) : CommandHandler {
  override suspend fun handle(context: CommandContext, arguments: List<String>) {
    owner.handleHelp(context)
  }
}

private class GuardedHandler(
  private val service: StandaloneServiceInfo,
  private val block: suspend (CommandContext) -> OutgoingMessage?,
) : CommandHandler {
  override suspend fun handle(context: CommandContext, arguments: List<String>) {
    if (!guarded(context)) return
    block(context)?.let { context.reply(it) }
  }

  private suspend fun guarded(context: CommandContext): Boolean {
    if (!service.enable) {
      context.reply("功能未启用")
      return false
    }
    if (service.groupOnly && context.groupId == null) {
      context.reply("该功能仅限群聊使用")
      return false
    }
    if (service.adminOnly && !context.isAdmin) {
      context.reply("权限不足")
      return false
    }
    return true
  }
}

private class ArgumentGuardedHandler(
  private val service: StandaloneServiceInfo,
  private val block: suspend (CommandContext, List<String>) -> OutgoingMessage?,
) : CommandHandler {
  override suspend fun handle(context: CommandContext, arguments: List<String>) {
    if (!guarded(context)) return
    block(context, arguments)?.let { context.reply(it) }
  }

  private suspend fun guarded(context: CommandContext): Boolean {
    if (!service.enable) {
      context.reply("功能未启用")
      return false
    }
    if (service.groupOnly && context.groupId == null) {
      context.reply("该功能仅限群聊使用")
      return false
    }
    if (service.adminOnly && !context.isAdmin) {
      context.reply("权限不足")
      return false
    }
    return true
  }
}
