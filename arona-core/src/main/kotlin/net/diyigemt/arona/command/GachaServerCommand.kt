/**
 * 文件说明：本文件属于 聊天命令及命令参数处理逻辑。
 * 具体职责：围绕 GachaServerCommand 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.command

import net.diyigemt.arona.Arona
import net.diyigemt.arona.gacha.GachaV2Service
import net.diyigemt.arona.service.AronaGroupService
import net.diyigemt.arona.util.GeneralUtils
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand

// 中文说明：定义 GachaServerCommand 对象，集中提供本文件的共享功能。
object GachaServerCommand : SimpleCommand(
  Arona, "gacha_server", "抽卡服务器",
  description = "查看/设置默认抽卡服务器: /抽卡服务器 日服|国服|国际服"
), AronaGroupService {

  @Handler
  suspend fun MemberCommandSenderOnMessage.gachaServer(server: String?) {
    if (!GeneralUtils.checkService(subject)) return
    if (server.isNullOrBlank()) {
      subject.sendMessage(
        "当前默认抽卡服务器: ${GachaV2Service.getUserServer(user.id).serverName}\n" +
          "用法: /抽卡服务器 日服|国服|国际服"
      )
      return
    }
    val target = GachaV2Service.resolveServer(server)
    if (target == null) {
      subject.sendMessage("未知服务器: $server, 可用: 日服/国服/国际服")
      return
    }
    GachaV2Service.setUserServer(user.id, target)
    subject.sendMessage("抽卡服务器已设置为 ${target.serverName}")
  }

  override val id: Int = 24
  override val name: String = "抽卡服务器设置"
  override var enable: Boolean = true
  override fun init() {
    registerService()
    register()
  }
}