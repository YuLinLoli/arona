/**
 * 文件说明：本文件属于 Mirai 事件监听与消息处理器。
 * 具体职责：围绕 NudgeEventHandler 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.handler

import net.diyigemt.arona.Arona.sendTeacherNameMessage
import net.diyigemt.arona.config.AronaNudgeConfig
import net.diyigemt.arona.service.AronaGroupService
import net.diyigemt.arona.service.AronaService
import net.diyigemt.arona.service.AronaServiceManager
import net.diyigemt.arona.util.MessageUtil
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.event.events.NudgeEvent

// 中文说明：定义 NudgeEventHandler 对象，集中提供本文件的共享功能。
object NudgeEventHandler: AronaGroupService {

  suspend fun preHandle(event: NudgeEvent) {
    if (AronaServiceManager.checkService(this, event.from as User, event.subject) == null) {
      handle(event)
    }
  }

  private suspend fun handle(event: NudgeEvent) {
    val messageList = AronaNudgeConfig.messageList
    val target = event.target
    val from = event.from
    val subject = event.subject
    val bot = event.bot
    if (target.id != bot.id || target.id == from.id || messageList.isEmpty()) return
    if (subject !is Group) return
    val total = messageList.sumOf { it.weight }
    var index = (0 until total).random()
    for (msg in messageList) {
      if (index < msg.weight) {
        subject.sendTeacherNameMessage(from, MessageUtil.atAndCTRL(from as User, msg.message))
        return
      } else {
        index -= msg.weight
      }
    }
  }

  override val id: Int = 10
  override val name: String = "摸头回复"
  override var enable: Boolean = true

  override fun init() {
    registerService()
  }

}
