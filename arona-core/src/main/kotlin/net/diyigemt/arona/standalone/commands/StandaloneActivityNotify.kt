/**
 * 文件说明：独立模式的每日活动推送与结束预警服务。
 * 具体职责：每天定时向配置的群推送 国服/国际服/日服 活动日历图片; 启动与每日运行时查询活动结束预警(提前1小时/双倍掉落提前5小时), 正负10分钟内立即发送, 过期抛弃, 未来添加定时任务(去重)。
 */
package net.diyigemt.arona.standalone.commands

import kotlinx.coroutines.runBlocking
import net.diyigemt.arona.entity.Activity
import net.diyigemt.arona.entity.ActivityType
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.quartz.QuartzProvider
import net.diyigemt.arona.runtime.MessageSender
import net.diyigemt.arona.runtime.MessageTarget
import net.diyigemt.arona.runtime.NotifyConfig
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.runtime.RuntimeServices
import net.diyigemt.arona.service.AronaQuartzService
import net.diyigemt.arona.util.ActivityUtil
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import java.util.Date

object StandaloneActivityNotify : AronaQuartzService {
  override var jobKey: JobKey? = null
  override val id: Int = 12
  override val name: String = "活动推送"
  override var enable: Boolean = true

  @Volatile
  var notifyConfig: NotifyConfig = NotifyConfig()

  private const val ActivityNotifyInitKey = "init"
  private const val OneHourJobKey = "StandaloneActivityNotifyOneHour"
  private const val ActivityKey = "activity"
  private const val ActivityNotifyBeforeHoursKey = "beforeHours"
  private const val NormalActivityNotifyBeforeHours = 1
  private const val DropActivityNotifyBeforeHours = 5
  private const val AlertImmediateWindowMillis = 10 * 60 * 1000L

  class ActivityNotifyJob : Job {
    override fun execute(context: JobExecutionContext?) {
      val init = context?.mergedJobDataMap?.getBoolean(ActivityNotifyInitKey) ?: false
      StandaloneActivityNotify.push(skipImage = init)
    }
  }

  /** 活动结束预警任务: 在活动结束前 5 小时与 1 小时发送提醒(除生日外的活动两次都会提醒) */
  @Suppress("UNCHECKED_CAST")
  class ActivityNotifyOneHourJob : Job {
    override fun execute(context: JobExecutionContext?) {
      val data = context?.mergedJobDataMap ?: return
      val ac = data.get(ActivityKey) ?: return
      ac as List<Activity>
      if (ac.isEmpty()) return
      val beforeHours = data.getInt(ActivityNotifyBeforeHoursKey).takeIf { it > 0 } ?: NormalActivityNotifyBeforeHours
      StandaloneActivityNotify.sendAlert(ac, ac[0].serverLocale, beforeHours)
    }
  }

  /**
   * 每日推送与预警调度入口。
   * skipImage=true 表示启动时的初始化运行: 只做 5小时/1小时 预警调度, 不推送日历图。
   */
  fun push(skipImage: Boolean = false) {
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
    // 5小时/1小时预警: 每次运行都查询; 正负10分钟内立即发送, 过时抛弃, 未来添加定时任务(去重)
    scheduleAlerts()
    if (skipImage) return
    if (config.jp) pushServer(sender, ServerLocale.JP, "${config.notifyText}(日服)", targets)
    if (config.global) pushServer(sender, ServerLocale.GLOBAL, "${config.notifyText}(国际服)", targets)
    if (config.cn) pushServer(sender, ServerLocale.CN, "${config.notifyText}(国服)", targets)
  }

  /** 计算推送目标群: 全局允许的群去掉 black_groups 黑名单中的群 */
  internal fun resolveTargets(config: NotifyConfig, globalGroups: List<Long>): List<Long> =
    globalGroups.filterNot { it in config.blackGroups }

