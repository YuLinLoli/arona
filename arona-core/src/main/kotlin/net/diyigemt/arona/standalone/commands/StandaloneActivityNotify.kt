/**
 * 文件说明：独立模式的每日活动推送服务。
 * 具体职责：每天定时向配置的群推送国服/国际服/日服活动日历图片。
 */
package net.diyigemt.arona.standalone.commands

import kotlinx.coroutines.runBlocking
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.runtime.NotifyConfig
import net.diyigemt.arona.quartz.QuartzProvider
import net.diyigemt.arona.runtime.MessageTarget
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.runtime.RuntimeServices
import net.diyigemt.arona.service.AronaQuartzService
import net.diyigemt.arona.util.ActivityUtil
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobKey

object StandaloneActivityNotify : AronaQuartzService {
  override var jobKey: JobKey? = null
  override val id: Int = 12
  override val name: String = "活动推送"
  override var enable: Boolean = true

  @Volatile
  var notifyConfig: NotifyConfig = NotifyConfig()

  class ActivityNotifyJob : Job {
    override fun execute(context: JobExecutionContext?) {
      StandaloneActivityNotify.push()
    }
  }

  fun push() {
    val config = notifyConfig
    if (!config.enable) return
    val sender = RuntimeServices.messageSender ?: run {
      RuntimeLog.warning("活动推送未执行: 消息发送器尚未就绪")
      return
    }
    val targets = resolveTargets(config, RuntimeConfig.groups)
    if (targets.isEmpty()) {
      RuntimeLog.warning("活动推送未执行: 未配置推送目标群")
      return
    }
    if (config.jp) pushServer(sender, ServerLocale.JP, "${config.notifyText}(日服)", targets)
    if (config.global) pushServer(sender, ServerLocale.GLOBAL, "${config.notifyText}(国际服)", targets)
    if (config.cn) pushServer(sender, ServerLocale.CN, "${config.notifyText}(国服)", targets)
  }

  /** 计算推送目标群：全局允许的群去掉 black_groups 黑名单中的群 */
  internal fun resolveTargets(config: NotifyConfig, globalGroups: List<Long>): List<Long> =
    globalGroups.filterNot { it in config.blackGroups }

  private fun pushServer(
    sender: net.diyigemt.arona.runtime.MessageSender,
    server: ServerLocale,
    prefix: String,
    targets: List<Long>,
  ) {
    val activities = runCatching {
      when (server) {
        ServerLocale.JP -> ActivityUtil.fetchJPActivity()
        ServerLocale.GLOBAL -> ActivityUtil.fetchENActivity()
        ServerLocale.CN -> ActivityUtil.fetchCNActivity()
      }
    }.onFailure { RuntimeLog.warning("拉取${server.serverName}活动失败: ${it.message}") }.getOrNull() ?: return
    val imageFile = runCatching { ActivityUtil.createActivityImage(activities, server) }
      .onFailure { RuntimeLog.warning("生成${server.serverName}活动图片失败: ${it.message}") }
      .getOrNull() ?: return
    val message = OutgoingMessage.text(prefix) + OutgoingMessage.image(imageFile.absolutePath)
    targets.forEach { groupId ->
      runBlocking {
        runCatching { sender.send(MessageTarget.Group(groupId), message) }
          .onFailure { RuntimeLog.warning("推送${server.serverName}活动到群 $groupId 失败: ${it.message}") }
      }
    }
  }

  override fun init() {
    registerService()
  }

  override fun enableService() {
    val hour = notifyConfig.everyDayHour.coerceIn(0, 23)
    jobKey = QuartzProvider.createCronTask(
      ActivityNotifyJob::class.java,
      "0 0 $hour * * ? *",
      "StandaloneActivityNotify",
      "StandaloneActivityNotify",
    ).first
    RuntimeLog.info("活动推送已启用, 每天 $hour 点推送")
  }
}
