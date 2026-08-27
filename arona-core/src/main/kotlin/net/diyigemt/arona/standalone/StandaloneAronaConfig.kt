package net.diyigemt.arona.standalone

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import net.diyigemt.arona.runtime.AronaConfig
import net.diyigemt.arona.runtime.AronaConfigLoader
import net.diyigemt.arona.runtime.ConfigValueParser
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.standalone.commands.StandaloneActivityNotify
import net.diyigemt.arona.util.other.KWatchChannel
import net.diyigemt.arona.util.other.KWatchEvent
import net.diyigemt.arona.util.other.asWatchChannel
import java.nio.file.Path

/**
 * 独立模式的 arona 业务配置持有者。
 * 负责：加载 arona.yml、监听文件变更自动热重载、为 /config 指令提供读写能力。
 */
object StandaloneAronaConfig {

  @Volatile
  var config: AronaConfig = AronaConfig()

  @Volatile
  private var file: Path? = null

  @Volatile
  private var watcher: KWatchChannel? = null

  /** 上一次应用到的推送小时，用于检测 every_day_hour 变更后重建定时任务 */
  @Volatile
  private var lastNotifyHour: Int = -1

  /** 可通过 /config 修改的配置项描述 */
  data class ConfigField(
    val key: String,
    val description: String,
    val get: (AronaConfig) -> Any?,
    val set: (AronaConfig, Any?) -> AronaConfig,
  )

  @Suppress("UNCHECKED_CAST")
  val fields: List<ConfigField> = listOf(
    ConfigField("groups", "允许响应的群号列表，留空表示响应所有群", { it.groups },
      { c, v -> c.copy(groups = v as List<Long>) }),
    ConfigField("managers", "管理员 QQ 号列表，可执行管理命令", { it.managers },
      { c, v -> c.copy(managers = v as List<Long>) }),
    ConfigField("notify.enable", "是否启用每日活动防侠推送", { it.notify.enable },
      { c, v -> c.copy(notify = c.notify.copy(enable = v as Boolean)) }),
    ConfigField("notify.every_day_hour", "每日推送的小时(0-23)", { it.notify.everyDayHour },
      { c, v -> c.copy(notify = c.notify.copy(everyDayHour = v as Int)) }),
    ConfigField("notify.jp", "是否推送日服活动", { it.notify.jp },
      { c, v -> c.copy(notify = c.notify.copy(jp = v as Boolean)) }),
    ConfigField("notify.global", "是否推送国际服活动", { it.notify.global },
      { c, v -> c.copy(notify = c.notify.copy(global = v as Boolean)) }),
    ConfigField("notify.cn", "是否推送国服活动", { it.notify.cn },
      { c, v -> c.copy(notify = c.notify.copy(cn = v as Boolean)) }),
    ConfigField("notify.black_groups", "不推送的群号列表（黑名单），留空表示推送到全部允许的群", { it.notify.blackGroups },
      { c, v -> c.copy(notify = c.notify.copy(blackGroups = v as List<Long>)) }),
    ConfigField("notify.notify_text", "推送消息开头文字", { it.notify.notifyText },
      { c, v -> c.copy(notify = c.notify.copy(notifyText = v as String)) }),
  )

  fun defaultFile(): Path = AronaConfigLoader.defaultFile()

  fun init(path: Path) {
    file = path
    reload()
    startWatcher(path)
  }

  fun findField(key: String): ConfigField? =
    fields.firstOrNull { it.key == key || it.key.removePrefix("notify.") == key }

  /** 重新从磁盘读取 arona.yml 并同步到运行期配置 */
  fun reload() {
    val path = file ?: return
    val newConfig = runCatching { AronaConfigLoader.load(path) }.getOrElse { error ->
      RuntimeLog.warning("arona.yml 热重载失败，保留当前配置: ${error.message}")
      return
    }
    apply(newConfig)
  }

  /** /config <key> <value>: 修改配置并写回文件后重载 */
  fun update(key: String, rawValue: String): String {
    val field = findField(key) ?: return "未找到配置项: $key"
    val parsed = runCatching { ConfigValueParser.parse(rawValue, field.get(config)) }
      .getOrElse { return "配置值解析失败: ${it.message}" }
    val newConfig = field.set(config, parsed)
    val path = file ?: return "配置文件尚未初始化"
    runCatching { AronaConfigLoader.save(path, newConfig) }
      .getOrElse { return "写入配置文件失败: ${it.message}" }
    reload()
    return "配置已更新: ${field.key} = ${field.get(config)}"
  }

  private fun apply(newConfig: AronaConfig) {
    config = newConfig
    RuntimeConfig.groups = newConfig.groups
    RuntimeConfig.managers = newConfig.managers
    val newNotify = newConfig.notify
    StandaloneActivityNotify.notifyConfig = newNotify
    if (lastNotifyHour != -1 && lastNotifyHour != newNotify.everyDayHour) {
      // 推送小时变更: 先删除旧定时任务再重建（Quartz 同 key 重复 scheduleJob 会抛异常）
      runCatching {
        StandaloneActivityNotify.disableService()
        StandaloneActivityNotify.enableService()
      }.onFailure { RuntimeLog.warning("更新活动推送定时任务失败: ${it.message}") }
    }
    lastNotifyHour = newNotify.everyDayHour
    RuntimeLog.info("arona.yml 配置已重载")
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun startWatcher(path: Path) {
    if (watcher != null) return
    GlobalScope.launch {
      val channel = path.toFile().asWatchChannel(KWatchChannel.Mode.SingleFile)
      watcher = channel
      channel.consumeEach { event ->
        if (event.kind == KWatchEvent.Kind.Modified) {
          reload()
        }
      }
    }
  }
}
