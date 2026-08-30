package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.quartz.QuartzProvider
import net.diyigemt.arona.runtime.OutgoingMessage

/**
 * 定时任务可视化: /任务 列出全部 Quartz 任务; /任务 触发 <任务名> 手动触发。
 * 用于排查活动推送、数据同步等定时任务是否正常调度。
 */
object StandaloneTaskCommand {
  fun handle(arguments: List<String>): OutgoingMessage = when (arguments.firstOrNull()?.lowercase()) {
    null, "list", "列表" -> list()
    "trigger", "触发" -> trigger(arguments.getOrNull(1))
    else -> usage()
  }

  private fun usage(): OutgoingMessage = OutgoingMessage.text(
    "用法: /任务 查看全部定时任务; /任务 触发 <任务名> 手动触发"
  )

  private fun list(): OutgoingMessage {
    val tasks = QuartzProvider.listTasks()
    if (tasks.isEmpty()) return OutgoingMessage.text("当前没有定时任务")
    val text = tasks.joinToString("\n", prefix = "定时任务(${tasks.size}):\n") { task ->
      buildString {
        append("· ${task.jobKey} [${task.group}]")
        if (task.state != null) append(" ${task.state}")
        if (task.nextFireTime != null) append(" 下次:${task.nextFireTime}")
        if (task.previousFireTime != null) append(" 上次:${task.previousFireTime}")
      }
    }
    return OutgoingMessage.text(text)
  }

  private fun trigger(name: String?): OutgoingMessage {
    if (name.isNullOrBlank()) return OutgoingMessage.text("用法: /任务 触发 <任务名>")
    val error = QuartzProvider.triggerTaskByName(name.trim())
    return if (error == null) OutgoingMessage.text("已触发任务: $name")
    else OutgoingMessage.text(error)
  }
}
