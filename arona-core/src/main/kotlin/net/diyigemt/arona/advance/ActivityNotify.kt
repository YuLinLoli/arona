/**
 * 文件说明：活动通知与定时推送相关逻辑。
 */
package net.diyigemt.arona.advance

import net.diyigemt.arona.Arona
import net.diyigemt.arona.config.AronaNotifyConfig
import net.diyigemt.arona.entity.Activity
import net.diyigemt.arona.entity.ActivityType
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.quartz.QuartzProvider
import net.diyigemt.arona.service.AronaQuartzService
import net.diyigemt.arona.util.ActivityUtil
import net.diyigemt.arona.util.MessageUtil
import net.diyigemt.arona.util.TimeUtil.calcDiffDayAndHour
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.message.code.MiraiCode
import org.quartz.InterruptableJob
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import java.io.File
import java.util.*

// 中文说明：定义 ActivityNotify 对象，集中提供本文件的共享功能。
object ActivityNotify : AronaQuartzService {
  private const val ActivityNotifyJobKey = "ActivityNotify"
  private const val ActivityNotifyDataInitKey = "init"
  private const val ActivityNotifyOneHour = "ActivityNotifyOneHour"
  private const val ActivityKey = "activity"
  private const val ActivityNotifyBeforeHoursKey = "beforeHours"
  private const val NormalActivityNotifyBeforeHours = 1
  private const val DropActivityNotifyBeforeHours = 5
  /** 预警时间正负10分钟内视为立即发送窗口 */
  private const val AlertImmediateWindowMillis = 10 * 60 * 1000L
  override var jobKey: JobKey? = null

  class ActivityNotifyJob : Job {
    override fun execute(context: JobExecutionContext?) {
      val jp = runCatching { ActivityUtil.fetchJPActivity() }.getOrNull()
      val en = runCatching { ActivityUtil.fetchENActivity() }.getOrNull()
      val cn = runCatching { ActivityUtil.fetchCNActivity() }.getOrNull()
      // 5小时/1小时预警: 每次运行(启动初始化/每日定时)都查询; 正负10分钟内立即发送, 过时抛弃, 未来添加定时任务(去重)
      jp?.let { scheduleAlertsForServer(it.first, ServerLocale.JP) }
      en?.let { scheduleAlertsForServer(it.first, ServerLocale.GLOBAL) }
      cn?.let { scheduleAlertsForServer(it.first, ServerLocale.CN) }
      val filterJP = (jp?.first ?: emptyList()).filter { filterActive(it) } to (jp?.second ?: emptyList()).filter { filterPending(it) }
      val filterEN = (en?.first ?: emptyList()).filter { filterActive(it) } to (en?.second ?: emptyList()).filter { filterPending(it) }
      val filterCN = (cn?.first ?: emptyList()).filter { filterActive(it) } to (cn?.second ?: emptyList()).filter { filterPending(it) }
      // 初始化不显示信息
      val init = context?.mergedJobDataMap?.getBoolean(ActivityNotifyDataInitKey) ?: false
      if (init) return
      if (AronaNotifyConfig.enableEveryDay) {
        if (AronaNotifyConfig.enableJP) {
          val jpMessage = ActivityUtil.createActivityImage(filterJP)
          sendMessage(jpMessage, AronaNotifyConfig.notifyStringJP, AronaNotifyConfig.enableJPGroup)
        }
        if (AronaNotifyConfig.enableEN) {
          val enMessage = ActivityUtil.createActivityImage(filterEN, ServerLocale.GLOBAL)
          sendMessage(enMessage, AronaNotifyConfig.notifyStringEN, AronaNotifyConfig.enableENGroup)
        }
        if (AronaNotifyConfig.enableCN) {
          val enMessage = ActivityUtil.createActivityImage(filterCN, ServerLocale.CN)
          sendMessage(enMessage, AronaNotifyConfig.notifyStringCN, AronaNotifyConfig.enableCNGroup)
        }
      }
    }

    private fun sendMessage(imageFile: File, source: String, targetGroup: List<Long>) {
      Arona.sendFilterGroupMessageWithFile(targetGroup) { group ->
        val image = group.uploadImage(imageFile, "png")
        MessageUtil.deserializeMiraiCodeAndBuild(source, group) {
          it.add("\n")
          it.add(image)
          it.build()
        }
      }
    }

    private fun filterPending(activity: Activity): Boolean {
      val extra = calcDiffDayAndHour(activity.time)
      val d = extra.first
      val h = extra.second
      return doFilter(d, h)
    }

    private fun filterActive(activity: Activity): Boolean {
      val extra = calcDiffDayAndHour(activity.time)
      val d = extra.first
      val h = extra.second
      return doFilter(d, h)
    }

    private fun doFilter(d: Int, h: Int): Boolean = when (AronaNotifyConfig.notifyType) {
      NotifyType.ALL -> true
      NotifyType.ONLY_24H -> d * 24 + h < 24
      NotifyType.ONLY_48H -> d * 24 + h < 48
    }

  }