  private fun pushServer(
    sender: MessageSender,
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

  /** 查询并安排各服务器的活动结束预警 */
  private fun scheduleAlerts() {
    val config = notifyConfig
    val servers = mutableListOf<ServerLocale>()
    if (config.jp) servers += ServerLocale.JP
    if (config.global) servers += ServerLocale.GLOBAL
    if (config.cn) servers += ServerLocale.CN
    servers.forEach { server ->
      val active = runCatching {
        when (server) {
          ServerLocale.JP -> ActivityUtil.fetchJPActivity()
          ServerLocale.GLOBAL -> ActivityUtil.fetchENActivity()
          ServerLocale.CN -> ActivityUtil.fetchCNActivity()
        }
      }.onFailure { RuntimeLog.warning("拉取${server.serverName}活动失败: ${it.message}") }.getOrNull()?.first
      if (active != null) {
        alertPlan(active).forEach { (activities, hours) -> scheduleAlertGroup(activities, server, hours) }
      }
    }
  }

  /**
   * 计算某服务器活动的预警安排(供调度与测试):
   * 除生日外的所有活动在结束前 5 小时与 1 小时各提醒一次; 维护单列只在 1 小时提醒(特殊文案)。
   */
  internal fun alertPlan(active: List<Activity>): List<Pair<List<Activity>, Int>> {
    val alertable = active.filterNot { it.type == ActivityType.BIRTHDAY } // 生日不预警
    val maintenance = alertable.filter { isMaintenanceActivity(it) }
    val normal = alertable.filterNot { isMaintenanceActivity(it) }
    return listOf(
      normal to DropActivityNotifyBeforeHours,
      normal to NormalActivityNotifyBeforeHours,
      maintenance to NormalActivityNotifyBeforeHours,
    )
  }

  /** 按提醒时间分组处理: 正负10分钟内立即发送, 过时抛弃, 未来添加定时任务(去重) */
  internal fun scheduleAlertGroup(
    activities: List<Activity>,
    locale: ServerLocale,
    beforeHours: Int,
  ) {
    if (activities.isEmpty()) return
    val now = System.currentTimeMillis()
    val window = AlertImmediateWindowMillis
    activities
      .filter { it.endTime > 0 }
      .filterNot { it.type == ActivityType.BIRTHDAY } // 生日不预警
      .groupBy { it.endTime - beforeHours * 60L * 60L * 1000L }
      .forEach { (notifyAt, group) ->
        when {
          notifyAt < now - window -> Unit // 已过期, 抛弃
          notifyAt <= now + window -> sendAlert(group, locale, beforeHours) // 正负10分钟内, 立即发送
          else -> insertAlert(group, Date(notifyAt), locale, beforeHours) // 未来, 添加定时任务
        }
      }
  }

  /** 创建单次定时任务用于活动结束预警; 已存在同 key 任务则跳过, 避免重复 */
  private fun insertAlert(activity: List<Activity>, expected: Date, locale: ServerLocale, beforeHours: Int) {
    val key = "${OneHourJobKey}-${locale.commandName}-${expected.time}-$beforeHours"
    if (QuartzProvider.checkTaskExists(key, OneHourJobKey)) return
    QuartzProvider.createSingleTask(
      ActivityNotifyOneHourJob::class.java,
      expected,
      key,
      OneHourJobKey,
      mapOf(ActivityKey to activity, ActivityNotifyBeforeHoursKey to beforeHours)
    )
  }

  /** 发送活动结束预警(定时任务与立即发送共用), 发送目标取当前配置 */
  private fun sendAlert(activity: List<Activity>, locale: ServerLocale, beforeHours: Int) {
    val sender = RuntimeServices.messageSender ?: return
    val targets = resolveTargets(notifyConfig, RuntimeConfig.groups)
    if (targets.isEmpty()) return
    val ac = activity.toMutableList()
    // 提取维护活动信息
    val maintenance: Activity? = ac
      .filter { isMaintenanceActivity(it) }
      .let {
        if (it.isNotEmpty()) {
          ac.removeAll(it)
          return@let it[0]
        } else {
          return@let null
        }
      }
    val serverName = locale.serverName
    if (maintenance != null) {
      sendToTargets(sender, "距离${serverName}维护还有${beforeHours}小时", targets)
    }
    if (ac.isEmpty()) return
    val activityString = ac
      .map { at -> "${at.content}\n" }
      .reduceOrNull { prv, cur -> prv + cur }
    sendToTargets(
      sender,
      "${notifyConfig.notifyText}(${serverName})\n" +
        "$activityString" +
        "将会在${beforeHours}小时后结束",
      targets
    )
  }

  private fun sendToTargets(sender: MessageSender, text: String, targets: List<Long>) {
    val message = OutgoingMessage.text(text)
    targets.forEach { groupId ->
      runBlocking {
        runCatching { sender.send(MessageTarget.Group(groupId), message) }
          .onFailure { RuntimeLog.warning("预警发送到群 $groupId 失败: ${it.message}") }
      }
    }
  }

  private fun isMaintenanceActivity(activity: Activity): Boolean = activity.type == ActivityType.MAINTENANCE

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
    // 启动后查询一次 5小时/1小时 预警(仅调度/即时发送, 不推送日历图)
    QuartzProvider.createSimpleDelayJob(20) {
      QuartzProvider.triggerTaskWithData(jobKey!!, mapOf(ActivityNotifyInitKey to true))
    }
    RuntimeLog.info("活动推送已启用, 每天 $hour 点推送")
  }
}
