/**
 * 文件说明：本文件属于 Quartz 定时任务注册与调度。
 * 具体职责：围绕 QuartzProvider 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.quartz

import kotlinx.coroutines.Dispatchers
import net.diyigemt.arona.Arona
import net.diyigemt.arona.interfaces.BaseFunctionProvider
import net.mamoe.mirai.console.util.safeCast
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.matchers.GroupMatcher
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

// 中文说明：定义 QuartzProvider 对象，集中提供本文件的共享功能。
object QuartzProvider: BaseFunctionProvider(Dispatchers.IO) {

  private val quartzScheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler().also { it.start() }
  private const val SimpleDelayJobKey: String = "SimpleDelayJobKey"
  private const val SimpleDelayJobData: String = "SimpleDelayJobData"
  override val tag: String = "quartz"

  fun createSingleTask(jobClass: Class<out Job>, expected: Date, jobKey: String, jobGroup: String, jogData: Map<String, Any>? = mapOf()): Pair<JobKey, TriggerKey> {
    val jobKeys = JobKey.jobKey("${jobKey}Job", jobGroup)
    val triggerKey = TriggerKey.triggerKey("${jobKey}Trigger", jobGroup)
    val job = JobBuilder
      .newJob(jobClass)
      .withIdentity(jobKeys)
      .setJobData(JobDataMap(jogData))
      .build()
    val trigger = TriggerBuilder
      .newTrigger()
      .withIdentity(triggerKey)
      .withSchedule(
        SimpleScheduleBuilder
          .simpleSchedule()
          .withRepeatCount(0)
          .withMisfireHandlingInstructionFireNow()
          .withIntervalInSeconds(1)
      )
      .startAt(expected)
      .build()
    quartzScheduler.scheduleJob(job, trigger)
    return jobKeys to triggerKey
  }

  fun createRepeatSingleTask(jobClass: Class<out Job>, interval: Int, jobKey: String, jobGroup: String, jogData: Map<String, Any>? = mapOf()): Pair<JobKey, TriggerKey> {
    val jobKeys = JobKey.jobKey("${jobKey}Job", jobGroup)
    val triggerKey = TriggerKey.triggerKey("${jobKey}Trigger", jobGroup)
    val now = Calendar.getInstance()
    now.get(Calendar.MINUTE).also {
      if (it > 30) {
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY) + 1)
      } else {
        now.set(Calendar.MINUTE, 30)
      }
    }
    now.set(Calendar.SECOND, 0)
    now.set(Calendar.MILLISECOND, 0)
    val job = JobBuilder
      .newJob(jobClass)
      .withIdentity(jobKeys)
      .setJobData(JobDataMap(jogData))
      .build()
    val trigger = TriggerBuilder
      .newTrigger()
      .withIdentity(triggerKey)
      .withSchedule(
        SimpleScheduleBuilder
          .simpleSchedule()
          .repeatForever()
          .withMisfireHandlingInstructionFireNow()
          .withIntervalInMinutes(interval)
      )
      .startAt(now.time)
      .build()
    quartzScheduler.scheduleJob(job, trigger)
    return jobKeys to triggerKey
  }

  fun createCronTask(jobClass: Class<out Job>, expected: String, jobKey: String, jobGroup: String, jogData: Map<String, Any> = mapOf()): Pair<JobKey, TriggerKey> {
    val jobKeys = JobKey.jobKey("${jobKey}Job", jobGroup)
    val triggerKey = TriggerKey.triggerKey("${jobKey}Trigger", jobGroup)
    val job = JobBuilder
      .newJob(jobClass)
      .withIdentity(jobKeys)
      .setJobData(JobDataMap(jogData))
      .build()
    val trigger = TriggerBuilder
      .newTrigger()
      .withIdentity(triggerKey)
      .withSchedule(
        CronScheduleBuilder.cronSchedule(expected)
      )
      .startNow()
      .build()
    quartzScheduler.scheduleJob(job, trigger)
    return jobKeys to triggerKey
  }

  fun createSimpleDelayJob(delay: Int, block: () -> Unit): Pair<JobKey, TriggerKey> {
    val now = Calendar.getInstance()
    now.set(Calendar.SECOND, now.get(Calendar.SECOND) + delay)
    return createSingleTask(
      SimpleDelayJob::class.java,
      now.time,
      "$SimpleDelayJobKey${UUID.randomUUID()}",
      SimpleDelayJobKey,
      mapOf(
        SimpleDelayJobData to block
      )
    )
  }

  @Suppress("UNCHECKED_CAST")
  class SimpleDelayJob: Job {
    override fun execute(context: JobExecutionContext?) {
      val block = context?.mergedJobDataMap?.get(SimpleDelayJobData) ?: return
      (block as () -> Unit)()
    }
  }

  fun triggerTaskWithData(jobKey: JobKey, data: Map<String, Any>) = quartzScheduler.triggerJob(jobKey, JobDataMap(data))

  fun triggerTaskWithData(jobKey: String, group: String, data: Map<String, Any>) = quartzScheduler.triggerJob(JobKey.jobKey("${jobKey}Job", group), JobDataMap(data))

  fun triggerTask(jobKey: String, group: String) = quartzScheduler.triggerJob(JobKey.jobKey("${jobKey}Job", group))

  fun triggerTask(jobKey: JobKey) = quartzScheduler.triggerJob(jobKey)

  fun deleteTask(jobKey: String, group: String): Boolean = deleteTask(JobKey.jobKey("${jobKey}Job", group))

  fun interruptTask(jobKey: String, group: String): Boolean = interruptTask(JobKey.jobKey("${jobKey}Job", group))

  fun pauseTask(jobKey: String, group: String) = pauseTask(JobKey.jobKey("${jobKey}Job", group))

  fun resumeTask(jobKey: String, group: String) = resumeTask(JobKey.jobKey("${jobKey}Job", group))

  fun deleteTask(jobKey: JobKey): Boolean = quartzScheduler.deleteJob(jobKey)

  fun interruptTask(jobKey: JobKey): Boolean = quartzScheduler.interrupt(jobKey)

  fun pauseTask(jobKey: JobKey) = quartzScheduler.pauseJob(jobKey)

  fun resumeTask(jobKey: JobKey) = quartzScheduler.resumeJob(jobKey)

  fun pauseAll() = quartzScheduler.pauseAll()

  fun resumeAll() = quartzScheduler.resumeAll()

  /** 列出全部 Quartz 任务及其触发器状态, 供 /任务 指令可视化排查 */
  fun listTasks(): List<QuartzTaskInfo> {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return quartzScheduler.jobGroupNames.flatMap { group ->
      quartzScheduler.getJobKeys(GroupMatcher.groupEquals(group)).mapNotNull { jobKey ->
        val trigger = quartzScheduler.getTriggersOfJob(jobKey).firstOrNull()
        QuartzTaskInfo(
          jobKey = jobKey.name,
          group = group,
          triggerKey = trigger?.key?.name,
          state = trigger?.let { quartzScheduler.getTriggerState(it.key).name },
          nextFireTime = trigger?.nextFireTime?.let(format::format),
          previousFireTime = trigger?.previousFireTime?.let(format::format),
        )
      }
    }
  }

  /** 触发任务: 支持完整 job 名或去掉 Job 后缀的基础名; 未找到时返回错误信息 */
  fun triggerTaskByName(name: String): String? {
    val candidates = listOf(name, name.removeSuffix("Job") + "Job")
    for (candidate in candidates) {
      for (group in quartzScheduler.jobGroupNames) {
        val key = JobKey.jobKey(candidate, group)
        if (quartzScheduler.checkExists(key)) {
          quartzScheduler.triggerJob(key)
          return null
        }
      }
    }
    return "未找到任务: $name"
  }

  override suspend fun main() {}

  override fun disable() {
    quartzScheduler.shutdown()
  }

}

/** Quartz 任务快照, 用于 /任务 指令展示 */
data class QuartzTaskInfo(
  val jobKey: String,
  val group: String,
  val triggerKey: String?,
  val state: String?,
  val nextFireTime: String?,
  val previousFireTime: String?,
)
