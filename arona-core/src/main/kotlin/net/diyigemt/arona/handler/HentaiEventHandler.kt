/**
 * 文件说明：本文件属于 Mirai 事件监听与消息处理器。
 * 具体职责：围绕 HentaiEventHandler 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.handler

import net.diyigemt.arona.Arona.sendTeacherNameMessage
import net.diyigemt.arona.config.AronaHentaiConfig
import net.diyigemt.arona.service.AronaGroupService
import net.diyigemt.arona.util.MessageUtil
import net.mamoe.mirai.contact.UserOrBot
import net.mamoe.mirai.event.events.GroupMessageEvent

// 中文说明：定义 HentaiEventHandler 对象，集中提供本文件的共享功能。
object HentaiEventHandler: AronaEventHandler<GroupMessageEvent>, AronaGroupService {

  override suspend fun handle(event: GroupMessageEvent) {
    val source = event.message.contentToString()
    if (!(source.contains("老婆") || source.contains("老公"))) return
    val messageList = AronaHentaiConfig.messageList
    val listen = AronaHentaiConfig.listen
    val target = event.sender
    val subject = event.subject
    if (!listen.contains(target.id)) return
    val total = messageList.sumOf { it.weight }
    var index = (0 until total).random()
    for (msg in messageList) {
      if (index < msg.weight) {
        subject.sendTeacherNameMessage(target as UserOrBot, MessageUtil.at(target, msg.message))
        return
      } else {
        index -= msg.weight
      }
    }
  }

  override val id: Int = 9
  override val name: String = "发情"
  override var enable: Boolean = true

  override fun init() {
    registerService()
  }
}
