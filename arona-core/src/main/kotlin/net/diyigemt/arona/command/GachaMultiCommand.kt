/**
 * 文件说明：本文件属于 聊天命令及命令参数处理逻辑。
 * 具体职责：围绕 GachaMultiCommand 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.command

import net.diyigemt.arona.Arona
import net.diyigemt.arona.config.AronaGachaConfig
import net.diyigemt.arona.gacha.GachaV2ImageRenderer
import net.diyigemt.arona.gacha.GachaV2Service
import net.diyigemt.arona.service.AronaGroupService
import net.diyigemt.arona.util.GeneralUtils
import net.diyigemt.arona.util.GeneralUtils.queryTeacherNameFromDB
import net.diyigemt.arona.util.MessageUtil
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.contact.Contact.Companion.uploadImage

// 中文说明：定义 GachaMultiCommand 对象，集中提供本文件的共享功能。
object GachaMultiCommand : SimpleCommand(
  Arona, "gacha_multi", "十连",
  description = "模拟十连, 可指定服务器: /十连 日服|国服|国际服"
), AronaGroupService {

  @Handler
  suspend fun MemberCommandSenderOnMessage.gachaMulti(server: String?) {
    if (!GeneralUtils.checkService(subject)) return
    val userId = user.id
    val groupId = subject.id
    val teacherName = queryTeacherNameFromDB(subject, user)
    val targetServer = GachaV2Service.resolveDrawServer(userId, server)
    if (targetServer == null) {
      subject.sendMessage("未知服务器: $server, 可用: 日服/国服/国际服")
      return
    }
    val report = runCatching { GachaV2Service.performDraw(userId, groupId, 10, targetServer) }
      .getOrElse {
        subject.sendMessage(it.message ?: "抽卡失败, 请稍后再试")
        return
      }
    if (report == null) {
      subject.sendMessage(MessageUtil.at(user, "${teacherName},石头不够了哦,明天再来抽吧"))
      return
    }
    val imageFile = runCatching {
      GachaV2ImageRenderer.render(report.results, report.pityCount, GachaV2ImageRenderer.newResultFile())
    }.getOrElse {
      subject.sendMessage(it.message ?: "抽卡图片生成失败, 请稍后再试")
      return
    }
    val handler = subject.sendMessage(subject.uploadImage(imageFile, "png"))
    if (AronaGachaConfig.revokeTime > 0) {
      MessageUtil.recall(handler, AronaGachaConfig.revokeTime * 1000L)
    }
  }

  override val id: Int = 5
  override val name: String = "抽卡十连"
  override var enable: Boolean = true
  override fun init() {
    registerService()
    register()
  }
}
