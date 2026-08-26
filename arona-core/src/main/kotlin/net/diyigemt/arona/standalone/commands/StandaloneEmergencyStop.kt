package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.service.AronaServiceManager
import java.util.Calendar
import java.util.Date

class StandaloneEmergencyStop(
  private val times: Int = 5,
  private val durationMinutes: Int = 5,
) {
  private var start: Date = Calendar.getInstance().time
  private val votes = mutableSetOf<Long>()

  fun vote(context: CommandContext): OutgoingMessage? {
    val now = Calendar.getInstance().time
    val userId = context.userId
    val minutes = (now.time - start.time) / 1000 / 60
    if (minutes >= durationMinutes) {
      start = now
      votes.clear()
    }
    votes.add(userId)
    if (votes.size >= times) {
      StandaloneServices.all.forEach { AronaServiceManager.disable(it.name) }
      return OutgoingMessage.text("达到目标票数,紧急停止")
    }
    return OutgoingMessage.text("当前:${votes.size}/${times}票, 剩余时间: ${durationMinutes - minutes}分钟")
  }
}