  @Suppress("UNCHECKED_CAST")
  class ActivityNotifyOneHourJob : InterruptableJob {
    /**
     * 活动结束预警任务
     *
     * 在活动结束前 5 小时与 1 小时向指定群组发送提醒消息(除生日外的活动两次都会提醒)。
     *
     * @property context 任务执行上下文，包含活动数据和通知配置信息
     */
    override fun execute(context: JobExecutionContext?) {
      val data = context?.mergedJobDataMap ?: return
      val ac = data.get(ActivityKey) ?: return
      ac as List<Activity>
      if (ac.isEmpty()) return
      val beforeHours = data.getInt(ActivityNotifyBeforeHoursKey).takeIf { it > 0 } ?: NormalActivityNotifyBeforeHours
      sendAlert(ac, ac[0].serverLocale, beforeHours)
    }

    override fun interrupt() {
      Arona.warning("interrupt")
    }
  }

  /** 为某个服务器的进行中活动安排结束预警: 除生日/维护外的活动在结束前5小时与1小时各提醒一次; 维护单独按1小时提醒 */
  private fun scheduleAlertsForServer(active: List<Activity>, locale: ServerLocale) {
    val maintenance = active.filter { isMaintenanceActivity(it) }
    val normal = active.filterNot { isMaintenanceActivity(it) }
    // 所有活动(掉落/总力战/大决战/卡池/普通活动等)结束前 5 小时与 1 小时各提醒一次
    scheduleAlertGroup(normal, locale, DropActivityNotifyBeforeHours)
    scheduleAlertGroup(normal, locale, NormalActivityNotifyBeforeHours)
    // 维护保持单条"还有1小时"文案, 避免 5 小时提前量语义不符
    scheduleAlertGroup(maintenance, locale, NormalActivityNotifyBeforeHours)
  }

  /** 按提醒时间分组处理: 正负10分钟内立即发送, 过时抛弃, 未来添加定时任务(去重) */
  private fun scheduleAlertGroup(activities: List<Activity>, locale: ServerLocale, beforeHours: Int) {
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
          else -> doInsert(group, Date(notifyAt), locale, beforeHours) // 未来, 添加定时任务
        }
      }
  }

  /** 创建单次定时任务用于活动结束预警; 已存在同 key 任务则跳过, 避免重复 */
  private fun doInsert(activity: List<Activity>, expected: Date, locale: ServerLocale, beforeHours: Int) {
    val key = "${ActivityNotifyOneHour}-${locale.commandName}-${expected.time}-$beforeHours"
    if (QuartzProvider.checkTaskExists(key, ActivityNotifyOneHour)) return
    QuartzProvider.createSingleTask(
      ActivityNotifyOneHourJob::class.java,
      expected,
      key,
      ActivityNotifyOneHour,
      mapOf(ActivityKey to activity, ActivityNotifyBeforeHoursKey to beforeHours)
    )
  }

  /** 发送活动结束预警(定时任务与立即发送共用) */
  private fun sendAlert(activity: List<Activity>, locale: ServerLocale, beforeHours: Int) {
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
    val targetGroup = when (locale) {
      ServerLocale.JP -> AronaNotifyConfig.enableJPGroup
      ServerLocale.GLOBAL -> AronaNotifyConfig.enableENGroup
      ServerLocale.CN -> AronaNotifyConfig.enableCNGroup
    }
    val serverName = locale.serverName
    if (maintenance != null) {
      Arona.sendFilterGroupMessage("距离${serverName}维护还有${beforeHours}小时", targetGroup)
    }
    if (ac.isEmpty()) return
    val notifyPrefix = when (locale) {
      ServerLocale.JP -> AronaNotifyConfig.notifyStringJP
      ServerLocale.GLOBAL -> AronaNotifyConfig.notifyStringEN
      ServerLocale.CN -> AronaNotifyConfig.notifyStringCN
    }
    val activityString = ac
      .map { at -> "${at.content}\n" }
      .reduceOrNull { prv, cur -> prv + cur }
    val serverString = MiraiCode.deserializeMiraiCode(notifyPrefix)
    Arona.sendFilterGroupMessage(
      "${serverString}\n" +
        "$activityString" +
        "将会在${beforeHours}小时后结束", targetGroup
    )
  }

  private fun isMaintenanceActivity(activity: Activity): Boolean = activity.type == ActivityType.MAINTENANCE

  override fun init() {
    registerService()
  }

  override fun enableService() {
    // 每天早上8点触发
    jobKey = QuartzProvider.createCronTask(
      ActivityNotifyJob::class.java,
      "0 0 ${AronaNotifyConfig.everyDayHour} * * ? *",
      ActivityNotifyJobKey,
      ActivityNotifyJobKey
    ).first
    QuartzProvider.createSimpleDelayJob(20) {
      QuartzProvider.triggerTaskWithData(jobKey!!, mapOf(ActivityNotifyDataInitKey to true))
    }
  }

  override val id: Int = 12
  override val name: String = "活动推送"
  override var enable: Boolean = true
}

// 每日防侠提醒类型
// 中文说明：定义 NotifyType 类型，用于封装本模块的数据或处理行为。
enum class NotifyType {
  ALL, ONLY_24H, ONLY_48H
}
