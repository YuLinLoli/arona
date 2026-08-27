package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.ForwardMessage
import net.diyigemt.arona.runtime.MessageSegment
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.standalone.StandaloneAronaConfig

/**
 * 独立模式的 /config 指令:
 * /config             查看全部配置(合并转发)
 * /config <配置名>     查看单个配置
 * /config <配置名> <值> 修改配置并热重载
 */
object StandaloneConfigCommand {

  private const val USAGE =
    "用法: /config 查看全部配置; /config <配置名> 查看单个; /config <配置名> <值> 修改配置"

  fun handle(context: CommandContext, arguments: List<String>): OutgoingMessage = when {
    arguments.isEmpty() -> viewAll()
    arguments.size == 1 -> viewOne(arguments[0])
    else -> update(arguments[0], arguments.drop(1).joinToString(" "))
  }

  private fun viewAll(): OutgoingMessage {
    val config = StandaloneAronaConfig.config
    val nodes = mutableListOf<ForwardMessage>().apply {
      add(headerNode())
      StandaloneAronaConfig.fields.forEach { field ->
        add(
          ForwardMessage(
            name = "Arona",
            uin = RuntimeConfig.botId,
            content = listOf(MessageSegment.Text(fieldLine(field.key, field.get(config), field.description))),
          )
        )
      }
    }
    return OutgoingMessage.forward("Arona 全部配置", nodes)
  }

  private fun viewOne(key: String): OutgoingMessage {
    val field = StandaloneAronaConfig.findField(key)
      ?: return OutgoingMessage.text("未找到配置项: $key\n$USAGE")
    return OutgoingMessage.text(fieldLine(field.key, field.get(StandaloneAronaConfig.config), field.description))
  }

  private fun update(key: String, value: String): OutgoingMessage =
    OutgoingMessage.text(StandaloneAronaConfig.update(key, value))

  private fun headerNode(): ForwardMessage = ForwardMessage(
    name = "Arona",
    uin = RuntimeConfig.botId,
    content = listOf(
      MessageSegment.Text(
        "Arona 全部配置\n配置文件: arona-standalone/arona.yml (修改保存后自动热重载)\n$USAGE"
      )
    ),
  )

  private fun fieldLine(key: String, value: Any?, description: String): String = buildString {
    appendLine("【$key】$value")
    appendLine("说明: $description")
  }
}
