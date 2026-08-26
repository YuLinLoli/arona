/**
 * 文件说明：独立模式的「/活动」命令。
 * 具体职责：查询国服/国际服/日服活动日历, 优先使用数据库缓存, 否则联网拉取并写入数据库。
 */
package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.entity.Activity
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.util.ActivityUtil

object StandaloneActivity {
  suspend fun activity(context: CommandContext, arguments: List<String>): OutgoingMessage {
    val source = arguments.firstOrNull()
    val preMatch = ServerLocale.values().firstOrNull { it.commandName == source || it.serverName == source }
    return when {
      source.isNullOrBlank() || preMatch == ServerLocale.JP -> runCatching { fetch(ServerLocale.JP) }
        .fold(onSuccess = { send(it, ServerLocale.JP) }, onFailure = { OutgoingMessage.text("获取日服活动失败: ${it.message}") })
      preMatch == null -> OutgoingMessage.text(
        "参数不匹配, 是否想要执行:\n" +
          "/活动 日服 # 查询日服活动\n" +
          "/活动 国服 # 查询国服活动\n" +
          "/活动 国际服 # 查询国际服活动"
      )
      preMatch == ServerLocale.GLOBAL -> runCatching { fetch(ServerLocale.GLOBAL) }
        .fold(onSuccess = { send(it, ServerLocale.GLOBAL) }, onFailure = { OutgoingMessage.text("获取国际服活动失败: ${it.message}") })
      else -> runCatching { fetch(ServerLocale.CN) }
        .fold(onSuccess = { send(it, ServerLocale.CN) }, onFailure = { OutgoingMessage.text("获取国服活动失败: ${it.message}") })
    }
  }

  /**
   * 优先读取数据库缓存(12小时内同步过), 否则联网拉取并写入数据库。
   */
  private fun fetch(server: ServerLocale): Pair<List<Activity>, List<Activity>> {
    StandaloneActivitySync.loadFromDb(server)?.let { return it }
    val remote = runCatching {
      when (server) {
        ServerLocale.JP -> ActivityUtil.fetchJPActivity()
        ServerLocale.GLOBAL -> ActivityUtil.fetchENActivity()
        ServerLocale.CN -> ActivityUtil.fetchCNActivity()
      }
    }.getOrNull()
    if (remote != null) {
      StandaloneActivitySync.saveToDb(server, remote)
      return remote
    }
    return StandaloneActivitySync.loadFromDb(server) ?: (emptyList<Activity>() to emptyList())
  }

  private fun send(activities: Pair<List<Activity>, List<Activity>>, server: ServerLocale): OutgoingMessage {
    val imageFile = ActivityUtil.createActivityImage(activities, server)
    return OutgoingMessage.image(imageFile.absolutePath)
  }
}
