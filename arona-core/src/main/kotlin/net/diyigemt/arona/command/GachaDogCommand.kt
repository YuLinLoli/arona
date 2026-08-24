/**
 * 文件说明：本文件属于 聊天命令及命令参数处理逻辑。
 * 具体职责：围绕 GachaDogCommand 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.command

import net.diyigemt.arona.Arona
import net.diyigemt.arona.service.AronaGroupService
import net.diyigemt.arona.util.GachaUtil
import net.diyigemt.arona.util.GeneralUtils
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick

// 中文说明：定义 GachaDogCommand 对象，集中提供本文件的共享功能。
object GachaDogCommand : SimpleCommand(
  Arona,"gacha_dog", "狗叫",
  description = "查看抽出pick的人"
), AronaGroupService {

  @Handler
  suspend fun MemberCommandSenderOnMessage.gachaDog() {
    val dogCall = GachaUtil.getDogCall(subject.id).filter { it.dog != 0 }
    if (dogCall.isEmpty()) {
      subject.sendMessage("还没有老师抽出来哦")
      return
    }
    var ss = "狗叫排行:\n"
    dogCall.map {
      val teacherName = GeneralUtils.queryTeacherNameFromDB(subject, subject[it.id.value]!!)
      "${teacherName}(${it.id.value}): ${it.dog}抽"
    }.forEachIndexed {
      index, s -> ss += "${index + 1}. $s\n"
    }
    subject.sendMessage(ss.subSequence(0, ss.length - 1).toString())
  }

  override val id: Int = 6
  override val name: String = "抽卡狗叫查询"
  override var enable: Boolean = true
  override fun init() {
    registerService()
    register()
  }

}
