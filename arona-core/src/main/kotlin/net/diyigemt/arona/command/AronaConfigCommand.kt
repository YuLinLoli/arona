/**
 * 文件说明：本文件属于 聊天命令及命令参数处理逻辑。
 * 具体职责：围绕 AronaConfigCommand 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.command

import net.diyigemt.arona.Arona
import net.diyigemt.arona.service.AronaManageService
import net.diyigemt.arona.service.AronaServiceManager
import net.diyigemt.arona.config.AronaConfig
import net.diyigemt.arona.runtime.ConfigValueParser
import net.mamoe.mirai.console.data.Value
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.message.data.ForwardMessageBuilder
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.contact.Contact

// 中文说明：定义 AronaConfigCommand 对象，集中提供本文件的共享功能。
@OptIn(ConsoleExperimentalApi::class)
object AronaConfigCommand: CompositeCommand(
  Arona,
  "config",
  "配置",
  description = "配置arona运行状态"
), AronaManageService {
  @SubCommand
  @Description("查看arona全部配置")
  suspend fun UserCommandSender.configDefault() {
    val bot = Arona.arona
    if (bot == null) {
      sendMessage("机器人尚未上线，无法转发配置")
      return
    }
    val builder = ForwardMessageBuilder(subject)
    builder.add(bot, PlainText(buildString {
      appendLine("Arona 全部配置")
      appendLine("修改: /config <配置名> <值>")
    }))
    AronaConfig.valueNodes.forEach { node ->
      val value = node.value.value
      val desc = node.annotations.filterIsInstance<ValueDescription>().firstOrNull()?.value.orEmpty()
      builder.add(bot, PlainText(buildString {
        appendLine("【${node.valueName}】$value")
        if (desc.isNotBlank()) {
          appendLine("说明: $desc")
        }
      }))
    }
    sendMessage(builder.build())
  }

  @SubCommand
  @Description("查看单个配置: /config <配置名>")
  suspend fun UserCommandSender.configShow(name: String) {
    val node = AronaConfig.valueNodes.firstOrNull { it.valueName.equals(name.trim(), ignoreCase = true) }
    if (node == null) {
      sendMessage("未找到配置项: $name")
      return
    }
    val value = node.value.value
    val desc = node.annotations.filterIsInstance<ValueDescription>().firstOrNull()?.value.orEmpty()
    sendMessage(buildString {
      appendLine("【${node.valueName}】$value")
      if (desc.isNotBlank()) {
        appendLine("说明: $desc")
      }
    })
  }

  @SubCommand
  @Description("修改配置: /config <配置名> <值>")
  @Suppress("UNCHECKED_CAST")
  suspend fun UserCommandSender.configSet(name: String, value: String) {
    val node = AronaConfig.valueNodes.firstOrNull { it.valueName.equals(name.trim(), ignoreCase = true) }
    if (node == null) {
      sendMessage("未找到配置项: $name")
      return
    }
    val parsed = runCatching { ConfigValueParser.parse(value.trim(), node.value.value) }
      .getOrElse {
        sendMessage("配置值解析失败: ${it.message}")
        return
      }
    (node.value as Value<Any?>).value = parsed
    // AutoSavePluginConfig 自动保存是防抖延迟的，先立即落盘再重载，避免 reload 读回旧值
    Arona.run { AronaConfig.save() }
    Arona.reloadAronaConfig()
    sendMessage("配置已更新: ${node.valueName} = ${node.value.value}")
  }

  @SubCommand("启用")
  @Description("启用一个功能模块")
  suspend fun UserCommandSender.enableService(idOrName: String) {
    val service = AronaServiceManager.enable(idOrName)
    if (service != null) {
      sendMessage("功能${service.name}启用成功")
    } else {
      sendMessage("服务未找到")
    }
  }

  @SubCommand("停用")
  @Description("停用一个功能模块")
  suspend fun UserCommandSender.disableService(idOrName: String) {
    val service = AronaServiceManager.disable(idOrName)
    if (service != null) {
      sendMessage("功能${service.name}停用成功")
    } else {
      sendMessage("服务未找到")
    }
  }

  @SubCommand("状态")
  @Description("查看功能模块的状态")
  suspend fun UserCommandSender.statusService(idOrName: String) {
    val service = AronaServiceManager.findServiceByName(idOrName)
    if (service != null) {
      sendMessage("${service.name} ${if(service.enable) "启用中" else "已停用"}")
    } else {
      sendMessage("服务未找到")
    }
  }

  @SubCommand("状态")
  @Description("全部功能模块的状态")
  suspend fun UserCommandSender.statusDefault() {
    val service = AronaServiceManager.getAllService().joinToString("\n") {
      "${it.name} ${if (it.enable) "启用中" else "已停用"}"
    }
    sendMessage(service)
  }

  private suspend fun sendAllServiceStatus(contact: Contact) {
    val msg = AronaServiceManager.getAllService().map {
      "${it.name}  ${if (it.enable) "启用" else "关闭"}"
    }.reduce {
      prv, cur -> "$prv\n$cur"
    }
    contact.sendMessage(msg)
  }

  override val id: Int = 0
  override val name: String = "配置"
  override var enable: Boolean = true
  override fun init() {
    registerService()
    register()
  }

}
