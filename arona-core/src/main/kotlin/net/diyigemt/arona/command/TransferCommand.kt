/**
 * 文件说明：本文件属于 聊天命令及命令参数处理逻辑。
 * 具体职责：围绕 TransferCommand 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.command

import net.diyigemt.arona.Arona
import net.diyigemt.arona.service.AronaService
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.ForwardMessageBuilder

// 中文说明：定义 TransferCommand 对象，集中提供本文件的共享功能。
object TransferCommand : SimpleCommand(
  Arona,"transfer", "转发",
  description = "合并转发一段时间内的内容"
), AronaService {

  @Handler
  suspend fun UserCommandSender.transferMessageBuilder() {
    Arona.warning("!@3")
    val builder = ForwardMessageBuilder(subject)
    GlobalEventChannel.subscribeOnce<MessageEvent>() {
      val message = builder.add(it).build()
      subject.sendMessage(message)
    }
  }

  override val id: Int = 15
  override val name: String = "合并转发"
  override var enable: Boolean = true
  override fun init() {
    registerService()
    register()
  }

}
